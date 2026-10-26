#!/usr/bin/env bash
# App Store 스크린샷 자동 촬영 — iPhone Duo(폴더블) 안쪽 화면 2007×2853. ios-screenshots.yml 에서 호출(macOS 러너).
#
# 1~3차(2026-10-21)에서 Duo 화면이 전부 검정이었다. 원인 추정: 안쪽(기본) 화면이 꺼진 채로 부팅 → 기본 화면 촬영은 검정,
#   `--display=N` 로 찍어 보면 켜져 있는 바깥 화면 하나만 응답해 "화면이 1개만 보인다"가 됐고, 안쪽을 켜는 코드(power on)는
#   화면이 2개 이상 보일 때만 실행되도록 되어 있어 **한 번도 실행되지 않았다.**
#   → 이번엔 화면을 찾기 전에 먼저 모든 화면에 power on 을 시도하고, 켜진 뒤 크기·밝기로 안쪽 화면을 고른다.
#   (안쪽 화면을 *끄는* 시도는 SpringBoard 가 죽어 잡이 멈췄다 — 절대 끄지 않는다.)
#
# CI 로그는 로그인 없이 못 읽으므로, 진단 결과(도움말 · enumerate · 화면별 크기/밝기 · ASCII 축소판)는 `::notice::` 주석으로도 남긴다.
#
# 환경변수:
#   MODE       probe = 시뮬레이터/화면 점검만(앱 설치·촬영 없음), shots = 촬영(기본)
#   APP_PATH   빌드된 Stary.app 경로(shots)
#   OUT_DIR    결과 폴더(duo/언어/장면.png)
#   LANGS      "ko" (성공하면 "ko en")
#   SCENES     "login map constellation globe"
#   SHOT_LAT / SHOT_LNG / SHOT_ZOOM   지도 위치·줌
#   PY         Pillow 가 있는 파이썬(없으면 python3)
set -uo pipefail

MODE="${MODE:-shots}"
BUNDLE_ID="com.chaminwoo.stary.ios"
LANGS="${LANGS:-ko}"
SCENES="${SCENES:-login map constellation globe}"
SHOT_LAT="${SHOT_LAT:-37.5665}"
SHOT_LNG="${SHOT_LNG:-126.9780}"
SHOT_ZOOM="${SHOT_ZOOM:-13}"
WANT_W=2007
WANT_H=2853
PY="${PY:-python3}"
HERE=$(cd "$(dirname "$0")" && pwd)
OUT_DIR="${OUT_DIR:-screenshots}"
mkdir -p "$OUT_DIR"
# simctl launch --stdout/--stderr 는 절대 경로만 받는다(상대 경로면 앱 실행 자체가 "No such process" 로 실패 — 2차 실행).
OUT_DIR=$(cd "$OUT_DIR" && pwd)
WORK=$(mktemp -d)

# 명령 시간 제한(macOS 에는 timeout 이 없다) — 시뮬레이터가 꼬이면 명령이 하염없이 기다려 잡 전체가 멈췄다(2차 실행).
limit() { local s="$1"; shift; perl -e 'alarm shift; exec @ARGV' "$s" "$@"; }

stats() { "$PY" "$HERE/shot_stats.py" brief "$1" 2>/dev/null || echo "?"; }

# stats 출력 줄("2007x2853 mean=.. bright=3.10%")이 검정이 아니면(밝은 픽셀 0.3% 초과) 참.
is_bright() {
  local b
  b=$(echo "$1" | sed -n 's/.*bright=\([0-9.]*\)%.*/\1/p')
  [ -n "$b" ] && [ "$(echo "$b > 0.3" | bc -l 2>/dev/null || echo 0)" = "1" ]
}

