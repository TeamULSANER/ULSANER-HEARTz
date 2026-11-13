# Linux 개발 환경 세팅 결과

- 실행 일시 / 담당: 2026-11-06 20:59–21:38 KST / Claude Code (Opus 5.5), 사용자 계정 `autolab`(root 아님)
- 배포판 / kernel / CPU 아키텍처 / RAM / GPU·드라이버: Ubuntu 22.04.5 LTS / 6.8.0-138-generic / x86_64 (i5-13420H, VT-x) / 31 GiB, 디스크 여유 345 GB / Intel iGPU(i915) + NVIDIA GeForce RTX 4050 Laptop 6 GB, 드라이버 580.178.04
- GUI 세션 / KVM 접근 / emulator -accel-check: X11 `DISPLAY=:1` / `/dev/kvm` 사용자 ACL `user:autolab:rw-`(logind), kvm 그룹 미가입 / `KVM (version 12) is installed and usable.` (exit 0)
- 프로젝트 루트: `/home/autolab/pleos_app` (로컬 Git 저장소 `main`, 커밋·원격 없음)
- Android Studio / JDK / Git / Python: Android Studio `AI-262.9437.185.2621.16467767`(기존 설치 재사용, 이번에 IDE는 실행하지 않음) / Gradle용 Temurin 17.0.21+9 (시스템 기본 java 11은 변경하지 않음) / git 2.34.1 / Python 3.10.12
- Gradle / AGP / Kotlin / Compose BOM: 8.10.2 / 8.8.2 / 2.0.0 (+ compose compiler plugin 2.0.0) / 2024.04.01 (실제 resolved compose ui·runtime 1.7.8)
- compileSdk / targetSdk / minSdk: 35 / 35 / 28
- 실제 resolved Vehicle SDK: `ai.pleos.playground:Vehicle:2.3.2` (+ vehicle-aidl 1.1.1, vehicle-data 1.1.11), kotlinx-coroutines 1.9.0, kotlin-stdlib 2.0.21
- Pleos 이미지 / API / AVD 이름 / serial / Vehicle Service: `system-images;android-34;pleos_car_emulator;x86_64` "Pleos Connect v2.1.0" rev 2 / API 34 (Android 14) / `Pleos_Connect_v2.1_probe`(이번에 신규 생성) / `emulator-5554` / `ai.pleos.playground.service.vehicle` 2.3.22 (versionCode 74)
- 빌드 fingerprint: `Pleos/crp_avd/crp_avd_x86_64:14/RELEASE.release_connect_v2.1.0/release_connect_v2.1.0:user/test-keys`
- 사용한 에이전트 / 지침·import·5개 스킬 인식: Claude Code. 파일 배치·frontmatter 확인 PASS, **새 세션에서의 인식 확인은 NOT_RUN**(아래 "사용자에게 필요한 작업" 1번)
- 스킬 출처·커밋·라이선스: 아래 "스킬" 절
- SDK 신청·권한 상태: Playground 계정·프로젝트·SDK 사용 신청 상태는 로그인이 필요해 **확인하지 못함(NOT_RUN)**. 에뮬레이터에서는 `pleos.car.permission.CAR_DOORS` Manifest 선언만으로 capability·get·callback이 동작함

## 0. 기존 상태와 재사용 결정

| 항목 | 발견한 상태 | 결정 |
| --- | --- | --- |
| 문서 폴더 | 요청·SETUP.md는 `.docs/`를 가정하지만 실제 폴더는 **`.docs/`** (SETUP.md, REFERENCES.md, 접수양식 docx) | SETUP.md 0.1에 따라 중복 폴더를 만들지 않고 `.docs/` 사용. 결과는 `.docs/SETUP-RESULTS.md`, 증거는 `.docs/evidence/` |
| 기존 SETUP-RESULTS.md | 없음 | 새로 작성 |
| Git / AGENTS.md / CLAUDE.md | 저장소·지침 없음, 상위 경로 지침 없음. git user.name/email 미설정 | `git init`(main)만 수행. 사용자 정보는 임의로 만들지 않음 |
| Android Studio·SDK·JDK | `~/interlude/.dev/environment`에 Studio, SDK(Platform 34·35, Build-Tools 35.0.0, Platform-Tools 37.0.1, Emulator 37.2.12, cmdline-tools 23.0), Temurin JDK 17, Pleos 2.1.0 이미지, Gradle 8.10.2 배포본·Vehicle 2.3.2 캐시가 설치되어 있음 | **재설치하지 않고 재사용**. 경로는 `.tools/env.sh`(Git 제외)에 모음. 셸 프로필은 수정하지 않음 |
| 기존 AVD | `pleos_connect_21`, `pleos_native_clean_20261028` (interlude 프로젝트용) | 건드리지 않고 같은 AVD 홈에 `Pleos_Connect_v2.1_probe`를 새로 생성 |
| 관리자 권한 | `sudo`는 암호 필요 | **이번 세팅에 sudo가 필요한 작업 없음** (패키지 설치·그룹 변경 불필요) |

## 공식 근거와 결정 (확인일 모두 2026-11-06, curl로 원문 확인, 사본은 `.docs/evidence/official/`)

| 결정 대상 | 공식 원문 URL | 요건·차이 | 선택·이유 |
| --- | --- | --- | --- |
| 개발 환경 최소 요건 | https://appdevchallenge.auto-hmg.io/guide/development , https://appdevchallenge.auto-hmg.io/faq | Android API 28 이상, Gradle 8.0 이상, Java/Kotlin. "지원 범위와 버전은 공식 문서 기준" | minSdk 28, Gradle 8.10.2, Kotlin |
| 에뮬레이터 이미지 | https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator , https://document.pleos.ai/docs/pleos-only/release-note/emulator-release-note , update site `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml` | Emulator 2.1.0(2026-10-19), API 34, RAM 4 GB, Cold boot 필수(Quick boot는 VHAL 불가). update site 최신 = "Pleos Connect v2.1.0" rev 2, 필요 emulator ≥ 24 | 설치된 이미지가 update site와 동일(rev 2) → 재사용. emulator 37.2.12 유지(37.2.13 업데이트 존재, 공식 지정 없음·공유 SDK라 변경 보류) |
| AVD 설정 | https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator | RAM 4 GB, GPU 필수(최소 RTX 3060 Laptop 6 GB, 권장 RTX 4060 8 GB), Cold boot | `hw.ramSize=4096`, `hw.gpu.mode=host`, `fastboot.forceColdBoot=yes`. **차이:** 이미지 기본값 `hw.ramSize=8192` → 공식 가이드 4 GB 우선. RTX 4050 Laptop 6 GB는 표에 없는 모델(VRAM은 최소 6 GB 충족, 권장 미달) |
| Vehicle SDK | https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup , https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note , `.../maven-releases/ai/pleos/playground/Vehicle/maven-metadata.xml` | 연동 페이지·릴리스 노트 2.3.2. **차이:** 공식 샘플 HEAD(959981d, 확인 시 main과 동일) 카탈로그는 2.3.1이지만 Maven에는 2.0.3·2.3.2만 존재 | **2.3.2** (세부 문서·릴리스 노트·Maven `release` 일치). SDK manifest의 최소 Service 2.3.13 ≤ 실제 2.3.22 |
| JDK·Gradle·AGP | https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app , https://developer.android.com/build/releases/agp-8-8-0-release-notes , https://gradle.org/releases/ | 샘플 가이드 기준 JDK 17 / Gradle 8.10.2 / AGP 8.8.2. AGP 8.8: Gradle ≥ 8.10.2, Build-Tools 35.0.0, JDK 17, 최대 API 35 | JDK 17 / Gradle 8.10.2 / AGP 8.8.2. 배포본 SHA-256 `31c55713…1524c26`, wrapper JAR SHA-256 `2db75c40…8448046` 모두 공식값과 일치 |
| Kotlin·Compose·Android SDK | 위 샘플 가이드, https://kotlinlang.org/docs/compose-compiler-migration-guide.html , 샘플 카탈로그(959981d) | Kotlin 2.0.0, compose plugin = Kotlin 버전, compile/target 35. 샘플 minSdk 23은 제품 요구 28보다 우선하지 않음 | Kotlin 2.0.0 + `org.jetbrains.kotlin.plugin.compose` 2.0.0, compile/target 35, minSdk 28. AndroidX(core-ktx 1.16.0, activity-compose 1.10.1, lifecycle 2.9.0, BOM 2024.04.01)는 샘플값. coroutines는 직접 사용하므로 실제 resolved 1.9.0을 직접 선언 |
| 권한 | https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission , 실제 `Door.kt` `@RequiresPermission` | Door 조회는 `CAR_DOORS`, 제어는 `CONTROL_CAR_DOORS` | `CAR_DOORS`만 선언 (제어 미구현). `distractionOptimized` 선언 없음 |
| Virtual Console | https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator | `adb forward tcp:8088 tcp:8088` 후 PC 브라우저 `http://127.0.0.1:8088/`, Emulator ≥ 2.1.0 | 그대로 사용. **추가 관찰:** 콘솔 backend는 브라우저 탭의 heartbeat(`POST /browser/session`, 1초 주기)가 없으면 변경 요청에 `503 {"error":"emulator_inactive"}`를 반환 → 테스트 중 브라우저 탭 유지 필요 |
| GitHub 공개 범위·접근·산출물 | https://appdevchallenge.auto-hmg.io/guide/submission , FAQ, 공지 3건 | 접수 2026-10-21~**2026-11-15**, 이메일 접수. GitHub·APK·시연 영상은 선택(가산점). 저장소에 소스와 **release APK를 `apk/팀명_앱이름_release.apk`**로, 용량 초과 시 GitHub Releases. Private 가능하나 "공지된 심사용 GitHub 계정"을 Collaborator로 초대해야 하며 **확인일 기준 그 계정은 공지에 없음**. README 필수 항목: 앱 이름·한 줄 소개, 실행 방법, 테스트 환경·설정, APK 위치 | 원격 저장소 생성·push·초대는 하지 않음(요청 없음). 동의서(docx)는 `.gitignore`로 제외. README는 환경 기준으로 작성, 제출 전 갱신 필요 |

### 공식 문서와 실제 SDK 아티팩트의 차이 (실제 코드는 아티팩트 기준으로 작성)

1. Callback 가이드 예제는 `registerDoorPosition(doorPositionListener = …)`(area 없음)이지만, 배포된 2.3.2 소스(`Door.kt`)의 실제 시그니처는 `registerDoorPosition(area: DoorArea, listener)` / `unregisterDoorPosition(area, listener)`.
2. Error code 가이드는 `e is VehicleException` / `e.errorCode` 분기를 안내하지만, 배포 AAR에서 `VehicleException`은 난독화(`callback/exception/a`)되어 있고 `ErrorCode`는 runtime scope인 `vehicle-data`에만 있어 **컴파일 시 참조 불가**. 앱은 SDK 소스상 `message = ErrorCode 이름`인 점을 이용해 메시지로 분류하고, 모르는 오류는 OTHER로 남김.
3. SDK에는 Service 연결 완료 콜백이 없음. `initialize()`는 bind 요청만 하고, 연결 전 호출은 큐에 쌓였다가 연결 후 실행됨 → 앱은 호출마다 10초 타임아웃을 두고 TIMEOUT을 성공과 구분.
4. SDK callback은 `Dispatchers.Default` 스레드에서 옴 → StateFlow.update로 갱신.
5. register 직후 현재값 callback 1회: 관찰된 6번의 register 중 4번에서 발생, 2번은 없음 → **보장되지 않는 동작**으로 간주(초기값은 별도 get으로 확인).

## 검증

| 항목 | 상태 | 명령·시나리오 | 실제 결과·증거 |
| --- | --- | --- | --- |
| 단위 테스트 TDD (red→green) | PASS | stub ViewModel로 `:app:testDebugUnitTest` → 구현 후 재실행 | red: 9 tests, 9 failed (`evidence/tdd-red.log`) → green: tests=9 failures=0 errors=0 (`evidence/tdd-green.log`). fake 기반, 실제 SDK 증거 아님 |
| Build / unit test / lint | PASS | `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | exit 0, `BUILD SUCCESSFUL`. lint 오류 0, 경고 28(고정한 버전의 "newer version available" 27, `allowBackup` 1). `evidence/build-test-lint.log`, merged manifest `evidence/merged-AndroidManifest.debug.xml` |
| AVD Cold Boot | PASS | `.tools/start-emulator.sh` (`-gpu host -no-snapshot`, PRIME offload) | `sys.boot_completed=1`, API 34, fingerprint `release_connect_v2.1.0`, Graphics Adapter = NVIDIA RTX 4050 (OpenGL ES 3.0 / 4.5.0 NVIDIA 580). 총 5회 cold boot(1회는 swiftshader 진단용) 모두 부팅 성공. 첫 부팅 로그에 `Your GPU cannot be used for hardware rendering` 1줄이 있으나 이후 NVIDIA가 선택됨(`emulator-boot-1.log`) |
| **에뮬레이터 안정성 (host GPU)** | **FAIL (간헐적)** | 같은 설정으로 앱 조작 중 | `-gpu host`에서 **SIGSEGV(exit 139)로 3회 종료**. 3회 모두 `uiautomator dump` 실행 중(3번째는 의도적 재현: 15회째 dump에서 종료). 직전 로그에 `Failed to make display surface context current: 12290`(EGL_BAD_ACCESS) 다수. 비교 실험 `-gpu swiftshader_indirect`에서는 dump 30회 무사고 → **host NVIDIA 렌더링 경로 문제로 추정, 근본 원인 미확정**. uiautomator 없이 진행한 4분 이상의 앱·SDK·콘솔 조작에서는 종료 없음. 크래시 덤프는 생성되지 않음. `emulator-boot-2/3/4-swiftshader.log`, `device-logcat-run2.txt` |
| 앱 설치·실행·IVI 앱 영역 표시 | PASS | `adb install -r`, `am start -W` | `Success`, `LaunchState: COLD`, `topResumedActivity=…pleossetup/.MainActivity`, 앱 크래시 버퍼 0건. 화면: IVI 오른쪽 앱 영역에 표시(`evidence/01-app-launched.png`). 부팅 직후에는 왼쪽 차량 패널이 그려지기 전이라 앱이 전체 폭으로 표시됨(`08-right-after-boot.png`) |
| Virtual Console | PASS | `adb -s emulator-5554 forward tcp:8088 tcp:8088`, 브라우저(Firefox) `http://127.0.0.1:8088/` | `HTTP 200`, title "Pleos Connect Virtual Console". 브라우저 탭이 열리기 전 변경 요청은 `503 emulator_inactive`, 탭을 연 뒤 `browser.active=True`, `PUT /vehicle/body?door_fl=…` → `HTTP 200`이고 `/vehicle/state`의 `doors.front_left`가 실제로 변경됨. 콘솔 Door Position(Front left) = car property `DOOR_POS` |
| Capability / read | PASS | Check support → Read value | `checkDoorPositionCapabilities` complete, `access=READ_WRITE`, ROW_1_LEFT `0..1`(ROW_1_RIGHT/ROW_2_* 0..1, HOOD/REAR 0..100). `getCurrentDoorPosition ROW_1_LEFT value=0` ↔ 콘솔 `front_left:false`. 콘솔에서 연 뒤 재조회 `value=1`. `evidence/probe-log.txt` |
| 실제 callback A → B → A (×2) | PASS | Start updates 후 콘솔 Front left Open→Close→Open→Close | `registerDoorPosition` returned, callback `1,0,1,0` 정확히 4회(각 PUT 후 약 40 ms), 화면 callbacks 4, 이후 별도 get `value=0`. `probe-log-AB.txt`, `02-after-callbacks.png` |
| 해제 | PASS | Stop updates 후 콘솔 Open→Close | `unregisterDoorPosition` returned, 이후 callback 0건 (`probe-log-C.txt`) |
| 재구독 | PASS | Start updates 후 Open | register 1회, callback `1` 1회 |
| 화면 숨김·재진입 | PASS | 다른 앱(Car Settings)을 앱 영역에 띄움 → 콘솔 2회 변경 → 앱 재진입 → 2회 변경 | 숨김 시 unregister, 숨김 중 변경 callback 0건, 재진입 시 register 1회 + 현재값 callback 1회(register 직후 초기값, 차이 5번 참고), 이후 변경당 정확히 1회(중복 없음). `probe-log-C3.txt`, `05-hidden-by-settings.png`, `06-after-reentry.png`. 참고: 이 이미지에서 HOME 키는 앱을 resumed 상태로 둔 채 홈 패널만 바꾸므로 숨김 테스트가 되지 않음(`04-after-home-key.png`) |
| Reconnect(앱 내 재초기화) | PASS | 구독 중 Reconnect → 지원 확인 → 조회 → 콘솔 변경 | unregister → release → initialize → register 순서, 이후 변경당 1회 callback. `probe-log-reconnect.txt`, `07-after-reconnect.png` |
| 앱 종료·재실행 | PASS | `am force-stop` → `am start -W` | 새 pid, initialize → capability READ_WRITE → get 0 → 콘솔 Open 후 get 1. `probe-log-C4-relaunch.txt` |
| AVD 재시작·forward 재연결 후 핵심 조회 | PASS | 에뮬레이터 재시작(5회째 boot) → forward 재설정 → 앱 실행 | capability READ_WRITE, get `value=0` (`probe-log-final-restart.txt`) |
| 창 크기 변경(폼팩터) | NOT_RUN | 공식 폼팩터 테스트는 Host Display Bridge(Go)·FormFactorConsole·가상 디스플레이 필요 | GPU 크래시 이슈로 이번에는 수행하지 않음. 앱은 폭 900dp 기준 1단/2단 전환을 구현했지만 기기에서 검증하지 않음 |
| 선택 제어 + readback·복원 | NOT_RUN | 범위 밖으로 둠 | capability는 READ_WRITE를 보고했으나 `setDoorPosition`은 구현·호출하지 않음 |
| Gleo end-to-end | NOT_RUN | — | 이미지에 `ai.pleos.gleoai.model.provider` 패키지가 있음을 확인했을 뿐, 실행 가능 여부는 미확인 |
| STT/TTS | NOT_RUN | — | — |
| draw.io doctor / GUI export | PASS / NOT_RUN | `python3 .agents/skills/drawio-skill/scripts/diagramctl.py doctor` | exit 0, python·graphviz·git available, draw.io Desktop missing → native export 불가. `evidence/drawio-doctor.json` |
| 지침 import 인식 (Claude Code) | PASS | 2026-11-07 세션 재개 시 하네스가 로드한 프로젝트 지침 확인 | 루트 `CLAUDE.md`와 `@AGENTS.md`로 import된 AGENTS.md 전문이 함께 로드됨 |
| 스킬 인식 (Claude Code) | PASS 4 / NOT_RUN 1 | 같은 세션의 사용 가능 스킬 목록 | karpathy-guidelines, systematic-debugging, test-driven-development, verification-before-completion이 목록에 표시됨. drawio-skill은 `disable-model-invocation: true`라 모델용 목록에 나오지 않는 것이 정상이며, 사용자가 `/` 메뉴에서 `/drawio-skill` 표시를 확인해야 함 |
| 지침·스킬 인식 (Codex) | NOT_RUN | Codex 새 세션에서 확인 필요 | — |
| Android Studio Device Manager에서 AVD 표시·IDE Gradle JDK | NOT_RUN | IDE를 실행하지 않음 | 명령행 Cold Boot만 검증 |
| GitHub 원격 생성·접근 검증 | NOT_RUN | 사용자 요청 시 | — |

## Vehicle SDK·주행 제한 참고 관찰 (2026-11-07)

- SDK 2.3.2 소스 확인: `DrivingMode`는 조회·구독만 있고 set API 없음. 가속 페달 위치·RPM API 없음.
- 주행 제한 관찰: Virtual Console에서 기어 Drive(`source: web`) → CarDrivingState 0→1(idling)→2(moving)→1, UX restriction `requiresDistractionOptimization=true`, 0x50(NO_VIDEO·NO_SETUP). HomeScreen TaskViewManager가 `distractionOptimized: false`인 앱 화면을 "주행 중에는 사용할 수 없습니다"로 가림. 정차(0 km/h)라도 D 기어(idling)면 가려지고 P(parked, 0x0)에서만 표시.

## 주행 중 화면 정책 확인 (공식 문서, 확인일 2026-11-08, web-fetch로 원문 확인·사본 미보관)

| 문서 | 확인 내용 |
| --- | --- |
| [주행 제한 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy) | DO 미선언 앱: Parking "사용 가능" / Drive "사용 불가". 주차 중에는 모든 기능 허용. 주행 중 "동영상 시청"(NO_VIDEO)은 모든 지역 ⛔. 애니메이션 언급 없음. 수치 기준 없음 |
| [운전자 주의 분산 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline) | "주행 중에는 운전자의 시선을 과도하게 끄는 동영상, 애니메이션 및 움직이는 이미지를 최소화하거나 제한합니다." / "장식적이거나 반복적으로 움직이는 시각 요소는 지양합니다." → 애니메이션은 금지가 아니라 주행 중 최소화 권고. 게임 등 주의 분산 활동 유도 금지 |
| [앱 카테고리](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category) | 주행 중 기능이 "제한적으로 허용될 수 있는" 카테고리로 Music/Audio, Maps/Navigation, Mobility Services만 명시. Entertainment는 주행 중 허용 목록에 없음 |
| [주행 제한 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide) | DO 선언 앱도 `CarUxRestrictions`·`ActiveRestrictions`를 확인해 기능별 제한을 직접 적용해야 함. DO 선언 방법(manifest 키)과 심사·승인 절차는 이 페이지들에 없음 → App Review Guidelines 등 미확인(NOT_RUN) |

