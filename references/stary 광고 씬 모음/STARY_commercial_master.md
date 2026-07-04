# STARY — Complete Commercial Master Package

**Source of truth:** `storyboard.md` (이 문서는 storyboard.md 를 1:1 로 실행하기 위한 감독 패키지다)
**Reference stills:** scene1~scene5, scene6-1~6-4, scene7 (각 씬의 구도·조명·인물·분위기 기준)
**Total runtime:** 16.4 s (scenes) + 2.6 s (end card) ≈ **19 s**
**Format:** 16:9 · 4K · 24 fps · realistic live-action only

---

## 1. Continuity Bible (전 씬 공통 고정값)

모든 씬 프롬프트 앞에 이 블록을 그대로 붙인다. 이것이 인물/장소/톤의 드리프트를 막는다.

```
CHARACTER LOCK — THE PROTAGONIST (identical in every scene he appears):
A Korean man in his late 20s. Short, softly tousled black hair. Slim build,
about 178 cm. Wearing a dark navy chore jacket with an open collar over dark
charcoal trousers. Calm, quiet, contemplative presence. He is always filmed
from behind or in near-profile; his full face is never clearly revealed
except the extreme macro shot of his eye (dark brown iris).

LOCATION LOCK — THE RIVERSIDE (all night scenes):
A Han-River-style riverside promenade at night. Thin metal railing in the
foreground. A wide, calm dark river. On the far bank, a dense Seoul-like
skyline: one tall communication tower with red aviation lights standing
right of center, an amber-lit bridge crossing the river on the left. City
lights reflect on the water as soft vertical streaks. Deep navy sky, thin
high clouds, no moon.

GRADE LOCK:
Night scenes — deep navy shadows, clean blacks, warm amber and cool blue
city bokeh, gentle contrast, no crushed detail.
Memory scenes — golden-hour Kodak-film warmth, soft halation around
highlights, fine 35 mm grain, slightly lifted blacks, nostalgic but real.

CAMERA GRAMMAR:
Smooth, restrained, gimbal-stabilized moves only — slow tracking, slow
push-in, gentle tilt. The pacing and sensibility of Apple / Google emotional
brand films. No whip pans, no drone swoops, no speed ramps.

HARD NEGATIVES (never violate):
Realistic live-action only. No fantasy VFX, no magic explosions, no glowing
orb or light ball in anyone's hand, no light beams shooting from people, no
lens-flare crosses, no anime or CG look, no text or watermark inside scenes.
Stars are quiet, symbolic points of warm light — believable, not magical.
```

---

## 2. Master Timeline (EDL)

| # | TC in–out | Dur | Shot | Ref image | Transition out |
|---|-----------|-----|------|-----------|----------------|
| S1 | 0:00.0–0:02.5 | 2.5 s | 다리 위 뒷모습 트래킹 | scene1.png | 걸음이 느려지며 컷 |
| S2 | 0:02.5–0:04.5 | 2.0 s | 멈춰서 스카이라인 응시, 푸시인 | scene2.png | 첫 별 점등 |
| S3 | 0:04.5–0:06.0 | 1.5 s | 시선 따라 틸트업, 첫 별 | scene3.png | 별 늘어남 |
| S4 | 0:06.0–0:08.0 | 2.0 s | 다섯 별, 푸시인 → 머리 옆 | scene4.png | 눈으로 매치컷 |
| S5 | 0:08.0–0:10.0 | 2.0 s | 눈 매크로, 동공 진입 | scene5.png | 화이트-웜 블룸 |
| M1 | 0:10.0–0:10.8 | 0.8 s | 노을 강변, 손잡고 달리는 연인 | scene6-1.png | 필름 디졸브 |
| M2 | 0:10.8–0:11.6 | 0.8 s | 벚꽃 피크닉 | scene6-2.png | 필름 프레임 플래시 |
| M3 | 0:11.6–0:12.4 | 0.8 s | 불꽃놀이 보는 가족 | scene6-3.png | 퀵 디졸브 |
| M4 | 0:12.4–0:13.4 | 1.0 s | 벤치의 아버지, 아이와 강아지 | scene6-4.png | 온기가 밤으로 페이드 |
| S10 | 0:13.4–0:16.4 | 3.0 s | 풍경→입자→하나의 별 | scene7.png | 카메라 풀백 |
| END | 0:16.4–0:19.0 | 2.6 s | 엔딩 타이틀 + 로고 | — | 슬로 페이드아웃 |

