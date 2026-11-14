# apk

- `REEVL_REEVLforPleos_release.apk` — 공모전 제출용 **release** APK (서명됨, v2+v3 서명 · ABI arm64-v8a·x86_64 · versionName 0.3.0)
- 서버: 실서비스 `https://reevl.co.kr` 차량 API. 첫 실행 → 동의 → 휴대폰 QR로 리블 계정 연결.
- 설치: `adb install REEVL_REEVLforPleos_release.apk` → `adb shell am start --user 10 -n kr.co.reevl.ontheway/.MainActivity`
