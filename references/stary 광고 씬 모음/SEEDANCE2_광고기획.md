# STARY × Seedance 2.0 — 광고 마스터 기획

> 작성: 2026-07-08. 기존 `storyboard.md`(씬 구성) + `STARY_commercial_master.md`(Veo용 프롬프트)를
> **Seedance 2.0 전용**으로 계승·확장한 문서. 이 파일 하나로 "무엇을, 어떤 순서로, 어떤 프롬프트로" 전부 해결.
>
> 현재 확보 자산: `scene1~8.png`(스틸), `영상/scene1~3.mp4`(생성 완료 컷), `min_zoom.png`(앱 글로브 실화면),
> `은하수.jpg`(글로브 은하수 레퍼런스), 앱 BGM 6트랙(`raw/bgm_*.mp3`).

---

## 0. 전략 요약 (TL;DR)

| 질문 | 결정 | 이유 |
|---|---|---|
| 이미지로 만들까, 텍스트로 만들까? | **이미지 기반(i2v) — 씬마다 첫 프레임 이미지 1장 + @레퍼런스** | 이미 고퀄 스틸 8장 확보(비주얼 복권 없음), 인물·장소 일관성, Seedance 2.0 멀티레퍼런스가 i2v에서 가장 강함 |
| 화면 안에 글자(카피)를 생성할까? | **절대 금지 → 후반 편집(CapCut/프리미어)에서 얹기** | AI 생성 한글은 깨짐·오탈자. 로고/자막은 검은 화면·엔딩 위에 직접 |
| 메인 광고는 어떤 방향? | **기획안 A "별이 된 기억" 30초 완결판** (+ 엔딩에 기획안 B의 글로브 흡수) | 진행 중 자산 재활용 + 마지막에 "제품 비주얼(별 박힌 지구)"로 앱 각인 |
| 보조 포맷? | **기획안 C 15초 세로(9:16) 티저** — 릴스/쇼츠/틱톡용 | 메인은 유튜브/앱스토어용 16:9, SNS는 세로 필수 |
| 음악? | Seedance 네이티브 오디오는 **앰비언스만**, 음악은 **앱 BGM 트랙**으로 편집에서 교체 | 광고 사운드 = 앱 사운드 → 사운드 브랜딩 일치 |

**추천 실행 순서**: ① 메인 필름 잔여 5컷 생성(S4~S8) → ② 30초/15초 편집 → ③ 세로 티저 4컷 → ④ (여유 시) 범퍼 D.

---

## 1. Seedance 2.0 사용법 핵심 (확인된 스펙)

- **입력**: 이미지 최대 **9장**, 영상 최대 **3개**(합산 15초), 오디오 최대 **3개**(합산 15초), 총 12파일까지 한 번에.
- **멘션 문법**: 첨부 순서대로 `@Image1`, `@Image2`, `@Video1`, `@Audio1` 로 프롬프트에서 지목하고
  **각 레퍼런스의 역할을 명시** — 예: `@Image1 is the first frame. Reference @Video1 for camera movement.`
- **길이**: 한 클립 **4~15초** 선택 생성. (2초짜리 컷도 5초로 생성 후 편집에서 자르는 게 안전)
- **모드**: 첫 프레임(+끝 프레임) 모드 / 만능 레퍼런스 모드. 오디오(효과음·앰비언스)도 네이티브 생성됨.
- **화면비**: 첫 프레임 이미지의 비율을 따라감 → 세로 광고는 **세로(9:16) 이미지부터** 준비.
- ⚠️ **실사 얼굴 정책**: 실존 인물로 식별 가능한 얼굴 사진 업로드 불가. 우리 씬은 전부 AI 생성 인물 + 뒷모습 위주라
  대부분 통과 예상. 거절당하면 → 해당 씬만 t2v(+스타일 레퍼런스)로 우회하거나 Kling/Veo로 그 컷만 생성.
- 해상도는 플랫폼/요금제에 따라 다름(480p~1080p) → 최종은 업스케일(Dreamina HD/Topaz) 후 납품.