> storyboard.md 의 "약 15초"는 씬 합계 16.4 s 기준. 15 s 엄수가 필요하면 S1 → 2.0 s, S5 → 1.8 s, S10 홀드 2.4 s 로 트림.

---

## 3. ONE-SHOT MASTER PROMPT (단일 연속 생성용)

한 번에 15–20 s 를 뽑을 수 있는 모델(Veo 3.1 long-take, Sora 2 등)에 통째로 넣는 프롬프트.
§1 Continuity Bible 을 먼저 붙이고 이어서 아래를 붙인다.

```
A single continuous 16-second cinematic commercial, one unbroken emotional
arc: curiosity → wonder → nostalgia → realization → hope. Shot like a
premium Apple emotional brand film. Realistic live-action.

[0.0–2.5 s] Night. The protagonist quietly walks away from camera along a
riverside bridge walkway lined with softly glowing street lamps. The blurred
city skyline glitters across the river; reflections shimmer on the dark
water. Slow, stabilized tracking shot from directly behind him, matching his
walking pace. Peaceful, lonely, contemplative. His steps gradually slow as
the skyline opens up in front of him.

[2.5–4.5 s] He stops at the railing and stands still, silhouetted against
the city across the river. Nothing moves except gentle reflections breathing
on the water. The camera pushes in very slowly over his shoulder. In the
final half-second, ONE single warm amber star quietly fades into existence
above the skyline — small, soft, believable, like a first star at dusk.

[4.5–6.0 s] The camera gently tilts upward, following his gaze as he slowly
raises his eyes to the star. The star glows warmly with a soft halo,
mysterious yet comforting, gently inviting. His profile stays dark and calm.

[6.0–8.0 s] Four more stars quietly fade in one after another until FIVE
stars float above the city, each a slightly different color — warm amber,
cool blue-white, soft white, deep red-orange, pale gold. They hang still and
silent, like lanterns of memory. He stands completely captivated. The camera
keeps pushing in slowly, drifting toward the back of his head and around his
shoulder, closer and closer.

[8.0–10.0 s] Seamless match-cut into an extreme macro of his eye. Dark brown
iris, every eyelash sharp. Inside the pupil, the city skyline and the five
colored stars are clearly reflected. The reflection slowly brightens; the
camera pushes into the pupil itself until the reflection fills the frame and
reality dissolves in a soft warm bloom of light — we are entering the
memories that live inside the stars.

[10.0–13.4 s] MEMORY MONTAGE — fragments of life racing through a mind, not
separate videos: old film memories connected by soft dissolves and subtle
motion blur, warm Kodak film texture, gentle handheld sway, fine grain,
halation. Each fragment breathes exactly once and melts into the next.
 • (0.8 s) Golden sunset riverside: a young couple runs hand in hand along
   the water, hair and clothes catching the low sun, the city glowing warm
   behind them. Joyful, free. Soft film dissolve.
 • (0.8 s) Spring daylight: the same warmth — a couple sits on a picnic
   blanket beneath white-pink cherry blossoms in full bloom, laughing
   naturally while sharing food. A single film-frame flash of light.
 • (0.8 s) Night: a family of four, seen from behind on a hill, quietly
   watches red, gold and blue fireworks bloom over the river and the city
   skyline. Together in silence. Quick dissolve.
 • (1.0 s) Golden hour: a father sits on a wooden bench under a tree,
   quietly watching his small child run along the riverbank with a little
   dog, everything drenched in low amber light. Hope. The warm light slowly
   dims and cools, fading back into the night skyline.

[13.4–16.4 s] FINAL SCENE — the most important. We are back on the night
riverside. The protagonist looks at the same skyline once more, the five
stars still above it. He gently raises one hand toward the view — nothing
comes out of his hand, no light in his palm. Instead, THE SCENERY ITSELF
begins to respond: the skyline, the river, the reflections — everything he
is looking at starts to glimmer and gently lift away from reality as
countless tiny particles of warm light, like dust rising in reverse. The
particles drift upward in a soft, wide stream across his whole field of
view, passing above his open hand, and slowly condense in the sky into ONE
single beautiful bright star. The new star rises quietly and settles among
the five stars already watching over the city — the sky now holds one more
story. The camera slowly pulls back, revealing him small against the city
and the star-lit sky. His memory has been preserved.

[16.4–19.0 s] Cut to black. Centered elegant white Korean text fades in:
「당신의 이야기를 우주에 남겨보세요.」 Below it, the STARY wordmark logo.
Hold, then everything fades out slowly to silence.

AUDIO: one intimate solo piano motif over soft strings; distant city hum and
water lapping in night scenes; in the montage the music warms with faint
film-projector flutter and distant laughter; at the final transformation the
strings swell gently and resolve into a single sustained note as the star
rises; near-silence with a soft shimmer under the end card.
```

