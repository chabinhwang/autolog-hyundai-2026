# AutoLog 설치 파일

- 제출 파일: [AutoLog_release.apk](AutoLog_release.apk)
- 앱 이름·버전: AutoLog 0.1.0 (`versionCode=1`)
- 패키지: `com.autolog.app` · 전용 키로 서명한 release · 디버그 모드 비활성
- 환경: Pleos Connect 전용 에뮬레이터 또는 호환 차량, Android API 28 이상
- API: `https://autolog-api.aihub.io.kr` · 자동 운행 종료: P 유지 180초

저장소 루트에서 다음 명령으로 설치합니다.

```sh
adb install "apk/AutoLog_release.apk"
```

앱에 표시된 6자리 코드를 관리자 대시보드의 차량 페어링에 입력합니다. 체험 계정과 상세 순서는 대회 제출 DOCX에서 안내합니다. [시연 영상](https://youtu.be/Z5fA_ZXDCBs) · [개발·서명 안내](../docs/contracts/development.md)

2026-11-04, 신규 설치·차량 연결·P 유지 180초 후 업무 운행 1.0km의 운영 서버 전송과 같은 키의 APK 업데이트 시 기록 보존을 확인했습니다. 상세 범위는 [검증 결과](../docs/contracts/validation.md)에 있습니다.

파일 SHA-256은 [SHA256SUMS](SHA256SUMS)에서 확인할 수 있습니다. [512px 앱 아이콘](app-icon-512.png)은 차량 앱·대시보드와 같은 도안입니다.
