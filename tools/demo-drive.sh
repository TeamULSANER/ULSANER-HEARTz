#!/usr/bin/env bash
# 시연 경로를 에뮬레이터 위치 입력(geo fix)으로 재생한다. 테스트 입력이며 실제 주행 위치가 아니다.
# 앱은 Pleos FusedLocation SDK 콜백으로 이 위치를 받는다(앱 수신 증거는 logcat -s Heartz).
# 사용: source .tools/env.sh && tools/demo-drive.sh [serial] [stepMeters=15] [intervalSec=1]
set -euo pipefail
cd "$(dirname -- "${BASH_SOURCE[0]}")/.."
# env.sh를 안 읽은 터미널에서도 JDK 17·SDK adb를 쓰도록(시스템 java 11이면 서버 실행 실패)
[[ -f .tools/env.sh ]] && source .tools/env.sh
serial="${1:-emulator-5554}"
step="${2:-15}"
interval="${3:-1}"
adb_bin="${adb:-adb}"
server_bin=server/build/install/server/bin/server
[[ -x "$server_bin" ]] || ./gradlew -q :server:installDist
"$server_bin" --print-demo-route "$step" | while read -r lng lat _bearing; do
  echo "$(date +%T) TEST INPUT geo fix $lng $lat"
  "$adb_bin" -s "$serial" emu geo fix "$lng" "$lat" 20 >/dev/null
  sleep "$interval"
done