---

## 4. Scene-by-Scene Prompts (이미지 조건부 클립 생성용)

클립 상한이 5–10 s 인 툴(Veo 3.1 Fast, Kling, Runway Gen-4, Pika 등)용.
**각 클립: first frame(또는 image reference) = 해당 scene 이미지**, §1 Bible 을 프롬프트 앞에 붙인다.
연속성 체인: **각 클립의 마지막 프레임을 캡처해 다음 클립의 시작 프레임으로 공급**한다(아래 각 씬의 IN/OUT 명시).

### S1 — 2.5 s — `scene1.png`
```
IMAGE = first frame. Night riverside bridge. The protagonist walks away from
camera at a calm pace between glowing street lamps, city bokeh across the
river. Slow stabilized tracking from directly behind, locked to his walking
rhythm. Reflections shimmer subtly; his steps gradually slow near the end.
No other people. Quiet, lonely, contemplative.
OUT: he is almost stopped, skyline fully visible.
```

### S2 — 2.0 s — `scene2.png`
```
IMAGE = first frame. He now stands still at the railing, back to camera,
facing the skyline across the river. Only the water reflections move,
breathing gently. Very slow cinematic push-in over his shoulder. In the last
0.5 s, one single warm amber star softly fades into existence above the
skyline — small, quiet, believable.
OUT: star just visible, camera slightly closer.
```

### S3 — 1.5 s — `scene3.png`
```
IMAGE = first frame. The warm star glows above the skyline with a soft halo.
He slowly raises his gaze; the camera tilts gently upward in sync with his
eyes, his dark profile on the left of frame. The star feels mysterious yet
comforting, softly pulsing once like a heartbeat. Nothing else changes.
OUT: framing biased to the sky, star bright.
```

### S4 — 2.0 s — `scene4.png`
```
IMAGE = first frame. Five stars now float above the city — warm amber, cool
blue-white, soft white, deep red-orange, pale gold — still and silent like
lanterns. He stands completely captivated, motionless. Slow push-in drifting
toward the back of his head and around his right shoulder, ending close.
The stars twinkle very subtly, never magically.
OUT: frame close to his head/shoulder — prepares the match-cut to the eye.
```

### S5 — 2.0 s — `scene5.png`
```
IMAGE = first frame. Extreme macro of his eye, dark brown iris, sharp
eyelashes. The city skyline and five colored stars are reflected inside the
pupil. The reflection slowly brightens as the camera pushes into the pupil;
in the final 0.4 s the reflected lights bloom into a soft warm white that
fills the frame completely.
OUT: near-white warm bloom → montage entry.
```

### M1 — 0.8 s — `scene6-1.png`
```
IMAGE = first frame. Warm Kodak film memory, fine grain, halation, subtle
handheld. Golden sunset riverside: a young couple runs hand in hand along
the water toward the low sun, hair flying, city glowing warm behind. One
breath of joyful motion — her laugh, his glance back.
OUT: soft film dissolve (slight motion blur).
```

### M2 — 0.8 s — `scene6-2.png`
```
IMAGE = first frame. Same film texture. Spring daylight under cherry
blossoms in full bloom: a couple on a picnic blanket laughs naturally while
sharing food; petals drift down slowly. One warm breath of life.
OUT: quick film-frame flash of light (like a projector frame skip).
```

### M3 — 0.8 s — `scene6-3.png`
```
IMAGE = first frame. Same film texture. Night: a family of four seen from
behind quietly watches red, gold and blue fireworks bloom over the river and
skyline. The fireworks flicker warm light on their shoulders; no one moves.
OUT: quick dissolve.
```