# 파일 내용을 GitHub 주석으로 — 공개 API(check-runs/annotations)로 읽을 수 있다.
note() {   # note <제목> <파일>
  "$PY" - "$1" "$2" <<'PYEOF'
import sys
title, path = sys.argv[1], sys.argv[2]
try:
    text = open(path, errors="replace").read()
except Exception as e:
    text = "(읽기 실패: %s)" % e
text = text[:55000]
esc = text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
t = title.replace("%", "%25").replace(":", "%3A").replace(",", "%2C")
print("::notice title=%s::%s" % (t, esc))
PYEOF
}

# 이미지 축소판(JPEG→base64)을 주석으로 — 공개 API 로 읽어 복원하면 PNG 를 내려받지 않고도 화면을 볼 수 있다.
# GitHub 주석 메시지는 API 응답에서 4096자로 잘리므로 이미지 1장당 주석 1개(base64 ≈ 3.9K자 이하)로 올린다.
thumbnote() {   # thumbnote <제목 접두> <PNG…>
  local title="$1"; shift
  local f out
  for f in "$@"; do
    [ -f "$f" ] || continue
    out="$WORK/thumb_$RANDOM.txt"
    echo "=== $(basename "$f") $(stats "$f")" > "$out"
    "$PY" "$HERE/shot_stats.py" thumb "$f" 150 2850 >> "$out" 2>/dev/null || echo "(썸네일 실패)" >> "$out"
    note "$title $(basename "$f" .png)" "$out"
  done
}

# 점검용 주석 — 촬영 잡에서는 건너뛴다(주석은 스텝당 10개 제한, 썸네일 자리 확보).
pnote() { [ "$MODE" = "probe" ] && note "$@"; return 0; }

# ── 0. Duo 기기 종류 / 런타임 ───────────────────────────────────────────────
INFO="$WORK/info.txt"
{
  echo "mode=$MODE langs=$LANGS scenes=$SCENES zoom=$SHOT_ZOOM"
  xcodebuild -version 2>&1
  echo "── Duo 기기 종류 ──"
  xcrun simctl list devicetypes 2>&1 | grep -i -E "duo|iphone 18" || true
  echo "── 런타임 ──"
  xcrun simctl list runtimes 2>&1 || true
} > "$INFO"
cat "$INFO"

duo_type() {   # 이름에 "Duo" 가 들어간 첫 기기 종류 → "이름|id"
  xcrun simctl list devicetypes -j | "$PY" -c '
import json, sys
for t in json.load(sys.stdin)["devicetypes"]:
    if "duo" in t["name"].lower():
        print(t["name"] + "|" + t["identifier"]); break
'
}

ios_runtimes() {   # 쓸 수 있는 iOS 런타임 id(최신 순)
  xcrun simctl list runtimes -j | "$PY" -c '
import json, sys
rs = [r for r in json.load(sys.stdin)["runtimes"] if r.get("isAvailable") and r["identifier"].startswith("com.apple.CoreSimulator.SimRuntime.iOS")]
rs.sort(key=lambda r: [int(x) for x in r["version"].split(".")], reverse=True)
for r in rs: print(r["identifier"])
'
}

create_device() {   # create_device <이름> <종류 id> → udid
  local name="$1" type_id="$2" rt udid
  for rt in $(ios_runtimes); do
    if udid=$(xcrun simctl create "Shot $name" "$type_id" "$rt" 2>/dev/null); then
      echo "$udid"; return 0
    fi
  done
  return 1
}

DUO=$(duo_type)
if [ -z "$DUO" ]; then
  echo "::error::이 Xcode 에는 iPhone Duo 시뮬레이터 기기 종류가 없다"
  exit 1
fi
DUO_NAME="${DUO%%|*}"; DUO_TYPE="${DUO#*|}"
echo "Duo: $DUO_NAME ($DUO_TYPE)"

if ! UDID=$(create_device "$DUO_NAME" "$DUO_TYPE"); then
  echo "맞는 런타임이 없어 iOS 플랫폼을 내려받는다"
  xcodebuild -downloadPlatform iOS || true
  UDID=$(create_device "$DUO_NAME" "$DUO_TYPE") || { echo "::error::$DUO_NAME 시뮬레이터를 만들지 못했다"; exit 1; }
