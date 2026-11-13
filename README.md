# HEARTz — 같은 길에서 만나는 마음들

**누군가 그 장소에 남긴 짧은 음성 사연을, 그곳을 지나는 운전자와 동승자에게 들려주는 Pleos Connect 앱.**

지원 구간에 들어서면 기본 내비 옆 HEARTz 창에 사연 카드가 도착합니다.
주행 중에는 도착 안내 뒤 바로 읽고, 다 들으면 공감 하트를 남길지 묻습니다(설정에서 자동 읽기 끄기 가능).
주차 중에는 선택한 사연만 재생합니다. 내 목소리 또는 변조 목소리로 사연을 남길 수 있고, 운영자 검수 뒤 공개됩니다.
다른 사람의 사연에는 공감 하트만 남길 수 있습니다.

> 현재 UI(2026-11-12): v8 시안의 흰 배경·검은 사연 카드·초록 강조에 **12간지 동물 프로필·닉네임·시작 화면**을 반영했습니다.
> "지나면서 남기기"와 자동 읽기는 **기본 켜짐**입니다. 가상 마이크의 합성 시험 음성으로 녹음·전송을 확인했으며,
> v8 UI에서 실제 음성 답변·Gleo 명령, 다양한 창 크기와 대체 글꼴은 추가 확인이 필요합니다.
> 시드 사연은 **합성 테스트 음원**(Pleos TTS 낭독, 실제 사람의 사연 아님)이고, 계정은 **테스트 계정**입니다.
> 화면과 동작: [UI 및 기능 정의서](.docs/ui-spec/HEARTz-UI-기능정의서.md) · [v8 캡처 17장](.docs/ui-spec/images-v8/)
> 주행 도착 재현: [주행 시험 명령과 반복 시험 절차](.docs/DRIVING-TEST.md)
> 자세한 검증 기록: [.docs/SETUP-RESULTS.md](.docs/SETUP-RESULTS.md)

## 주요 기능

| 상황 | 화면 | 기능 |
| --- | --- | --- |
| 주행 중 (CarUxRestrictions 제한) | 사연 카드·큰 동작 버튼 | 도착 안내 → 바로 읽기 → 공감 질문 → 다음 사연. 읽는 중 도착한 사연은 대기열에 보관 |
| 주차 중 | 왼쪽 메뉴·미니 플레이어 | 지금 이 길 · 지나온 사연 · 남기기(녹음) · 내 사연함 · 검수(운영자) · 설정·프로필 꾸미기 |
| 편성 | — | 승인·미청취·숨기지 않은 작성자의 사연 중 **현재 구간에 맞는 후보를 무작위 선택** (인기 순위 없음) |
| 공개 | — | 보내면 검수 중 → 공개/반려, 공개 후 숨김 가능. 나만 보기 없음. 보내지 못한 녹음은 기기 초안으로 보관 |
| 지나면서 남기기 (M3, 기본 켜짐) | 주행 화면 큰 버튼·음성 질문 | 시작 위치 고정 → 안내 → 녹음(말이 멈추면 자동 종료) → 보내기/다시 녹음/버리기 → 목소리 선택. 통화 등으로 끊기면 기기 초안으로 보관 |
| 목소리 | 내 목소리 / 변조 목소리 | 원본은 기기에서 잡음을 줄임. 변조 목소리는 받아쓴 글을 Pleos TTS로 읽음 |
| 검수 보조 | 운영자 검수 | Pleos SpeechToText(기기 내)로 받아쓰기 + 의심 구간(전화번호·주소·반복·큰 소리·무음) 표시. 승인·반려는 사람이 원본을 듣고 결정 |
| Gleo 앱 액션 | — | schema 0.4.0: `START_STORY_RECORDING` · `MANAGE_MY_STORY` · `HANDLE_ARRIVED_LETTER`. 이전 schema의 실제 재생 호출은 확인했으며, 현재 schema 전체의 실제 음성 호출은 미검증 |
| 시스템 미디어 | 미디어 라이브러리 | Media3 `MediaLibraryService`, 하위 카테고리 = 팟캐스트(폴더·회차 mediaType), 미디어 키 재생 제어 |

## 실행 방법

### 1. 테스트 환경

