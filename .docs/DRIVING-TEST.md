# 주행 중 사연 듣기·지나면서 남기기 시험

대상은 Pleos 전용 에뮬레이터다. 위치·기어·마이크는 시험 입력이며 실제 차량 주행이 아니다. 앱은 플랫폼 `CarUxRestrictions`로 화면을 바꾸고, `geo fix`로 넣은 위치는 Pleos FusedLocation 콜백으로 받는다. 장소는 서울 랜드마크이고 **반경 1 km 안에 들어오면** 사연이 도착한다(2026-11-13 기준).

## 준비

```bash
cd ~/pleos_app && source .tools/env.sh
s=emulator-5554
adb -s $s shell am start --user 10 -n ai.heartz.app/.MainActivity
adb -s $s forward tcp:8088 tcp:8088
xdg-open http://127.0.0.1:8088/      # Virtual Console 탭을 열어 둔다(닫으면 기어 명령이 503)
```

- 데이터 연결은 **기기 안 데모**를 권장한다(설정 › 데이터 연결). 개발 서버를 쓰면 서버를 먼저 켠다: `server/build/install/server/bin/server --data build/heartz-server-data --seed-audio seed/audio`.
- 이미 받은 사연은 다시 오지 않는다. 기기 안 데모에서만 받은 기록을 지울 수 있다(debug 전용, 하트·숨긴 사람은 유지):
  ```bash
  adb -s $s shell am broadcast --user 10 -n ai.heartz.app/.debug.DebugCommandReceiver -a ai.heartz.app.debug.CLEAR_LISTENS
  ```
  또는 P → 설정 → 정보 · 기술 진단 → 받은 사연 기록 지우기. 개발 서버에서는 지워지지 않는다(`ok=false`).

## 기어

```bash
curl -X PUT "http://127.0.0.1:8088/vehicle/gear?gear=Drive"   # 주행
curl -X PUT "http://127.0.0.1:8088/vehicle/gear?gear=Park"    # 주차
```

## 위치 (`geo fix` 인자는 경도, 위도 순)

| 위치 | 명령 | 기대 |
|---|---|---|
| 랜드마크 밖(한강 위, 가장 가까운 랜드마크 1.08 km) | `adb -s $s emu geo fix 126.9450 37.5290` | 대기 화면. 지나면서 남기기 칩 "위치 정보 없음" |
| 이촌한강공원(시드 사연) | `adb -s $s emu geo fix 126.9650 37.5175` | 이촌한강공원 사연 도착 |
| 국립중앙박물관(시드 사연) | `adb -s $s emu geo fix 126.9803 37.5240` | 박물관 사연 도착. 312 m 거리의 국립한글박물관도 반경 안이라 그 사연이 대기열에 선다 |
| 잠수교(시드 사연) | `adb -s $s emu geo fix 126.9955 37.5165` | 잠수교 사연 도착 |
| 시연 경로 전체(서→동) | `tools/demo-drive.sh $s 15 1` | 15 m 간격·1초마다 위치를 보낸다. Ctrl+C로 멈춤 |

같은 위치를 다시 보내도 새 사연이 오지 않으면 밖 → 안 순서로 보내거나 받은 기록을 지운다. 위치 명령은 입력만 성공시킨다. 카드·음성은 화면과 로그로 확인한다.

## 지나면서 남기기(녹음)

1. 기어 D, 위치를 넣은 뒤 아래 큰 버튼 **지나면서 남기기**를 누른다(또는 Gleo "지금 지나는 곳에 사연 남길래").
2. 직접 말하려면 에뮬레이터 마이크를 PC 마이크에 연결한다. 끝나면 끈다.
   ```bash
   adb -s $s emu avd hostmicon
   adb -s $s emu avd hostmicoff
   ```
   켜 두면 주변 소리가 하트·전달 질문의 답으로 들어갈 수 있다.
3. "사연을 말해주세요" 뒤에 말하고, 3초 조용하면 자동으로 멈춘다. 전달 질문에 "보내줘"/"다시"/"버려", 목소리 질문에 "내 목소리로"/"변조로"라고 답하거나 버튼을 누른다.
4. 랜드마크 밖에서 녹음하면 장소가 없어 초안으로 남는다. 주차 후 내 사연함에서 장소를 고른다.

## 로그

```bash
adb -s $s logcat -s Heartz:I | grep -E "location|card shown|playback|heart|roadside|stt\]|studio\] saved"
```

관찰할 흐름: 위치 콜백 → `card shown` → 도착 안내 → 재생 → 공감 질문 / `roadside start … place=` → 녹음 → `voice answer=` → `studio] saved`.

## 끝내기

기어를 P로 되돌리고 `hostmicoff`를 실행한다.