fi
echo "udid: $UDID"

xcrun simctl boot "$UDID" 2>/dev/null || true
limit 600 xcrun simctl bootstatus "$UDID" -b || echo "::warning::bootstatus 가 끝나지 않았다"

# ── 1. 사용법 / enumerate (진단) ────────────────────────────────────────────
HELP="$WORK/help.txt"
{
  # (1차 점검: `simctl help | head` 가 30초 알람에 죽어 출력이 비었다 → 파일로 받고 stdin 을 막는다)
  echo "### xcrun simctl --help"; limit 20 xcrun simctl --help </dev/null > "$WORK/h1.txt" 2>&1; head -90 "$WORK/h1.txt"
  echo; echo "### xcrun simctl io help"; limit 30 xcrun simctl io "$UDID" help 2>&1 | head -80
  echo; echo "### simctl io screenConfig (사용법)"; limit 30 xcrun simctl io "$UDID" screenConfig 2>&1 | head -40
  echo; echo "### simctl io screenshot (사용법)"; limit 30 xcrun simctl io "$UDID" screenshot --help 2>&1 | head -30
  echo; echo "### 환경"; cat "$INFO"
} > "$HELP" 2>&1
pnote "Duo 1 환경·simctl 사용법" "$HELP"

# (io enumerate 는 2차 점검에서 화면 1=바깥 1398x2034 · 3=안쪽 2007x2853 · 2/4=TV출력 720x480 으로 확인했다 — 주석에서 뺀다)
ENUM="$WORK/enum.txt"
limit 60 xcrun simctl io "$UDID" enumerate > "$ENUM" 2>&1 || true

