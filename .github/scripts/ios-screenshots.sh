#!/usr/bin/env bash
# App Store 스크린샷 자동 촬영 — ios-screenshots.yml 에서 호출(macOS 러너).
# 시뮬레이터를 만들어 앱을 `-StaryShot <장면>` 으로 띄우고(iosApp/Sources/Core/ScreenshotMode.swift) simctl 로 찍는다.
#
# 환경변수:
#   APP_PATH   빌드된 Stary.app 경로
#   OUT_DIR    결과 폴더(기기/언어/장면.png)
#   LANGS      "ko en ja"
#   SCENES     "login map constellation globe"
#   SHOT_LAT / SHOT_LNG / SHOT_ZOOM   지도 위치·줌
set -uo pipefail

BUNDLE_ID="com.chaminwoo.stary.ios"
LANGS="${LANGS:-ko}"
SCENES="${SCENES:-login map constellation globe}"
SHOT_LAT="${SHOT_LAT:-37.5665}"
SHOT_LNG="${SHOT_LNG:-126.9780}"
SHOT_ZOOM="${SHOT_ZOOM:-11}"
mkdir -p "$OUT_DIR"

echo "── 설치된 기기 종류(iPhone) ──"
xcrun simctl list devicetypes | grep -i iphone || true
echo "── 설치된 런타임 ──"
xcrun simctl list runtimes || true
echo "── simctl io 도움말(폴더블 디스플레이 옵션 확인용) ──"
xcrun simctl io help 2>&1 | head -60 || true

# 이름(정확히 일치)으로 기기 종류 id 찾기. 없으면 빈 문자열.
devicetype_id() {
  xcrun simctl list devicetypes -j | python3 -c '
import json, sys
name = sys.argv[1]
for t in json.load(sys.stdin)["devicetypes"]:
    if t["name"] == name:
        print(t["identifier"]); break
' "$1"
}

# 이름에 "Duo" 가 들어간 기기 종류 이름들(폴더블 — 바깥/안쪽이 따로 있을 수도 있다).
duo_names() {
  xcrun simctl list devicetypes -j | python3 -c '
import json, sys
for t in json.load(sys.stdin)["devicetypes"]:
    if "duo" in t["name"].lower():
        print(t["name"])
'
}

# 사용 가능한 iOS 런타임 id(최신 순).
ios_runtimes() {
  xcrun simctl list runtimes -j | python3 -c '
import json, sys
rs = [r for r in json.load(sys.stdin)["runtimes"] if r.get("isAvailable") and r["identifier"].startswith("com.apple.CoreSimulator.SimRuntime.iOS")]
rs.sort(key=lambda r: [int(x) for x in r["version"].split(".")], reverse=True)
for r in rs: print(r["identifier"])
'
}

# 기기 하나 만들기 — 최신 런타임부터 시도. 성공하면 udid 출력.
create_device() {
  local name="$1" type_id="$2" rt udid
  for rt in $(ios_runtimes); do
    if udid=$(xcrun simctl create "Shot $name" "$type_id" "$rt" 2>/dev/null); then
      echo "$udid"; return 0
    fi
  done
  return 1
}

prepare_device() {
  local udid="$1"
  xcrun simctl boot "$udid" 2>/dev/null || true
  xcrun simctl bootstatus "$udid" -b
  xcrun simctl status_bar "$udid" override --time "9:41" --dataNetwork wifi --wifiMode active --wifiBars 3 \
    --cellularMode active --cellularBars 4 --batteryState charged --batteryLevel 100 || true
  xcrun simctl install "$udid" "$APP_PATH"
  xcrun simctl privacy "$udid" grant location "$BUNDLE_ID" || true
  xcrun simctl location "$udid" set "$SHOT_LAT,$SHOT_LNG" || true
}

# 장면 전부 촬영. $3 = 찍을 화면(--display, 빈 값이면 기본 화면).
# 앱 stdout(print — 익명 로그인/Firestore 오류)은 장면별 .log 로 함께 남긴다.
shoot_scenes() {
  local udid="$1" slug="$2" display="${3:-}"
  local disp_arg=()
  [ -n "$display" ] && disp_arg=(--display="$display")
  for lang in $LANGS; do
    local dir="$OUT_DIR/$slug/$lang" i=0
    mkdir -p "$dir"
    for scene in $SCENES; do
      i=$((i + 1))
      local wait=16 base
      case "$scene" in
        login) wait=8 ;;
        globe) wait=20 ;;
      esac
      base="$dir/$(printf '%02d' "$i")_$scene"
      SIMCTL_CHILD_NSUnbufferedIO=YES xcrun simctl launch --terminate-running-process \
        --stdout="$base.log" --stderr="$base.err.log" "$udid" "$BUNDLE_ID" \
        -StaryShot "$scene" -StaryShotZoom "$SHOT_ZOOM" -StaryShotLat "$SHOT_LAT" -StaryShotLng "$SHOT_LNG" \
        -app_language "$lang" -AppleLanguages "($lang)" -AppleLocale "$lang" >/dev/null
      sleep "$wait"
      # (macOS 기본 bash 3.2 는 set -u 에서 빈 배열 전개가 오류 → ${arr[@]+…} 형태)
      xcrun simctl io "$udid" screenshot ${disp_arg[@]+"${disp_arg[@]}"} "$base.png" 2>/dev/null
      echo "  $base.png  $(sips -g pixelWidth -g pixelHeight "$base.png" | awk '/pixel/{printf "%s ", $2}')"
      cat "$base.log" "$base.err.log" 2>/dev/null | grep -E "❌|⚠️|Firestore 원본 문서 개수" | head -5 | sed 's/^/    log: /'
    done
  done
}