**클립 연속성 팁**: 이어지는 두 컷은 앞 클립의 **마지막 프레임을 캡처**해 다음 클립의 첫 프레임으로 넣으면 이음새가 사라진다.
인물 일관성은 **매 클립에 `scene1.png`(주인공 뒷모습)를 identity 레퍼런스로 함께 첨부**하고 프롬프트에 같은 인물 묘사 문구를 유지.

---

## 2. 기획안 4종 — "어떤 방면으로 광고할까"

### A. 「별이 된 기억」 — 감성 브랜드 필름 ★메인 추천
- **한 줄**: 도시의 밤하늘 별 하나하나가 누군가의 기억임을 깨달은 남자가, 자신의 오늘을 별로 남긴다.
- **포맷**: 30초 16:9 (15초 컷다운 병행) / 유튜브 인스트림, 앱스토어 프리뷰, 브랜드 사이트.
- **훅**: Apple식 절제된 감성 + 마지막 "도시가 지구가 되는" 스케일 반전.
- **강점**: 이미 씬 스틸 8장 + 앞 3컷 영상 완성(제작비 절반 회수). 앱의 정서(기억 보존)를 가장 정확히 전달.
- **약점**: 앱 화면이 직접 안 나옴 → 엔딩 글로브+스토어 카피로 보완.
- **상태**: S1~S3 영상 확보. 잔여 S4~S8 프롬프트는 §3.

### B. 「지구는 일기장」 — 제품 비주얼 스케일 필름
- **한 줄**: 우주에서 본 지구 — 대륙마다 반짝이는 색색의 별이 전부 사람들의 기억. 카메라가 별 하나로 다이브하면 한 사람의 하루.
- **포맷**: 20초 16:9. 글로브(앱 시그니처 화면)가 주인공.
- **훅**: min_zoom.png 그대로의 "별 박힌 지구" — 앱을 깔면 실제로 보는 화면이라 과장이 없다.
- **강점**: 제품 아이덴티티 각인 최강. 앱 실화면 녹화와 생성 영상을 매치컷하기 쉬움.
- **약점**: 인물 서사가 없어 감정 환기가 약함.
- **판정**: 단독 집행 대신 **A의 엔딩(S8)으로 흡수** — 서사와 제품 비주얼을 둘 다 가져감.

### C. 「그 자리의 별」 — 발견/소셜 세로 티저 ★보조 추천
- **한 줄**: 여행지에서 앱을 연 여자 — 지금 서 있는 그 자리에 누군가의 기억(별)이 빛나고 있고, 그녀도 자신의 별을 남긴다.
- **포맷**: 15초 9:16 / 인스타 릴스·유튜브 쇼츠·틱톡.
- **훅**: "탐험하며 남의 기억을 발견한다"는 앱의 소셜 코어를 유일하게 보여주는 안. 실제 앱 UI 녹화 1컷 포함(신뢰).
- **강점**: 짧고 후킹, 위치 기반이라는 차별점 전달. 제작 4컷이면 끝.
- **약점**: 세로 이미지 3장 신규 생성 필요(§4에 프롬프트 완비).

### D. 「3초의 마법」 — 기능 데모 범퍼
- **한 줄**: 부메랑 3초 움짤 촬영 → 별이 되어 지구에 콕 박히는 10초 기능 광고.
- **포맷**: 6~10초 범퍼(스킵 불가 지면) / 리타게팅용.
- **강점**: 신기능(8.34 부메랑) 직접 소구, 제작 최소(실촬영 UI + 생성 1컷).
- **약점**: 브랜드 정서 전달은 약함. A/C 집행 후 후속으로.

> **결론**: **A(메인) + C(세로)** 를 만들고, B는 A의 엔딩으로, D는 추후 리타게팅용으로.

---

## 3. 메인 필름 상세 — 「별이 된 기억: 완결판」 (30초, 16:9)

### 3.0 타임라인 & 씬-이미지 매핑