# 기기 프로필 + 시뮬레이터 바이너리에서 fold/posture/hinge 단서 찾기(접기/펴기를 CLI 로 하는 법을 모른다).
PROF="$WORK/profile.txt"
{
  bundle=$(xcrun simctl list devicetypes -j | "$PY" -c '
import json, sys
for t in json.load(sys.stdin)["devicetypes"]:
    if "duo" in t["name"].lower():
        print(t.get("bundlePath", "")); break
')
  echo "bundle: $bundle"
  ls -R "$bundle/Contents/Resources" 2>&1 | head -40
  echo "### profile.plist (앞 60줄)"
  plutil -p "$bundle/Contents/Resources/profile.plist" 2>&1 | head -60
  echo "### profile.plist (fold/screen/display/posture/hinge/chrome/orientation 줄)"
  plutil -p "$bundle/Contents/Resources/profile.plist" 2>&1 | grep -i -E "fold|screen|display|posture|hinge|chrome|orient|primary" | head -80
  echo "### strings"
  for b in "$(xcrun -f simctl)" "/Library/Developer/PrivateFrameworks/CoreSimulator.framework/CoreSimulator" \
           "$(xcode-select -p)/Applications/Simulator.app/Contents/MacOS/Simulator"; do
    echo "-- $b"
    strings -a "$b" 2>/dev/null | grep -i -E "fold|posture|hinge" | sort -u | head -40
  done
} > "$PROF" 2>&1
pnote "Duo 3 프로필·문자열" "$PROF"

# ── 2. 모든 화면 켜기 → 화면별 크기/밝기 ────────────────────────────────────
TABLE="$WORK/table.txt"
: > "$TABLE"
echo "── 화면 켜기 시도(끄지는 않는다) ──" | tee -a "$TABLE"
for id in 0 1 2 3 4 5 6 7 8; do
  out=$(limit 20 xcrun simctl io "$UDID" screenConfig --display="$id" power on 2>&1); rc=$?
  echo "screenConfig --display=$id power on → rc=$rc ${out:0:160}" | tee -a "$TABLE"
done
sleep 6

# 홈 화면이 그려지도록 설정 앱을 하나 띄워 둔다(검정이 아닌 내용이 있어야 화면이 켜졌는지 밝기로 알 수 있다).
limit 60 xcrun simctl launch "$UDID" com.apple.Preferences >/dev/null 2>&1 || true
sleep 5

echo "── 화면별 촬영 ──" | tee -a "$TABLE"
SEL_ARGS=""; SEL_LABEL=""; SEL_AREA=0; SEL_EXACT=0
probe() {   # probe <라벨> [simctl screenshot 인자…]
  local label="$1"; shift
  local f="$WORK/probe_$label.png" line w h bright area exact
  rm -f "$f"
  if limit 40 xcrun simctl io "$UDID" screenshot "$@" "$f" >/dev/null 2>&1 && [ -s "$f" ]; then
    line=$(stats "$f")
    echo "$label [$*]: $line" | tee -a "$TABLE"
    w=${line%%x*}; h=${line#*x}; h=${h%% *}
    bright=$(echo "$line" | sed -n 's/.*bright=\([0-9.]*\)%.*/\1/p')
    case "$w$h" in ''|*[!0-9]*) return ;; esac
    area=$((w * h)); exact=0
    { [ "$w" = "$WANT_W" ] && [ "$h" = "$WANT_H" ]; } && exact=1
    # 켜진(검정 아님) 화면만 후보. 정확한 크기(2007×2853)가 있으면 그걸 우선, 없으면 가장 넓은 화면.
    if [ -n "$bright" ] && [ "$(echo "$bright > 0.3" | bc -l 2>/dev/null || echo 0)" = "1" ]; then
      if [ "$exact" -gt "$SEL_EXACT" ] || { [ "$exact" = "$SEL_EXACT" ] && [ "$area" -gt "$SEL_AREA" ]; }; then
        SEL_ARGS="$*"; SEL_LABEL="$label"; SEL_AREA=$area; SEL_EXACT=$exact
      fi
    fi
  else
    echo "$label [$*]: 촬영 실패" | tee -a "$TABLE"
  fi
}
probe default
for id in 0 1 2 3 4 5 6 7 8; do probe "d$id" "--display=$id"; done
probe internal "--display=internal"
probe external "--display=external"
echo "선택: label=${SEL_LABEL:-없음} args='${SEL_ARGS}' exact=$SEL_EXACT" | tee -a "$TABLE"
pnote "Duo 2 화면 점검" "$TABLE"


# ── 2.5 안쪽 화면(2007×2853) 만들기 ──────────────────────────────────────────
# 점검 결과(2026-10-26): 앱은 화면 1(바깥 1398×2034)에만 그려지고 화면 3(안쪽 2007×2853)은 켜도 검정이다.
#   실험 A/B/C(바깥 끄기 · 안쪽 껐다 켜기 · 재부팅 후 바깥 끄기)는 전부 실패했고, 바깥(1)을 끄면 SpringBoard 가 죽어
#   그 뒤 앱 실행이 전부 "The system shell probably crashed" 로 실패했다 → **화면을 끄는 실험은 하지 않는다**(촬영 잡도 같은 스크립트를 탄다).
# 새 방법: 앱이 그려지는 화면 1 자체를 안쪽 크기로 바꾼다 — `screenConfig --display=1 geometry 2007x2853@3`
#   (프로필에 resizableScene 기능이 있다). 논리 크기 669×951pt @3x 라 실제 안쪽 화면과 같은 레이아웃이다.
EXP="$WORK/exp.txt"; : > "$EXP"
GEOM_OK=0

reboot_device() {   # 시스템 화면이 죽었을 때 — 껐다 켜서 처음 상태로
  xcrun simctl shutdown "$UDID" >/dev/null 2>&1; sleep 3
  xcrun simctl boot "$UDID" >/dev/null 2>&1
  limit 600 xcrun simctl bootstatus "$UDID" -b >/dev/null 2>&1 || echo "bootstatus 시간 초과" | tee -a "$EXP"
  sleep 6
}

geom_try() {   # geom_try <WxH[@scale]> — 화면 1 을 바꾸고 설정 앱이 뜨는지·크기·밝기를 확인
  local spec="$1" out rc f line tag
  tag=$(echo "$spec" | tr -c 'a-z0-9
' '_')
  echo "── 실험 G: 화면 1 geometry $spec ──" | tee -a "$EXP"
  out=$(limit 30 xcrun simctl io "$UDID" screenConfig --display=1 geometry "$spec" 2>&1); rc=$?
  echo "rc=$rc ${out:0:700}" | tee -a "$EXP"
  sleep 8
  if ! limit 60 xcrun simctl launch "$UDID" com.apple.Preferences >/dev/null 2>&1; then
    echo "[G $spec] 설정 앱 실행 실패 — 시스템 화면이 죽은 듯 → 재부팅" | tee -a "$EXP"
    reboot_device
    return 1
  fi
  sleep 8
  f="$WORK/exp_G_${tag}_d1.png"; rm -f "$f"
  if limit 40 xcrun simctl io "$UDID" screenshot --display=1 "$f" >/dev/null 2>&1 && [ -s "$f" ]; then
    line=$(stats "$f"); echo "[G $spec] d1: $line" | tee -a "$EXP"
    case "$line" in
      "${WANT_W}x${WANT_H} "*) if is_bright "$line"; then GEOM_OK=1; return 0; fi ;;
    esac
  else
    echo "[G $spec] d1: 촬영 실패" | tee -a "$EXP"
  fi
  return 1
}

if [ "$SEL_EXACT" != 1 ] && [ "$MODE" = "probe" ]; then
  geom_try "${WANT_W}x${WANT_H}@3" || geom_try "${WANT_W}x${WANT_H}" || true
  if [ "$GEOM_OK" = 1 ]; then
    SEL_ARGS="--display=1"; SEL_LABEL="geometry"; SEL_EXACT=1
    echo "결론: 화면 1 을 ${WANT_W}x${WANT_H} 로 바꿨다 → 촬영에 쓴다" | tee -a "$EXP"
  else
    echo "결론: geometry 로는 ${WANT_W}x${WANT_H} 를 못 만들었다" | tee -a "$EXP"
    # 실패가 설정을 남겼을 수 있으니 처음 상태로 돌려 놓는다(바깥 화면 촬영이라도 되게).
    reboot_device
  fi
fi
pnote "Duo 4 geometry 실험" "$EXP"
[ "$MODE" = "probe" ] && thumbnote "Duo 5" "$WORK"/exp_G_*_d1.png

# 점검 전용 — 접기/펴기 단서(simctl ui 옵션 · capabilities.plist · SimulatorKit 문자열). 촬영 잡에서는 건너뛴다.
if [ "$MODE" = "probe" ]; then
  INFO2="$WORK/info2.txt"
  {
    bundle=$(xcrun simctl list devicetypes -j | "$PY" -c '
import json, sys
for t in json.load(sys.stdin)["devicetypes"]:
    if "duo" in t["name"].lower():
        print(t.get("bundlePath", "")); break
')
    echo "### simctl help ui"; limit 20 xcrun simctl help ui </dev/null 2>&1 | head -40
    echo; echo "### simctl ui <device> (인자 없이)"; limit 20 xcrun simctl ui "$UDID" </dev/null 2>&1 | head -30
    echo; echo "### simctl help io"; limit 20 xcrun simctl help io </dev/null 2>&1 | grep -i -E "screenConfig|geometry|power|posture|fold" | head -20
    echo; echo "### capabilities.plist"; plutil -p "$bundle/Contents/Resources/capabilities.plist" 2>&1 | head -80
    echo; echo "### SimulatorKit / Simulator 문자열(fold·hinge·posture·flip·dual·spanning·screen mode)"
    for b in $(find "$(xcode-select -p)" -maxdepth 7 \( -name SimulatorKit -o -name Simulator -o -name CoreSimulatorUtilities -o -name DeviceKit \) -type f 2>/dev/null | head -6); do
      echo "-- $b"
      strings -a "$b" 2>/dev/null | grep -i -E "fold|hinge|posture|flip|spanning|screenConfig|geometry|display.*mode|dual" | sort -u | head -40
    done
  } > "$INFO2" 2>&1
  note "Duo 8 접기 단서" "$INFO2"

  # Simulator.app 을 띄워 메뉴에 접기/펴기·화면 전환 항목이 있는지 본다(접근성 권한이 없으면 오류만 남는다).
  MENU="$WORK/menu.txt"
  {
    open -a Simulator --args -CurrentDeviceUDID "$UDID" 2>&1; sleep 12
    echo "### 창 목록"
    limit 30 osascript -e 'tell application "System Events" to tell process "Simulator" to get name of every window' 2>&1
    for m in File Edit Device Features Window "I/O" Debug; do
      echo "### 메뉴 $m"
      limit 30 osascript -e "tell application \"System Events\" to tell process \"Simulator\" to get name of every menu item of menu \"$m\" of menu bar 1" 2>&1 | tr ',' '
' | sed 's/^ *//' | head -60
    done
    echo "### 메뉴 막대"
    limit 30 osascript -e 'tell application "System Events" to tell process "Simulator" to get name of every menu bar item of menu bar 1' 2>&1
  } > "$MENU" 2>&1
  note "Duo 9 Simulator 메뉴" "$MENU"

  # 화면 1 이 받아들이는 다른 크기(모드 목록 단서) — 화면 1 만 건드리고 곧바로 원래대로 돌려 놓는다.
  GM="$WORK/geom_modes.txt"; : > "$GM"
  for spec in 1398x2034@3 2034x1398@3 1206x2622@3 1320x2868@3 2007x2853@2 1003x1426@2; do
    r=$(limit 30 xcrun simctl io "$UDID" screenConfig --display=1 geometry "$spec" 2>&1 | tr '
' ' ')
    echo "$spec → ${r:0:160}" >> "$GM"
  done
  limit 30 xcrun simctl io "$UDID" screenConfig --display=1 geometry 1398x2034@3 >/dev/null 2>&1 || true
  limit 30 xcrun simctl io "$UDID" screenConfig --display=3 geometry 2007x2853@3 2>&1 | head -3 | sed 's/^/d3 2007x2853@3 → /' >> "$GM"
  limit 30 xcrun simctl io "$UDID" enumerate 2>&1 | grep -i -E "mode|size|scale|name|id:|uuid|power" | head -40 >> "$GM"
  note "Duo 10 geometry 모드 단서" "$GM"
fi

if [ -z "$SEL_LABEL" ]; then
  echo "::warning::켜진(검정 아닌) 화면을 찾지 못했다 — 기본 화면으로 시도한다"
  SEL_ARGS=""
fi

if [ "$MODE" = "probe" ]; then
  mkdir -p "$OUT_DIR/probe"; cp "$WORK"/probe_*.png "$OUT_DIR/probe/" 2>/dev/null || true
  xcrun simctl shutdown "$UDID" || true
  exit 0
fi

# ── 3. 앱 설치 + 촬영 ────────────────────────────────────────────────────────
xcrun simctl status_bar "$UDID" override --time "9:41" --dataNetwork wifi --wifiMode active --wifiBars 3 \
  --cellularMode active --cellularBars 4 --batteryState charged --batteryLevel 100 || true
xcrun simctl install "$UDID" "$APP_PATH" || { echo "::error::앱 설치 실패"; exit 1; }
xcrun simctl privacy "$UDID" grant location "$BUNDLE_ID" || true
xcrun simctl location "$UDID" set "$SHOT_LAT,$SHOT_LNG" || true

# 설치 직후 첫 실행은 첫 화면이 늦게(8초 넘게) 그려진다 — 한 번 띄워 데워 두고 시작한다.
limit 60 xcrun simctl launch "$UDID" "$BUNDLE_ID" -StaryShot map -app_language "${LANGS%% *}" >/dev/null 2>&1 || true
sleep 12
xcrun simctl terminate "$UDID" "$BUNDLE_ID" >/dev/null 2>&1 || true
sleep 2

SHOT_NOTE="$WORK/shot_note.txt"; : > "$SHOT_NOTE"
SHOT_FILES=()
for lang in $LANGS; do
  dir="$OUT_DIR/duo/$lang"; mkdir -p "$dir"
  i=0
  for scene in $SCENES; do
    i=$((i + 1))
    wait=16
    case "$scene" in
      login) wait=8 ;;
      globe) wait=22 ;;
    esac
    base="$dir/$(printf '%02d' "$i")_$scene"
    args=(-StaryShot "$scene" -StaryShotZoom "$SHOT_ZOOM" -StaryShotLat "$SHOT_LAT" -StaryShotLng "$SHOT_LNG"
      -app_language "$lang" -AppleLanguages "($lang)" -AppleLocale "$lang")
    if ! limit 60 env SIMCTL_CHILD_NSUnbufferedIO=YES xcrun simctl launch --terminate-running-process \
      --stdout="$base.log" --stderr="$base.err.log" "$UDID" "$BUNDLE_ID" "${args[@]}" >/dev/null; then
      echo "    (로그 없이 다시 실행)"
      limit 60 xcrun simctl launch --terminate-running-process "$UDID" "$BUNDLE_ID" "${args[@]}" >/dev/null \
        || { echo "::warning::$lang/$scene 앱 실행 실패"; continue; }
    fi
    sleep "$wait"
    line=""
    for try in 1 2 3 4; do
      # (SEL_ARGS 는 "--display=3" 한 단어이거나 빈 문자열)
      limit 40 xcrun simctl io "$UDID" screenshot $SEL_ARGS "$base.png" >/dev/null 2>&1
      line=$(stats "$base.png")
      bright=$(echo "$line" | sed -n 's/.*bright=\([0-9.]*\)%.*/\1/p')
      # 상태바 시계만 찍혀도 0.3% 를 넘는다(로그인 첫 실행이 검정인데 통과했다) → 촬영은 1.5% 넘어야 "그려졌다"로 본다.
      [ -n "$bright" ] && [ "$(echo "$bright > 1.5" | bc -l 2>/dev/null || echo 0)" = "1" ] && break
      echo "    $lang/$scene 시도 $try: 거의 검정($line) — 8초 더 기다린다"
      sleep 8
    done
    echo "  $base.png  $line"
    case "$line" in
      "${WANT_W}x${WANT_H} "*) ;;
      *) echo "::warning::$lang/$scene 크기가 ${WANT_W}x${WANT_H} 가 아니다: $line" ;;
    esac
    {
      echo "$lang/$(printf '%02d' "$i")_$scene  $line"
      cat "$base.log" "$base.err.log" 2>/dev/null | grep -E "❌|⚠️|Firestore" | head -3 | sed 's/^/  log: /'
    } >> "$SHOT_NOTE"
    SHOT_FILES+=("$base.png")
    case "$line" in
      "${WANT_W}x${WANT_H} "*) ;;
      *) "$PY" "$HERE/shot_stats.py" fit "$base.png" "${base}_${WANT_W}x${WANT_H}.png" "$WANT_W" "$WANT_H" >/dev/null 2>&1            && echo "    사본: ${base}_${WANT_W}x${WANT_H}.png (확대·중앙 자르기)" ;;
    esac
  done
done
note "Duo 6 촬영 결과 요약($LANGS)" "$SHOT_NOTE"
thumbnote "Duo 7" ${SHOT_FILES[@]+"${SHOT_FILES[@]}"}

xcrun simctl shutdown "$UDID" || true
echo "── 결과 ──"
find "$OUT_DIR" -name '*.png' | sort