### M4 — 1.0 s — `scene6-4.png`
```
IMAGE = first frame. Same film texture. Golden hour: a father on a wooden
bench under a tree watches his small child run along the riverbank with a
little dog, low amber sun flooding the scene. Hope for the future. In the
last 0.3 s the warm light gradually dims and cools, melting back toward the
night skyline.
OUT: warmth fading into night blue.
```

### S10 — 3.0 s — `scene7.png` ★ 최종 씬(가장 중요)
```
IMAGE = key reference for particle direction and composition. Night
riverside, same framing family as S2. The protagonist gazes at the skyline,
five stars above. He gently raises one hand toward the view.

CRITICAL: nothing is emitted from his hand. No orb, no glow in his palm.
Instead the SCENERY he is looking at responds: the skyline band, the river
and its reflections begin to shimmer and separate from reality as countless
tiny warm particles of light — like golden dust rising in reverse across his
entire field of view. The wide particle stream drifts upward, passing above
his open hand, and slowly condenses into ONE single beautiful bright star.
The new star rises quietly and settles among the five stars already above
the city. The real scenery remains beneath — only its luminous impression
has lifted into the sky. The camera slowly pulls back, revealing him small
against the city under a sky that now holds one more story.
OUT: wide, still, one new star among the others.
```

### END — 2.6 s — 엔드 카드 (편집 단계에서 합성 권장)
```
Pure black frame. Centered elegant white sans-serif Korean text fades in:

    당신의 이야기를 우주에 남겨보세요.

Below it, after 0.4 s, the STARY wordmark logo fades in. Hold 1 s.
Everything fades out slowly over the final 0.8 s to black and silence.
(한글 렌더링 오류 방지를 위해 생성이 아니라 편집 툴에서 텍스트/로고를 올릴 것.)
```

---

## 5. Transition & Post Spec

| 경계 | 처리 |
|------|------|
| S1→S2 | 걸음 멈춤에 맞춘 히든 컷(속도 일치로 컷 은폐) |
| S2→S3→S4 | 컷 없음처럼 — 별 점등을 앵커로 한 연속 푸시인/틸트 |
| S4→S5 | 어깨 뒤 어둠 → 눈 매크로 매치컷(어두운 면적 매칭) |
| S5→M1 | 동공 안 반사광 블룸 → 화이트-웜 오버랩 디졸브 6프레임 |
| M1→M2→M3→M4 | 소프트 필름 디졸브 4–6프레임 + 모션블러 / M2 뒤엔 1프레임 화이트 플래시 |
| M4→S10 | 색온도 크로스페이드(골드→네이비) 8프레임 |
| S10→END | 별 안착 후 0.3 s 홀드 → 12프레임 페이드 투 블랙 |

**공통 포스트:** 몽타주 구간만 35 mm 그레인 +12, 하이라이트 할레이션, 미세 핸드헬드(±2 px). 나이트 씬은 그레인 +4 만. 전체에 아주 옅은 비네트. LUT: 나이트 = teal-navy 섀도 + amber 하이라이트, 몽타주 = Kodak 2383 계열.

**사운드:** 솔로 피아노 모티프(같은 4음) — S2 첫 별에서 첫 등장, S5 블룸에서 스트링 합류, 몽타주에서 필름 프로젝터 플러터 + 멀리 웃음소리, S10 입자 상승에서 현악 스웰 → 별 안착 순간 단일 지속음으로 해소, 엔드 카드는 잔향만.

---

## 6. Generation Workflow (권장)

1. **원샷 경로:** §3 마스터 프롬프트(+Bible) → 장시간 클립 지원 모델에 투입 → 16 s 본편 → 편집 툴에서 엔드 카드만 합성.
2. **체인 경로(품질 우선, 권장):** §4 를 씬 순서대로 생성. 각 클립 마지막 프레임을 캡처해 다음 클립의 first frame 으로 공급(특히 S2→S3→S4, S4→S5). 몽타주 4클립은 병렬 생성 가능. §5 트랜지션 스펙대로 스티치.
3. **검수 체크리스트:** ① 주인공 의상/머리 동일? ② 스카이라인의 빨간 타워 위치 유지? ③ 별 5개 색 구성 유지? ④ S10 에서 손에서 빛이 나오지 않는가? ⑤ 몽타주가 "별개 영상"처럼 끊기지 않는가? ⑥ 십자 렌즈플레어/판타지 이펙트 없는가?