| 씬 | 최종 컷 길이 | 내용 | 첫 프레임 이미지 | 상태 |
|---|---|---|---|---|
| S1 | 2.5s | 밤 강변을 걷는 뒷모습 | scene1.png | ✅ 영상 확보(scene1.mp4) |
| S2 | 2.0s | 난간에 멈춤, 첫 별 등장 | scene2.png | ✅ 영상 확보(scene2.mp4) |
| S3 | 1.5s | 시선을 올림, 별이 맥동 | scene3.png | ✅ 영상 확보(scene3.mp4) |
| S4 | 2.0s | 다섯 별이 도시 위에 | scene4.png | 🔲 생성 (5초 생성→2초 사용) |
| S5 | 2.5s | 눈 매크로 → 화이트 블룸 | scene5.png | 🔲 생성 (5초→2.5초) |
| S6 | 5.5s | 기억 몽타주 4컷 | scene6-1~4.png (4장 동시) | 🔲 생성 (8초→5.5초, **1회 생성**) |
| S7 | 5.0s | 손 위로 파티클 → 새 별 탄생 | scene8.png (+scene7.png 와이드 참조) | 🔲 생성 (8초→5초) |
| S8 | 5.0s | 도시에서 물러나면 — 별 박힌 지구 | **신규 글로브 이미지**(§6-①) | 🔲 이미지부터 생성 |
| S9 | 4.0s | 검은 화면 로고 + 카피 | (편집에서 제작) | 🔲 편집 |

합계 30.0초. **15초 컷다운**: S2(1.5)+S4(2)+S5(2)+S6(3.5)+S7(4)+S9(2).

⚠️ 화면비 정리: scene6-1~4, scene8은 3:2(1536×1024) → 생성 전에 **16:9로 상하 크롭**(구도상 하늘/바닥 여유 있어 안전)
하거나, 그대로 생성 후 편집에서 크롭. 나머지는 이미 16:9.

⚠️ 기존 scene1~3.mp4 와 새 컷의 톤이 다르면, 아래 같은 요령으로 S1~S3도 재생성(첫 프레임 = scene1/2/3.png,
프롬프트는 구 `STARY_commercial_master.md`의 해당 타임코드 문단 재사용 + 아래 공통 스타일 문구 부착).

### 3.1 공통 스타일 문구 (모든 프롬프트 끝에 부착됨)

각 프롬프트에 이미 포함해 두었지만, 직접 수정할 때 이 원칙 유지:
- `Realistic live-action, premium Apple-style emotional brand film. Deep navy night grade, warm amber and cool blue city bokeh, soft halation, fine 35mm film grain.`
- `Smooth stabilized camera only — slow push-in / tracking / gentle tilt. No whip pans, no speed ramps.`
- `NEVER: fantasy VFX, magic explosions, light emitting from a person's hand or body, cross-shaped lens flares, CG or anime look, any text or logos or UI.`

### 3.2 S4 — 다섯 별 (생성 5초)
**첨부**: ① scene4.png
```
@Image1 is the first frame. A quiet night riverside. The man stands perfectly
still at the railing, seen from behind, while FIVE soft colored stars float
above the city skyline — warm amber, cool blue-white, soft white, deep
red-orange, pale gold. The stars stay in place like silent lanterns of
memory; each one pulses very subtly in turn, like slow heartbeats. The water
reflections breathe gently. The camera pushes in very slowly toward the back
of his head, drifting slightly upward. No new objects appear, no cuts.
Realistic live-action, premium Apple-style emotional brand film, deep navy
night grade, warm amber and cool blue bokeh, soft halation, fine 35mm film
grain. Smooth stabilized camera only. NEVER: fantasy VFX, light from his
body, cross-shaped lens flares, CG look, any text or logos.
Audio: distant city hum, calm water lapping, one soft intimate piano note.
```