| 항목 | 값 |
| --- | --- |
| 호스트 | Ubuntu 22.04 x86_64, KVM, NVIDIA RTX 4050 Laptop 6GB |
| 에뮬레이터 | Pleos Connect v2.1.0 이미지(API 34, `release_connect_v2.1.0`), RAM 4GB, GPU host, **Cold boot** |
| 화면 규격 | 2560×1440 px, 160 dpi, 가로(landscape). HEARTz는 기본 내비 옆 분할 창(오른쪽 약 840 px 폭)에서 확인 |
| AVD 내 서비스 | Vehicle 2.3.22, FusedLocation 1.0.0, Navi 3.0.4, CAAS(TTS·STT) 4.2.10 |
| 빌드 | JDK 17 · Gradle 8.10.2 · AGP 8.8.2 · Kotlin 2.0.0 · compile/target 35 · minSdk 28 |
| Pleos SDK | `FusedLocation:1.1.3`, `NaviHelper:3.0.5`, `TextToSpeech:3.3.1`, `SpeechToText:3.3.1`(ON_DEVICE, 앱 등록·비밀키 없음) (Maven `https://nexus-playground.pleos.ai/repository/maven-releases/`) |
| 기타 | Media3 1.9.4, Compose BOM 2024.04.01, kotlinx-serialization 1.7.3 |

### 2. 빌드·설치

```bash
source .tools/env.sh            # 로컬 전용(JAVA_HOME·ANDROID_HOME 등). 없으면 직접 설정
.tools/start-emulator.sh &      # Pleos AVD Cold Boot
"$adb" devices -l               # serial 확인
serial=emulator-5554

./gradlew :heartz:assembleDebug
"$adb" -s "$serial" install -r heartz/build/outputs/apk/debug/heartz-debug.apk
"$adb" -s "$serial" shell am start -n ai.heartz.app/.MainActivity
```

`local.properties`에 `sdk.dir=<Android SDK 경로>`를 둡니다(Git 제외).

### 3. 필요한 설정

1. **주차(P) 상태**에서 처음 실행하면 차량 위치 권한(`pleos.car.permission.FUSED_LOCATION`)을 묻습니다. 허용해 주세요.
   녹음은 `남기기`에서 마이크 권한을 허용한 뒤 사용할 수 있습니다.
2. 기본 내비와 나란히 보려면 앱 영역 오른쪽 가장자리의 창 핸들(⋮)을 왼쪽으로 끌어 분할합니다.
3. 주행 상태는 Virtual Console에서 바꿉니다: `"$adb" -s "$serial" forward tcp:8088 tcp:8088` 후 브라우저에서
   `http://127.0.0.1:8088/`을 열어 둔 채(탭을 닫으면 변경 요청이 거부됨) Motion › Gear Selection을 D/P로 바꿉니다.
4. 위치는 에뮬레이터 위치 입력으로 재현합니다(테스트 입력). 시연 경로 재생:

   ```bash
   tools/demo-drive.sh "$serial"     # 용산구 랜드마크(사연 있는 곳)를 지나는 시연 경로, 15 m/초
   "$adb" -s "$serial" logcat -s Heartz:I   # 앱이 받은 SDK 콜백·카드 표시 확인
   ```

   장소별 위치 명령과 시연 순서는 [.docs/DRIVING-TEST.md](.docs/DRIVING-TEST.md), [.docs/DEMO-SCENARIO.md](.docs/DEMO-SCENARIO.md)에 있습니다.

5. 테스트 계정 전환·검수: 주차 후 `설정 › 테스트 계정`에서 **장클로드·김피티·상도동메시(운전자)**, **운영자**를 고릅니다.
   운영자 계정에는 `검수` 탭이 나타납니다.
6. 지나면서 남기기: 주차 후 `설정 › 지나면서 남기기`와 마이크 권한을 확인합니다(기본 켜짐). 주행 화면의 큰 버튼이나 Gleo 명령으로 시작합니다.
   Gleo 모의 호출(테스트 입력): `"$adb" -s "$serial" shell am start -n ai.heartz.app/.MainActivity -a ai.heartz.app.START_STORY_RECORDING`
7. 앱을 새로 실행하면 약 1.4초 동안 검은 바탕의 HEARTz 로고가 나오고 바로 메인 화면으로 갑니다(첫 실행 프로필 설정 없음).
   프로필은 닉네임 첫 글자 원(Pleos Connect 상태 표시줄 모양)이고, 닉네임은 테스트 계정 이름입니다. Pleos 프로필 이름 연동은 향후 개발입니다(일반 앱이 읽을 수 있는 API·권한 없음).
8. Gleo 연동: `assets/schema.json`(App Schema)을 넣었고 에뮬레이터의 Gleo가 부팅 때 읽습니다. 공모전 기간에는 Gleo Interface 검토·승인을 신청할 수 없으므로 실제 차량에서의 활성화는 Pleos 검토가 필요합니다.

### 4. 데이터 백엔드

