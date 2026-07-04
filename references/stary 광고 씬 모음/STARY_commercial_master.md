# STARY — Veo 투입용 프롬프트

아래 블록을 **통째로 복사**해 Veo 프롬프트창에 붙여넣는다.
- 한 번에 12초 이상 생성이 되면 → **PROMPT 1** 하나로 끝.
- Veo 기본 8초 제한이면 → **PROMPT 2** 의 Clip A, Clip B 를 각각 생성 후 이어붙인다.
- 엔딩 자막("당신의 이야기를 우주에 남겨보세요.")과 STARY 로고는 **영상 생성에 포함하지 말고** 캡컷/프리미어에서 검은 화면 위에 직접 얹는다(한글 깨짐 방지).
- (선택) 시작 프레임 업로드가 가능하면 Clip A(또는 PROMPT 1)에 scene1.png 를 시작 이미지로 준다.

---

## PROMPT 1 — 원샷 (16초 연속 생성)

```
A single continuous 16-second cinematic commercial. Realistic live-action
only, premium Apple-style emotional brand film. One unbroken emotional arc:
curiosity → wonder → nostalgia → realization → hope.

THE PROTAGONIST (identical throughout): a Korean man in his late 20s, short
softly tousled black hair, slim build, dark navy chore jacket over charcoal
trousers. Always filmed from behind or near-profile; his face is never fully
shown except one extreme macro of his eye (dark brown iris). Calm and quiet.

THE LOCATION (all night scenes): a Han-River-style riverside promenade at
night. Thin metal railing, wide calm dark river, dense Seoul-like skyline on
the far bank with one tall tower with red aviation lights right of center
and an amber-lit bridge on the left. City lights reflect as soft vertical
streaks on the water. Deep navy sky, no moon.

GRADE: night — deep navy shadows, warm amber and cool blue city bokeh.
memories — golden Kodak-film warmth, soft halation, fine 35 mm grain.
CAMERA: smooth, restrained, gimbal-stabilized only — slow tracking, slow
push-in, gentle tilt. No whip pans, no drone swoops, no speed ramps.
NEVER: fantasy VFX, magic explosions, a glowing orb or light in anyone's
hand, light beams from people, cross-shaped lens flares, CG or anime look,
any text or logos. Stars are quiet symbolic points of warm light.

[0.0–2.5s] Night. The protagonist walks away from camera along the riverside
walkway between softly glowing street lamps, blurred skyline glittering
across the river. Slow tracking shot from directly behind, matching his
pace. Peaceful, lonely. His steps gradually slow as the skyline opens up.

[2.5–4.5s] He stops at the railing, silhouetted against the city. Only the
water reflections breathe. Very slow push-in over his shoulder. In the last
half-second, ONE warm amber star quietly fades into existence above the
skyline — small, soft, believable like a first star at dusk.

[4.5–6.0s] The camera tilts gently upward following his gaze as he raises
his eyes. The star glows with a soft halo, mysterious yet comforting,
pulsing once like a heartbeat.

[6.0–8.0s] Four more stars fade in one after another until FIVE stars float
above the city — warm amber, cool blue-white, soft white, deep red-orange,
pale gold — still and silent like lanterns of memory. He stands captivated.
The camera keeps pushing in, drifting close to the back of his head.

[8.0–10.0s] Seamless match-cut to an extreme macro of his eye: dark brown
iris, sharp eyelashes, the skyline and the five colored stars reflected
inside the pupil. The reflection slowly brightens as the camera pushes into
the pupil until the light blooms soft warm white and reality dissolves.

[10.0–13.4s] MEMORY MONTAGE — fragments of life racing through a mind, not
separate videos: old film memories joined by soft dissolves and subtle
motion blur, warm Kodak texture, gentle handheld, fine grain, halation.
 • (0.8s) Golden sunset riverside: a young couple runs hand in hand along
   the water toward the low sun, hair flying, joyful and free.
 • (0.8s) Spring daylight: a couple laughs on a picnic blanket beneath
   cherry blossoms in full bloom, sharing food, petals drifting down.
 • (0.8s) Night: a family of four seen from behind quietly watches red,
   gold and blue fireworks bloom over the river and the city skyline.
 • (1.0s) Golden hour: a father on a wooden bench watches his small child
   run along the riverbank with a little dog, low amber light everywhere.
   The warm light then slowly dims and cools back into the night skyline.

[13.4–16.0s] FINAL SCENE. Back on the night riverside, same framing as
before, five stars above the city. The protagonist gently raises one hand
toward the view. CRITICAL: nothing comes out of his hand — no orb, no glow
in his palm. Instead the SCENERY he is looking at responds: the skyline, the
river and its reflections begin to shimmer and lift away from reality as
countless tiny warm particles of light, like golden dust rising in reverse
across his whole field of view, passing above his open hand. The wide
particle stream drifts upward and slowly condenses into ONE beautiful bright
star. The new star rises and settles among the five stars already above the
city. The camera slowly pulls back, revealing him small against the city
under a sky that now holds one more story. Hold on this wide still frame.

AUDIO: one intimate solo piano motif over soft strings; distant city hum and
water lapping at night; faint film-projector flutter and distant laughter in
the montage; a gentle string swell as the particles rise, resolving into a
single sustained note when the star settles. No dialogue, no narration.
```