### 3.3 S5 — 눈 매크로 → 화이트 블룸 (생성 5초)
**첨부**: ① scene5.png
```
@Image1 is the first frame. Extreme macro of a human eye at night; the city
skyline and five tiny colored stars are reflected inside the dark brown
iris. One slow, natural half-blink at the very start, then the eye holds
almost still with only micro-movements. The reflection slowly brightens as
the camera pushes deeper into the pupil in one continuous move; the
eyelashes drift out of focus, the reflected stars bloom, and by the final
frame the whole image floods into soft warm white light. The LAST frame must
be almost pure soft warm white. Realistic macro photography, shallow depth
of field, fine film grain, no CG look, no text.
Audio: a hushed rising string swell that resolves into near-silence.
```
> 거절(얼굴 정책) 시: 이미지 없이 t2v로 같은 문단을 생성하거나, scene5.png를 어둡게 크롭(눈만)해 재시도.

### 3.4 S6 — 기억 몽타주 (4장 → 1회 생성, 8초) ★Seedance 2.0 핵심 활용
**첨부**: ① scene6-1.png ② scene6-2.png ③ scene6-3.png ④ scene6-4.png
```
@Image1, @Image2, @Image3 and @Image4 are four still memories. Play them as
ONE continuous memory montage in this exact order, about 1.6 seconds each,
joined by soft warm film dissolves with subtle motion blur — fragments of
life racing through someone's mind, never looking like separate video clips.
- @Image1: the young couple runs hand in hand along the golden sunset
  riverside, hair and clothes flying, joyful and free.
- @Image2: the couple laughs over a picnic beneath cherry blossoms in full
  bloom, petals drifting down between them.
- @Image3: the family watches red and gold fireworks bloom over the night
  river; the fireworks flicker and fall softly.
- @Image4: the small child runs along the riverbank with the little dog in
  low golden-hour light while the father watches from the bench; at the end
  the warm light slowly dims and cools into deep night tones.
Golden Kodak-film warmth, soft halation, fine 35mm grain, gentle handheld
feel, dreamy and nostalgic. No text, no logos. The final frame fades toward
near-black night.
Audio: faint film-projector flutter, distant children's laughter, warm
nostalgic piano over soft strings.
```

### 3.5 S7 — 피날레: 풍경이 별이 되다 (생성 8초)
**첨부**: ① scene8.png ② scene7.png
```
@Image1 is the first frame — a man seen from behind raising one open hand
toward the night city, a wide stream of golden dust particles rising from
the skyline into the sky where a bright star is forming. @Image2 is a wide
clean night-skyline plate: use it ONLY as the framing reference for the
final pulled-back composition.
Continue the motion of @Image1: countless golden particles keep lifting off
the city, the river and its reflections, drifting upward past his open hand.
CRITICAL: nothing is emitted from his hand or body — the light rises only
from the scenery he is looking at. The particles slowly condense into ONE
beautiful bright warm star high above the skyline; the new star settles and
calms among a few faint colored stars. Then the camera pulls back slowly and
smoothly, the man becoming small against the railing, matching the wide
framing of @Image2, the new star glowing quietly above the city. End on a
still wide hold. Realistic live-action, premium emotional brand film, deep
navy grade, soft halation, fine film grain. NEVER: CG explosion, beam of
light, cross-shaped flares, any text.
Audio: a gentle string-and-piano swell that resolves into one sustained
warm note as the star settles.
```

### 3.6 S8 — 도시 → 별 박힌 지구 (생성 8초) ★앱 시그니처 컷
**첨부**: ① 신규 글로브 이미지(§6-① 프롬프트로 먼저 생성, 또는 앱 글로브 실화면 고해상 캡처)
```
@Image1 is the first frame — the Earth seen from space at night, its dark
side covered with thousands of tiny colorful twinkling stars where people's
memories live, a few thin luminous orbit trails circling the planet, a faint
pink-and-violet milky way band behind it.
The Earth rotates very slowly. The colorful memory-stars twinkle softly at
different rhythms across the continents, and a few NEW ones quietly light up
one by one — the last new star appears over Seoul, South Korea, and pulses
gently like a heartbeat. The milky way drifts almost imperceptibly. The
camera performs an extremely slow, majestic pull-back with a very subtle
lateral drift. Photoreal cinematic space shot but warm and emotional, not
cold sci-fi; soft bloom on the star lights; fine film grain. NEVER: text,
UI, satellites, explosions, lens flares.
Audio: vast quiet space ambience; a warm choir-like pad resolves the piano
motif into stillness. End on a still hold suitable for a logo overlay.
```
> 편집 팁: S8 끝에서 **실제 앱 글로브 녹화 화면**으로 1초 크로스디졸브하면 "광고 = 실제 앱" 신뢰가 생긴다(선택).

