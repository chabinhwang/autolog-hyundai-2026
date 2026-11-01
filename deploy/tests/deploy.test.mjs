// Docker/SSH/운영 DB를 사용하지 않는 배포 스크립트의 행동 검증입니다.
// fake 명령은 호출 기록과 가짜 상태만 만듭니다. 실제 서버 자격 증명은 읽지 않습니다.
import assert from 'node:assert/strict'
import { test } from 'node:test'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const source = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../deploy.sh')
const oldImage = `ghcr.io/chabinhwang/autolog-api:${'a'.repeat(40)}`
const newImage = `ghcr.io/chabinhwang/autolog-api:${'b'.repeat(40)}`

const fakeCommand = String.raw`#!/usr/bin/env node
const fs = require('node:fs');
const path = require('node:path');
const tool = path.basename(process.argv[1]);
const args = process.argv.slice(2);
const scenario = process.env.TEST_SCENARIO;
const log = entry => fs.appendFileSync(process.env.TEST_CALLS, JSON.stringify(entry) + '\n');
const done = (status = 0, output = '') => { if (output) process.stdout.write(output); process.exit(status); };
log({ tool, args, image: process.env.AUTOLOG_API_IMAGE, dockerConfig: process.env.DOCKER_CONFIG });
if (tool === 'sleep') done();
if (tool === 'flock') done(scenario === 'lock-busy' ? 1 : 0);
if (tool === 'curl') done(scenario === 'public-fails' ? 22 : 0);
if (tool !== 'docker') done(2);
if (args[0] === '--config' && args.includes('logout')) done();
if (args[0] === 'login') {
  const token = fs.readFileSync(0, 'utf8');
  if (!token || !process.env.DOCKER_CONFIG || !process.env.DOCKER_CONFIG.includes('autolog-ghcr.')) done(1);
  fs.writeFileSync(path.join(process.env.DOCKER_CONFIG, 'config.json'), '{}');
  done();
}
if (args[0] === 'network') done(scenario === 'missing-network' ? 1 : 0);
if (args[0] === 'image') done();
if (args[0] === 'inspect') done(0, process.env.TEST_OLD_IMAGE + '\n');
if (args[0] !== 'compose') done(2);
const command = args.find(a => ['version', 'config', 'ps', 'pull', 'up', 'stop'].includes(a));
if (command === 'version') done(0, 'Docker Compose version v2.39.1\n');
if (command === 'up' && args.includes('--help')) done(0, '--wait --wait-timeout\n');
if (!args.includes('--project-name') || args[args.indexOf('--project-name') + 1] !== 'autolog') done(2);
if (command === 'ps') done(0, args.includes('--quiet') && process.env.TEST_HAS_OLD === '1' ? 'existing-api\n' : '');
if (command === 'config') done();
if (command === 'pull') done(scenario === 'pull-fails' && process.env.AUTOLOG_API_IMAGE === process.env.TEST_NEW_IMAGE ? 1 : 0);
if (command === 'up') {
  const fails = ['upgrade-fails', 'initial-fails'].includes(scenario) && process.env.AUTOLOG_API_IMAGE === process.env.TEST_NEW_IMAGE;
  done(fails ? 1 : 0);
}
if (command === 'stop') done();
done(2);
`

function fixture(t, scenario = 'success', existing = false) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'autolog-deploy-test-'))
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }))
  const work = path.join(directory, 'deploy')
  const bin = path.join(directory, 'bin')
  const temp = path.join(directory, 'tmp')
  for (const item of [work, bin, temp]) fs.mkdirSync(item)
  fs.copyFileSync(source, path.join(work, 'deploy.sh'))
  fs.writeFileSync(path.join(work, '.env'), 'DB_PASSWORD=fixture-password\nJWT_SECRET=fixture-jwt-key-only\n')
  fs.writeFileSync(path.join(work, 'compose.yaml'), 'name: autolog\n')
  if (existing) {
    fs.mkdirSync(path.join(work, '.state'))
    fs.writeFileSync(path.join(work, '.state/current-image'), oldImage + '\n')
  }
  for (const name of ['docker', 'curl', 'flock', 'sleep']) fs.writeFileSync(path.join(bin, name), fakeCommand, { mode: 0o755 })
  const callsFile = path.join(directory, 'calls.jsonl')
  const env = { ...process.env, PATH: `${bin}${path.delimiter}${process.env.PATH}`, TMPDIR: temp,
    TEST_SCENARIO: scenario, TEST_CALLS: callsFile, TEST_HAS_OLD: existing ? '1' : '0',
    TEST_OLD_IMAGE: oldImage, TEST_NEW_IMAGE: newImage, AUTOLOG_API_IMAGE: newImage,
    AUTOLOG_GHCR_USERNAME: '', AUTOLOG_ENV_FILE: '', AUTOLOG_HEALTH_URL: 'https://example.invalid/actuator/health', AUTOLOG_WAIT_TIMEOUT: '1' }
  const run = overrides => spawnSync('bash', [path.join(work, 'deploy.sh')], {
    cwd: work, env: { ...env, ...overrides }, encoding: 'utf8', input: 'fixture-registry-token', timeout: 20_000,
  })
  const calls = () => fs.existsSync(callsFile) ? fs.readFileSync(callsFile, 'utf8').trim().split('\n').filter(Boolean).map(JSON.parse) : []
  const state = file => fs.readFileSync(path.join(work, '.state', file), 'utf8').trim()
  return { directory, work, temp, calls, state, run }
}

