// Content-Disposition 헤더에서 RFC 5987 / RFC 6266의 filename* 값을 읽습니다.

function decodeLatin1(percentEncoded: string): string {
  return percentEncoded.replace(/%([0-9A-Fa-f]{2})/g, (_, hex: string) => String.fromCharCode(parseInt(hex, 16)))
}

/** 경로 구분자·제어 문자를 지워 저장할 수 있는 파일명으로 만듭니다. */
export function sanitizeFilename(name: string): string {
  // eslint-disable-next-line no-control-regex
  return name.replace(/[\\/\u0000-\u001f\u007f]/g, '_').trim()
}

/**
 * `filename*=UTF-8''%EC%9A%B4...xlsx` 형식만 해석합니다.
 * filename*이 없거나 해석할 수 없으면 null (호출하는 쪽에서 기본 파일명 사용)
 */
export function parseContentDispositionFilename(header: string | null | undefined): string | null {
  if (!header) return null
  const match = /(?:^|;)\s*filename\*\s*=\s*("?)([^;"]*)\1\s*(?:;|$)/i.exec(header)
  if (!match) return null
  const value = match[2].trim()
  const parts = /^([^']*)'[^']*'(.*)$/.exec(value)
  if (!parts) return null
  const charset = parts[1].toLowerCase()
  const encoded = parts[2]
  let decoded: string
  try {
    if (charset === 'utf-8') {
      decoded = decodeURIComponent(encoded)
    } else if (charset === 'iso-8859-1') {
      decoded = decodeLatin1(encoded)
    } else {
      return null
    }
  } catch {
    return null
  }
  const safe = sanitizeFilename(decoded)
  return safe || null
}

/** filename*이 없을 때 쓰는 기본 파일명 (rules §9) */
export function defaultLogbookFilename(regNo: string, from: string, to: string): string {
  return sanitizeFilename(`운행기록부_${regNo}_${from}_${to}.xlsx`)
}