shoot_device() {
  local name="$1" udid="$2" slug
  slug=$(echo "$name" | tr ' ' '_' | tr -cd '[:alnum:]_')
  echo "── $name ($udid) ──"
  prepare_device "$udid"
  shoot_scenes "$udid" "$slug"
  xcrun simctl shutdown "$udid" || true
}

# 폴더블(iPhone Duo) — 화면이 둘(바깥/안쪽)이라 화면 id 를 찾아 하나씩 켜고(나머지는 끄고) 찍는다.
# 1회차(2026-10-21): 기본 화면(안쪽 2007×2853)이 꺼진 채라 전부 검정 → 전원을 직접 켠다.
shoot_foldable() {
  local name="$1" udid="$2" slug id w h ids=() sizes=()
  slug=$(echo "$name" | tr ' ' '_' | tr -cd '[:alnum:]_')
  echo "── $name ($udid) — 폴더블 ──"
  prepare_device "$udid"
  echo "  io enumerate:"
  xcrun simctl io "$udid" enumerate 2>&1 | sed 's/^/    /' | head -80
  for id in 1 2 3 4 5 6; do
    if xcrun simctl io "$udid" screenshot --display="$id" "/tmp/probe_$id.png" >/dev/null 2>&1; then
      w=$(sips -g pixelWidth "/tmp/probe_$id.png" | awk '/pixel/{print $2}')
      h=$(sips -g pixelHeight "/tmp/probe_$id.png" | awk '/pixel/{print $2}')
      echo "  화면 $id: ${w}x${h}"
      ids+=("$id"); sizes+=("$((w * h))")
    fi
  done
  if [ "${#ids[@]}" -lt 2 ]; then
    echo "::warning::$name 화면이 ${#ids[@]}개만 보인다 — 기본 화면만 찍는다"
    shoot_scenes "$udid" "$slug"
  else
    # 넓이가 가장 큰 화면 = 안쪽(펼친 화면), 가장 작은 화면 = 바깥.
    local inner="" outer="" maxa=0 mina=999999999 k
    for k in "${!ids[@]}"; do
      if [ "${sizes[$k]}" -gt "$maxa" ]; then maxa="${sizes[$k]}"; inner="${ids[$k]}"; fi
      if [ "${sizes[$k]}" -lt "$mina" ]; then mina="${sizes[$k]}"; outer="${ids[$k]}"; fi
    done
    for mode in inner outer; do
      local on off
      if [ "$mode" = inner ]; then on="$inner"; off="$outer"; else on="$outer"; off="$inner"; fi
      echo "  [$mode] 화면 $on 켜기 / 화면 $off 끄기"
      xcrun simctl io "$udid" screenConfig --display="$on" power on || true
      xcrun simctl io "$udid" screenConfig --display="$off" power off || true
      sleep 3
      shoot_scenes "$udid" "${slug}_$mode" "$on"
    done
  fi
  xcrun simctl shutdown "$udid" || true
}

# 6.3"(필수 1206×2622) · 6.9"(1320×2868) — 이름이 없으면 다음 후보.
pick_type() {
  local n id
  for n in "$@"; do
    id=$(devicetype_id "$n")
    if [ -n "$id" ]; then echo "$n|$id"; return 0; fi
  done
  return 1
}

DEVICES=()
if p=$(pick_type "iPhone 18 Pro" "iPhone 17 Pro" "iPhone 16 Pro"); then DEVICES+=("$p"); fi
if p=$(pick_type "iPhone 18 Pro Max" "iPhone 17 Pro Max" "iPhone 16 Pro Max"); then DEVICES+=("$p"); fi

DUO=$(duo_names)
if [ -z "$DUO" ]; then
  echo "::warning::이 Xcode 에는 iPhone Duo 시뮬레이터 기기 종류가 없다 — Duo 스크린샷은 건너뛴다"
else
  while IFS= read -r n; do DEVICES+=("$n|$(devicetype_id "$n")"); done <<< "$DUO"
fi

for entry in "${DEVICES[@]}"; do
  name="${entry%%|*}"; type_id="${entry#*|}"
  if ! udid=$(create_device "$name" "$type_id"); then
    # Duo 는 최신 런타임(iOS 27.1+)에서만 돌 수 있다 — 한 번만 내려받고 다시 시도.
    if [ -z "${RUNTIME_DOWNLOADED:-}" ]; then
      echo "$name: 맞는 런타임이 없어 iOS 플랫폼을 내려받는다"
      xcodebuild -downloadPlatform iOS || true
      RUNTIME_DOWNLOADED=1
      udid=$(create_device "$name" "$type_id") || { echo "::warning::$name 시뮬레이터를 만들지 못했다"; continue; }
    else
      echo "::warning::$name 시뮬레이터를 만들지 못했다"; continue
    fi
  fi
  case "$name" in
    *Duo*) shoot_foldable "$name" "$udid" ;;
    *) shoot_device "$name" "$udid" ;;
  esac
done

echo "── 결과 ──"
find "$OUT_DIR" -name '*.png' | sort