- 기본값은 **기기 내 데모 백엔드**입니다. 서버 규칙(공개 범위·검수 권한)을 앱 안에서 그대로 실행하므로 서버 없이 단독으로 동작하며, 모든 테스트 계정이 같은 기기 안에서 공유됩니다.
- debug 빌드는 호스트의 **로컬 개발 서버**에도 연결할 수 있습니다(외부 배포 서버 아님, 비밀키 없음):

  ```bash
  ./gradlew :server:installDist
  server/build/install/server/bin/server --data build/heartz-server-data --seed-audio seed/audio
  # 127.0.0.1:8787에만 바인딩. 앱 설정 › 데이터 연결 › 개발 서버 (에뮬레이터에서 http://10.0.2.2:8787)
  ```

## APK

- **제출 APK: [`apk/ULSANER_HEARTz_release.apk`](apk/)** — 서명한 release 빌드(기기 내 데모 백엔드만 사용, 서버 불필요).
  설치: `"$adb" -s "$serial" install apk/ULSANER_HEARTz_release.apk` 후 위 3번 설정대로 실행합니다(debug용 시험 명령은 release에 없음).
- 직접 빌드: `./gradlew :heartz:assembleRelease` → `heartz/build/outputs/apk/release/heartz-release.apk`.
  서명은 저장소 밖의 `keystore.properties`(Git 제외)가 있을 때만 적용되며, 없으면 서명되지 않은 APK가 만들어집니다. 서명키는 저장소에 넣지 않습니다.

## 테스트

```bash
./gradlew test :heartz:lintDebug     # core(편성·검수 규칙) · server(HTTP 계약) · heartz(화면 상태) 단위 테스트
```

단위 테스트는 fake 기반이며 실제 SDK 연결 증거가 아닙니다. 에뮬레이터에서 확인한 항목과 테스트 입력은
[.docs/SETUP-RESULTS.md](.docs/SETUP-RESULTS.md)의 "Heartz 개발 결과"에 구분해 기록했습니다.

## 구조

```text
core/     순수 Kotlin 도메인: 모델, 구간 판정·무작위 편성(LetterSession), 검수·공개 규칙(HeartzService), 시드
server/   로컬 개발 API 서버 (JDK HttpServer, core 규칙 재사용)
heartz/   Android 앱 ai.heartz.app
  platform/location     Pleos FusedLocation SDK (위치·근접 알림 진단)
  platform/navi         Pleos NaviHelper SDK (내비 상태·M0 비교)
  platform/restrictions CarUxRestrictionsManager
  platform/audio        Media3 MediaLibraryService·MediaController·변조 목소리 TTS (포커스 처리)
  platform/record       녹음 (MediaRecorder)·녹음용 오디오 포커스
  platform/speech       Pleos TextToSpeech·SpeechToText, 받아쓰기 기반 검수 보조
  platform/gleo         Gleo 앱 액션 계약 (assets/schema.json)
  data/                 기기 내 데모 백엔드·개발 서버 클라이언트·초안 저장소
  ui/                   주행·녹음 화면, 주차 화면(사연·남기기·사연함·검수·설정·프로필·기술 진단)
seed/audio/  시드 합성 테스트 음원 (README 참고)
tools/       시연 경로 재생 스크립트
app/      세팅 단계의 Vehicle SDK 검증 앱(PleosSetupProbe, 참고용)
```

## 안전 원칙

- 주행 중 자동 읽기는 설정에 따릅니다(기본 켜짐). 도착 안내부터 공감 결정까지 음악을 멈추고, 결정 2초 뒤 포커스를 반납합니다. 대기열이 있으면 이어 읽으며, 주차 중에는 사용자가 고를 때만 재생합니다.
- 주행 화면 여부는 기어 값이 아니라 플랫폼 `CarUxRestrictions`로 판단하며, 상태를 알 수 없으면 주행 화면으로 처리합니다.
- 청취 화면의 `distractionOptimized` 선언은 팀 결정이며, Pleos 심사 승인 여부는 아직 확인하지 않았습니다.
- 지나면서 남기기는 위치 진입만으로 켜지지 않습니다. 안내 음성이 끝난 뒤 녹음하고, 통화·음성 비서 등으로 소리가 끊기거나 앱이 화면에서 사라지면 즉시 멈춰 공개하지 않은 초안으로만 남깁니다.
- 받아쓰기는 작성자와 운영자에게만 보이며 자동 승인·반려에 쓰지 않습니다. 오버레이(지도 위 그리기)는 쓰지 않습니다.
- 서버로 보내는 위치는 사연의 장소 ID뿐이며 연속 주행 경로를 저장하지 않습니다. 로그의 좌표는 debug 빌드에서만 반올림해 남깁니다.

공통 개발 지침: [AGENTS.md](AGENTS.md) · 기획서: [.docs/HEARTZ-PLAN.md](.docs/HEARTZ-PLAN.md) · 세팅: [.docs/SETUP.md](.docs/SETUP.md) · 참고 자료: [.docs/REFERENCES.md](.docs/REFERENCES.md)