### 3.7 S9 — 로고 엔딩 (편집 제작, 생성 없음)
검은 화면 → `logo.webp` 페이드 인 → 카피 2줄(§5) → 스토어 뱃지. Seedance 불필요.

---

## 4. 세로 티저 상세 — 「그 자리의 별」 (15초, 9:16)

> 신규 세로 이미지 3장 필요(§6-②~④). V2는 생성이 아니라 **실제 앱 화면 녹화**.

| 컷 | 길이 | 내용 | 소스 |
|---|---|---|---|
| V1 | 4s | 여행지 밤, 하늘을 올려다보는 여자 — 색색의 별 | 신규 이미지 §6-② + 아래 프롬프트 |
| V2 | 3s | 실제 앱: 지도의 별 탭 → 누군가의 다이어리 열림 | 📱 앱 녹화(폰 목업 프레임) |
| V3 | 4s | 그 별에 담긴 기억(불꽃놀이) 재생 | 신규 이미지 §6-③ + 아래 프롬프트 |
| V4 | 4s | 그녀도 폰을 들어 순간을 남김 → 하늘에 새 별 + 로고 | 신규 이미지 §6-④ + 아래 프롬프트 |

**V1 프롬프트** (첨부: ① §6-② 이미지)
```
@Image1 is the first frame. Vertical 9:16. Night in a quiet seaside travel
town. A young woman seen from behind looks up at the deep navy sky where
five small soft colored stars glow above the rooftops — believable, quiet,
not fantasy. Her hair moves faintly in the sea breeze; string lights bokeh
breathes behind her. The camera tilts up very slowly from her shoulders to
the stars. Realistic live-action, premium emotional brand film, deep navy
grade, warm lamp bokeh, fine film grain. No text, no UI, no VFX.
Audio: soft night waves, distant wind chime.
```

**V3 프롬프트** (첨부: ① §6-③ 이미지 ② scene6-3.png — 스타일 참조)
```
@Image1 is the first frame; match the warm nostalgic film look of @Image2.
Vertical 9:16. A family seen from behind on a riverside lawn watches red and
gold fireworks bloom high over the night city across the water; sparks fall
slowly, reflections shimmer on the river. Gentle handheld warmth, golden
Kodak-film grade, soft halation, fine 35mm grain — a memory being replayed.
No text. Audio: distant fireworks crackle, children's laughter, warm piano.
```

**V4 프롬프트** (첨부: ① §6-④ 이미지)
```
@Image1 is the first frame. Vertical 9:16. The same young woman now smiling
softly, holding her phone up with both hands toward the sea and the night
sky (the phone screen stays a soft warm blur — no readable UI). She lowers
the phone and looks up: directly above her, ONE new warm star quietly
brightens among the others and pulses once like a heartbeat. The camera
drifts upward past her toward that star. Realistic live-action, deep navy
night, warm bokeh, film grain, emotional and hopeful. No text, no VFX beams.
Audio: soft waves, a single warm piano motif rising.
```

**컷 편집**: V1 위 자막 "지금 서 있는 그 자리에" → V2 "누군가의 기억이 빛나고 있어요" → V4 "당신의 오늘도, 별이 됩니다" + 로고.

---

## 5. 카피(자막)·로고 마스터 — 전부 후반 편집에서

