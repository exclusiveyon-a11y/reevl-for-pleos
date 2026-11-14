# REEVL for Pleos

**지나가는 동네가 궁금해질 때, 리블.**

Pleos Connect 차량용 앱입니다. 차량의 위치와 진행 방향으로 주변 아파트 단지 후보(왼쪽·오른쪽·앞)를 찾고, 실거래 근거를 보여 줍니다. 마음에 든 단지는 방문 계획에 넣고 Pleos 기본 내비게이션으로 안내합니다.
데이터는 운영 중인 부동산 데이터 서비스 [리블](https://reevl.co.kr)(전국 아파트 단지·국토교통부 실거래가 기반)에서 옵니다.

현대자동차그룹 차량용 앱 개발 공모전(Pleos App Market) 출품작입니다. 현대자동차그룹·42dot과의 공식 제휴를 뜻하지 않습니다.

| 주변 단지 좌우 후보 (정차 중) | NaviHelper로 기본 내비에 목적지 전달 |
|---|---|
| ![주변 단지 좌우 후보](docs/screenshots/nearby_left_right.png) | ![NaviHelper 안내](docs/screenshots/navihelper_handoff.png) |

> 화면은 Pleos Connect Emulator v2.1.0에서 찍었습니다. 단지·거래 값은 리블 실데이터, 차량 위치는 에뮬레이터 좌표입니다.

## 제출 정보 (현대자동차그룹 차량용 앱 개발 공모전)

| 항목 | 내용 |
|---|---|
| 앱 이름 | REEVL for Pleos — 지나가는 동네가 궁금해질 때, 리블 |
| release APK | [`apk/REEVL_REEVLforPleos_release.apk`](apk/REEVL_REEVLforPleos_release.apk) (서명된 release 빌드, arm64-v8a·x86_64) |
| 시연 영상 | **YouTube: https://youtu.be/c1amhZA4Kro** · 원본 [`demo/REEVL_for_Pleos_demo.mp4`](demo/REEVL_for_Pleos_demo.mp4) — 휴대폰 + Pleos Connect Emulator 2.1.0 실제 화면 녹화 |
| 개발·테스트 에뮬레이터 | **Pleos Connect Emulator v2.1.0** (Automotive, API 34, arm64 시스템 이미지), 화면 **2560×1440**(가로) — 분할 화면·전체 화면 모두 대응 |
| Pleos SDK | NaviHelper 3.0.5 · Gleo Interface(App Schema, `app/src/main/assets/schema.json`) |

## 실행 방법

1. Pleos Connect Emulator v2.1.0 AVD를 띄웁니다([설정 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)).
2. APK를 설치하고 실행합니다. AAOS 에뮬레이터의 사용자 ID는 10입니다.
   ```sh
   adb install apk/REEVL_REEVLforPleos_release.apk
   adb shell am start --user 10 -n kr.co.reevl.ontheway/.MainActivity
   ```
3. 첫 화면에서 안내(위치·계정 연결·보내는 곳)를 읽고 **동의하고 시작** → 화면의 QR을 휴대폰으로 찍어 [리블](https://reevl.co.kr) 계정으로 로그인 → 확인 숫자가 같으면 **연결**. (release 빌드는 실서비스 `https://reevl.co.kr`의 차량 API를 씁니다.)
4. 차량 위치는 에뮬레이터 Extended Controls → Location(또는 `adb emu geo fix <경도> <위도>`)으로 바꿉니다. 주행 제한은 Virtual Console의 기어·속도로 확인합니다.

### 필요한 설정

- 인터넷 연결(리블 서버·지도 타일). 별도 API 키는 없습니다. 차량 API는 위 계정 연결로 받은 차량 토큰을 씁니다.
- 권한: 위치(앱을 쓰는 동안), 카메라 없음.

## 지금 동작하는 것 (에뮬레이터 실측)

| 기능 | 상태 |
|---|---|
| 주변 단지 좌·우·앞 후보, 방금 지나온 단지 | 동작. `bearing(차→단지) − heading`을 ±35°/145° 기준으로 분류, 방향을 모르면 표시 안 함 |
| 리블 AI 자연어 조건 검색 ("8억 이하로 거래된 1천 세대 넘는 곳") | 동작. 조건 해석은 서버, 진위 판정은 DB |
| 단지 상세·비교·지금 동네 요약 | 동작 (정차 중) |
| 관심 단지 저장·웹 동기화, 휴대폰으로 넘기기(QR) | 동작 |
| 방문 계획 불러오기·현장에서 끼워 넣기·순서 정리·되돌리기, 경로 지도 | 동작 |
| Pleos **NaviHelper 3.0.5**로 목적지 전달 | 동작. 요청 좌표와 `getDestinationInfo()`가 1e-5 안에서 일치할 때만 성공 |
| 주행 제한 (`CarUxRestrictions`) | 동작. 주행 중 화면 전체 잠금, 음성(Gleo)만 |
| **Gleo Interface** 함수 3개 (`SEARCH_PROPERTIES`·`ANALYZE_PROPERTY`·`MANAGE_VISIT_PLAN`) | 구현. Gleo가 schema를 읽고, 앱이 `success/asking/fail` 콜백으로 6초 안에 답하며 Gleo가 수신하는 것까지 에뮬레이터 로그로 확인. 에뮬레이터에 음성 입력 경로가 없어 실제 발화 호출은 미확인 |
| 차량 연결 (QR·확인 숫자 → 차량 토큰) | 동작. 토큰은 AndroidKeyStore AES-GCM 암호화 저장 |
| 오프라인 | 캐시 표시 + `마지막 동기화 HH:MM` |

### Gleo 호출을 직접 시험하려면 (debug 빌드)

release 빌드의 수신기는 Gleo만 가진 서명 권한(`ai.umos.gleo.permission.APP_ACTION`)을 요구합니다. debug 빌드에서는 그 권한을 빼서 adb로 Gleo와 같은 형식의 호출을 보낼 수 있습니다.

```sh
adb shell "am broadcast --user 10 -a SEARCH_PROPERTIES -p kr.co.reevl.ontheway --es ACTION_PARAM '{\"query\":\"1000세대 넘는 곳\"}'"
adb logcat | grep processAppActionResponse   # Gleo(ai.umos.ambientai)가 받은 콜백
```

## 구조

```
app/src/main/java/kr/co/reevl/ontheway/
  navi/      NaviAdapter (차량 내비로 가는 유일한 문) · PleosNaviAdapter · MockNaviAdapter · CoordsGate
  nearby/    NearbyEngine (좌우 판정, 순수 로직) · CarLocation
  car/       CarUxGate (주행 제한)
  plan/      방문 계획 순서·삽입·결과 판정
  data/      리블 차량 API 클라이언트 · KeyStore 토큰 저장
  ui/        Compose 화면 (리블 디자인 토큰)
```

- flavor `pleos` = 실제 NaviHelper SDK, flavor `mock` = SDK 없이 동작하는 모의 어댑터.
- 패키지 이름 `kr.co.reevl.ontheway`는 개발 초기 코드명입니다.

## 빌드

Android Studio(JDK 17), Android SDK 34, [Pleos Connect Emulator](https://document.pleos.ai).
NaviHelper SDK는 Pleos 공개 저장소(`nexus-playground.pleos.ai`)에서 받습니다. 별도 키가 필요 없습니다.

```sh
./gradlew testPleosDebugUnitTest testMockDebugUnitTest   # 단위 테스트
./gradlew installPleosDebug                              # 에뮬레이터에 설치
```

AAOS 에뮬레이터의 사용자 ID는 10입니다(`adb shell am start --user 10 -n kr.co.reevl.ontheway/.MainActivity`).
디버그 빌드는 `http://10.0.2.2:3300`의 리블 개발 서버를 바라봅니다. 차량 API(`/api/car/*`)는 계정 연결이 필요하며 이 저장소에는 서버 코드와 비밀값이 없습니다.
