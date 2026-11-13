# 프로젝트 공통 개발 지침

## 작업 기준
- 사용자와 한국어로 소통한다. 코드 식별자는 영어를 사용한다.
- .docs/SETUP.md와 .docs/REFERENCES.md를 먼저 읽는다.
  (이 저장소의 실제 문서 폴더는 .docs/이다. 중복 폴더를 만들지 않는다.)
- 실제 참고 파일명이 reference.md라면 그 파일을 읽고 이름 차이를 기록한다.
- GitHub 저장소 준비와 버전 선택 전에는 참고 목록의 공모전 공식 사이트와
  Pleos 개발자 가이드 원문을 확인하고 현재 요건을 따른다.
- 이 SETUP의 버전 표는 조사 당시 참고값이다. 현재 공식 요건이 우선한다.
- 공식 문서가 정하지 않은 버전은 도구별 공식 호환성을 확인해 선택한다.
- 출처·확인일·선택 이유·실제 버전은 .docs/SETUP-RESULTS.md에 기록한다.
- 저장소 공개 범위·심사 접근 권한·소스/APK 제출 방식도 공식 안내를 따른다.
- 사용자 요청 없이 원격 저장소 생성·공개·push·초대·외부 배포·메일을 하지 않는다.

## 구현
- Linux, Kotlin + Jetpack Compose, Pleos 전용 에뮬레이터를 사용한다.
- 아이디어 확정 전에는 환경과 중립적인 SDK 검증 앱만 만든다.
- 기존 파일·사용자 변경을 보존하고 필요한 범위만 수정한다.
- 실차·DevBox 또는 계기판·슬림 디스플레이·HUD 제어를 가정하지 않는다.
- 실제 SDK의 capability·area·권한·서비스 가용성을 확인한다.
- 초기화 반환, 실제 read, callback, 제어 요청 수락, 실제 상태 변화를 구분한다.
- null·미지원·오류를 0 또는 성공으로 바꾸지 않는다.
- fake는 명시하고 실제 SDK 연결 증거로 쓰지 않는다.
- 리스너·코루틴·연결 자원은 수명주기에 맞춰 정리한다.
- 차량 제어는 명시적인 사용자 동작으로만 수행한다.
- 주행 제한 우회·임의 distractionOptimized 선언을 하지 않는다.
- Gleo mock 호출과 실제 Gleo end-to-end를 구분한다.
- 비밀키·개인정보·서명키를 소스나 로그에 넣지 않는다.
- IDE·Gradle·adb·에뮬레이터를 root로 실행하지 않는다.
- 사용자 요청 없이 하위 에이전트를 생성하지 않는다.

## 로컬 환경
- 도구 경로·환경 변수는 `source .tools/env.sh`로 설정한다(로컬 전용, Git 제외).
- 재시작 절차는 .docs/SETUP-RESULTS.md의 "재시작" 절을 따른다.

## 스킬
- karpathy-guidelines: 구현 전 가정·범위·복잡도를 점검한다.
- test-driven-development: 기능 코드의 동작 변경에 적용한다.
- 설정·문서 변경에 의미 없는 테스트를 만들지 않는다.
- systematic-debugging: 재현·원인 확인 후 수정한다.
- verification-before-completion: 실제 실행 증거를 확인하고 완료를 보고한다.
- drawio-skill: 다이어그램 요청 시 명시적으로 호출한다.
- unslop: 화면이 AI가 만든 티가 나거나 다듬기 요청이 있을 때 쓴다.
  원본: github.com/heydamir/unslop(97daa3e), CC BY 4.0, 작성자 표기 유지.
  차량 화면 예외: tokens.md의 웹 크기 대신 HEARTz 크기를 지킨다
  (본문 26sp 이상, 주요 버튼 76dp 이상, 아이콘 32dp 이상).
  snapshot·story 스크립트(Playwright)는 Android 화면에 쓰지 않는다.
- standalone 설치에서 superpowers:test-driven-development와
  superpowers:verification-before-completion은 같은 이름의 로컬 스킬로 해석한다.
- .agents/skills와 .claude/skills의 버전·지원 파일은 동일하게 유지한다.
- 스킬은 사용자 요청과 상위 시스템·개발자 지시보다 우선하지 않는다.

## 완료
- 빌드 성공만으로 실제 SDK 연결 성공을 선언하지 않는다.
- 실행 명령·종료 코드·버전·관찰·증거를 SETUP-RESULTS.md에 남긴다.
- FAIL / BLOCKED / UNSUPPORTED / NOT_RUN을 PASS로 바꾸지 않는다.