| 타이밍 | 한국어(메인) | English(수출/영문판) |
|---|---|---|
| 메인 S4 (7.5~9.5s) | 밤하늘의 별 하나하나는 | Every star above this city |
| 메인 S5 (10~12s) | 누군가의 기억입니다 | is someone's memory. |
| 메인 S7 (20~24s) | 당신의 오늘도, 별이 됩니다 | Your today becomes a star, too. |
| 메인 S9 (26~30s) | 당신의 이야기를 우주에 남겨보세요 · **STARY** | Leave your story among the stars. · **STARY** |
| 티저 V1/V2/V4 | 위 §4 표 참고 | Right where you stand / someone's memory is shining / Your today becomes a star |

- 서체: 감성 세리프 **마루 부리**(무료) 또는 프리텐다드 Light. 흰색 85~90% 불투명, 화면 하단 1/4, 페이드 0.3s.
- 몽타주(S6) 구간은 **무자막** — 숨 쉬는 구간으로 남긴다.
- 마지막 프레임에 스토어 뱃지(App Store / Google Play) + 앱 아이콘(`app_image.webp`).

---

## 6. 신규 생성 필요 이미지 4장 — 이미지 생성 AI용 프롬프트

> Claude(이 환경)는 이미지를 직접 생성할 수 없다. 아래 블록을 **Dreamina의 Seedream(권장 — Seedance와 같은
> 생태계라 룩이 붙는다)** 또는 Nano Banana/Midjourney에 붙여넣어 만들면 된다.
> 참고 첨부가 가능한 툴이면 ①에는 `min_zoom.png`+`은하수.jpg`, ②~④에는 `scene1.png`(밤 톤 참조)를 함께 준다.

### ① S8용 — 별 박힌 지구 글로브 (16:9, 1920×1080↑)
```
Cinematic photoreal shot of Earth from deep space at night, planet centered
slightly right of frame, 16:9. The night side glows with faint golden city
lights, and scattered across the continents shine thousands of tiny
brilliant multicolored star sparkles — pink, cyan, gold, violet, mint — like
jewels of light marking people's memories. Two or three thin elegant
luminous orbit trails circle the planet at a slight tilt. Behind the Earth a
faint dreamy pink-and-violet milky way band crosses the deep navy starfield
diagonally. Soft atmospheric rim light on the Earth's limb, gentle bloom on
the sparkles, warm and emotional rather than cold sci-fi, extremely
detailed. No text, no UI, no satellites.
```
> 대안: 앱을 글로브 최소 줌으로 띄워 고해상 스크린샷(문자 UI 없는 각도) — `min_zoom.png`가 그 예시. 실화면을 쓰면 "광고=실제 앱"이 된다.

### ② V1용 — 올려다보는 여자 (9:16, 1080×1920)
```
Vertical 9:16 cinematic photo, night. A young Korean woman in her twenties,
seen from behind at waist-up, stands on a quiet seaside boardwalk in a small
travel town, looking up at the deep navy sky. Above the rooftops five small
soft colored stars glow — warm amber, blue-white, pink, mint, pale gold —
believable and quiet, not fantasy. Warm string-light bokeh and closed cafés
in the distance, sea breeze in her hair. Deep navy grade, fine film grain,
premium emotional brand-film still. No text, no logos.
```

### ③ V3용 — 불꽃놀이 기억 (9:16)
```
Vertical 9:16 cinematic photo, night. A family of four seen from behind on a
riverside lawn, watching red and gold fireworks bloom high above a city
skyline across the water, warm sparks reflecting on the river. Golden
Kodak-film warmth, soft halation, fine 35mm grain, nostalgic and warm — a
precious memory. No text.
```

### ④ V4용 — 폰을 든 여자 + 새 별 (9:16)
```
Vertical 9:16 cinematic photo, night. The same young Korean woman from
behind-side angle, smiling softly, holding her phone up with both hands to
capture the sea and the starry night sky; the phone screen is a soft warm
glow with no readable interface. High above her, one NEW small warm star
shines slightly brighter than the rest. Deep navy night, warm bokeh, fine
film grain, hopeful and tender, premium brand-film still. No readable text.
```

---

## 7. 음악(BGM) 플랜 — 앱 사운드 그대로