## 스킬

| 스킬 | 원본 · 커밋(확인 시 상류 HEAD와 동일) | 설치 | 라이선스 |
| --- | --- | --- | --- |
| karpathy-guidelines | multica-ai/andrej-karpathy-skills `2c606141936f1eeef17fa3043a72095b4765b9c2` | Codex skill-installer(`--ref` 고정) | 루트 LICENSE 파일 없음. SKILL.md frontmatter·plugin.json에 `MIT` 선언 → `UPSTREAM-LICENSE`에 그 사실만 기록(본문 임의 작성 안 함) |
| systematic-debugging / test-driven-development / verification-before-completion | obra/superpowers `22f0b463e7a5e579904dd6211a520321035526f7` | 동일 | MIT, `UPSTREAM-LICENSE` |
| drawio-skill | agents365-ai/drawio-skill `053600a8996159f256470dd49ac8d783e5fde588` | 동일 | MIT (스킬 내 LICENSE + `UPSTREAM-LICENSE`) |

- helper 결과를 같은 커밋의 clone(`.tools/skill-sources/`)과 `diff -qr` 비교: 5개 모두 동일.
- `.agents/skills`와 `.claude/skills`: `diff -qr` 동일, SKILL.md 각 5개, `root-cause-tracing.md`·`writing-good-tests.md`·`scripts/diagramctl.py` 존재, drawio `disable-model-invocation: true` 보존.
- 실행 전 `diagramctl.py doctor` 코드를 읽고 네트워크·외부 실행이 없음을 확인함(`--probe` 미사용).

## 생성·변경한 파일

