# 마이로드 코치 (MyRoad Coach)

차량 데이터로 나의 나쁜 운전 습관을 찾아 알려 주고, 그 습관이 자주 나오는 도로 유형에 들어서기 직전에 음성으로 미리 코칭해 주는 Pleos Connect 앱입니다.

## 실행 방법

1. Android Studio로 이 폴더(`MyRoadCoach`)를 엽니다. Gradle 동기화 중 SDK 라이선스 동의 창이 나오면 내용을 확인하고 동의합니다.
2. Pleos Connect 에뮬레이터(AVD)를 실행합니다.
3. 실행 대상으로 에뮬레이터를 선택하고 **Run**을 누릅니다.
4. 앱 상단의 **시나리오 재생** 버튼을 누르면 약 40초 동안 '출근길' 주행이 재생됩니다.
   - 주행 시작 → 합류 구간 500m 전 음성 코칭 → 40m 전 제동(늦은 감속 기록)
   - IC 진출 500m 전 음성 코칭 → 방향지시등 없이 진출(방향지시등 미사용 기록)
   - 어린이보호구역 과속 안내 → 도착 후 정차 → **오늘의 하이라이트** 화면으로 전환
5. 하이라이트에서 `앞에 돌발 상황이 있었어요`를 누르면 해당 기록이 습관 분석에서 빠집니다.
6. 오른쪽 위 마이크 버튼을 누르면 주간 요약을 음성으로 들려줍니다.

release APK 만들기: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
(공모전 제출 규칙에 따라 `apk/팀명_앱이름_release.apk`로 복사해 올립니다.)

판단 로직 단위 테스트: `./gradlew test`

자동 빌드: PR과 main push마다 GitHub Actions(`.github/workflows/android-build.yml`)가 단위 테스트와 debug/release APK 빌드를 실행합니다. 빌드된 release APK는 Actions 실행 결과의 Artifacts(`myroad-coach-release-apk`)에서 내려받을 수 있습니다.

## 테스트 환경

- Android Studio (최신 버전), Android SDK API 34
- minSdk 28 / compileSdk 34 / targetSdk 34, Gradle 8.11.1, AGP 8.7.3, Kotlin 2.0.21, Jetpack Compose
- Pleos Connect Emulator: 2.1.0 (API 34 system image), 화면 규격 Connect-S-17 (2560×1440, 앱 창 가로 1700×1184 / 세로 840×1184)
  - 실제로 사용한 에뮬레이터 버전과 화면 규격이 다르면 이 항목을 수정하세요.

## 사용한 Pleos Connect SDK

| SDK | 버전 | 사용 API | 용도 |
|---|---|---|---|
| Vehicle | 2.3.2 | Brake `registerBrakePedalStatus`, Gear `registerGearSelection`, SteeringWheel `registerSteeringWheelAngle`, TurnSignal `registerTurnSignalState` | 늦은 감속·방향지시등 미사용 판별, 주행/정차 전환 |
| NaviHelper | 3.0.5 | `onTBTInfo`, `onCautionPointInfo`, `onCameraAlert`, `onDestinationArrived` | 도로 유형 판별, 진입 전 코칭 시점, 과속 안내, 주행 종료 |
| TextToSpeech | 3.3.1 | `TextToSpeech(context, Mode.ON_DEVICE)`, `speak()` | 진입 전 음성 코칭, 주간 요약 음성 |
| FusedLocation | 1.1.3 | (의존성 포함, 자주 위험했던 지점 근접 알림 확장 예정) | |

선언한 권한: `CAR_DRIVE`, `CAR_LIGHTS`, `NAVI_ROUTE`, `FUSED_LOCATION`, `TTS_SERVICE`

## 구조

```
app/src/main/java/com/myroad/coach/
├─ MainActivity.kt           앱 화면 전환 (주행 중에는 코칭 카드만, 정차 중에는 탭 3개)
├─ engine/CoachEngine.kt     판단 로직: 진입 전 코칭, 늦은 감속, 방향지시등 미사용, 과속, 운전자 제외 처리
├─ engine/ScenarioPlayer.kt  시연용 주행 시나리오 (실제 SDK 이벤트와 같은 경로로 엔진에 입력)
├─ sdk/LiveSources.kt        Vehicle·NaviHelper SDK 연결 (실차/버추얼 콘솔 신호)
├─ sdk/Speaker.kt            Pleos TTS (실패 시 안드로이드 기본 TTS로 대체)
├─ sdk/RoadTypeMapper.kt     TurnType / CautionPointType → 도로 유형
├─ data/Models.kt            데이터 구조와 습관 리포트 예시 데이터
└─ ui/                       Pleos 디자인 가이드 색상·타이포그래피 기반 화면
```

## 현재 한계

- 습관 리포트와 성장 리포트의 수치는 예시 데이터입니다. 주행 기록 누적 저장과 통계 계산은 다음 단계에서 구현합니다.
- 음성 질문은 마이크 버튼을 누르면 정해진 요약을 들려주는 형태이며, SpeechToText SDK 연동은 다음 단계입니다.
- Vehicle SDK에는 차량 속도 API가 없어, 급제동은 '감속 구간 직전 제동(늦은 감속)' 기준으로 판단합니다.