| 용도 | 1순위 | 2순위 | 비고 |
|---|---|---|---|
| 메인 필름 30s | `bgm_star_whisper.mp3` | `bgm_celestial_drift.mp3` | 피아노/서정 트랙 위주로 직접 들어보고 확정 |
| 세로 티저 15s | `bgm_nebula_garden.mp3` | `bgm_celestial_drift.mp3` | 잔잔+맑은 톤 |
| 범퍼 D 10s | `bgm_cosmic_funk.mp3` | `bgm_tiny_explorer.mp3` | 리듬감 |

- Seedance가 만들어 주는 오디오는 **도시 소음·물소리 등 앰비언스로만** 낮게 깔고, 음악 트랙은 편집에서 교체.
- 광고 음악 = 앱 배경음악 → 설치 후 "광고에서 듣던 그 소리"가 나며 브랜딩이 완성된다.
- ⚠️ 집행 전 BGM 트랙의 라이선스(광고 사용 가능 여부) 확인.

---

## 8. 제작 워크플로 체크리스트

1. **이미지 준비**: scene6-1~4·scene8 → 16:9 크롭본 저장(메인용). §6 프롬프트로 신규 4장 생성.
2. **클립 생성**(Dreamina/즉몽 or fal·WaveSpeed API): 씬마다 §3·§4 블록 복붙, 이미지를 **프롬프트의 @번호 순서대로 첨부**.
   길이는 표의 "생성 초" 선택. 마음에 들 때까지 씬 단위 리롤(한 번에 전체 생성 금지 — 비용/통제 둘 다 불리).
3. **연속성**: 이어지는 컷이 어색하면 앞 클립 마지막 프레임 캡처 → 다음 클립 첫 프레임으로 교체 생성.
4. **편집**(CapCut/프리미어): §3.0 타임라인대로 컷 → 자막·로고(§5) → BGM(§7) → 전 클립에 동일한 미세 LUT(네이비 강조)로 톤 통일.
5. **출력**: 16:9 1080p 30s/15s(유튜브·앱스토어) + 9:16 15s(릴스·쇼츠). 필요 시 업스케일.
6. **앱 실화면 촬영 리스트**(티저 V2·S8 대안·스토어 프리뷰 공용):
   - 글로브 최소 줌 30초 녹화(자동 회전 + 유성 떨어질 때까지 대기 — 유성 컷은 광고 B컷으로 최고)
   - 지도에서 별 탭 → 다이어리 상세 열림 플로우
   - 부메랑 3초 움짤 촬영 → 저장 플로우(범퍼 D용)

**문제 해결**
- 파티클이 손에서 뿜어져 나옴 → 프롬프트의 CRITICAL 문장 유지한 채 리롤, 계속되면 scene7(무인물 플레이트)로 파티클 상승만 만들고 인물 컷과 교차 편집.
- 얼굴 이미지 업로드 거절 → 뒷모습 크롭으로 재시도, S5(눈)는 t2v 폴백(§3.3 참고).
- 별이 움직여버림 → "The stars stay in place; only subtle pulsing" 문구가 앞쪽에 오도록 프롬프트 순서 조정.
- 톤 널뜀 → 같은 씬 리롤 시 시드 고정(플랫폼 지원 시) 또는 잘 나온 클립을 @Video 스타일 레퍼런스로 첨부.

---

## 9. 참고 소스
- [BytePlus ModelArk — Dreamina Seedance 2.0 prompt guide](https://docs.byteplus.com/en/docs/ModelArk/2222480)
- [WaveSpeed — Seedance 2.0 Complete Guide (멀티모달 한도·@문법)](https://wavespeed.ai/blog/posts/seedance-2-0-complete-guide-multimodal-video-creation/)
- [RunDiffusion — Seedance 2.0 Prompt Guide (구조·얼굴 정책·해상도)](https://www.rundiffusion.com/seedance-2-0-prompt-guide)
- 구버전 기획: `storyboard.md`(씬 서사 원본), `STARY_commercial_master.md`(Veo용 통합 프롬프트)