- 루트: `AGENTS.md`, `CLAUDE.md`(@AGENTS.md import), `README.md`, `.gitignore`, `.gitattributes`, `.editorconfig`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`(실행 비트, LF), `gradlew.bat`(CRLF), `local.properties`(Git 제외)
- 앱: `app/build.gradle.kts`, `AndroidManifest.xml`, `MainActivity.kt`, `ProbeViewModel.kt`, `ProbeUiState.kt`, `VehicleProbeRepository.kt`, 리소스, 테스트 2개
- 로컬 전용(`.tools/`, Git 제외): `env.sh`, `start-emulator.sh`, `probe-ui.sh`, `skill-sources/`, `downloads/gradle-8.10.2-bin.zip`(체크섬 검증용)
- 공유 설치물에 추가한 것: interlude AVD 홈에 `Pleos_Connect_v2.1_probe.avd/.ini` 1개. 기존 AVD·SDK 패키지·셸 프로필·시스템 설정은 변경하지 않음

## 재시작

1. `cd /home/autolab/pleos_app && source .tools/env.sh`
2. `.tools/start-emulator.sh &` — Cold Boot(스냅샷 로드·저장 안 함, wipe-data 안 함). Quick Boot로 띄운 AVD는 연결 검증에 쓰지 않는다.
3. `until [[ "$("$adb" -s emulator-5554 shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]; do sleep 2; done` 후 `"$adb" devices -l`로 serial 확인(여러 기기면 모든 명령에 `-s`).
4. `"$adb" -s <serial> forward tcp:8088 tcp:8088 && "$adb" forward --list` — 8088이 점유되어 있으면 다른 프로세스를 죽이지 말고 `tcp:18088 tcp:8088` + `PLEOS_VC_PORT=18088`.
5. 브라우저에서 `http://127.0.0.1:8088/`을 열고 **테스트 내내 탭을 유지**(닫으면 변경 요청이 503).
6. `./gradlew :app:assembleDebug` → `"$adb" -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk` → `"$adb" -s <serial> shell am start -n com.example.pleossetup/.MainActivity`
7. 앱에서 Check support → Read value → Start updates, 콘솔에서 Door Position Front left 변경, `"$adb" -s <serial> logcat -s PleosSetupProbe:I`로 확인.
8. 에뮬레이터 안정성: `uiautomator dump`(`.tools/probe-ui.sh tap|dump`)는 크래시를 유발한 이력이 있으므로 화면 조작은 직접 클릭 또는 `.tools/probe-ui.sh button <check|read|start|stop|reconnect>`(기본 IVI 레이아웃 고정 좌표; 부팅 직후 전체 폭 레이아웃에서는 빗나감)를 사용한다.
9. Android Studio를 쓸 때: `source .tools/env.sh && "$STUDIO_HOME/bin/studio.sh"` (같은 SDK·AVD 홈·Studio 설정을 보도록 환경 변수를 넘김). Gradle JDK는 IDE 설정에서 `jdk17` 경로로 맞춘다.
10. 종료: 앱에서 Stop updates → `"$adb" -s <serial> forward --remove tcp:8088` → `"$adb" -s <serial> emu kill`.

## 제약과 다음 조치

- 차단 원인
  - host GPU 모드에서 에뮬레이터가 간헐적으로 SIGSEGV 종료(위 표). SDK 검증 자체는 완료했지만, 시연·녹화 전 안정화가 필요하다.
  - Playground 계정·SDK 신청 상태, 새 세션의 지침·스킬 인식, IDE 내 확인은 사용자 로그인·조작이 필요해 수행하지 못함.
- 사용자에게 필요한 작업
  1. Claude Code 입력창에서 `/`를 입력해 `/drawio-skill`이 메뉴에 보이는지 확인(나머지 4개 스킬과 AGENTS.md import는 2026-11-07 확인됨). 확인은 내장 명령 `/memory`·`/context`로 하며, `/anthropic-skills:import-memory`는 다른 AI의 메모리를 가져오는 별개 기능이라 이 확인과 무관함. Codex를 쓰면 Codex 새 세션에서도 지침·스킬 목록 확인.
  2. **Pleos Playground 로그인**(https://pleos.ai/playground/console/projects) 후 프로젝트 생성·SDK 사용 신청 상태 확인. 패키지명은 아이디어 확정 후 실제 앱 ID로 등록(현재 `com.example.pleossetup`은 더미).
  3. 첫 커밋 전에 `git config user.name` / `user.email`을 본인 값으로 설정(현재 미설정, 임의 생성하지 않음).
  4. (선택) 에뮬레이터 안정화 방향 결정: ① SDK Manager에서 Emulator 37.2.12 → 37.2.13 업데이트(interlude와 공유하는 SDK라 보류함), ② PRIME offload 없이(Intel iGPU) `-gpu host` 시험, ③ 기능 검증 한정으로 `-gpu swiftshader_indirect` 사용(공식 GPU 조건·시연 성능 검증 대체 불가). 원하면 순서대로 진행 가능.
  5. Virtual Console 사용 중에는 브라우저 탭을 열어 둔다.
  6. 원격 저장소는 요청 시 생성. Private으로 할 경우 심사용 GitHub 계정 공지를 먼저 확인(확인일 기준 미공지). 접수 마감 **2026-11-15**.
  - sudo·그룹 변경·재로그인은 현재 필요 없음. 단, `/dev/kvm` 접근이 로컬 데스크톱 세션의 logind ACL로 주어지므로 SSH·원격 세션에서 에뮬레이터를 띄우면 실패할 수 있다. 그때는 `sudo usermod -aG kvm autolab` 후 데스크톱 로그아웃·재로그인이 필요하다.
- 다시 실행할 검증: 안정화 조치 후 host GPU에서 시나리오 A·B·C 재실행, 폼팩터(창 크기) 테스트, Android Studio Device Manager에서 `Pleos_Connect_v2.1_probe` Cold Boot 확인, 새 세션 지침·스킬 인식.
- 공식 문서 간 차이·해결 근거: 위 "공식 근거와 결정"과 "공식 문서와 실제 SDK 아티팩트의 차이" 절. Vehicle 2.3.1(샘플) vs 2.3.2(문서·릴리스 노트·Maven) → 2.3.2. RAM 8192(이미지 기본) vs 4 GB(가이드) → 4 GB.
- 제품 개발 전 추가 결정: 타겟 사용자·핵심 경험·데모 흐름, 기능별 실제 API·read/write·권한·정차 조건, 제어 기능 사용 여부(`CONTROL_CAR_*`·readback·복원 정책), Gleo·음성 필요성, 제출용 release 서명키와 APK 배포 방식, 앱 이름·applicationId.

---

# Heartz 개발 결과 (M0 → M1 → M2, 2026-11-09)

- 실행 일시 / 담당: 2026-11-09 16:22–17:50 KST / Claude Code (Opus 5.5), 사용자 `autolab`(root 아님)
- 기준 문서: `.docs/HEARTZ-PLAN.md` 8절. 범위는 사용자 요청에 따라 M1을 넘어 M2(작성·검수·두 번째 사용자 수신)와 UI 정리까지 진행. M3(주행 중 녹음·전송)은 6절 조건 미충족으로 **비활성**.
- 재사용: 기존 Gradle Wrapper·JDK17·SDK·AVD `Pleos_Connect_v2.1_probe`·`.tools/env.sh`·`start-emulator.sh`·버전 카탈로그. 세팅 단계 검증 앱(`:app`, `com.example.pleossetup`)은 그대로 두고 제품은 새 모듈로 분리.
- 추가 모듈: `:core`(JVM 도메인·편성·검수 규칙·시드), `:server`(로컬 개발 API, JDK HttpServer, 127.0.0.1 바인딩), `:heartz`(Android 앱 `ai.heartz.app`).
- 사용자 결정(2026-11-09): ① 청취 화면 Activity에 `distractionOptimized` 선언 ② applicationId `ai.heartz.app`.

## 공식 근거와 결정 (확인일 2026-11-09, curl로 원문 확인)

| 결정 대상 | 근거 | 결정 |
| --- | --- | --- |
| 공모전 제출 조건 | https://appdevchallenge.auto-hmg.io/guide/submission , 공지 3건(…0001, …0005, …0006) | 11-06 확인 내용과 동일: 접수 ~2026-11-15, GitHub·APK·영상 선택(가산점), `apk/팀명_앱이름_release.apk`(실행 가능한 release), README 4항목. **심사용 GitHub 계정은 여전히 미공지**. 원격 저장소 생성·push·초대 안 함(요청 없음) |
| 위치 SDK | FusedLocation intro/permissions/data-access/initialization (SDK v1.1.3 기준), Maven `release=1.1.3`, 소스 JAR | `ai.pleos.playground:FusedLocation:1.1.3`. 권한 `pleos.car.permission.FUSED_LOCATION`(dangerous → 런타임 요청). `isSupported()` → `initialize()` → `registerFusedLocationCallback`, null 콜백은 위치로 취급 안 함 |
| 내비 SDK | NaviHelper intro/importing/permissions/initialization/event-listeners (v3.0.5), Maven `release=3.0.5`, 공식 샘플 `pleos-sample-navi-app@76ed592` | `NaviHelper:3.0.5`. 현재 위치는 `getCurrentLocationInfo()` 요청→`onCurrentLocationInfo` 조회형이라 편성에는 쓰지 않고 M0 비교·내비 상태 관찰용. `NAVI_ROUTE`(normal). 시험 경로 요청용 `NAVI_ROUTE_SEARCH`는 **debug 매니페스트에만** |
| 주행 제한 | driving-restriction-development-guide, driving-restiction-policy, app-category, driver-distraction-guideline | AAOS `CarUxRestrictionsManager` 구독(`useLibrary("android.car")`, 기기 부트클래스패스 `com.android.car.framework`). 연결 전·실패는 Unknown = 주행 화면으로 처리(주차로 간주 안 함) |
| DO 선언 | 정책: DO 미선언 앱은 Drive에서 사용 불가. Music/Audio 카테고리는 주행 중 "단순 시각 정보·재생/일시정지가 제한적으로 허용될 수 있음". **DO 선언 방법·심사 승인 절차는 공개 문서에 없음** | 사용자 결정으로 `MainActivity`에 AAOS 표준 meta-data `distractionOptimized=true`. 주행 중 화면은 CarUxRestrictions에 따라 정적 카드 + 듣기/정지만(목록·녹음·설정·키보드 없음). Pleos 심사 승인 여부: **NOT_RUN(미확인)** |
| 앱 카테고리·미디어 | music-audio-app-developement-guide, audio-focus-development-guide | `android:appCategory="audio"`, `androidx.car.app.launchable`, Media3 `MediaLibraryService`(MediaBrowserService 호환)·MediaSession, 자동 재생·resumption 없음, 영구 포커스 상실 시 stop, `onTaskRemoved` 정지→stopSelf→super |
| Media3 | AAR 메타데이터 직접 확인 | **1.9.4**: `minCompileSdk=35`, minSdk 23, kotlin-stdlib 2.0.20 → AGP 8.8.2·compileSdk 35·Kotlin 2.0.0과 호환 |
| 직렬화 | Kotlin 플러그인은 Kotlin 버전과 같아야 함 | `kotlin.plugin.serialization 2.0.0` + `kotlinx-serialization-json 1.7.3`(Kotlin 2.0 계열) |
| 마이크 | configure-pleos-connect-emulator "마이크 접근 설정": Extended Controls > Microphone에서 오디오 입력 수신 | 호스트 오디오 입력은 **켜지 않음**(노트북 주변 소리가 녹음되므로 사용자 결정 필요). 앱 권한·파일 생성까지만 확인 |

## 실제 SDK 아티팩트·AVD에서 확인한 차이

1. AVD의 FusedLocation **Service는 1.0.0**(SDK 1.1.3의 최소 요구 0.5.0 이상). SDK 1.1.3의 `addProximityAlert`도 이 Service에서 `onRegistered`·ENTER·EXIT까지 동작.
2. FusedLocation `LocationData.timestampMillis`는 벽시계 기준(수신 지연 1–36 ms). 정지 입력에서도 약 1초마다 같은 좌표를 재전달. `heading`은 정지·`geo fix` 입력에서 0.0, `confidence` 5.0.
3. **NaviHelper 현재 위치(37.4127,127.0947 = 성남 수정구)는 에뮬레이터 GPS 입력과 다름**. 원인: 기본 내비(`ai.umos.maps.android.navigation.app` 3.3.2-rc5-hotfix.3)의 `GpsLocationSource`가 에뮬레이터 `geo fix`가 만든 NMEA를 체크섬 불일치(`expected=47, actual=45`)로 거부. 체크섬이 올바른 `geo nmea` 5회 전송도 내비 위치를 바꾸지 못함(에뮬레이터가 마지막 `geo fix`를 1초마다 재전송) → **미해결**. Heartz 편성은 FusedLocation 기준이므로 동작에는 영향 없음. 단, 시연 화면에서 내비 지도 위치(수정구)와 Heartz 편지 장소(이촌)가 일치하지 않는다.
4. NaviHelper는 연결 직후 현재 안내 상태(`onNavigationStatus`)를 보내지 않음(변경 시에만으로 추정). 시험 경로 요청 시 내비 앱이 앞으로 오면서 Heartz가 onStop → 리스너 해제되어 route 이벤트는 관찰하지 못함.
5. 시험 경로 안내 시작 시 내비의 오디오 포커스 요청은 0건(이 AVD에서 내비 음성 안내 미관찰).
6. Pleos 권한 `FUSED_LOCATION`에 라벨이 없어 시스템 대화상자가 "알 수 없는 작업 실행"으로 표시됨.
7. AAOS 다중 사용자(현재 user 10): `adb push /sdcard`는 user 0 저장소, `run-as`도 user 0 기준이라 앱 파일 확인 불가 → 앱 로그(파일 크기·측정 길이)로 확인.
8. Media3 `AudioFocusManager`는 **STATE_IDLE에서만 포커스 반납**(ENDED·일시정지에서는 유지) → 아래 결함 1.
9. release 설치 직후 시스템(GAA/GNB)이 `HeartzPlaybackService`를 미디어 탐색 서비스로 인식해 `MEDIA_TEMPLATE` 앱 목록에 등록. 단, 재생 중에도 왼쪽 시스템 미디어 위젯은 "재생 중인 항목 없음"(미디어 센터 표시 연동은 미확인).

## 검증 결과

> 구분: **AVD 동작** = 실제 AVD·SDK에서 앱이 받은 콜백·상태로 확인. **테스트 입력** = `adb emu geo fix`, Virtual Console 기어 변경, `input tap`(사용자 탭 재현), `pm grant`. **fake/로컬 테스트** = JVM 단위 테스트(실제 SDK 증거 아님).
> 실차·DevBox 검증이 아니다. 실제 사람 음성 사연은 없다(시드는 합성 테스트 음원).

| 단계·항목 | 상태 | 테스트 입력 | AVD에서 앱이 받은/보인 것 (증거) |
| --- | --- | --- | --- |
| M0 런타임 위치 권한 | PASS | `input tap` 허용 | 시스템 대화상자 → `FUSED_LOCATION: granted=true, USER_SET` (`m0-01-permission-dialog.png`) |
| M0 위치 수신 | PASS | 없음(직전 `geo fix 126.97 37.52`) | `registerFusedLocationCallback` 후 1초 주기 `callback 37.5200,126.9700` ageMs 1–5 |
| M0 입력 좌표 변경 ↔ 앱 수신 | PASS | 17:06:25.897 `geo fix 126.97100 37.52215` | 17:06:25.790 SDK callback `37.5222,126.9710` → 17:06:25.791 `card shown s-seed-ichon-cheerful` (`m0-02`) |
| M0 주행 제한 | PASS | 17:07:01.895 VC `PUT /vehicle/gear?gear=Drive` | 17:07:01.813 `callback requiresDO=true active=0x50`, 주행 화면 전환. Car Driving State 1(idling) |
| M0 DO 반영 | PASS | 기어 D | 세팅 단계 프로브(DO 미선언)는 "주행 중에는 사용할 수 없습니다"로 가려졌으나 Heartz는 표시됨, 정적 카드+듣기만 (`m0-03`) |
| M0 기본 내비와 나란히 | PASS | 창 핸들 드래그(`input swipe`) | Heartz 왼쪽 창(약 845dp)·내비 오른쪽, 리사이즈 중 위치 콜백 지속 (`m0-09-split-parked.png`, `m0-10-split-driving.png`) |
| M0 내비 안내 활성/비활성 차이 | PARTIAL | debug 버튼 `requestRoute`(→서빙고) | 안내 시작 확인(`m0-08`). 안내 중에도 위치 콜백 지속, 간격 1–2초로 불규칙(비활성 시 1초). 내비 상태 콜백은 미수신(차이 4). 내비 위치 불일치(차이 3) |
| M0 보이지만 소리 없을 때 수신 | PASS | 카드 표시 상태 유지 | 위치 콜백 계속, CarAudioService 포커스 보유자 **0건** |
| M0/M1 재생 포커스 | PASS | 17:07:20 `듣기` 탭(주행 중) | 포커스 보유자 = Heartz `USAGE_MEDIA/SPEECH, Wants Pause Instead of Ducking: true` (`m0-04`) |
| 재생 종료 시 포커스 반납 | PASS(수정 후) | 끝까지 재생 | 수정 전 FAIL: ENDED 후에도 보유(결함 1). 수정 후 17:10:06 `ended -> stop()` → 보유자 0건 |
| 사용자 "그만 듣기" | PASS | 탭 | `abandonAudioFocus()` 즉시 기록 |
| 다른 MUSIC 앱과 경쟁 | BLOCKED | `com.android.music` AudioPreview | user 10 저장소 접근 불가(ENOENT), http 평문 차단(setDataSource failed) → 경쟁 재현 실패 |
| 내비 음성·통화와의 포커스 | NOT_RUN | — | 이 AVD에서 내비 음성 포커스 요청 미관찰, 통화 미시험 |
| 근접 알림(addProximityAlert) | PASS(진단용) | `geo fix` 이촌 안/밖 | `onRegistered` → ENTER(17:14:49) → EXIT(17:14:55). 편성에는 미사용 |
| NaviHelper getCurrentLocationInfo | PASS(값 불일치) | 진단 버튼 | `onCurrentLocationInfo 37.4127,127.0947` (차이 3) |
| **M1 시연 경로 주행 E2E** | PASS | 기어 D + `tools/demo-drive.sh` 168지점(15 m/1 s) | SDK callback 169회, 카드 이촌(17:46:47)→동부이촌동(17:47:46)→서빙고(17:48:41), **근접 도로(약 180 m) 편지 0회**. 카드 표시만으로 소리 없음 (`e2e-demo-drive-input.log`, `e2e-demo-drive-app.log`, `m1-e2e-*.png`) |
| M1 무작위 선택·이미 들은 편지 제외 | PASS | 이촌 재진입 | 들은 2편 제외로 재진입 시 카드 없음 → 근접 알림만 동작. 다른 계정에서 무작위 선택 관찰 |
| M2 마이크 권한·파일 생성 | PASS(음성 내용 미확인) | 탭 녹음 5초 | `RECORD_AUDIO granted`, `file created bytes=64945 durationMs=5039 (measured=true)`. 호스트 오디오 입력 꺼짐 → 실제 음성이 담겼는지 미확인 |
| M2 공개 제출 → 검수 대기 | PASS | 탭 | `status=PENDING_REVIEW` (`m2-03`) |
| M2 대기 사연 비공개 | PASS | 계정 전환 | 바다 계정 feed=5(대기 미포함). 개발 서버 API: 다른 계정 audio/story 404, 승인 시도 403, 무토큰 401 |
| M2 운영자 검수 | PASS | 운영자 탭: 원본 재생 후 승인 / 시드 반려(장난·반복 음성) | `APPROVE -> APPROVED playable=true`, `REJECT -> REJECTED` |
| M2 두 번째 사용자 수신·재생 | PASS | 계정 전환 + 이촌 | 기기 내 데모: 바다 계정 카드로 승인 사연 도착·재생. 개발 서버: 하늘 계정 카드 도착, `GET …/audio -> 200` |
| M2 답장(나도 여기서…) | PASS | 탭 | 같은 녹음·검수 흐름, 원글 장소 고정, 최대 20초, `PENDING_REVIEW` (`m2-08`) |
| 녹음 중 주행 시작 | PASS | 녹음 3.5초 중 VC 기어 D | 즉시 중단 `interrupted by driving restriction`, 초안 보관·사유 표시 → 주차 후 같은 clientDraftId로 재전송 `PENDING_REVIEW` (`m2-07`) |
| 개발 서버 경로 | PASS | `server --data build/heartz-server-data` | 생성·업로드·제출이 서로 다른 요청·결과(`POST /stories`, `PUT …/audio`, `POST …/submit`) (`dev-server.log`) |
| release 빌드(R8) 실행 | PASS | 저장소 밖 임시 사본을 debug 키로 시험 서명, `pm grant` | 위치 콜백(좌표 `(redacted)`)·카드·재생 정상, FATAL/ClassNotFound 0 |
| 에뮬레이터 안정성(host GPU) | 관찰 | 약 1.5시간 사용, uiautomator 미사용 | SIGSEGV 종료 0회 |
| 지나면서 녹음·전송(M3) | → 아래 "Heartz 추가 개발" 절에서 갱신 | — | 사용자 요청(2026-11-09 21시)으로 구현·AVD 검증 |
| Gleo end-to-end / STT·TTS | → 아래 절에서 갱신 | — | STT·TTS는 AVD에서 동작 확인, Gleo는 모의 호출만 |

### AVD에서 찾아 고친 결함 (systematic-debugging)

1. **재생 종료 후 포커스 미반납** — 원인: Media3 `AudioFocusManager.shouldHandleAudioFocus()`가 `playbackState != STATE_IDLE`일 때 포커스 유지. 수정: 서비스가 ENDED에서 컨트롤러가 ENDED를 먼저 받도록 한 번 post 후 `stop()`(IDLE). AVD에서 보유자 0건 확인.
2. **끝까지 들은 카드가 다시 "듣기"로 표시** — 원인: ENDED→IDLE이 5 ms 안에 이어져 컨트롤러가 ENDED를 건너뜀. 수정: 순수 함수 `isCompleted(state, position, duration)`(IDLE이어도 끝 위치면 완료). TDD red 4 → green.
3. **다른 계정의 재생 상태가 카드에 표시** — 원인: 플레이어 상태가 계정과 무관하고 카드가 `playback.ended`를 직접 읽음. 수정: 계정 전환 시 재생 정지, 카드는 세션에서 관찰한 `playedToEnd`만 사용. TDD red 1 → green.
4. UI: 설정의 마이크 권한 표시 갱신 안 됨, 좁은 창에서 검수 행·녹음 헤더 겹침, 초안의 답장 표시 누락 → 수정 후 AVD 확인.
5. lint 오류 3: API 28에서 `MediaMetadataRetriever.use`(API 29 AutoCloseable) → `release()`, Media3 UnstableApi opt-in 누락 → 수정.

### 자동 테스트 (fake/로컬, 실제 SDK 증거 아님)

| 명령 | 결과 |
| --- | --- |
| `./gradlew :heartz:clean test :heartz:lintDebug :app:lintDebug :heartz:assembleRelease` | exit 0. core 64 / server 7 / heartz 30(debug+release 변형 각 15) / app 18, 실패 0. lint 오류 0, 경고 1(DataExtractionRules) (`build-test-lint-release.log`) |
| TDD 기록 | `tdd-core-red-1..6`, `tdd-core-green-1..6`, `tdd-server-red/green`, `tdd-app-red-1..4`, `tdd-app-green-1..2` (`.docs/evidence/heartz/`) |

## 기획서 6절(주행 중 녹음·전송) 상태

> 이 표는 첫 작업 시점(17:50) 기록이다. 최신 상태는 아래 "Heartz 추가 개발" 절의 6절 표를 따른다.

| 조건 | 상태 | 비고 |
| --- | --- | --- |
| 명시적 시작·종료 입력 | NOT_VERIFIED | 스티어링 버튼·Gleo 앱 액션 등 허용 입력 미확인 |
| 마이크 | NOT_VERIFIED(부분) | 주차 상태 앱 녹음 파일 생성은 확인. 실제 음성 입력·통화/음성비서 충돌 미확인 |
| 시작 시점 위치 고정 | NOT_VERIFIED | 주차 녹음은 작성자가 장소를 고름 |
| 허용 입력으로 공개 의사 확인 | NOT_VERIFIED | — |
| Gleo 연동 범위 | NOT_VERIFIED | Gleo가 원본 오디오를 넘기거나 음색 TTS를 제공한다고 가정하지 않음 |

→ `DrivingRecordingGate.isEnabled() == false`. 주행 중에는 녹음 UI 자체가 없고, 주차 녹음 중 주행 제한이 걸리면 즉시 멈춘다.

## Heartz 재시작·시연

1. 위 "재시작" 1–5단계(에뮬레이터 Cold Boot, forward 8088, Virtual Console 탭 유지).
2. (선택) 개발 서버: `./gradlew :server:installDist && server/build/install/server/bin/server --data build/heartz-server-data --seed-audio seed/audio` (127.0.0.1:8787, 에뮬레이터에서 10.0.2.2:8787). 앱 설정 > 데이터 연결 > 개발 서버(debug 빌드만).
3. `./gradlew :heartz:assembleDebug && "$adb" -s <serial> install -r heartz/build/outputs/apk/debug/heartz-debug.apk && "$adb" -s <serial> shell am start -n ai.heartz.app/.MainActivity`
4. 주차(P) 상태에서 위치 권한 허용 → 창 핸들(오른쪽 가장자리 ⋮)을 왼쪽으로 끌어 기본 내비와 나란히 배치.
5. VC에서 기어 D → `tools/demo-drive.sh <serial>` (테스트 입력). `"$adb" -s <serial> logcat -s Heartz:I`로 `callback`·`card shown` 확인.
6. 계정 전환·검수: 주차 후 설정 > 테스트 계정(하늘·바다·운영자).

## 제약과 다음 조치

- 시연 정합성: 기본 내비 위치가 에뮬레이터 GPS를 따르지 않음(차이 3). 시연 영상에서 내비 지도와 편지 장소 불일치를 설명하거나, 내비 위치 입력 방법(Pleos 문의 또는 Virtual Console 후속 버전)을 확인해야 한다.
- 지도 타일이 렌더링되지 않음(흰 화면, 원인 미확인).
- 실제 음성 사연: 사용 허락을 받은 음성을 앱 녹음 경로로 남기려면 호스트 마이크 입력을 켜야 함(사용자 결정).
- 오디오 포커스 경쟁(다른 MUSIC 앱·내비 음성·통화)은 이 AVD에서 재현 수단을 찾지 못함.
- DO 선언의 Pleos 심사 승인, 미디어 센터 표시 연동, 스티어링 휠 키 동작은 미확인.
- 제출 전 필요: 팀명 확정, release 서명키 생성(사용자), `apk/팀명_Heartz_release.apk` 배치, 원격 저장소 생성·공개 범위(요청 시), 심사용 GitHub 계정 공지 확인.

## Heartz 시드 데이터

- 장소 4곳(`core/.../seed/SeedData.kt`): 이촌·동부이촌동·서빙고(시연 경로 위), 한강 산책로 입구(근접 도로 시험 구간, 경로에서 약 180 m). 좌표는 서울 이촌동 일대를 **대략적으로** 따른 값이며 실제 도로 정합성은 검증하지 않았다. 구간 판정 반경 45 m.
- 테스트 계정: 하늘·바다(청취자), 운영자(검수), 시드 작성자 2명(로그인 목록 비표시). 실명 확인 없음.
- 사연 6편: 승인 5편(이촌 2·동부이촌동·서빙고·한강 산책로) + 검수 대기 1편. 모두 `sampleLabel = "합성 테스트 음원 · 실제 사연 아님"`.
- 음원 `seed/audio/*.m4a`: 호스트에 TTS가 없어(`espeak`·`pico2wave` 등 없음) ffmpeg로 생성한 **합성 화음**(15–30초, AAC 64 kbps mono). 예:
  `ffmpeg -f lavfi -i "aevalsrc=0.16*sin(2*PI*261.63*t)*(0.75+0.25*sin(2*PI*0.25*t))+0.11*sin(2*PI*329.63*t)*(…)+…:s=44100:d=30" -af "afade=t=in:d=2,afade=t=out:st=27:d=3,aecho=0.8:0.7:120:0.3" -ac 1 -c:a aac -b:a 64k seed/audio/seed-warm.m4a`
- 앱(기기 내 데모 백엔드)과 개발 서버가 같은 시드·같은 음원 파일을 쓴다(`heartz` assets에 `seed/`를 포함).

---

# Heartz 추가 개발 (STT·TTS · M3 지나면서 녹음 · 미디어 하위 카테고리 · Gleo · 검증 보강 · 데모, 2026-11-09)

- 실행 일시 / 담당: 2026-11-09 21:00–22:50 KST / Claude Code (Opus 5.5), 사용자 `autolab`(root 아님)
- 사용자 요청(원문 요지): ① 이전에 "구현했지만 AVD에서 눌러 보지 않은 것" 검증 ② STT·TTS 연동, 지나면서 녹음·전송(M3), 미디어 앱 라이브러리 하위 카테고리 구현 ③ 내비 위치 불일치·지도 타일은 **추가 SDK가 풀릴 때까지 보류(사용자 결정)** ④ 써드파티 앱이 지도 위에 UI를 그릴 수 있는지 검증 ⑤ 흐름대로 데모 영상 녹화.
- M3는 기획서상 "6절 조건 확인 후 활성화"다. 사용자가 구현을 요청했으므로 구현하되, **기본값은 꺼짐**이고 주차 중 설정에서 사용자가 켜야 주행 화면에 버튼이 생긴다. 6절 조건별 실제 상태는 아래 표에 그대로 남긴다(전부 확인된 것은 아님).

## 공식 근거와 실제 아티팩트 확인 (2026-11-09)

| 대상 | 근거 | 결정·확인 |
| --- | --- | --- |
| TTS / STT SDK | TextToSpeech·SpeechToText intro, importing-sdk, declaring-permissions, initialization-and-release, event/result-listeners, set-locale, app-registration, start-and-stop, sending-audio-data, error-code, SDK 릴리스 노트(2026-10-19) | `ai.pleos.playground:TextToSpeech:3.3.1`, `SpeechToText:3.3.1`(Maven release=3.3.1). 권한 `pleos.car.permission.TTS_SERVICE`·`STT_SERVICE`. **ON_DEVICE 모드**: 앱 등록·Client ID/Secret 불필요 → 비밀키 없음. AAR `javap`로 시그니처 확인(`speak(String, isFinal)`, `request()`, `sendAudio(ByteArray)`, `completeAudioSend()`, `ResultListener.onUpdated(stt, completed)`) |
| CAAS 서비스 | AAR 매니페스트 `ai.pleos.playground.caas.min_version_name=4.1.29` | AVD `ai.pleos.playground.caas` **4.2.10** → 충족 |
| 미디어 하위 카테고리 | music-audio-app-developement-guide "미디어 앱 라이브러리 연동": 음악/팟캐스트/오디오북/라디오 하위 카테고리 전달 **필수**, 단일 평면 목록 지양 | **전달 키는 공개 문서에 없음**(웹 문서 확인). AVD Media Center(`ai.umos.mediaapp`) dex에서도 전용 키 미발견. → Media3 표준 `MediaMetadata.mediaType`(루트 FOLDER_PODCASTS, 폴더 PODCAST, 항목 PODCAST_EPISODE) + 폴더 구조(지금 이 길의 편지 / 오늘 지나친 편지) + 앱 네임스페이스 루트 extra `ai.heartz.app.MEDIA_SUBCATEGORY=PODCAST`(앱 정의 값, Pleos 공식 키 아님) |
| Gleo 앱 액션 | app-integration-guide core-concepts·app-capability·action_callback·examples·deliver-schema, AVD `ai.umos.mediaapp`의 `assets/schema.json`·매니페스트 | `assets/schema.json`(함수 3개: START_STORY_RECORDING, FINISH_STORY_RECORDING, PLAY_ARRIVED_LETTER). 가이드상 액션 = 함수 이름, AVD 시스템 앱은 `패키지.함수 이름` → 둘 다 intent-filter. 파라미터는 `ACTION_PARAM`(JSON) 또는 개별 extra, 응답은 `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE`(packageName, response JSON) |
| 주행 중 입력 | driver-distraction-guideline, driving-restiction-policy, navigation-app-development-guide | 공개 문서에 탭 횟수·시간 한도는 없음. "음성 명령·물리 버튼 연계", 내비 앱 기준 "단일 터치 기반 주요 기능" 허용. → 주행 화면은 큰 버튼 1개(시작), 녹음 중 큰 버튼 2개(끝내기·취소)만. 키보드·목록·장소 선택·편집 없음 |
| 지도 위 UI | app-structure-screen-model, window 정책: "Driving view는 앱이 임의로 가리거나 종료할 수 없다", 오버레이 규정은 문서에 없음. NaviHelper 3.0.5 / navi-data 3.0.4 `javap` | NaviHelper 공개 API에 **지도 마커·오버레이 API 없음**(경로·경유지·조회·`requestNavigationScreen`, 내부 `updateOccupiedRegion(Rect)`만) |

## 구현 요약

- `platform/speech/PleosSpeech.kt`: `PleosTextToSpeech`·`PleosSpeechToText`(ON_DEVICE, ko-KR). 초기화 반환과 `OnInitializeListener.onSuccess` 콜백을 구분하고, 타임아웃·오류를 성공으로 바꾸지 않는다.
- `platform/speech/PcmDecoder.kt`·`ReviewAssistRunner.kt`, `core/moderation/ReviewAssist.kt`: 녹음 → 16 kHz PCM → STT(sendAudio) → 규칙 기반 의심 구간(전화번호·주소·반복·갑작스러운 큰 소리·무음·받아쓰기 실패) → 백엔드 `attachAssist`. **승인·반려를 하지 않으며** 검수 대기열 정렬(플래그 우선)만 돕는다. 받아쓰기는 작성자·운영자에게만 내려간다(`HeartzService.redactedFor`).
- `core/drive/Roadside.kt`, `ui/drive/RoadsideController.kt`·`RoadsideViews.kt`: M3. 시작 순간의 유효 위치로 장소 고정 → TTS 안내 → 녹음용 포커스(`GAIN_TRANSIENT_EXCLUSIVE`, `USAGE_ASSISTANT`) → MediaRecorder → 끝내기/취소. 포커스 상실(통화 등)·앱이 화면에서 사라짐 → 즉시 중단, **기기 초안으로만**(공개 안 함, 안내 음성 없음). 공개 범위는 주차 중 설정(나만 듣기 기본 / 말로 정하기 / 바로 검수). 장소를 못 정하면 초안 + 주차 후 장소 선택(시작 위치에서 가까운 순, 원본 좌표는 기기에만 4자리로 보관·서버 미전송).
- `data/DraftSender.kt`: 주차 녹음과 M3가 같은 생성→업로드→제출 경로, 같은 clientDraftId 재시도.
- `platform/gleo/GleoProtocol.kt`, `assets/schema.json`, `MainActivity.handleGleo`.
- `HeartzPlaybackService`: 폴더 구조 + mediaType(위 표).
- 시드 음원 6편을 **Pleos TTS 낭독(합성 테스트 음원)**으로 교체: 문장(예: "…오늘 꽃이라도 사들고 가야겠네요.")을 AVD TTS로 말하게 하고 호스트에서 에뮬레이터 출력 스트림만 녹음·정규화 + 이전 합성 화음을 0.16 배경으로 혼합. 라벨은 그대로 "합성 테스트 음원 · 실제 사연 아님". 길이 8–12초(`SeedData` 갱신).

## AVD에서 확인한 것 (테스트 입력과 구분)

> 테스트 입력: `adb emu geo fix`(위치), Virtual Console(기어), `input tap`(사용자 탭 재현), `adb emu gsm call`(에뮬레이터 모뎀 전화), `input keyevent`(미디어 키), debug 전용 `DebugCommandReceiver`(TTS·STT·브라우즈·오버레이 시험), `am start -a <Gleo 액션>`(Gleo 모의 호출), `pm grant --user 10`·`appops`(시험용 권한).

| 항목 | 상태 | 테스트 입력 | 앱이 받은/보인 것 (증거) |
| --- | --- | --- | --- |
| TTS 초기화·발화 | PASS | debug TTS 명령 | `initialize callback onSuccess (ON_DEVICE, ko-KR)`, `onReady→onStart→onDone`(54자 6.8초), 호스트에서 에뮬레이터 출력으로 음성 녹음됨 |
| STT 파일 경로(sendAudio) | PASS | TTS로 만든 음성 파일(debug assets) | 100 ms 실시간 전송 + 1초 무음 덧붙임 → `completed=true` "이천역 앞을 지날 때마다 할머니가 사주시던 붕어빵이 생각나요 오늘 꽃이라도 사들고 가야겠네요"(이촌→이천 오인식) |
| STT 마이크 경로(request) | BLOCKED | 가상 마이크에 TTS 음성 재생 | `onReady`만 수신, 인식 이벤트 0, 시스템 녹음 기록에 CAAS 녹음 시작 없음 → 아래 마이크 차단과 같은 원인으로 판단 |
| **AVD 마이크 실제 음성 입력** | **BLOCKED** | PulseAudio 가상 입력(`heartz_vmic`, 노트북 마이크 미사용) + `adb emu avd hostmicon`, 재부팅 `-allow-host-audio`, AudioSource MIC·VOICE_RECOGNITION·UNPROCESSED·CAMCORDER·DEFAULT | 호스트 가상 입력에는 신호(-8 dB)가 있고 qemu 입력 스트림이 그 장치에 연결됐으나 **게스트 녹음 최대 진폭 0–2**. 녹음 파일 생성·권한·포커스·시스템 마이크 표시(상태 표시줄 초록 아이콘)는 확인 |
| M3 시작(주행 화면 버튼) | PASS | 기어 D, 이촌 구간, `input tap` | `start source=TOUCH anchor=valid place=p-ichon`, TTS `onDone` 뒤에 `recording focus request result=1` → `MediaRecorder.start` (안내 음성 녹음 안 됨) |
| M3 시작 위치 고정 | PASS | 녹음 중 서빙고로 위치 이동 | 끝내기 후 `saved … status=PENDING_REVIEW roadside=true`, 장소 = 이촌역 앞 이촌로 (`m3-done.png`) |
| M3 통화 중단 | PASS | `emu gsm call` | telecom `GAIN_TRANSIENT`(벨소리) → `recording focus change=-2` → `outcome=LocalDraft`, 프리셋 "바로 검수"여도 미전송, 결과 안내 음성 없음(수정 후) |
| M3 Gleo 모의 호출 | PASS(모의) | `am start -a ai.heartz.app.START_STORY_RECORDING` / `FINISH_STORY_RECORDING {"decision":"CANCEL"}` / `PLAY_ARRIVED_LETTER` | 시작·취소(파일 삭제)·편지 재생 + 응답 브로드캐스트 JSON 송신. **실제 Gleo 음성 → 호출 E2E는 미검증**(Gleo AI 앱 `ai.umos.ambientai`는 호출어를 마이크로 받음 → 마이크 차단) |
| 받아쓰기 첨부·표시 | PASS | 저장 | `assist attached … transcript=NO_SPEECH flags=[NO_SPEECH]`(무음 녹음), 실패 시 `FAILED`(timeout) + "직접 들어 확인". 운영자 검수 화면 표시·플래그 우선 정렬 (`r6.png`) |
| 미디어 하위 카테고리 전달 | PASS(레거시 브라우저 기준) | debug BROWSE_DUMP(`android.media.browse.MediaBrowser`, 시스템 미디어 앱과 같은 경로) | 루트 extras `ai.heartz.app.MEDIA_SUBCATEGORY=PODCAST`, 폴더 `BT_FOLDER_TYPE=1`·`EXTRAS_KEY_MEDIA_TYPE_COMPAT=16`(PODCAST), 편지 `EXTRAS_KEY_MEDIA_TYPE_COMPAT=3`(PODCAST_EPISODE). **Pleos 미디어 라이브러리가 이를 하위 카테고리로 읽는지는 미확인**(Media Center를 Intent로 열면 화면이 뜨지 않고 Heartz에 루트 요청도 오지 않음) |
| 시스템 미디어 소스 | 관찰 | 재생 | `CarMediaService` user 10 현재 재생 소스 = Heartz, Media button session = Heartz. Driving view 미디어 위젯은 계속 "재생 중인 항목 없음"(user 0 값은 비어 있음, 시스템 쪽 표시 문제로 추정) |
| 스티어링 휠(미디어 키) | PASS(키 이벤트 입력) | `input keyevent MEDIA_PAUSE/PLAY/PLAY_PAUSE/STOP` | 일시정지·재생·전환·정지(포커스 반납). 실제 스티어링 하드키 매핑은 공개 문서에 없음 |
| 재생 중 통화 | PASS | `emu gsm call` → `cancel` | `suppression=TRANSIENT_AUDIO_FOCUS_LOSS` → 정지 표시 → 통화 끝 `suppression=NONE` 자동 재개 (`f1.png`). 문구를 "다른 소리 때문에 잠시 멈춤"으로 수정 |
| 시스템 라디오와 경쟁 | NOT_RUN(재현 불가) | 라디오 앱 Gleo 브로드캐스트 `PLAY_FREQUENCY` | 라디오 프로세스만 시작, 포커스 요청·재생 없음(AVD 튜너 경로 없음으로 추정) |
| 나만 듣기 → 공개 제출 | PASS(수정 후) | 탭 | `PRIVATE` → 상세에서 공개 제출 → 검수 대기. 결함: 상세 화면이 상태를 다시 읽지 않음 → 수정 |
| 신고 → 운영자 신고 목록 | PASS | 탭 | "신고를 접수했어요", 운영자 신고 탭에 사유·사연 표시 |
| 작성자 숨기기·해제 | PASS(수정 후) | 탭 | 결함 2건 수정(아래). 해제 후 목록·배지 복원 |
| 운영자 공개 중 숨김 | PASS | 탭 | `HIDE -> HIDDEN playable=false`, 작성자 사연함에 "숨김 · 사유: 운영자 숨김" |
| 답장 승인 후 원글 답장 목록 | PASS | 탭 | 승인 전 작성자에게 "검수 대기", 승인 후 원글 상세 "이 자리에 남긴 답장 1" (`c5.png`) |
| 내 사연 삭제 | PASS | 탭 | 확인 대화상자 → `mine 2 → 1` |
| 네트워크 실패 → 초안 → 재시도 | PASS | 개발 서버 종료 후 제출, 재기동 후 재시도 | `save deferred … ConnectException` → 같은 clientDraftId 재전송 `PENDING_REVIEW`, 서버 로그 `PUT …/assist -> 200` |
| 지도 위 오버레이(써드파티) | 아래 별도 표 | | |

### 써드파티 앱이 지도 위에 UI를 그릴 수 있는가

| 확인 | 결과 |
| --- | --- |
| NaviHelper SDK | 지도에 그리는 API 없음(위 표) |
| 기본 설치 상태 `Settings.canDrawOverlays` | `false` |
| 사용자가 오버레이 권한을 줄 화면(`ACTION_MANAGE_OVERLAY_PERMISSION`) | 처리 Activity **없음**(Pleos 설정에 해당 화면 없음) |
| `TYPE_APPLICATION_OVERLAY` 창 추가 | `BadTokenException: permission denied for window type 2038` |
| (시험용) `appops set --user 10 … SYSTEM_ALERT_WINDOW allow` 후 | **창이 추가되어 내비 지도 위와 Driving view 위에 그려짐**(`overlay-granted.png`, `overlay-drivingview.png`) → 시험 후 `default`로 복원 |

→ 결론: 일반 사용자 경로에서는 써드파티 앱이 지도 위에 그릴 수 없다(권한을 줄 수단이 없고 기본 거부). 다만 AVD 창 관리자가 기술적으로 막는 것은 아니며, adb로 권한을 강제하면 그려진다. Pleos 문서는 Driving view를 가리지 못하게 정하고 있으므로 Heartz는 오버레이를 쓰지 않는다. `SYSTEM_ALERT_WINDOW`는 debug 매니페스트에만 시험용으로 선언했다.

### 이번에 찾아 고친 결함 (systematic-debugging)

1. **STT 최종 결과 유실** — 원인: `completed=true` 결과가 `onEndedRecognition` 뒤(~20 ms)에 도착하는데 종료 콜백에서 바로 NoSpeech로 끝냄. 수정: 종료 후 1.5초 유예. 또 빠른 전송에서는 최종 결과 없음 → 가이드대로 100 ms 실시간 전송 + 1초 무음.
2. **debug 수신기 ANR** — `goAsync()`를 60초 넘게 유지(무음 STT 타임아웃 대기). 수정: 즉시 반환 후 앱 스코프에서 처리. 수신기 context는 서비스 바인딩 불가 → applicationContext.
3. **숨긴 작성자의 카드가 계속 보임** — 원인: `LetterSession`이 구간 안에서 현재 카드를 유지하면서 숨김·공개 철회를 다시 확인하지 않음. TDD red 2 → green, AVD에서 `card cleared` 즉시 확인.
4. **숨긴 작성자의 편지가 지나친 편지·배지·시스템 미디어 트리에 남음** — 계산을 core `PassedLetters`로 통합(TDD red → green 3).
5. **공개 제출 후 상세 화면이 "나만 듣기"로 남음** — 상세 재조회 추가(AVD 확인). LibraryViewModel은 구체 Repository 의존이라 단위 테스트 없음.
6. **통화로 끊긴 M3 녹음 뒤 결과 안내 음성이 통화 중에 나옴** — 중단 시 화면 표시만(TDD red → green).
7. 문구: 일시적 포커스 상실 사유가 통화여도 "안내 음성 때문에"로 표시 → "다른 소리 때문에", 서버 미연결 시 완료 화면 제목 "남겼어요" → "초안으로 보관했어요", 숨긴 작성자 목록에 ID 대신 이름.
8. lint 오류: Media3 `LibraryParams.Builder.setExtras` UnstableApi opt-in 누락 → 수정.

## 기획서 6절 상태 (갱신, 앱 `DrivingRecordingGate.current`와 동일)

| 조건 | 상태 | 근거 |
| --- | --- | --- |
| 명시적 시작·종료 입력 | PARTIAL | DO 주행 화면의 큰 버튼 한 번(시작)·두 개(끝내기/취소) AVD 확인. Gleo는 모의 호출만. 주행 중 단일 탭의 Pleos 심사 허용 여부·스티어링 키 매핑은 문서에 없음 |
| 마이크 | BLOCKED | 권한·파일 생성·포커스 상실 중단 확인. AVD 게스트 입력 무음으로 실제 음성 미확인 |
| 시작 시점 위치 고정 | VERIFIED | 녹음 중 이동해도 시작 장소 유지(AVD) + 단위 테스트(변형 시험으로 실패 확인) |
| 허용 입력으로 공개 의사 확인 → 검수 대기 | PARTIAL | 주차 중 사전 설정 "바로 검수" → `PENDING_REVIEW` AVD 확인. "말로 정하기"(TTS 질문 + STT 답)는 마이크 차단으로 AVD 미확인(단위 테스트만: 분명한 "보내 줘"만 제출, 그 외 나만 듣기) |
| Gleo 연동 범위 | PARTIAL | schema·Intent·응답 계약 모의 확인. Gleo가 원본 오디오를 넘기거나 Gleo 음색 TTS를 쓴다고 가정하지 않음(앱 녹음 + Pleos TTS 사용) |

→ `DrivingRecordingGate.isEnabled() == false`(전부 VERIFIED 아님). M3는 기본 꺼짐이며, 주차 중 설정에서 켠 경우에만 동작한다. 첫 심사 포함 여부는 이 표로 결정한다.

## 자동 테스트 (fake/로컬, 실제 SDK 증거 아님)

| 명령 | 결과 |
| --- | --- |
| `./gradlew test :heartz:lintDebug :app:lintDebug :heartz:assembleDebug :heartz:assembleRelease :server:installDist` (22:47) | exit 0. core 98 / server 8 / heartz 31(debug·release 변형 각각) / app 18, 실패 0. heartz lint 오류 0, 경고 2. 중간 실행 1회는 lint 오류 1(UnstableApi opt-in)로 FAIL → 수정 후 통과 (`m3/final-build-test-lint.log`) |
| TDD | core: ModerationAssist 14, RoadsideRules 10, ReviewAssistService 5, LetterSession +2, PassedLetters 3 — 모두 red(컴파일 실패 또는 실패) 확인 후 green. heartz: RoadsideController 12·GleoProtocol 4는 구현과 함께 작성 → **변형 시험**(규칙을 일부러 깨면 해당 테스트 실패, 복원하면 통과)으로 검증 |

## 데모 영상

- 파일: `.docs/evidence/heartz/demo/heartz-demo.mp4`(Git 제외) — 3분 58초, 1920×1080 30 fps, AAC. 편집 스크립트·자막·동작 시각 기록 동봉(`edit.py`, `captions.ass`, `s1–s6.marks`).
- 녹화 방법: 장면마다 기기 `screenrecord --size 1920x1080` + 호스트 `parecord --monitor-stream=<qemu>`(에뮬레이터 출력만, 노트북 마이크 미사용) → 대기 구간 자르기, 소리 맞추기(오디오 +16 dB, 리미터), 한국어 자막 burn-in.
- 흐름: ① 주차 중 지나면서 남기기 설정(마이크 권한·공개 범위) ② 주행: 이촌 진입 → 소리 없는 카드 → 듣기("…오늘 꽃이라도 사들고 가야겠네요") → 포커스 반납 → 지나면서 남기기(TTS 안내 → 녹음 중 이동 → 끝내기 → 검수 대기, TTS 결과 안내) ③ 주차 후 남기기 → 공개 제출 → 내 사연함 ④ 운영자: 받아쓰기 보조 정보, 원본 듣기, 승인 ⑤ 바다 계정이 동부이촌동에서 하늘의 새 편지 수신·재생 ⑥ Gleo 모의 호출로 녹음 시작 → 전화 수신 → 즉시 중단·초안.
- 영상의 한계(자막에도 표시): 위치·기어·전화·Gleo는 테스트 입력, 시드는 합성 테스트 음원, AVD 마이크 무음이라 사용자 녹음은 소리가 비어 있음, 내비 지도 위치·타일 불일치(사용자 결정으로 보류).
- 연출 준비(영상 밖): 앱 데이터 초기화(`pm clear --user 10`), 바다 계정이 녹화 전에 동부이촌동을 한 번 지나 시드 편지를 받은 상태(새 편지가 무작위 후보로 단독이 되도록).

## 환경 메모

- 호스트 PulseAudio: 시험용 `module-null-sink heartz_feed` + `module-remap-source heartz_vmic`를 기본 입력으로 사용했다. 에뮬레이터가 `-allow-host-audio`로 떠 있는 동안 이 설정을 풀면 노트북 마이크가 연결될 수 있으므로, 에뮬레이터를 일반 옵션으로 다시 시작한 뒤 기본 입력을 원래 장치로 되돌리고 모듈을 내린다(아래 "정리").
- 에뮬레이터는 21:43경 host GPU 렌더링 오류(`eglMakeCurrent failed`)로 1회 종료 → Cold Boot 재시작.
- 분할 화면에서 창 막대(⋮)를 탭하면 두 앱의 좌우가 바뀐다.
- 정리(22:50): 기어 P → 에뮬레이터 종료 → PulseAudio 기본 입력을 원래 장치(`alsa_input…sofhdadsp_6__source`)로 복원하고 `heartz_feed`·`heartz_vmic` 모듈 제거 → 에뮬레이터를 일반 옵션(`.tools/start-emulator.sh`, 호스트 오디오 없음)으로 재시작. 시험용 appops `SYSTEM_ALERT_WINDOW`는 `default`로 복원, 로컬 개발 서버 종료.

---

# 앱 이름 변경과 운영사무국 안내 반영 (2026-11-10)

- **앱 이름 HEARTz**(사용자 결정): 사용자에게 보이는 이름만 바꿨다. `app_name`, 상단 워드마크, 설정 정보, 시스템 미디어 루트("HEARTz 음성 편지"), 녹음 중단 사유, Gleo `schema.json`·응답 문구, 개발 서버 표시명. applicationId `ai.heartz.app`, 패키지·클래스 이름, logcat 태그 `Heartz`, 이전 기록 절의 제목은 그대로 둔다. 제출용 APK 이름은 `apk/팀명_HEARTz_release.apk`로 예정.
- **운영사무국 문의 답변**(사용자 전달): "에뮬레이터 내비게이션이 가상 GPS를 인식하지 않는 문제가 있으며, 앱에서 LocationManager를 이용하면 가상 GPS를 정상적으로 받을 수 있다는 공식 안내가 있다. 순정 내비게이션의 모의 주행 절차를 확정한 것은 아니다. 나머지는 공식 확인 후 추가 안내."
  - 이 기록의 "실제 SDK 아티팩트·AVD에서 확인한 차이" 3번(내비가 `geo fix` NMEA를 거부)과 일치한다. 앱 결함이 아니라 에뮬레이터 내비의 알려진 문제로 확인됐다. 내비 위치·지도 타일은 사용자 결정대로 보류한다.
  - AVD 확인(2026-11-10, `dumpsys location`, 테스트 입력 `geo fix` 이촌 → 서빙고): 플랫폼 `gps` 제공자의 마지막 위치가 입력을 그대로 따라감(37.521998,126.972998 → 37.519798,126.986998). Pleos FusedLocation 서비스(`ai.pleos.playground.service.location.fused`)가 바로 이 플랫폼 위치 요청을 구독하고 있다.
  - 결정: **코드 변경 없음.** HEARTz는 이미 Pleos FusedLocation SDK 콜백으로 가상 GPS를 받는다(M1 E2E 콜백 169회). `LocationManager`를 직접 쓰면 `ACCESS_FINE_LOCATION` 런타임 권한이 추가되고 차량 융합 위치가 아닌 GPS 원값을 쓰게 된다. FusedLocation이 미지원·실패인 환경이 확인되면 그때 대체 경로로 검토한다.
  - 추가 안내가 오면 이 절에 이어서 기록한다.

---

# Gleo 실제 음성 호출 관찰과 대답·편지 겹침 수정 (2026-11-10)

사용자가 AVD에서 직접 말해 시험했다(마이크 동작은 사용자 확인. 이전 절의 "AVD 마이크 무음" 판정은 별도 재확인 전까지 갱신하지 않는다). 아래 판정은 Gleo 로그(`[GAA]`, `ami-sdk`, `MediaFocusControl`, `dumpsys audio`)로 확인했다.

| 항목 | 판정 | 증거 | 비고 |
|---|---|---|---|
| Gleo가 HEARTz schema를 읽음 | PASS | `builtin.get3rdPartyAppActionSchemaPrompt` 출력에 `ai.heartz.app.START_STORY_RECORDING`·`FINISH_STORY_RECORDING`·`PLAY_ARRIVED_LETTER` | 부팅 시 `assets/schema.json` 스캔(deliver-schema 문서와 일치). 별도 등록 없이 AVD에서 반영됨 |
| 음성 → `PLAY_ARRIVED_LETTER` 호출 | PASS (실제 Gleo E2E) | 19:37:46 발화 "허츠 앱에 온 … 편지 읽어줘" → `launch.externalApp.intent {packageName=ai.heartz.app, action=PLAY_ARRIVED_LETTER, component=activity}` → 앱 `[gleo] action=…` → 응답 브로드캐스트 → Gleo `APP_ACTION_RESPONSE` 수신 | 인텐트 액션은 접두어 없는 함수 이름으로 옴 |
| 앱 이름으로 열기("하츠 앱 열어줘") | FAIL | STT "artg"/"heartg" → `settings.launch.installedApp {extractedName=heartg 앱, packageName=UNKNOWN}` → "앱을 찾을 수 없어요" | 설치 목록(`installedPackages`)에는 `ai.heartz.app` 있음. 이름↔패키지 매핑(`app_invoke_keywords`)은 Gleo 서버 앱 목록(`getAppListInfo`)에서 오는 것으로 보이며 HEARTz는 없음(추정, 문서 미기재). 등록 절차는 운영사무국 문의 필요 |
| Gleo 대답과 편지 겹침 | FAIL → 수정 | 앱이 호출 즉시 재생(46.39 시작) → Gleo가 응답을 받은 뒤 대답(`USAGE_ASSISTANT` AudioTrack 47.36–48.75, 끝 효과음 48.52–49.51) | 아래 수정 |

- 수정: `PLAY_ARRIVED_LETTER`는 응답을 즉시 보내고, 재생은 다른 앱의 `USAGE_ASSISTANT` 재생이 시작됐다가 0.6초 조용해질 때까지 미룬다(공개 API `AudioManager.registerAudioPlaybackCallback`). 말소리가 4초 안에 없으면 그때 재생, 전체 15초를 넘기면 기다리기를 그만둔다. 그사이 같은 편지가 이미 재생 중이면 다시 틀지 않는다.
  - 코드: `platform/gleo/AssistantQuiet.kt`(`AssistantQuiet`, `AssistantSpeechMonitor`), `MainActivity.playAfterGleoSpeaks`.
  - 테스트: `AssistantQuietTest` 4개(fake 흐름, 관찰 시각을 본뜸) — 컴파일 실패(red) 확인 후 구현 → 통과. 변형 시험(조용한 시간 확인 제거) 시 2개 실패 확인 후 복원.
  - 실행: `./gradlew :heartz:testDebugUnitTest :heartz:lintDebug :heartz:assembleDebug` exit 0, heartz 테스트 35개 실패 0, lint 오류 0. debug APK를 AVD에 설치.
  - **AVD 재검증: NOT_RUN.** 모의 호출은 현재 위치에 편지 카드가 없어 `fail` 응답으로 끝남(재생 단계 미도달). 실제 Gleo 음성으로 다시 확인 필요.
- 남은 점: `START_STORY_RECORDING`도 같은 순서(앱 안내 음성·녹음 시작 ↔ Gleo 대답)라 겹치거나 Gleo 대답이 녹음에 섞일 수 있다(미확인).

# UI·기능 정의서 작성과 화면 캡처 (2026-11-10)

- 산출물: `.docs/ui-spec/HEARTz-UI-기능정의서.md`, `.docs/ui-spec/images/*.png`(22장), Notion 가져오기용 `.docs/ui-spec/HEARTz-UI-기능정의서-Notion.zip`. Notion에는 직접 쓰지 않았다(이 세션에 Notion 연결 도구 없음).
- 캡처 조건: 사용자 허락 후 AVD 조작. 위치 `geo fix`·기어 Virtual Console(테스트 입력), 녹음 화면은 `adb emu avd hostmicoff` 상태에서 찍고 시험 녹음은 저장하지 않음(다시 녹음/취소).
- **에뮬레이터 종료 1회:** 20:26경 기어 P 전환 직후 adb `device offline` → qemu 프로세스 종료, crash 보고서 없음. 직전에 `uiautomator dump`를 반복 사용했다(재시작 절 8번의 크래시 이력과 같은 조건, 원인 확정은 못 함). `.tools/start-emulator.sh`로 Cold Boot 재시작 → `forward tcp:8088` 재설정 → 이후 캡처는 고정 좌표 탭만 사용.
- 복구: 하늘 계정·지금 이 길 탭·기어 D·위치 37.2987,126.9747로 되돌림. 재부팅으로 내비 경로 안내와 분할 창 배치는 초기화됨. 호스트 마이크는 꺼진 상태로 둠(사용자가 다시 켜야 함).

---

# 주행 중 음성 흐름·공감 하트·UI 정리 (2026-11-10 밤)

사용자 요청(같은 날): ① 사연은 주차 중·지나면서 두 방식 ② 남의 사연에 음성 답장 금지, 공감 하트만 ③ 주행 중 Gleo·음성으로 조작(도착 알림·읽기, 말이 멈추면 녹음 자동 종료, 시작 위치로 저장, "전달할까요?"·공개/나만 질문, 언제든 버리기) ④ 음악 덕킹하며 "열어볼까요?", 편지 읽는 동안 음악 잠시 멈춤, 다 들은 뒤 공감 하트 질문 ⑤ 결정 후 약 5초 뒤 대기 화면 ⑥ AI 티 나는 요소·글 줄이고 글자 키우기.

## 공식 근거 (Gleo app-integration-guide, 확인일 2026-11-10)
- app-capability: actionCalls **최대 3개**, 이름 대문자+밑줄, 설명은 **영어**, enum은 문자열만.
- action_callback: status = success / asking / fail, message는 영어, mode = voice / beep / silent, **7초 뒤 취소**.
- **앱이 Gleo를 먼저 말하게 하거나 대화를 시작하는 방법은 문서에 없음.** → 앱이 먼저 묻는 질문은 Pleos TTS로 말하고 Pleos STT로 바로 듣는다. 운전자가 Gleo로 말하는 경우는 함수로 받는다(두 경로를 같은 동작에 연결).

## 구현
- Gleo 함수(3개): `START_STORY_RECORDING`, `MANAGE_MY_STORY(decision: FINISH|SUBMIT_PUBLIC|KEEP_PRIVATE|DISCARD)`, `HANDLE_ARRIVED_LETTER(choice: PLAY|SKIP|SEND_HEART|NO_HEART)`. schema 0.3.0, 매니페스트 액션(접두어 有/無) 갱신. 응답은 결과를 기다려 7초 안에 보냄. Gleo로 정한 결과는 앱이 다시 말하지 않음.
- 편지 음성 흐름 `LetterVoiceGuide`: 주행 중 카드 도착 → 덕킹 포커스 + "OO에서 편지가 도착했어요. 열어볼까요?" → STT 6초 → 예: 재생 / 아니오: 넘기기(1.5초 뒤 대기) / 모름: 카드 유지. 끝까지 들으면 "공감 하트를 남길까요?" → 하트 여부 → 5초 뒤 카드 닫기(`LetterSession.dismiss`, 같은 장소 구간을 벗어날 때까지 새 편지 안 띄움).
- 지나면서 남기기 `RoadsideController` 재작성: 시작 시 포커스(GAIN_TRANSIENT_EXCLUSIVE) → 안내 → 녹음, 진폭 기반 `SpeechEndDetector`(말한 뒤 3초 조용 → 종료, 처음 8초 무음 → 버림), "이 사연을 전달할까요?" → "모두에게 공개할까요, 나만 들을까요?"(모르면 한 번 더, 그래도 모르면 **공개하지 않고 기기 초안**), 언제든 버리기(저장 후 30분 안이면 방금 사연 삭제), 통화(AudioManager mode)면 말없이 초안, 그 밖의 포커스 상실(Gleo)이면 녹음을 멈추고 Gleo 명령을 기다린 뒤 질문. 공개 범위 사전 설정(`RoadsideVisibility`)은 없앰. 설정 기본값: 지나면서 남기기 켜짐(새 키 `roadsideEnabled2`), "편지가 오면 말로 묻기" 켜짐.
- 편지 재생 포커스: ExoPlayer 자동 포커스(AUDIOFOCUS_GAIN) → `LetterFocus`가 **GAIN_TRANSIENT** 요청·반납(음악이 멈췄다가 다시 나옴). 상태는 세션 extras로 화면에 전달.
- 안내 음성: Pleos TTS `setAudioAttributes(USAGE_ASSISTANT, SPEECH)`(SDK 공개 메서드, AAR에서 확인).
- 공감 하트: core `HeartzService.heart`(다른 사람의 공개 사연에만, 1인 1개, 작성자에게 `heartCount`), 서버 `POST/DELETE /v1/stories/{id}/heart`. 음성 답장: `createDraft`가 `parentStoryId`를 INVALID로 거절, `replies` API·화면 제거(예전 데이터 필드는 호환용으로 유지).
- UI: 글자 체계 확대(본문 26sp·제목 32sp 이상, 화면 160 dpi = 1dp≈1px), 상태 알약·설명 문단·계정 표시·"기획서/M0" 문구 제거, 큰 버튼만 남김. 주행 카드 상태(질문·재생·하트·닫힘), 지나면서 남기기 화면, 지금/지난 편지/상세/남기기/내 사연/설정/검수 정리.
- debug 전용: "받은 편지 기록 지우기"(기술 진단 화면, core `clearListenHistory` — 하트·숨긴 사람은 유지), `FOCUS_TEST`·`CLEAR_LISTENS` 시험 명령.
- 시험 전용 모듈 `:testmusic`(applicationId `ai.heartz.testmusic`): 포커스에 표준 방식으로 반응하는 가짜 음악 앱(440/554 Hz 톤). 제출물 아님.

## 자동 테스트 (fake/로컬, 실제 SDK 증거 아님)

| 명령 | 결과 |
|---|---|
| `./gradlew test :heartz:lintDebug :heartz:assembleDebug :heartz:assembleRelease :server:installDist :testmusic:assembleDebug` | exit 0. core 112 / server 9 / heartz 60(debug·release 각각) / app 9, 실패 0. heartz lint 오류 0, 경고 3 |
| TDD | 새 테스트는 모두 컴파일 실패(red) 확인 후 구현: VoiceReplies 6, SpeechEndDetector 5, LetterSession dismiss 2, HeartzService 하트·답장 거절·기록 지우기 4, RoadsideRules 6(개정), 서버 하트 1, RoadsideController 21(재작성), LetterVoiceGuide 10, DriveViewModel 음성 4, GleoProtocol 6(schema 일치 포함) |
| 변형 시험 | ① 무음 판정 최소 길이 0·"공개하지 마" 규칙 제거 → 2개 실패 ② 닫기 지연 제거·"아니"를 나만 듣기로·바쁠 때 질문 허용 → 6개 실패. 복원 후 통과 |
| 버그(AVD에서 발견) | 무음 자동 종료 시 "말소리가 들리지 않아…" 안내가 나오지 않음 → 원인: 감시 코루틴이 `finishWith`에서 자신을 취소, TTS 래퍼가 `CancellationException`을 오류로 삼킴. fake 화자가 취소를 따르게 바꿔 red 재현 → 수정(감시 작업 참조 해제, TTS·STT 래퍼가 취소를 다시 던짐) → green, AVD 재확인 |

## AVD 확인 (Pleos Connect v2.1.0, 2560×1440·160 dpi)

입력: 위치 `geo fix`·기어 Virtual Console(테스트 입력). 음악은 `:testmusic`(fake). Gleo는 **adb 모의 Intent**(실제 Gleo 음성 아님). 말로 답하기는 아래 "가상 마이크" 방법(합성 음성).

| 항목 | 판정 | 증거(logcat `Heartz`/`TestMusic`) |
|---|---|---|
| 도착 질문(덕킹 요청 → TTS → STT) | PASS | 22:20:35 `card shown` → `prompt focus request MAY_DUCK result=1` → TTS 22자 onDone → STT 6초 → 답 없음 `UNCLEAR` → 카드 유지 |
| 덕킹(음악 크기 줄이기) | **UNSUPPORTED(AVD)** | MAY_DUCK 허용되나 음악 앱에 포커스 변경 없음, 출력 녹음에서 톤 크기 변화 없음(rms 4241 유지). `dumpsys car_service`: `Use hal ducking signals false`. 실차 HAL에서 확인 필요 |
| 편지 재생 중 음악 잠시 멈춤 → 재개 | PASS | 22:21:11 `letter focus request GAIN_TRANSIENT result=1`, `TestMusic: focus change=-2 PAUSED` → 22:21:27 `letter focus abandoned`, `TestMusic: focus change=1 PLAYING` |
| Gleo 모의 PLAY → 응답 → 재생 | PASS(모의) | 응답 즉시 송신 → `assistant quiet wait=NO_SPEECH`(4초) → 재생 |
| 하트 질문 → 5초 뒤 대기 화면 | PASS | 22:21:27 `ask heart` → 답 없음 → 22:21:41 `card dismissed` |
| 말로 열기·하트(합성 음성) | PASS | 22:26:57 STT "응 읽어줘" → `YES` → 재생, 22:27:15 "응 하트 남겨줘" → `heart ok=true` → "하트를 남겼어요" → 22:27:22 닫기 |
| 지나면서 남기기: 음악 멈춤/재개 | PASS | 22:22:10 녹음 포커스 → `TestMusic PAUSED` → 22:22:22 반납 → `PLAYING` |
| 무음 자동 버림 + 안내 | FAIL → 수정 → PASS | 수정 전 안내 없음, 수정 후 22:24:01 `no speech -> discard` → TTS 21자 onDone → 포커스 반납 |
| 말 멈춤 자동 종료 → 두 질문 → 검수 대기 | PASS | 22:27:55 `silence after speech -> auto finish`(8.45초) → "응 전달해 줘" `YES` → "모두에게 공개해 줘" `PUBLIC` → `status=PENDING_REVIEW` |
| Gleo 모의 DISCARD(저장 후) | PASS(모의) | 22:28:24 `deleted saved story … ok=true`, 응답 success, 앱 음성 없음 |
| Gleo 모의 START → 녹음 중 DISCARD | PASS(모의) | Gleo 조용함 대기 후 녹음 → `discarded in progress (gleo=true)` |
| 녹음 중 다른 앱 어시스턴트 포커스 | PASS(시험 입력) | debug `FOCUS_TEST`(USAGE_ASSISTANT, GAIN_TRANSIENT) → `inCall=false` → 5.27초 보관 → 질문 → "그거 버려줘" `DISCARD` |
| 녹음 중 전화(`emu gsm call`) | PASS | `inCall=true` → `interrupted reason=통화 -> local draft`, 안내 음성 없음. 초안은 모의 DISCARD로 삭제 |
| 실제 Gleo 음성 → 새 함수 | NOT_RUN | 함수 이름을 바꿨으므로 사용자 음성으로 다시 확인 필요 |

- **가상 마이크(노트북 마이크 미사용):** Pleos TTS 출력을 qemu 출력 스트림에서만 녹음해 답 음성 6개를 만들고, PulseAudio `module-null-sink heartz_feed` + `module-remap-source heartz_vmic`을 기본 입력으로 둔 뒤 `adb emu avd hostmicon`. 에뮬레이터 입력이 `heartz_vmic`에 연결된 것 확인(`pactl list short sources`). debug `REC_TEST`: 5,016 ms, 최대 진폭 26,550, STT "오늘 이 길을 지나는데 노을이 정말 예뻤어요 다들". **이전의 "게스트 마이크 무음(BLOCKED)" 판정은 이 증거로 대체**하고 `DrivingRecordingGate`의 마이크를 VERIFIED로 바꿈(사람 목소리·차량 마이크는 미확인). 시험 후 `hostmicoff`, 기본 입력을 원래 장치(`…sofhdadsp_6__source`)로 되돌리고 두 모듈 제거 확인.
- 화면 캡처 20장으로 UI 기능정의서를 다시 씀(`.docs/ui-spec/HEARTz-UI-기능정의서.md`, `images/`, Notion zip — 참조 20, 누락 0, 미사용 0).

## 환경 메모
- 21:59 에뮬레이터가 정상 종료 절차로 꺼짐(로그 `Wait for emulator … to shutdown gracefully`, crash 기록 없음). 직전 시험 음악 톤이 계속 재생 중이었다. 22:15 `.tools/start-emulator.sh`로 Cold Boot 재시작, `forward tcp:8088`.
- 시험 데이터: 시험 중 저장한 사연은 모두 지움. 운영자 계정이 시드 편지 1개, 하늘 계정이 시드 편지 2개에 하트를 남김(시험). 하늘 계정의 받은·들은 기록을 debug 버튼으로 한 번 지움.
- 끝 상태: 하늘 계정, 기어 D, 위치 37.2987,126.9747, 호스트 마이크 꺼짐, 시험 음악 꺼짐.

---

# unslop 스킬 설치 (2026-11-10~11)

- 사용자 요청: github.com/heydamir/unslop 적용. 사용자 선택: "설치 후 다듬기까지" → 감사·방향 확인 단계에서 **"고치지 않음"** 선택. 화면 코드는 바꾸지 않았다.
- 설치: 커밋 `97daa3eb365670ea3f87ed85356cbfcee18105a5`(2026-11-09)의 `skills/unslop`을 `.claude/skills/unslop`과 `.agents/skills/unslop`에 그대로 복사(두 폴더 `diff -rq` 동일). 라이선스 CC BY 4.0, 작성자 표기(Damir Sokolovsky, @heydamir)는 원본 파일에 유지.
- AGENTS.md 스킬 절에 unslop 항목과 차량 화면 예외를 적음: tokens.md의 웹 크기 대신 HEARTz 크기(본문 26sp↑, 주요 버튼 76dp↑, 아이콘 32dp↑) 유지, snapshot·story 스크립트(Playwright)는 Android 화면에 쓰지 않음. 스크립트는 실행하지 않았다.
- 감사 결과(다듬기 모드 수준, 미적용): 4dp 체계 밖 간격 약 106곳, 분홍 강조색 22곳, 색 원 아이콘, 어두운 테마 그림자(편지지 10dp·녹음 버튼 8dp), 채운 버튼 두 색 경쟁, 제목 Bold(700), 완전 알약 모양 17곳, 색 알약 상태 표시, 아이콘 크기 26–60dp 혼재, 화면 짧은 문구의 끝 마침표. 이모지·그라데이션·빛 효과·반복 애니메이션은 없음.

---

# 화면 다듬기 18개 항목, 샘플 편지 목소리, 음악·내비·편지 흐름 (2026-11-11)

사용자 목록(18개): 편지지 3줄, 앱 표시 키우기, 목록 아이콘·"새 편지"·상태 표시의 AI 티 제거, 공감은 하트 색만, 미니 플레이어 문구 제거, 편지 음성과 안내 음성 목소리 통일, 진행 막대 제거, 짧은 녹음 경고는 아래, 상세·남기기 단계 가운데 정렬, 내 사연 상세 버튼 한 줄, 분위기 칩 색감, unslop 기반 정리, 음악+내비 상황 시험과 공감 뒤 음악 재개.

## 구현
- 화면: 편지지(어디서 온 편지 / 보낸 사람 / 어떤 기억, 길이·샘플 표시 제거), 목록은 분위기 종이색의 작은 봉투(`EnvelopeThumb`)와 안 읽음 점, 상태는 점 + 조용한 글씨, 공감 버튼은 하트 색만 바뀜, 상세·남기기 ②③④·완료 가운데 정렬, 경고는 아래, 내 사연 상세는 같은 크기 버튼 한 줄(공개 제출/상태 + 삭제), 미니 플레이어 상태 문구 제거, 카드 진행 막대 제거, 앱 표시 64–72dp.
- unslop(`.claude/skills/unslop`, 차량 예외 적용): 4dp 간격(약 50곳 정리), 주 버튼 미색·보조 버튼 한 단계 밝은 면, 분홍은 하트·안 읽음·앱 표시에만, Material 기본 강조도 미색(`primary=Hz.Text`, `surfaceTint` 투명), 모서리 크기별(버튼 16–20dp), 그림자는 편지지만 4dp, 제목 굵기 600, 상태 색 채도 낮춤, 색 원 아이콘 제거, 화면용 한 문장 끝 마침표 제거(`forScreen`, 음성 문장은 그대로), 편지지 색을 실제 종이에 가까운 낮은 채도로.
- 목소리: CAAS 로그 `getSpeakerType(ai.heartz.app) - found gleoAiAppId key … speaker=haeun` — 앱 TTS는 이미 Gleo 설정 목소리를 따름. 다른 목소리로 들린 것은 시드 음원. 기존 음원을 기기 STT로 받아써 문장을 되살리고 같은 TTS(haeun)로 다시 녹음(qemu 출력 스트림만, `loudnorm I=-16`, 배경 화음 없음). `seed/audio/README.md`에 문장 기록. 기존 기기·서버 데이터에도 반영되게 `HeartzService.refreshSeedAudio`(시드 사연만, TDD) + 음원 캐시 이름에 크기 포함.
- 소리: `AudioFocusLeases`(앱 공용 포커스 하나: PAUSE→GAIN_TRANSIENT, DUCK→MAY_DUCK). 편지를 열 때부터 공감 결정 2초 뒤까지 음악 멈춤 유지(`LetterVoiceGuide.letterPlaying/close`), 질문 전 내비·다른 음성 대기(`awaitOtherSpeechQuiet`, 최대 10초), 편지 재생 중 내비 안내면 편지 볼륨 0.3(`LetterFocus`, AudioPlaybackCallback).
- debug: `NAVI_ROUTE`(내비 시험 경로 요청·취소). 시험 음악 앱 `:testmusic`은 실제 곡 재생기로 바꿈(캐논 D장조, Kevin MacLeod, CC BY 3.0, Wikimedia Commons). 음원은 `.gitignore`로 저장소 제외.

## 자동 테스트
- `./gradlew test :heartz:lintDebug :heartz:assembleDebug :heartz:assembleRelease :server:installDist :testmusic:assembleDebug` exit 0. core 113 / server 9 / heartz 64(debug·release) / app 9, 실패 0. lint 오류 0.
- TDD: 시드 음원 갱신 1(red: 컴파일 실패), 음성 흐름 4개 추가(음악 멈춤 유지·조기 정지·내비 대기·취소) + 기존 1개 순서 갱신, DriveViewModel 음악 멈춤 순서. 변형 시험(재개 지연 제거·재생 시작 시 멈춤 제거) → 4개 실패, 복원 후 통과.

## AVD 확인
| 항목 | 판정 | 증거 |
|---|---|---|
| 실제 Gleo 음성 → `HANDLE_ARRIVED_LETTER`(새 함수 이름) | PASS(사용자 실제 발화) | 2026-11-10 23:54:45 STT "하르츠 앱에서 이천에서 온 편지 읽어줘" → `launch … action=HANDLE_ARRIVED_LETTER` → 응답 → Gleo "…편지를 읽어드릴게요" → 앱 `assistant speaking=true→false` → `AFTER_SPEECH` → 재생 |
| 내비 말하는 중 편지 도착 → 기다렸다 질문 | PASS | 00:39:01.9 `other speech playing -> wait` → 00:39:03.4 질문(MAY_DUCK) |
| "응 읽어줘" → 음악 멈춤 → 편지 | PASS | 00:39:09.4 `TestMusic PAUSED (transient)`, `lease +letter-session(PAUSE)` |
| 편지 끝 → 음악 멈춘 채 공감 질문 → 하트 → 2초 뒤 음악 재개 → 5초 뒤 닫기 | PASS | 00:39:19 `lease -letter now=[letter-session]`, 00:39:25.5 `heart ok=true`, 00:39:29.06 `TestMusic focus change=1 PLAYING`, 00:39:32 `card dismissed` |
| 편지 재생 중 내비 안내 → 편지 소리 낮춤 | PASS(로그) | `navigation guidance speaking -> letter volume 0.3` |
| 질문 때 음악 덕킹 | UNSUPPORTED(AVD) | MAY_DUCK 허용, 출력 음량 변화 없음. 시스템 내비도 MAY_DUCK이고 역시 줄지 않음(`Use hal ducking signals false`) |
| 화면 18개 항목 | PASS(눈으로 확인) | 분할 창 캡처 25장을 정의서에 넣음 |

- 시나리오 녹음(에뮬레이터 출력만): 스크래치 `heartz-music-nav-letter.mp3`(46초). 1초 단위 음량: 0–8초 음악, 9–10 내비, 12–14 질문(음악 그대로), 16–25 편지(음악 멈춤), 26–34 공감, 35–36 조용, 37–46 음악 재개.
- 환경 문제(앱 결함 아님으로 판단): 내비 시험 경로를 띄운 뒤 HEARTz 창이 하얗게 남음(`OpenGLRenderer: dequeueBuffer failed, error = -110`) → 앱 재시작으로 복구. 이후 대화상자가 그려지지 않음(프레임당 3초대) → 00:47경 에뮬레이터 종료(충돌 보고서 없음) → Cold Boot 재시작 후 같은 대화상자 정상 표시.
- 시험 데이터: 완료 화면 확인용 사연 1개를 만들고 바로 삭제. 하늘·바다 계정의 받은 기록을 debug 버튼으로 지움, 시드 편지 몇 개에 하트(시험).
- 끝 상태: 바다 계정, 기어 P, 위치 37.2987,126.9747, 내비 옆 분할 창, 시험 음악 꺼짐, 내비 시험 경로 취소, 호스트 마이크는 시험 전처럼 켬(기본 입력 = 원래 장치).

---

# 피드백 6개 반영: 바로 읽기·대기열, 다시 녹음·변조 목소리·잡음 줄이기, 공개 범위 단일화, 임의 색, "사연" 용어 (2026-11-11)

사용자 피드백(요약): ① 주행 중에도 다시 녹음, "원하시면 변조 목소리로 사연을 전달할까요?"로 원본/TTS 선택, 원본 잡음 줄이기 ② 나만 보기 삭제(검수 중·공개만) ③ 공감 뒤 "사연자님께 마음이 전달되었어요" ④ 도착 시 묻지 않고 "OO에서 사연이 도착했어요 읽어드릴게요" 후 바로 읽기, 읽는 중 도착분은 대기열, 공감 뒤 "이번에는 …", 끝난 지 10초 안이면 "이번에는" ⑤ 분위기 화면에서 삭제, 사연마다 파랑·주황·노랑·초록 임의 색, 감정 분석→앰비언트 라이트는 향후 개발 ⑥ "편지"→"사연", 탭 "지금 이 길 / 지나온 사연 / 내 사연함".

## 구현
- core: `VoiceMode{ORIGINAL,MODULATED}`, `Story.voiceMode/spokenText`(MODULATED는 DONE 받아쓰기를 spokenText로, 원본 음원은 작성자·운영자만, 받아쓰기 없으면 승인 거절), `keepPrivate`·`/private` 경로 삭제(예전 PRIVATE 데이터는 submit 가능), `StoryTone.of(id)`(해시 → 4색 고정), `VoiceReplies`에 RERECORD("다시"·"재녹음")·`parseVoiceMode`, `Roadside` SUBMIT/DISCARD, `LetterSession.busy`+장소별 1개 대기열(`SessionEvent.Queued`), `audio/VoiceCleaner`(80 Hz 고역통과 양방향, STFT 1024/512 스펙트럼 차감 1.6·바닥 0.12, RMS 4200 정규화·피크 제한).
- app: `LetterVoiceGuide.arrive`(멈춤 포커스 → 다른 음성 대기 → 안내 → 재생, 닫은 지 10초 안·대기열이면 "이번에는"), 공감 성공 시 "사연자님께 마음이 전달되었어요."(주행 중 터치 하트도, Gleo는 Gleo가 대답), 대기열이 있으면 바로 닫고 음악 멈춤 유지(2초 안에 다음 사연이 안 오면 반납). `DriveViewModel`은 평가 전 `session.busy = guide.busy || 재생 중`. `RoadsideController` 재작성(전달/다시 녹음 → 목소리 질문, 답 없으면 내 목소리, 다시 녹음은 같은 장소로 새 녹음, 보내기 전 `cleanAudio`). `TtsLetterReader`(문장 단위 Pleos TTS 읽기, 일시정지·이어 읽기, 오류·외부 정지는 끝으로 치지 않음, PAUSE 임대) + `HeartzPlayer`(원본은 미디어 서비스, 변조는 리더). `VoiceCleanup`(MediaCodec 디코딩 → VoiceCleaner → AAC/MP4 인코딩 → 바꿔치기, 실패 시 원본 유지). Studio: 분위기·나만 듣기 삭제, 목소리 선택·잡음 줄이기 표시·보내기. 상태 문구 검수 중/공개(예전 초안·PRIVATE는 "보내지 않음" + 검수에 보내기). 검수 화면에 변조 목소리 표시·원본 듣기. 색: `StoryTone.paper()`, 사연지 머리 두 줄(분위기·우표 삭제). 문구·탭·미디어 브라우저·설정 토글 "사연이 오면 바로 읽어 주기". 변조 사연은 시스템 미디어 트리에서 제외.
- Gleo `schema.json` 0.4.0: `MANAGE_MY_STORY.decision` = FINISH·SUBMIT·RERECORD·DISCARD + 선택 `voice` = ORIGINAL·MODULATED, 설명·응답은 "story". 함수 이름 `HANDLE_ARRIVED_LETTER`는 실제 Gleo로 확인한 등록과 맞추려 유지.
- debug 전용 입력: `READ`(변조 읽기 경로), `CLEAN_TEST`(시험 음성 + 합성 가우시안 잡음 → 실제 인코더·VoiceCleanup → 10/90 백분위 프레임 RMS 비교).
- 문서: `HEARTZ-PLAN.md` 3절(바로 읽기·공개 범위·목소리·색, **향후 개발: 감정 분석 → 분위기 색 매핑 → 읽을 때 앰비언트 라이트**), UI 정의서 2차 개정(그림은 개정 전 화면임을 표시).

## 자동 테스트 (fake/로컬, 실제 SDK 증거 아님)
- `./gradlew test :heartz:lintDebug :heartz:assembleDebug :heartz:assembleRelease :server:installDist` exit 0(최종 코드로 재실행). core 125 / heartz 75×2(debug·release) / server 9, 실패 0. lint 오류 0(경고 3, 기존 항목).
- 새·변경 테스트: LetterVoiceGuideTest 11, TtsLetterReaderTest 7, DriveViewModelTest 대기열 2 추가, RoadsideControllerTest 25, GleoProtocolTest(voice·"letter" 설명 금지), core VoiceCleaner 5·StoryTone·세션 대기열·HeartzService 변조.
- 변형 시험(복원 후 통과): "이번에는" 제거·대기열 이어가기 제거·hasQueued false·TTS 오류를 끝으로 처리·하트 문구 되돌림 → 각각 실패. `session.busy` 제거는 처음엔 잡히지 않아(재생 중 카드는 playing으로 보호됨) 공감 질문 중 도착 테스트를 추가 → 실패 확인. core 쪽 변형 4개(고역통과·게이트·변조 음원 가드·세션 busy)도 실패 확인.

## AVD 확인 (Pleos Connect v2.1.0, Cold Boot, user 10, 기어는 Virtual Console, 위치는 geo fix 테스트 입력)
| 항목 | 판정 | 증거 |
|---|---|---|
| 도착 → 묻지 않고 안내 → 바로 재생 → 공감 질문 | PASS | 22:41:06 `card shown s-seed-ichon-pending` → `arrive followUp=false` → `tts speak chars=23` → 22:41:09 `user action play` → 22:41:14 `ask heart` |
| 화면: "이촌에서 온 사연", 두 줄 머리, 사연 색, 분위기·우표 없음 | PASS(눈으로) | 스크래치 `arrive.png` |
| 읽는 중 다른 장소 → 대기열 → 터치 공감 → 고마움 안내 → "이번에는" → 다음 재생 | PASS | 22:41:49 `story queued s-seed-seobinggo`(재생 중) → 22:41:59 `heart by touch ok=true` → `tts chars=17` → 22:42:01.827 `next story queued -> continue` → `card shown seobinggo` → `arrive followUp=true` → `tts chars=29` → 22:42:05 재생. 그동안 `letter-session(PAUSE)` 임대 유지 |
| 답 없음 → 하트 없음 → 2초 뒤 음악 반납 → 5초 뒤 닫힘 | PASS | 22:42:22.9 `heart reply=UNCLEAR` → 22:42:24.9 `app focus abandoned` → 22:42:27.9 `card dismissed` |
| 변조 목소리 읽기(HeartzPlayer → TtsLetterReader → Pleos TTS) | PASS(debug 입력) | `parts=3`, 문장마다 `onDone`, 위치 0→2666→5333→8000, 마지막 뒤에만 `ended=true`, PAUSE 임대 획득·반납. 읽는 중 보낸 다른 TTS는 겹치지 않고 끝난 뒤 말함. 자기 TTS로 인한 포커스 상실 없음 |
| 잡음 줄이기(실제 MediaCodec 디코딩·AAC 인코딩) | PASS(debug 입력, 합성 잡음) | 44.1 kHz 6초, 처리 1.6초, 잡음 바닥 1279→209(약 −16 dB), 말소리 5272→4792, 간격 12.3→27.2 dB |
| 지나면서 남기기: 다시 녹음·목소리 질문(실제 STT) | NOT_RUN | fake 단위 테스트만. 가상 마이크 구성이 필요해 이번에는 실행하지 않음 |
| 주차 중 남기기 화면(목소리 선택·잡음 줄이기 표시) | NOT_RUN | 컴파일·빌드만 |
| 새 schema(0.4.0) 실제 Gleo 호출 | NOT_RUN | 앱 측 파싱 테스트만. 사람 목소리로 다시 확인 필요 |
| 실제 사람 목소리·차량 소음에서 잡음 줄이기 효과 | NOT_RUN | 합성 잡음으로만 측정 |

- 시험 중 `adb shell am broadcast --es text "…"`가 공백에서 잘려(`chars=2`) 첫 READ 시험은 무효 → 따옴표로 감싸 재실행.
- 끝 상태: 기어 P, 에뮬레이터 켜 둠, Virtual Console 탭 열림, 시험 음악 사용 안 함. 시드 사연 1개(동부이촌동)에 하트(시험), 하늘 계정의 받은 기록 debug 버튼으로 지움.

## 후속 수정: 변조 목소리 재생 끊김·첫 재생 원본, 하트 안 남김 안내 (2026-11-11 23시)
- 사용자 보고: 변조 목소리로 저장한 사연을 내 사연함에서 들으면 첫 클릭은 원본 목소리, 두 번째부터 TTS가 처음만 나오고 끊김. 요청: 하트를 안 남기면 "다음에는 따뜻한 마음 하나 남겨보아요".
- 원인(사용자 시험 로그로 확인)
  1. 끊김: Pleos STT `sendAudio`는 말이 멈출 때마다 조각을 닫고 `completed=true`를 보낸 뒤 다음 조각을 다시 인식한다(23:07:42 "은행나무길 하면은 또 제가" → 23:07:45 "은행 밟고 냄새 났던 적이 있죠"). 앱은 첫 `completed=true`에서 끝내 10초 녹음의 받아쓰기가 14자/11자만 남음 → TTS가 그 첫 조각만 읽고 끝남(TTS 자체는 `onDone` 정상).
  2. 첫 클릭 원본: 재생 23:07:47.3, 받아쓰기 부착 23:07:48.7. 받아쓰기가 없으면 원본을 대신 트는 대체 경로가 있었음(직전 작업의 설계 실수).
- 수정: `TranscriptSegments`(받아쓰기 모드는 확정 조각을 모두 모아 줄바꿈으로 연결, 답 모드는 기존대로 첫 확정 결과), `PleosSpeechToText.transcribePcm`은 오디오를 다 보낸 뒤 1.5초(+인식 중이면 최대 3초) 기다려 모음. 변조 목소리 사연은 받아쓰기가 없으면 재생하지 않고 "변조 목소리를 아직 준비하는 중이에요…" 안내(운영자 원본 듣기는 `original=true`로 별도). 하트 안 남김(말 아니오·답 없음·주행 중 괜찮아요 버튼) → "다음에는 따뜻한 마음 하나 남겨보아요."; Gleo `NO_HEART`는 Gleo가 대답("…Maybe leave a warm heart next time.").
- 테스트(TDD, 컴파일 실패로 시작): `TranscriptSegmentsTest` 5, LetterVoiceGuideTest 3(거절·무응답·터치/Gleo), DriveViewModelTest 1(받아쓰기 없음 → 재생 안 함, 있음 → TTS, 운영자 → 원본). 변형 시험 4개(첫 조각만·안내 제거·Gleo도 말함·원본 대체 복원) 모두 실패 확인 후 복원.
- `./gradlew test :heartz:lintDebug :heartz:assembleDebug :heartz:assembleRelease :server:installDist` exit 0: core 125 / heartz 83×2 / server 9, 실패 0, lint 오류 0.

| 항목 | 판정 | 증거 |
|---|---|---|
| 실제 STT 여러 조각 모으기 | PASS(debug 입력) | 시험 음성 2번 + 1.5초 무음(13.5초, `ko-two-parts.m4a`, ffmpeg로 기존 시험 음원에서 생성): `completed=true` 2회 → `transcript segments done lines=2 chars=103`. 수정 전 규칙이면 51자에서 끝남 |
| 하트 답 없음 → 안내 → 음악 반납 → 닫힘 | PASS | 23:20:05.5 `heart reply=UNCLEAR` → `tts chars=21` → 23:20:10.2 `lease -letter-session` → 23:20:13.2 `card dismissed` |
| 사람 목소리 녹음 → 변조 목소리 전체 재생 | NOT_RUN | 마이크 입력이 필요해 사용자 확인 대기. 이미 저장된 두 사연(e014a730…, 9ec7b375…)은 수정 전 받아쓰기(첫 조각만)가 그대로 남아 있음 |

---

# v8 화면 전면 개편 (2026-11-12)

- 요청: 루트의 `heartz_v8_screens.zip`(화면 시안 44장, 2560×1440)대로 화면을 만들되 지금 기능은 그대로 유지.
- 사용자 결정(질문 2개): ① 분위기 칩·선택기는 넣지 않고 사연 색 4종만 임의 배정 유지 ② 시안의 Apple Memoji 얼굴은 쓸 수 없어 앱이 직접 그린 얼굴 12종으로 대체.
- zip은 스크래치 폴더에 풀어 이미지로만 열람(저장소 안 파일은 그대로 둠).

## 구현
- 디자인 기반: `ui/theme/Theme.kt`(흰 바탕·검은 카드·초록 #1DB954, 시안에서 픽셀로 뽑은 색, 글꼴은 기기의 `/product/fonts/Pretendard-*.otf`가 있으면 사용·없으면 시스템 "pretendard" 이름 글꼴·기본 글꼴 순), `ui/components/Components.kt` 전면 교체(글자 로고, Hz 표시, 초록/테두리/회색/검은 버튼, 칩, 어두운 카드+색 번짐, 화면 머리, 회색 목록 줄, 흰 대화상자+하나 고르기, 알림 띠), `ui/components/Faces.kt`(얼굴 12종 Canvas 그림).
- 화면: 주행(대기·도착·넘김·재생 중 초록 테두리·공감 질문·하트 남김·다음 사연), 지나면서 남기기(안내·녹음·전달 질문·목소리 질문·결과 9종), 주차(왼쪽 메뉴, 지금 이 길 + 아직 안 들은 사연, 지나온 사연, 사연 상세·신고·숨기기·삭제 대화상자, 남기기 4단계, 내 사연함, 검수 3탭·반려 사유, 설정, 프로필 꾸미기, 기술 진단 요약 카드 + 자세히), 미니 플레이어.
- 기능 추가(시안에 있어서): 프로필 얼굴 — core `User.avatar`·`Story.authorAvatar`(응답 때 채움)·`Avatars`(12, 기본값은 id 해시)·`HeartzService.setAvatar`, 서버 `PUT /v1/me/avatar`, 앱 Backend·Repository·LibraryViewModel. 지나면서 남기기 상태에 화면용 정보 추가(`Preparing(placeName)`, `Asking(placeName, durationMs)`, `Finished.kind: FinishKind`) — 음성 문장·흐름은 그대로. 시스템 뒤로 가기로 상세·프로필·진단 닫기.
- 그대로 둔 것: 음성 흐름(바로 읽기·대기열·공감 안내), 변조 목소리, 잡음 줄이기, Gleo 3개 함수, 오디오 포커스, 미디어 서비스, 검수 규칙, 초안·재시도, debug 진단 기능.
- 시안과 다르게 둔 것: 분위기 칩·분위기 선택(색만), "모두에게/나만 듣기"(나만 보기는 없앴으므로 보내기/다시 녹음/버리기 → 내 목소리로/변조 목소리), 도착 카드의 "들어볼까요?" 질문(묻지 않고 바로 읽으므로 "곧 읽어드릴게요"), 상태 문구(검수 중·공개), 왼쪽 메뉴 이름 글자 22sp(시안 크기, 본문 26sp 규칙의 탐색 표시 예외).

## 자동 테스트 (fake/로컬, 실제 SDK 증거 아님)
- `./gradlew test :heartz:lintDebug :heartz:assembleDebug :heartz:assembleRelease :server:installDist` exit 0(최종 코드). core 128 / heartz 85×2 / server 10, 실패 0. lint 오류 0(경고 3: 기존 2 + EmptyState 인자 순서 1). 첫 실행은 lint 오류 1(UnusedBoxWithConstraintsScope)로 exit 1 → 고친 뒤 통과.
- TDD로 추가: 얼굴(서비스 3·HTTP 1·그림 수 1), 지나면서 남기기 결과 종류·화면 정보(기존 테스트 9곳에 단언 추가, 컴파일 실패로 시작), 조사 붙이기 1.

## AVD 확인 (Pleos Connect v2.1.0, 내비 옆 분할 창 840px, 기어·위치는 테스트 입력)
| 항목 | 판정 | 증거 |
|---|---|---|
| 시안과 화면 대조(주차 14종) | PASS(눈으로, 나란히 비교) | 지금 이 길·지나온 사연·사연 상세·신고·삭제·남기기 4단계·내 사연함·검수 대기/반려/공개 중·설정·프로필·기술 진단·미니 플레이어. `.docs/ui-spec/images-v8/` |
| 주행 흐름이 새 화면에서 동작 | PASS | 02:32:41 도착 안내 → 02:32:45 재생(초록 테두리) → 02:32:54 공감 질문 → 답 없음 → "다음 사연을 기다릴게요" → 02:33:11 대기 |
| 지나면서 남기기: 안내 → 녹음 → 말소리 없음 | PASS | 가상 마이크(무음) → 결과 "말소리가 들리지 않아 녹음을 멈췄어요" |
| 지나면서 남기기: 녹음 → 잡음 줄이기 → 전달 질문 → (터치) 보내기 → 목소리 질문 → 내 목소리로 → 검수에 보냄 | PASS(시험 음성을 가상 마이크로 재생, 터치 답) | 02:36:04 `silence after speech -> auto finish` → `answer=SUBMIT` → `submit voice=ORIGINAL` → `saved … PENDING_REVIEW roadside=true` |
| 전달 질문에 답 없음 → 초안 | PASS | 02:35:28 `deliver=null` → "답을 듣지 못해 초안으로 보관했어요" 화면 |
| 프로필 얼굴 저장 | PASS | `avatar set=0`·`set=5`, 설정·머리 얼굴 즉시 바뀜 |
| 주차 남기기(무음 녹음) → 검수에 보냄 → 내 사연함 → 삭제 | PASS | mine 16 → 15 → 14 |
| 시스템 뒤로 가기로 상세 닫기 | PASS | 상세 → 목록 |
| 말로 답하기(STT)·Gleo 호출을 새 화면에서 | NOT_RUN | 로직은 바꾸지 않았고 fake 테스트만 통과 |
| 넓은 창(전체 폭)·다른 창 크기 | 일부만 | 전체 폭 "지금 이 길" 1장만 확인. 나머지는 840px 분할 창 기준 |
| Pretendard 파일이 없는 기기의 글꼴 대체 | NOT_RUN | 코드 경로만 있음 |

- 시험 데이터 정리: 시험으로 만든 사연 2개(무음 5초, 시험 음성 10초)와 초안 1개 삭제. 하늘 계정 얼굴은 원래(5번)로 되돌림. 사용자가 전에 만든 사연 14개·초안 2개는 그대로.
- 환경: 가상 마이크용 PulseAudio 모듈 2개를 만들었다가 제거, qemu 입력을 원래 마이크로 복원(기본 입력 변경 없음). 노트북 마이크는 쓰지 않음.
- 끝 상태: 하늘 계정, 기어 P, 위치 37.5220,126.9700(이촌), 내비 왼쪽·HEARTz 오른쪽 분할 창, 에뮬레이터 켜 둠.

---

# v8 UI 작업 인계 정리 (2026-11-12, Codex)

- 범위: 클로드의 중단 지점(02:47 KST, 사용량 제한)과 저장된 코드·검증 기록·캡처를 대조하고 문서를 마무리했다. 기능 코드·SDK 버전·기기 데이터는 변경하지 않았다.
- `README.md`: 오래된 수동 재생·음성 답장·지나면서 남기기 기본 꺼짐·공개 범위 선택 설명을 현재 코드와 맞췄다. 자동 읽기·대기열·공감·내 목소리/변조 목소리·프로필 얼굴과 현재 Gleo 함수 3개를 반영했다.
- `ui-spec/HEARTz-UI-기능정의서.md`: 개편 전 인라인 그림을 보관된 v8 캡처로 교체하고 17장 전체의 링크 목록을 추가했다. 캡처 없는 상태는 명시하고 기존 `images/` 자료는 보존했다. 프로필 꾸미기와 주행 화면 판정 기준(`CarUxRestrictions`)을 보완했다.
- 실제 실행 환경: 일반 사용자 `autolab`(uid 1000), `.tools/env.sh` 사용, Temurin JDK 17.0.21+9 / Gradle 8.10.2 / AGP 8.8.2 / Kotlin 2.0.0. 앱 버전은 APK 메타데이터에서 0.1.0(versionCode 1) 확인. 버전 선택·변경은 수행하지 않았다.

## 이번 실행 검증

| 항목 | 판정 | 명령·종료 코드·관찰·증거 |
|---|---|---|
| 최초 기본 샌드박스 빌드 시도 | BLOCKED(실행 환경) | `source .tools/env.sh` 후 아래 통합 명령 실행 시 기존 공용 Gradle 캐시의 wrapper 잠금 파일이 읽기 전용이라 exit 1. 자동 승인된 확장 실행으로 같은 명령을 재실행했다. 코드 결함으로 판정하지 않음 |
| 빌드·lint·서버 배포 디렉터리 | PASS | `./gradlew test :heartz:lintDebug :heartz:assembleDebug :heartz:assembleRelease :server:installDist --offline` exit 0, `BUILD SUCCESSFUL in 34s`, 202 tasks(8 executed / 194 up-to-date). lint 오류 0, 경고 3(`ModifierParameter`, `DataExtractionRules`, `SetTextI18n`). APK·서버 산출물은 대부분 기존 결과 재사용 |
| 단위 테스트 실제 재실행 | PASS(fake/로컬) | `./gradlew :core:test --rerun :heartz:testDebugUnitTest --rerun :heartz:testReleaseUnitTest --rerun :server:test --rerun :app:testDebugUnitTest --rerun :app:testReleaseUnitTest --rerun --offline --console=plain` exit 0, `BUILD SUCCESSFUL in 12s`, 테스트 작업 6개 실행. core 128 / heartz 85×2 / server 10 / probe app 9×2 = 총 326건, 실패·오류·건너뜀 0. `evidence/ui-wrapup-tests-2026-11-12.log` 및 각 모듈 `build/test-results/` XML |
| 문서 링크·캡처 연결 | PASS | Python 표준 라이브러리로 README 로컬 링크 8개·UI 정의서 34개 대상 존재 확인, v8 캡처 17/17 참조, 개편 전 인라인 그림 0개 확인. 이 표 추가 후 문서 3개의 로컬 링크를 다시 확인 |
| 이번 세션의 AVD·실제 SDK·STT·Gleo·창 크기·대체 글꼴 | NOT_RUN | 문서 정리와 로컬 빌드·테스트만 수행. 앞 절의 클로드 AVD 결과와 이번 검증을 구분한다. 단위 테스트를 실제 SDK 연결 근거로 쓰지 않음 |

- 빌드·문서 점검 요약: `evidence/ui-wrapup-summary-2026-11-12.txt`(로컬 전용). debug APK: `heartz/build/outputs/apk/debug/heartz-debug.apk`, release APK: `heartz/build/outputs/apk/release/heartz-release-unsigned.apk`(서명 안 됨).

## 남은 확인과 다음 시작

1. 위 "재시작" 절의 환경·Cold Boot·serial 확인 절차를 따른다. 기존 AVD를 임의 종료·초기화하거나 계정·사연 데이터를 바꾸지 않았다.
2. v8 UI의 실제 STT 답변과 schema 0.4.0 Gleo 음성 호출은 **NOT_RUN**. 이전 schema 호출·mock·터치 답변 결과로 대체하지 않는다.
3. 넓은 창·다른 창 크기는 앞선 클로드 기록에서도 전체 폭 "지금 이 길" 한 장만 확인했다. 나머지 화면 검증은 **NOT_RUN**. Pretendard 파일 없는 기기의 글꼴 대체, 실제 사람 목소리·주행 소음도 **NOT_RUN**.
4. 기본 내비 위치 불일치·지도 타일 문제는 기존 사용자 결정대로 추가 SDK 제공까지 보류한다.
5. 문서 정리는 완료했다. 위 미검증 항목은 UI 구현 완료·빌드 통과와 별도로 남긴다.

---

# 12간지 프로필·최초 설정·브랜드·도착 카드 (2026-11-12, Codex)

## 구현과 저장 범위

- 사용자 요청: 루트 `image.png` 호랑이 기반 12간지 선택, HEARTz 시작 화면(H만 그라데이션), 최초 동물·닉네임 설정, Hz 그라데이션, 도착 카드 정보 간소화, 주행 도착 시험 명령.
- 이미지: 사용자 호랑이는 원본 그대로 `heartz/src/main/res/drawable-nodpi/avatar_tiger.png`에 복사했다. 내장 `image_gen`으로 나머지 11종을 각각 생성하고 원본을 앱 리소스에 포함했다. 순서 0–11은 쥐·소·호랑이·토끼·용·뱀·말·양·원숭이·닭·개·돼지. [프롬프트·생성 기록](assets/zodiac-prompts.md), [생성 파일 대응](assets/zodiac-generation.json). 네트워크 이미지 로딩은 없다.
- 프로필: `HeartzService.setProfile`로 닉네임·동물을 함께 검증·저장한다. 닉네임은 앞뒤 공백을 정리한 뒤 1–12자, 공백만·제어 문자는 거절한다. 기존 사연 응답도 최신 닉네임·동물을 사용한다. 로컬·HTTP 백엔드(`PUT /v1/me/profile`)와 앱 Repository·ViewModel을 연결했다. 기존 얼굴 번호는 같은 번호의 동물로 표시하며 사연·초안을 초기화하지 않는다.
- 최초 설정: Android 차량 사용자별 SharedPreferences에 계정·백엔드를 구분해 완료 여부를 저장한다. **백엔드 저장이 성공한 뒤에만** 완료 처리한다. 새로 실행하면 약 1.4초 정적인 HEARTz 로고 → 계정 로딩 → 최초 설정 또는 메인. 주행 제한·상태 미확인 시 키보드·설정 화면 대신 주차 안내를 표시한다. 설정 화면은 주행 제한 전환 시 사라지고 키보드를 정리한다. 프로필 설정 전 자동 읽기를 시작하지 않는다.
- 브랜드: H를 별도 텍스트로 그려 **H 내부**에서 초록→흰색 그라데이션이 보이게 했다. EARTz는 단색. 메뉴 Hz와 실제 런처 벡터 아이콘도 초록→흰색으로 바꿨다. 주행 중 장식 애니메이션은 추가하지 않았다.
- 도착 카드: 정보는 **이 길에 남겨진 사연 / 장소 / 초수 / 보낸 이 동물·닉네임**. 합성 음원·변조 목소리 칩·색 표시를 제거했다. 재생·공감 버튼과 음성 흐름은 유지한다. 합성 음원 메타데이터와 진단·README의 시험 음원 설명은 보존했다.
- 기존 닉네임 처리: 임의의 괄호 뒤를 잘라내던 규칙을 수정해 `구름 (호랑이)`를 보존하고 기존 테스트·시드 계정의 알려진 접미사만 줄인다. 설정의 현재 계정 표시도 저장한 이름을 바로 사용한다.
- 문서: [주행 시험 절차·명령](DRIVING-TEST.md), README, UI 정의서를 갱신했다. 새 [UI 캡처](ui-spec/HEARTz-UI-기능정의서.md#7-12간지닉네임시작-화면-캡처)는 컴포넌트 시험 데이터와 실제 앱 화면을 구분했다.

## 실행 환경과 검증

- 일반 사용자 uid 1000, `source .tools/env.sh`, Temurin 17.0.21+9 / Gradle 8.10.2 / AGP 8.8.2 / Kotlin 2.0.0 / 앱 0.1.0(versionCode 1). SDK·도구 버전은 변경하지 않았다.
- UI 시험 의존성은 기존 `:app`과 같은 버전 카탈로그·Compose BOM·Espresso 3.6.1을 재사용했다. 첫 오프라인 UI 시험 빌드는 Espresso 3.5.0 캐시 부재로 exit 1. 원인 확인 뒤 기존 고정 Espresso를 직접 선언해 해결했다(무작위 버전 변경·네트워크 다운로드 없음).
- 도메인·시작 분기는 미구현 API로 컴파일 실패를 확인한 뒤 구현했다(`evidence/zodiac-tdd-red.log`). HTTP 프로필 경로는 404로 시험 실패 → 경로 구현 뒤 통과(`zodiac-tdd-profile.log`). H 그라데이션은 전체 단어 폭에 걸려 흰색까지 변하지 않는 문제를 픽셀 검사로 재현(`zodiac-gradient-red.log`, `H ends near white` 실패) → H 별도 렌더링 뒤 통과했다. 괄호 닉네임은 2건 중 1건 실패 → 수정 뒤 통과(`zodiac-nickname-red.log`).
- UI 저장소 격리 시험의 재실행에서 7건 중 1건이 실패했다(`evidence/zodiac-instrumentation-isolation-failed.log`). `Settings`가 `applicationContext`를 사용해 시험 ContextWrapper를 벗어나는 것이 원인이었다. 시험 Wrapper의 `getApplicationContext()`를 자기 자신으로 고치고 **실제 사용자 설정의 시험 전후 동일성 단언**을 추가했다. 앞선 시험이 실제 설정에 남긴 `profile-v1:LOCAL_DEMO:driver-a` 키 1개만 제거했으며, 그 외 XML 항목·값이 모두 동일함을 확인했다. 실제 사용자 계정의 프로필 완료 여부는 변경하지 않았다.

| 항목 | 판정 | 명령·결과·증거 |
|---|---|---|
| 전체 단위 테스트·lint·빌드 | PASS(로컬) | `./gradlew test :heartz:lintDebug :heartz:assembleDebug :heartz:assembleRelease :heartz:assembleDebugAndroidTest :server:installDist --offline --console=plain` exit 0. 최종 `BUILD SUCCESSFUL in 1m 3s`, 229 tasks(27 executed / 202 up-to-date). `evidence/zodiac-final-verification.log` |
| 단위 테스트 | PASS(fake/로컬) | core 131 / heartz 90×2 / server 11 / probe app 9×2 = **340건**, 실패·오류·건너뜀 0. 프로필의 원자적 저장·검증·기존 사연 반영·store 복원, HTTP 재로그인·잘못된 입력·미인증 거절, 최초 화면 분기, 닉네임 괄호 보존. 각 모듈 `build/test-results/` XML |
| lint | PASS(오류 0) | 경고 3건 유지: `ModifierParameter`, `DataExtractionRules`, `SetTextI18n`. `heartz/build/reports/lint-results-debug.xml` |
| Pleos UI 자동 시험 | PASS(UI, 실제 SDK 증거 아님) | `adb -s emulator-5554 shell am instrument --user 10 -w -r -e class ai.heartz.app.ProfileUiTest ai.heartz.app.test/androidx.test.runner.AndroidJUnitRunner` → **OK (7 tests)**. 닉네임 없는 시작 거절·돼지 선택/입력 값 전달·닉네임만 변경·도착 카드 정보/칩 제거·H 색상 픽셀·시작 로고·Hz/실제 런처 아이콘 렌더링·시험 namespace의 설정 완료 보관/다른 계정·백엔드 미완료 확인. `evidence/zodiac-instrumentation-final.log`. shell exit 0만으로 판정하지 않고 시험 결과 본문을 확인 |
| 실제 앱 설치·첫 설정 화면 | PASS(화면) | `adb install -r -t --user 10 ...heartz-debug.apk`, `am start --user 10 -W -n ai.heartz.app/.MainActivity` → Success / Status ok. P 상태에서 동물·닉네임 첫 설정 화면 확인. 실제 계정의 이름·동물을 대신 저장하지 않았다. `evidence/zodiac-first-profile.png`, `zodiac-final-app.png` |
| 주행 시험 명령 검증 | PASS(입력 전 준비) | `bash -n tools/demo-drive.sh` exit 0. `server/build/install/server/bin/server --print-demo-route 15` exit 0, 경도·위도·방향 168개 확인. `evidence/zodiac-demo-route.txt`. AVD로 위치를 보내거나 기어를 변경하지 않음 |
| 새 UI의 실제 주행 도착·STT·Gleo | NOT_RUN | 요청에 따라 재현 명령을 제공했다. 이번 UI 컴포넌트 시험을 실제 위치 callback·음성 end-to-end 증거로 쓰지 않는다 |
| 다른 Android 차량 사용자로 전환·실차 | NOT_RUN | 사용자별 저장소 설계이며 실제 여러 Android 사용자를 생성·전환해서 시험하지 않았다. 실차·DevBox는 범위 밖 |

## 에뮬레이터와 끝 상태

- 대상: `emulator-5554`, Android user 10, API 34, fingerprint `release_connect_v2.1.0`.
- UI 재검증 전 adb 대상이 사라진 적이 있다. `emulator-latest.log`에는 `shutdown gracefully`가 남아 있었으며 충돌로 단정하지 않는다. `.tools/start-emulator.sh`로 데이터 초기화 없이 Cold Boot → `sys.boot_completed=1`, user 10 확인. `evidence/zodiac-emulator-restart.log`.
- 시험에서는 별도 UI 사용자 객체·카드를 썼고, 설정 보관 시험의 임시 SharedPreferences는 지웠다. 사용자 사연·초안·기록을 지우는 명령, 마이크 입력·기어·위치 변경은 실행하지 않았다.
- 최신 debug APK를 설치하고 실제 앱을 최초 프로필 설정 화면으로 다시 열어 둔다. 사용자가 동물과 닉네임을 직접 정하면 메인 화면으로 넘어간다. Virtual Console은 `forward tcp:8088 tcp:8088`을 준비했으며 기어 D/P 선택은 사용자가 한다.

---

# 장소 = 서울 구별 랜드마크(반경 1 km), 프로필 = Pleos Connect 첫 글자 원 (2026-11-12)

- 요청 1: 서울 각 구의 대표 장소를 "구 동 랜드마크" 더미 데이터로 넣고 사연을 장소에 연결. 지나온 사연 목록에는 "구 랜드마크", 사연 상세·주행 카드에는 랜드마크만. 랜드마크 반경 1 km 안에 들어오면 사연 도착.
- 요청 2: 동물 프로필 제거. 프로필은 Pleos Connect 상태 표시줄 모양(닉네임 첫 글자 + 원). 최초 접속의 프로필 설정 화면 제거. 닉네임·프로필은 Pleos Connect를 따름.

## 구현
- core: `Place`에 `district`·`dong`·`radiusMeters`(기본 1000)·`listLabel`("구 랜드마크") 추가, `segment`는 예전 데이터 호환용 nullable. `Eligibility.inSegment`(도로 구간·진행 방향) → `inArea`(랜드마크 중심 거리 ≤ 반경, 방향 무시), `Exclusion.OUTSIDE_SEGMENT` → `OUTSIDE_AREA`. 지나면서 남기기 장소 연결은 반경 안 랜드마크 중 가장 가까운 곳.
- `seed/SeoulLandmarks.kt`: 25개 구 105곳(구·동·랜드마크·근사 좌표). 기존 ID 4개는 용산구 랜드마크로 바꿔 시드 사연·기기 데이터를 유지(p-ichon 국립중앙박물관, p-dongbu 국립한글박물관, p-seobinggo 잠수교, p-riverside 이촌한강공원). `HeartzService.refreshSeedPlaces`로 예전 기기·서버 데이터의 장소 목록도 시작 때 갱신(사연은 그대로, 같으면 저장 안 함).
- 프로필: `Avatars`·`User.avatar`·`Story.authorAvatar`·`setAvatar`·`/v1/me/avatar` 삭제, `setProfile(nickname, avatar)` → `setNickname(nickname)`(`PUT /v1/me/profile {nickname}`, 예전 avatar 필드는 무시). 동물 그림 12장(`res/drawable-nodpi/avatar_*.png`)·`ProfileEditor`·프로필 설정 시작 분기·`ProfileUiTest`·`FacesTest` 삭제(루트의 사용자 원본 이미지는 그대로). `ProfileCircle`(흰 원, #B0B0B0 테두리, #9C27B0 첫 글자 — 상태 표시줄 프로필에서 색을 뽑음)을 보낸 이·목록·내 프로필·숨긴 사람·미니 플레이어 자리에 넣음. 계정 고르기는 "청취자 1·2·운영자"로 표시.
- `platform/profile/PleosProfile`: Pleos 프로필 이름 = 차량 Android 사용자 이름(에뮬레이터 user 10 = "운전자" → 상태 표시줄 "운"). 로그인 때 읽어 청취자 계정 닉네임에 맞추고(운영자 계정 제외), 못 읽으면 기존 이름을 쓰고 설정에 "Pleos 프로필을 읽지 못해 계정 이름을 써요"라고 표시.

## Pleos 프로필 이름 읽기 — BLOCKED
- 공식 문서(SDK API 정책·하드웨어 접근 정책·Resources·Status bar, 2026-11-12 확인)에 앱용 프로필/닉네임 API 없음. Resources에는 "cloud-managed driver profiles"가 향후 지원 예정으로만 적혀 있음.
- 앱이 쓰는 Pleos SDK 12개 패키지의 클래스 목록에도 프로필 API 없음(STT의 `setProfileId`는 음성 서버 설정).
- AVD: `UserManager.getUserName()` → `SecurityException`(23:13:29 `pleos profile name unavailable SecurityException`). 필요한 `QUERY_USERS`(signature|privileged|role)·`MANAGE_USERS`(signature|privileged)는 일반 앱이 받을 수 없음. `ai.pleos.service.profile`에 외부 데이터 경로 없음, 계정(AccountManager) 없음.
- 결과: 닉네임을 Pleos에서 가져오는 부분은 이 이미지에서 동작하지 않음. 지어낸 이름을 쓰지 않고 저장된 계정 이름을 첫 글자 원으로 보여 준다. 필요하면 운영사무국에 프로필 API를 문의.

## 자동 테스트 (fake/로컬, 실제 SDK 증거 아님)
- `./gradlew test :heartz:lintDebug :heartz:assembleDebug :heartz:assembleRelease :server:installDist` exit 0. core 131 / heartz 92×2 / server 10, 실패 0. lint 오류 0(경고 3, 기존). 첫 실행은 lint `MissingPermission`(getUserName) 오류로 실패 → AVD에서 확인한 대로 실패를 처리하는 코드에 사유를 적고 `@SuppressLint` 처리.
- 새 테스트: `PlacesTest` 6(반경 950 m 안·1050 m 밖, 목록/카드 표기, 25개 구·구/동/랜드마크·좌표 범위·중복 없음, 시드 사연과 시연 경로, 가장 가까운 랜드마크 연결, 예전 데이터 장소 갱신), 닉네임 3, `PleosProfileTest` 4, `StartupPolicyTest` 2(첫 방문도 바로 메인). 기존 테스트는 좌표·이름을 새 랜드마크에 맞춤.
- 변형 시험(매번 복원 확인): 반경 2배·장소 갱신 없음·가까운 곳 대신 첫 번째·운영자도 이름 변경 → 모두 실패 확인. "가까운 곳" 변형은 처음엔 안 잡혀 반경이 겹치는 경우를 테스트에 추가.
- **사고 기록**: 첫 변형 시험에서 스크래치 폴더가 없어 원본 백업·복원이 실패했고, 변형 4개가 소스에 남았음. 즉시 손으로 4곳을 복원하고 grep으로 확인한 뒤, 백업 성공·복원 후 cmp 확인을 넣은 방식으로 다시 실행. 첫 결과는 무효로 처리.

## AVD 확인
| 항목 | 판정 | 증거 |
|---|---|---|
| 첫 실행에 프로필 설정 화면 없이 메인 | PASS | 강제 종료 후 실행 → 로고 → 지금 이 길 |
| 보낸 이·목록·내 프로필이 첫 글자 원 | PASS(눈으로) | "바다"→"바", "봄날"→"봄" 등 |
| 지나온 사연 목록 "구 랜드마크", 상세·카드 랜드마크만 | PASS | "용산구 잠수교"/"잠수교" |
| 반경 1 km 도착 | PASS | 이촌한강공원에서 555 m 지점 → 23:16:18 `card shown story=s-seed-riverside place=p-riverside`, 1.7 km 떨어진 국립중앙박물관 사연은 오지 않음 |
| Pleos 프로필 이름 → 닉네임 | BLOCKED | 위 절 |
| 주행 화면·지나면서 남기기에서 새 장소 이름 | 단위 테스트만 | AVD 주행 흐름은 이번에 다시 돌리지 않음 |

- 남은 데이터: 하늘 계정 닉네임은 이전 세션의 프로필 설정에서 저장된 "장ㅗ"가 그대로 있음(앱에서 지우거나 바꾸지 않음).

---

# 장소 추가·검색, 프로필 연동은 향후 개발, 주행 UI 정리 (2026-11-13)

- 요청: 더 작은 장소까지 추가, 남기기 장소 검색창, Pleos 프로필을 읽을 수 없으면 향후 개발로 넘김, 주행 UI 수정(지금 이 길 도로 그림 간격, 도착·재생·하트 화면 힌트/“재생 중” 제거, 지나면서 남기기 녹음 화면을 남기기 녹음과 같게, 안내 “사연을 말해주세요”, 오른쪽 위 1 km 안 랜드마크/“위치 정보 없음”, 질문 화면 힌트·예시 제거, 결과 화면 운영자 문장 제거, 답이 없는데 변조 목소리가 골라지는 버그, 마이크가 꺼진 뒤 약 3초 녹음 화면이 남는 문제).

## 구현
- 장소: `SeoulLandmarks`에 생활권 장소 131곳(시장·역 앞·작은 공원 등) 추가 → 25개 구 236곳. 좌표는 근사값(지도에서 하나씩 확인하지 않음). `PlaceSearch.filter`: 띄어 쓴 낱말이 모두 구+동+이름에 들어가야 하고, 이름에 맞는 곳이 먼저. 남기기 장소 고르기를 LazyColumn + 검색창으로.
- 프로필: `PleosProfile`은 `initial(name)`만 남기고 사용자 이름 읽기·로그인 동기화·설정 안내 문구 삭제(이전 절의 `@SuppressLint` 처리도 같이 사라짐). 닉네임은 계정에 저장된 이름. HEARTZ-PLAN 3절에 “향후 개발: Pleos 프로필 연동” 추가.
- 주행 화면: 도착·재생·하트 질문의 VoiceHint와 “재생 중/읽는 중” 줄 삭제(“곧 읽어드릴게요”는 유지), 대기 그림 위 여백 56dp.
- 지나면서 남기기: `RecordPanel`(● 녹음 중, 큰 시간 / 1:00, 220dp 정지·마이크 원, 버리기)을 안내 중·녹음 중에 사용. 머리 칩은 녹음 장소(반경 1 km 안 가장 가까운 랜드마크) 또는 “위치 정보 없음”. 질문 화면 힌트·예시, 결과 화면 본문 삭제. TTS “사연을 말해주세요.” / “사연을 다시 말해주세요.”
- 변조 목소리 자동 선택 버그 원인: 앱이 낸 질문 TTS(“원하시면 변조 목소리로…”)를 STT가 되받아 적어 “변조”가 답으로 해석됨. `VoiceReplies.isEcho`(정규화 8자 이상, 들은 말의 2글자 묶음 85% 이상이 질문에 있음)면 답으로 쓰지 않고 한 번 더 듣는다(지나면서 남기기·사연 읽기 질문 모두).
- 화면 지연 원인: 녹음을 멈춘 뒤 잡음 줄이기(약 3초)를 기다린 다음 질문 상태로 바꿨음. 잡음 줄이기를 `async`로 돌리고 보내기·초안 저장 직전에만 기다리도록 변경(지운 녹음은 정리가 끝난 뒤 삭제).

## 자동 테스트 (fake/로컬, 실제 SDK 증거 아님)
- `./gradlew test :heartz:lintDebug :heartz:assembleDebug :heartz:assembleRelease :server:installDist` exit 0. core 138 / heartz 95×2 / app 9×2 / server 10, 실패 0. lint 오류 0, 경고 3(기존).
- 새·바뀐 테스트: `PlaceSearchTest` 5, `VoiceRepliesTest` 되받기 판정, `RoadsideControllerTest`(짧은 녹음 안내, 정리 중에도 바로 질문 화면, 되받은 질문으로 변조가 골라지지 않음, 되받기 뒤 실제 답 사용), `LetterVoiceGuideTest` 되받기.
- 변형 시험(백업·cmp 복원 확인): `echo-never`(되받기 판정 끔), `roadside-no-echo-check`·LetterVoiceGuide 되받기 확인 제거, `cleanup-blocks-screen`(질문 전에 정리를 기다림), `send-before-cleanup`(정리 전에 보냄), `search-any-token`(낱말 하나만 맞아도 통과) → 해당 테스트 실패 확인. 검색 변형은 처음에 안 잡혀 “용산구 잠수” 여러 낱말 테스트를 추가.

## AVD 확인 (emulator-5554, user 10)
마이크는 노트북 마이크가 아닌 PulseAudio 가상 소스(`heartz_feed`/`heartz_vmic`)로 넣었다. 사연 음성은 debug 자산 `ko-sample.m4a`, 답 음성(“네, 보내줘.”, “아니요, 내 목소리로 해줘.”)은 Pleos TTS 출력을 녹음한 시험 입력이다. 끝난 뒤 `hostmicoff`, qemu 입력을 원래 소스로 되돌리고 모듈을 제거했다. 기어는 P로 돌렸다.

| 항목 | 판정 | 증거 |
|---|---|---|
| 지금 이 길 도로 그림 간격 | PASS(눈으로) | 주차 화면 캡처 |
| 남기기 검색 | PASS | “63” → 63빌딩 |
| 도착·재생·하트 화면에 힌트·“재생 중” 없음, “이번 사연은 어떠셨어요?”만 | PASS(눈으로) | 이촌한강공원 도착 10장 캡처 |
| 녹음 화면 = 남기기 녹음 구성, 칩 “이촌한강공원” | PASS(눈으로) | 00:22·00:25 실행 캡처 |
| 안내 “사연을 말해주세요.” | PASS | `[tts] speak chars=10`, CAAS `doSpeak … text=사연을 다시 말해주세요.`(다시 녹음) |
| 마이크가 꺼진 뒤 바로 질문 화면 | PASS | 00:25:42.971 auto finish → 43.032 파일 → 43.72–44.26 캡처가 전달 질문 화면, 잡음 정리는 46.580에 끝남(3547 ms, 뒤에서) |
| 질문 화면 힌트·예시 없음 | PASS(눈으로) | 전달·목소리 질문 캡처 |
| 답 없음 → 성공으로 바꾸지 않음 | PASS | 00:22 실행: 두 번 null → `deliver=null` → “답을 듣지 못해 초안으로 보관했어요” |
| 목소리 질문에서 답 없이 변조 선택 안 됨 | PASS | 00:25 실행: STT “아니요 내 목소리로 해줘” → `voice answer=ORIGINAL`, 되받기 로그 없음, 저장 `voice=ORIGINAL status=PENDING_REVIEW` |
| 결과 화면 운영자 문장 없음 | PASS(눈으로) | “검수에 보냈어요” + 확인 |
| Pleos 프로필 이름 → 닉네임 | 향후 개발(BLOCKED 유지) | 이전 절 |

- 00:24 실행 중 시스템 로그에 스크립트가 아닌 화면 터치 3번(`SingleFingerEventView clicked` 02.953·10.107·11.967)이 있었고, 그대로 다시 녹음·정지·보내기가 실행됐다. 앱 오동작이 아닌 실제 터치로 판단하고 판정에 쓰지 않았다.
- 직전 주행 시험의 하트 “남겨줘”(STT)는 되받기가 아니었다. 그때 qemu 입력이 노트북 기본 마이크에 연결돼 있어 실제 소리가 들어간 것으로 보인다.
- 남은 데이터(기기 안 데모): 시험으로 지나면서 남기기 사연 2개(`ad1b0c95…`, `c8055921…`, 검수 중)와 초안 1개가 생겼다. 받은 사연 기록은 CLEAR_LISTENS로 지웠다. 하늘 계정 닉네임 “장ㅗ”는 그대로.

---

# 시연 계정·시드 정리, 팀원 녹음 반영 (2026-11-13)

- 요청: 계정을 장클로드·김피티·상도동메시 세 운전자로(바다→장클로드, 봄날→김피티, 은행나무→상도동메시), 루트의 팀원 녹음 파일(대본 R1)를 김피티 사연으로, 더미 사연은 데모에 필요한 몇 개만 남기고 삭제.

## 구현
- `SeedData`: 계정 id는 그대로 두고 이름만 바꿈(`DRIVER_JANG`=u-listener-b, `DRIVER_KIM`=u-seed-spring, `DRIVER_MESSI`=u-seed-ginkgo, 셋 다 로그인 가능). 하늘(u-listener-a) 삭제, 운영자 유지. `LISTENER_A/B` 상수 삭제.
- 시드 사연 6 → 4: 김피티 이촌한강공원(팀원 녹음 `seed-kim`, 13초, `TEAM_LABEL` "팀원 녹음 시연 사연 · 실제 사연 아님"), 김피티 잠수교(합성), 상도동메시 국립한글박물관(합성), 상도동메시 국립중앙박물관(검수 대기, 합성). 쓰지 않는 합성 음원 seed-warm·seed-cheerful·seed-river 삭제.
- 녹음 확인: 가상 마이크(노트북 마이크 아님)로 에뮬레이터 Pleos STT에 들려줌 → "2000한강공원 입구 벤치에서 퇴근하고 매일 10분씩 강을 보다 들어가곤 했어요 / … 이상하게 괜찮아지더라고요 / 오늘 이 길 지나는 분도 하루 정말 고생 많으셨습니다" = 녹음 대본 R1. 변환: 앞뒤 무음 제거 + `loudnorm I=-16` + AAC 64 kbps mono → 13.45초. 원본 파일은 루트에 그대로 둠.
- 설정 프로필 설명 "테스트 청취자" → "테스트 운전자".

## 데이터 삭제(사용자 요청)
- 기기(user 10): 이전 db에 사연 35개(하늘 24, 바다 5, 시드 6). 바다의 5개는 2–4초 시험 녹음뿐이어서 남기지 않음. `pm clear --user 10 ai.heartz.app` → 권한 재부여(`pleos.car.permission.FUSED_LOCATION`, `RECORD_AUDIO`) → 새 시드 4개 확인.
- 개발 서버: 서버 중지 → `build/heartz-server-data` 삭제(사연 9개) → 다시 실행 → `/v1/test-users` = 장클로드·김피티·상도동메시·운영자, 사연 4개 확인.

## 검증
- 시드 테스트를 먼저 바꿔 컴파일 실패(red) 확인 → 구현 → 통과. 서버 하트 테스트는 김피티가 자기 사연에 하트를 눌러 400("cannot heart your own story") → 시드 사연이 없는 장클로드로 바꿈.
- `./gradlew test :heartz:lintDebug :heartz:assembleDebug :heartz:assembleRelease :server:installDist` exit 0. core 139 / heartz 95×2 / app 9×2 / server 10, 실패 0. lint 오류 0, 경고 3(기존).
- AVD: 지금 이 길 = 장클로드('장'), 설정 계정 = 장클로드·김피티·상도동메시·운영자. 기어 D + 밖 → 이촌한강공원(126.9650 37.5175): 01:14:04 `card shown story=s-seed-riverside`, 카드 "이촌한강공원 · 13초 · 보낸 이 김피티", 재생 08.93 → 22.60(13.7초) → 공감 질문 → 마이크 입력 없음 → `heart reply=UNCLEAR`(하트 0). 이후 기어 P, `CLEAR_LISTENS ok=true`로 장클로드 받은 기록 비움.

---

# 상도동메시 팀원 녹음 반영 (2026-11-13)

- 요청: 루트의 팀원 녹음 파일(대본 R4)(14.9초)를 상도동메시 사연으로, 김피티 때와 같은 방식으로.
- 녹음 확인: 에뮬레이터를 Cold Boot로 다시 켬(노트북 재부팅 뒤 꺼져 있었음). 가상 마이크(노트북 마이크 아님)로 Pleos STT에 들려줌 → "한글박물관에서 아이가 처음으로 자기 이름을 써봤어요 / 비투비트라는 글씨를 보고 둘이 한 장 웃었는데 그 종이가 아직 냉장고에 붙어있어요" = 녹음 대본 R4. 끝난 뒤 qemu 입력을 원래 소스로 되돌리고 가상 마이크 모듈만 제거. 사용자의 녹화용 `rec_mix` 모듈은 그대로 둠.
- 변환: seed-kim과 같은 방법(앞뒤 무음 제거, `loudnorm I=-16`, AAC 64 kbps mono) → `seed/audio/seed-jeon.m4a` 12.8초. `s-seed-dongbu`(상도동메시 · 국립한글박물관)를 `seed-jeon`, 13초, `TEAM_LABEL`로. 쓰지 않는 합성 음원 seed-nostalgic 삭제.
- **데이터를 지우지 않음:** 기기에 리허설 기록이 있어서 김피티 때처럼 `pm clear`를 하지 않았다. 대신 `refreshSeedAudio`(음원 바이트만 교체)를 `refreshSeedStories`로 바꿨다. 시드 사연의 id가 같으면 음원 파일·길이·표시를 지금의 정의로 맞추고, 상태·작성 시각·청취 기록·사람이 남긴 사연은 그대로 둔다. 앱(기기 안)과 서버가 시작할 때 부른다. `durationsByBlob`은 쓰지 않게 되어 삭제.
- TDD: 서비스 테스트(예전 데이터의 합성 음원 → 새 팀원 녹음, 작성 시각·상태 유지, 음원이 없으면 그대로, 사람 사연 그대로)와 시드 테스트(상도동메시 사연 = seed-jeon·TEAM_LABEL·13초, 음원 목록 = 쓰는 것만)를 먼저 써서 실패 확인 → 구현 → 통과. 처음 쓴 하트 수 단언은 하트 수가 청취 기록에서 계산되는 값이라 틀린 가정이었고, 작성 시각 보존 단언으로 바꿈.
- 변형 시험(백업·cmp 복원 확인): 표시 갱신 빼기·음원 파일 바꾸지 않기 → 실패 확인. "합성 표시 없는 사연도 바꿈"은 사람 사연과 시드 id가 겹칠 수 없어 동작이 같은 변형(살아남음, 문제 아님).
- `./gradlew test :heartz:lintDebug :heartz:assembleDebug :heartz:assembleRelease :server:installDist` exit 0. core 140 / heartz 95×2 / app 9×2 / server 10, 실패 0. lint 오류 0, 경고 3(기존).
- AVD(user 10): 설치 전 `s-seed-dongbu` = seed-nostalgic 7초 합성 → 설치(데이터 유지) 후 seed-jeon 13초 팀원 녹음, 사연 4개·청취 기록 그대로. 기기 blob `seed-jeon`을 꺼내 `seed/audio/seed-jeon.m4a`와 `cmp` 일치(12.8초). 리허설 기록을 바꾸지 않으려고 주행 재생은 하지 않았다(재생 경로는 seed-kim 때 주행으로 확인한 것과 같음).
- 개발 서버: 기존 데이터(seed-nostalgic) 그대로 다시 실행 → seed-jeon 13초 팀원 녹음, blob `cmp` 일치.

---

# 상도동메시 사연 → 변조 목소리(TTS), 데모 흐름 변경·리허설 (2026-11-13)

- 요청: 방금 넣은 상도동메시 사연을 TTS 사연으로. 데모 흐름 변경: 장클로드 D → 바깥 → 이촌한강공원(김피티 음성 사연) → Gleo로 지나면서 남기기(내 목소리) → 국립한글박물관(상도동메시 TTS 사연) → 바깥 → P → 지나온 사연에서 재생 → 주차 중 장소 골라 남기기 → 내 사연함.

## 구현
- `SeedData`: `s-seed-dongbu`를 `voiceMode=MODULATED`, `spokenText`=대본 R4 글로. 원본 녹음(seed-jeon)은 운영자만 받는다(기존 규칙 `audio()`: 변조 목소리 사연 원본은 작성자·운영자만).
- `refreshSeedStories`가 목소리·읽어 줄 글도 맞춤 → 기존 기기·서버 데이터도 지우지 않고 바뀜.
- TDD: 시드 테스트(상도동메시 사연 MODULATED + 글, 나머지 ORIGINAL)와 서비스 테스트(갱신 때 목소리·글 반영) 먼저 실패 확인 → 구현 → 통과. 서비스 테스트가 일반 청취자로 원본을 받으려다 NOT_FOUND → 규칙대로라 테스트를 운영자 수신 + 청취자 거부 확인으로 고침.
- `./gradlew test :heartz:lintDebug :heartz:assembleDebug :heartz:assembleRelease :server:installDist` exit 0. core 140 / heartz 95×2 / app 9×2 / server 10, 실패 0. lint 오류 0, 경고 3(기존).

## 리허설 (AVD, 가상 마이크, 시험 입력)
- 준비: 앱 데이터(files·shared_prefs)를 run-as tar로 백업 → 리허설 → 복원.
- 1차: Virtual Console 탭이 닫혀 있어 기어 PUT 503 → 기어 P인 채 진행(무효). 2차: 탭을 연 뒤 D에서 카드가 안 뜸 → 원인: 1차에서 이미 띄운 사연을 `LetterSession`이 앱 메모리에 기억(`CLEAR_LISTENS`는 저장된 기록만 지움). 3차: `CLEAR_LISTENS` + 앱 재시작 후 정상.
- 3차 결과(12:20–12:23): 이촌한강공원 `card shown story=s-seed-riverside` → 재생 13.7초 → 공감 질문 → (답 없음) `heart reply=UNCLEAR`. Gleo 모의 호출 `am start -a START_STORY_RECORDING` → `[gleo] action=START_STORY_RECORDING` → `roadside start source=GLEO place=p-riverside` → Gleo 조용함 대기(`NO_SPEECH`, 4초) → "사연을 말해주세요." → 시험 음성 → 자동 종료 → `voice answer=SUBMIT` → `voice answer=ORIGINAL` → `saved … PENDING_REVIEW voice=ORIGINAL roadside=true`. 한글박물관 안쪽(126.9850 37.5240) `card shown story=s-seed-dongbu` → `[reader] user action read story=s-seed-dongbu parts=2` → `read to end`(약 9.4초) → 공감 질문 → UNCLEAR. 바깥(126.9900 37.5330) → P → 지금 이 길에는 카드 없이 대기 화면 + 지나온 사연 버튼 → 목록 "용산구 국립한글박물관 · 상도동메시 · 방금 들음", "용산구 이촌한강공원 · 김피티" → 상세 듣기 → TTS로 다시 읽음(`read to end`). 내 사연함에 지나면서 남긴 사연 "검수 중".
- 실제 Gleo 음성 → `START_STORY_RECORDING`: NOT_RUN(모의 호출만). 주차 중 남기기 녹음은 이번 리허설에서 하지 않음(장소 검색은 이전에 확인).
- 복원: 사연 4개·장클로드 청취 기록이 백업과 같음, 상도동메시 사연은 시작 때 MODULATED로 갱신됨. 리허설에서 생긴 사연 3개·빈 초안 파일 제거됨. 가상 마이크 모듈 제거, qemu 입력 원래 소스로, 사용자의 녹화용 `rec_mix`는 유지. 기어 P, 위치 바깥(126.9450 37.5290).
- 발견: 장클로드의 김피티 사연 하트는 백업 시점(12:05)에 이미 있던 것(사용자 리허설). `CLEAR_LISTENS`는 하트를 지우지 않는다.
- 문서: `.docs/DEMO-SCENARIO.md`를 새 흐름으로 다시 씀(좌표·대사·촬영 전 준비: 받은 기록 지우기 + 앱 재시작), `.docs/DEMO-RECORDING-SCRIPT.md`에 L1(이촌한강공원)·P1(남산서울타워) 대본.

---

# 공식 가이드 대비 규칙 점검 (2026-11-13)

출처(모두 2026-11-13 열람, HTTP 200): 공모전 guide/overview·submission·development·judging-awards·rules·help, 공지 5건(001·005·006·007·008), FAQ / Pleos 기획 가이드 9쪽, 개발 가이드 9쪽 + 폼팩터 테스트, SDK(TTS·STT·FusedLocation·NaviHelper) 소개, Gleo core-concepts·app-capability·action_callback·deliver-schema, 디자인 layout·window, getting-started·create-my-project.

## 제출 요건과 다른 점 (수정 필요)
| 항목 | 규칙 | 현재 | 
|---|---|---|
| APK | 실행 가능한 release, `apk/팀명_앱이름_release.apk` | `heartz-release-unsigned.apk`(서명 없음, 설치 불가), apk/ 폴더 없음 |
| README | 앱 이름·소개, 실행 방법, 테스트 환경, **에뮬레이터 버전과 화면 규격**, APK 위치 | 화면 규격 없음, APK는 "넣을 예정" |
| 저장소 파일 | 제3자 권리·개인정보 주의 | 루트의 녹음 원본 2개·노션 zip·화면 zip·image.png가 .gitignore에 없음 |
| 공개 전환 | 11/16까지 Public, 11/23까지 유지, 메일에 최종 커밋 해시 | 아직 커밋·원격 없음(사용자 요청 시 진행) |

## 가이드 규칙과 다른 점 (수정 권장)
- Gleo App Schema: "선택적 parameter는 포함하지 마세요" ↔ `MANAGE_MY_STORY.voice`가 선택 파라미터. `version`은 "앱 마켓에 등록된 앱 버전" ↔ 스키마 0.4.0, 앱 versionName 0.1.0.
- CCP Back 하드키: 최상단에서 Back이면 종료하지 말고 `moveTaskToBack(true)`로 숨김 ↔ 최상단 Back 처리 없음(Activity 종료).
- 보안·개인정보: 위치·음성을 요구할 때 "왜 필요한지" 명확히 ↔ 위치 권한은 앱 시작 때 설명 없이 요청, 마이크는 설정·지나면서 남기기 스위치에서 설명 없이 요청(남기기 화면에는 설명 있음).
- 권한 최소화: release의 `pleos.car.permission.NAVI_ROUTE`는 기술 진단 화면의 NaviHelper 상태 조회에만 쓰임.
- Music/Audio 가이드 "앱 연결 시 자동 재생 금지, 사용자의 명시적 동작 또는 시스템 재개 요청에 따라 재생" ↔ 도착 사연 바로 읽기(기본 켜짐, 사용자 결정). 연결 시 재생은 아니지만 회색 지대.

## 권장 사항
- 음성 안내 선행 묵음 150–200 ms 권장 ↔ seed-kim 앞 무음 133 ms, 플레이어가 무음을 더하지 않음.
- Suspend 모드 진입 시 재생 중단·포커스 반납 처리 없음(재생할 때만 포커스를 잡음).
- 디자인 가이드: Landscape·Portrait 모두 지원 ↔ 폼팩터 시험 NOT_RUN.
- 공지 008: 참가팀은 Gleo Interface 검토·승인을 신청할 수 없음 → Gleo 연동은 "에뮬레이터 시연, 실제 활성화는 Pleos 검토 필요"로 표기. 실제 Gleo 음성 → START_STORY_RECORDING은 NOT_RUN(사용자 촬영에서 사용했다면 그 결과로 갱신).

## 규칙에 맞음 (확인 근거)
- 공지 008 "차량 마이크 음성을 외부 음성 인식 서비스로 보내지 않음": TTS·STT 모두 Pleos SDK ON_DEVICE(`PleosSpeech.kt`), 앱 등록·비밀키 불필요. LLM SDK·비공개 SDK 미사용.
- 개발 환경: minSdk 28, Gradle 8.10.2, Kotlin.
- 개인정보: 사연 생성 요청에 좌표 없음(`CreateStoryRequest(clientDraftId, placeId, mood, durationMs)`). release 로그에서 좌표(`HeartzLog.coord`)와 STT 글(`BuildConfig.DEBUG`일 때만) 가림. 받아쓰기는 작성자·운영자만. release 평문 HTTP 금지.
- 비밀값: 저장소 후보 파일 358개에서 키·토큰·비밀번호·사용자 이메일 없음. `.gitignore`에 keystore·접수양식 docx 포함. 전화번호 형태는 테스트용 010-1234-5678과 출처 URL ID뿐.
- 주행 제한: DO 선언(사용자 결정) + `CarUxRestrictions`로 주행 화면 전환, 주행 중 키보드·리스트 없음. FusedLocation `isSupported()` 확인.
- 오디오: 재생 전 포커스 요청, LOSS/LOSS_TRANSIENT 처리. Music/Audio: `appCategory="audio"`, MediaLibraryService·MediaSession, `androidx.car.app.launchable`.
- 성능(AVD, D 대기 상태): PSS 146 MB(≤400 MB), CPU 0–0.5%(<5%). 재생 중 수치는 재지 않음.
- 제3자 자료: Pretendard는 기기 /product/fonts에서 읽음(앱에 미포함), 시험 음악은 저장소 제외 + CC BY 표기, unslop CC BY 4.0 표기 유지, 시드 합성 음성은 Pleos TTS 출력, 팀원 녹음은 동의 전제.

---

# 제출 요건 정리: release 서명·README·저장소 제외 파일 (2026-11-13)

- release 서명: `keytool`로 `.tools/release/heartz-release.jks`(PKCS12, RSA 2048, 유효 10000일, alias heartz) 생성. 비밀번호는 무작위 24자로 루트 `keystore.properties`(권한 600)에만 기록하고 화면·로그에 출력하지 않음. 두 파일 모두 `.gitignore` 대상(`.tools/`, `keystore.properties`, `*.jks`) 확인. `heartz/build.gradle.kts`는 `keystore.properties`가 있을 때만 release에 서명(없으면 기존처럼 unsigned).
  - **키를 잃으면 같은 서명으로 업데이트할 수 없으므로 `.tools/release/`와 `keystore.properties`를 저장소 밖에 따로 백업해야 한다.**
- `./gradlew :heartz:assembleRelease` exit 0 → `heartz-release.apk`(19.3 MB). `apksigner verify --print-certs` exit 0, `CN=HEARTz, O=HEARTz Team, C=KR`.
- release 실행 확인(AVD): debug 앱 데이터를 run-as tar로 백업(`.tools/backup/heartz-debug-data-20261113.tar`, 사연 6개) → debug 제거 → release 설치(pkgFlags에 DEBUGGABLE 없음) → 시작 시 충돌 없음, TTS·STT·FusedLocation 초기화, 장클로드 로그인(LOCAL_DEMO), 위치 로그 `(redacted)`. D에서 이촌한강공원 → `card shown story=s-seed-riverside` → 재생 13.7초 → 공감 질문. 한글박물관 사연은 release 시작 때 위치가 이미 그 근처여서 주차 화면에 먼저 떠 '받은 사연'이 되어 주행 중 다시 오지 않음(정상 동작, 로그 `card cleared story=s-seed-dongbu` 13:23:45) → 주차 화면 상세에서 듣기 → `[reader] read story=s-seed-dongbu parts=2 → read to end`. R8 축소 빌드에서 직렬화·SDK 문제 없음.
  - 복원: release 제거 → debug 재설치 → 권한 재부여 → 백업 복원 → 사연 6개·녹음 blob 2개·lastUser 장클로드 확인. 위치 바깥(126.9450 37.5290).
- README: 테스트 환경에 에뮬레이터 빌드(`release_connect_v2.1.0`)와 **화면 규격(2560×1440 px, 160 dpi, 가로, 분할 창 약 840 px)** 추가. APK 절을 서명 APK 경로로 바꿈(팀명 미정이라 `TEAMNAME` 자리표시). 7번의 동물 프로필·최초 설정 설명을 현재 동작(첫 글자 원, 설정 화면 없음)으로 고치고, 8번에 Gleo 연동 범위(에뮬레이터, 공모전 기간 승인 신청 불가) 추가.
- `.gitignore`: 루트의 팀원 녹음 파일(대본 R1), 팀원 녹음 파일(대본 R4), `*.zip`, `image.png` 제외(`git check-ignore` 확인). 파일 자체는 지우지 않음.
- 남은 일: 팀명 확정 → `apk/<팀명>_HEARTz_release.apk` 복사·README 반영. 커밋·원격 저장소·공개 전환은 사용자 요청 시.