test('successful upgrade records current/previous images and only operates on the autolog project', t => {
  const f = fixture(t, 'success', true)
  const result = f.run()
  assert.equal(result.status, 0, result.stderr)
  assert.equal(f.state('current-image'), newImage)
  assert.equal(f.state('previous-image'), oldImage)
  assert.match(f.state('history.tsv'), /SUCCESS/)
  const operations = f.calls().filter(c => c.tool === 'docker' && c.args[0] === 'compose' && !c.args.includes('--help') && !c.args.includes('version'))
  assert.ok(operations.length > 0)
  assert.ok(operations.every(c => c.args[c.args.indexOf('--project-name') + 1] === 'autolog'))
  assert.ok(!f.calls().some(c => c.args.includes('down') || c.args.includes('--volumes')))
})

test('failed upgrade restores the previous image and reports the deployment as failed', t => {
  const f = fixture(t, 'upgrade-fails', true)
  const result = f.run()
  assert.equal(result.status, 1)
  assert.equal(f.state('current-image'), oldImage)
  assert.match(f.state('history.tsv'), /FAILED/)
  const starts = f.calls().filter(c => c.tool === 'docker' && c.args.includes('up') && !c.args.includes('--help'))
  assert.deepEqual(starts.map(c => c.image), [newImage, oldImage])
})

test('public routing failure also rolls back a locally healthy upgrade', t => {
  const f = fixture(t, 'public-fails', true)
  assert.equal(f.run().status, 1)
  assert.equal(f.state('current-image'), oldImage)
  assert.ok(f.calls().some(c => c.args.includes('up') && c.image === oldImage))
})

test('first deployment failure stops only the API and keeps the database volume', t => {
  const f = fixture(t, 'initial-fails')
  assert.equal(f.run().status, 1)
  assert.ok(!fs.existsSync(path.join(f.work, '.state/current-image')))
  const stops = f.calls().filter(c => c.args.includes('stop'))
  assert.equal(stops.length, 1)
  assert.equal(stops[0].args.at(-1), 'autolog-api')
  assert.ok(!f.calls().some(c => c.args.includes('down') || c.args.includes('volume')))
})

test('image pull failure leaves the running version and state untouched', t => {
  const f = fixture(t, 'pull-fails', true)
  assert.equal(f.run().status, 1)
  assert.equal(f.state('current-image'), oldImage)
  assert.ok(f.calls().some(c => c.args.includes('pull') && c.args.at(-1) === 'autolog-api'))
  assert.ok(!f.calls().some(c => c.args.includes('up') && !c.args.includes('--help')))
})

test('registry credentials are isolated from other projects and removed after execution', t => {
  const f = fixture(t)
  const existingConfig = path.join(f.directory, 'existing-docker')
  fs.mkdirSync(existingConfig)
  fs.writeFileSync(path.join(existingConfig, 'config.json'), 'existing-registry-config')
  const result = f.run({ AUTOLOG_GHCR_USERNAME: 'fixture-user', DOCKER_CONFIG: existingConfig })
  assert.equal(result.status, 0, result.stderr)
  const login = f.calls().find(c => c.args[0] === 'login')
  assert.ok(login)
  assert.notEqual(login.dockerConfig, existingConfig)
  assert.ok(!fs.existsSync(login.dockerConfig))
  assert.equal(fs.readFileSync(path.join(existingConfig, 'config.json'), 'utf8'), 'existing-registry-config')
  for (const value of ['fixture-registry-token', 'fixture-password', 'fixture-jwt-key-only']) {
    assert.ok(!(result.stdout + result.stderr).includes(value))
  }
})

test('unsafe/incomplete deployment inputs fail before pulling or starting containers', async t => {
  for (const scenario of ['missing-env', 'missing-network', 'lock-busy', 'invalid-image']) {
    await t.test(scenario, st => {
      const f = fixture(st, scenario)
      if (scenario === 'missing-env') fs.unlinkSync(path.join(f.work, '.env'))
      const result = f.run(scenario === 'invalid-image' ? { AUTOLOG_API_IMAGE: 'ghcr.io/chabinhwang/autolog-api:latest' } : {})
      assert.equal(result.status, 1)
      const expected = { 'missing-env': '환경변수 파일이 없습니다', 'missing-network': 'shared-proxy가 없습니다',
        'lock-busy': '다른 AutoLog 배포가 실행 중', 'invalid-image': 'AUTOLOG_API_IMAGE는' }
      assert.ok(result.stderr.includes(expected[scenario]), result.stderr)
      assert.ok(!f.calls().some(c => c.args.includes('pull') || (c.args.includes('up') && !c.args.includes('--help'))))
    })
  }
})