---

## PROMPT 2 — 8초 × 2클립 (Veo 기본 8초 제한용)

### Clip A (전반 8초 — 발견)

```
An 8-second cinematic commercial shot. Realistic live-action only, premium
Apple-style emotional brand film, quiet and contemplative.

THE PROTAGONIST: a Korean man in his late 20s, short softly tousled black
hair, slim build, dark navy chore jacket over charcoal trousers, always
filmed from behind or near-profile, face never fully shown except an extreme
macro of his eye (dark brown iris).
THE LOCATION: a Han-River-style riverside promenade at night — thin metal
railing, wide calm dark river, dense Seoul-like skyline on the far bank with
one tall red-lit tower right of center and an amber-lit bridge on the left,
city lights reflecting as soft vertical streaks. Deep navy sky.
GRADE: deep navy shadows, warm amber and cool blue city bokeh.
CAMERA: smooth gimbal moves only — slow tracking, slow push-in, gentle tilt.
NEVER: fantasy VFX, glowing objects in hands, light beams from people,
cross-shaped lens flares, CG look, any text.

[0.0–1.5s] He walks away from camera along the night riverside walkway, slow
tracking from directly behind; his steps slow as the skyline opens up.
[1.5–3.5s] He stops at the railing, silhouetted. Slow push-in over his
shoulder; in the last half-second ONE warm amber star quietly fades into
existence above the skyline, small and believable.
[3.5–4.5s] The camera tilts gently up with his gaze; the star glows with a
soft halo, pulsing once like a heartbeat.
[4.5–6.0s] Four more stars fade in until FIVE float above the city — warm
amber, cool blue-white, soft white, deep red-orange, pale gold — silent like
lanterns of memory. The camera drifts close to the back of his head.
[6.0–8.0s] Seamless match-cut to an extreme macro of his eye: the skyline
and five colored stars reflected in the pupil. The reflection brightens as
the camera pushes into the pupil until it blooms soft warm white, filling
the frame completely on the final frame.

AUDIO: intimate solo piano, distant city hum, water lapping. No dialogue.
```

### Clip B (후반 8초 — 기억과 보존)

```
An 8-second cinematic commercial shot continuing from a soft warm white
bloom. Realistic live-action only, premium Apple-style emotional film.

THE PROTAGONIST: a Korean man in his late 20s, short softly tousled black
hair, dark navy chore jacket over charcoal trousers, filmed from behind.
THE LOCATION (night shots): a Han-River-style riverside promenade — metal
railing, wide dark river, Seoul-like skyline with one red-lit tower right of
center and an amber-lit bridge left, five soft colored stars floating above
the city (warm amber, blue-white, white, red-orange, pale gold).
MEMORY GRADE: golden Kodak-film warmth, soft halation, fine 35 mm grain,
subtle handheld. NIGHT GRADE: deep navy, warm amber and cool blue bokeh.
NEVER: fantasy VFX, a glowing orb or light in anyone's hand, light beams
from people, cross lens flares, CG look, any text.

[0.0–3.4s] MEMORY MONTAGE opening from the white bloom — fragments of life
racing through a mind, joined by soft film dissolves and subtle motion blur,
never feeling like separate videos:
 • (0.8s) golden sunset riverside, a young couple running hand in hand
   toward the low sun, hair flying, joyful;
 • (0.8s) spring daylight, a couple laughing over a picnic beneath cherry
   blossoms in full bloom, petals drifting;
 • (0.8s) night, a family of four seen from behind watching red, gold and
   blue fireworks bloom over the river and skyline;
 • (1.0s) golden hour, a father on a bench watching his small child run
   along the riverbank with a little dog — then the warm light dims and
   cools, dissolving back into the night skyline.
[3.4–8.0s] FINAL SCENE. The protagonist stands at the night railing, five
stars above the city. He gently raises one hand toward the view. CRITICAL:
nothing comes out of his hand — no orb, no glow in his palm. Instead the
scenery he is looking at responds: the skyline, river and reflections
shimmer and lift away from reality as countless tiny warm particles of
light, like golden dust rising in reverse across his whole field of view,
passing above his open hand, slowly condensing into ONE beautiful bright
star. The new star rises and settles among the five. The camera slowly pulls
back, revealing him small against the city under a sky that now holds one
more story. Hold on this wide still frame to the end.

AUDIO: piano joined by soft strings, faint film-projector flutter and
distant laughter in the montage, a gentle swell as the particles rise,
resolving to a single sustained note as the star settles. No dialogue.
```
