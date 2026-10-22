/**
 * 이름 배지·천체 망원경 아이콘 글리프(0~1 단위). 이름 옆 14~18dp 에 들어가므로 굵고 단순한 실루엣 위주,
 * 등급이 오를수록 부품·구멍이 는다.
 */
const G = require("./geom");
const { Sub, circle, ellipse, roundedPoly, poly, capsule, taper, sparkle, starPts, path, polar, rotate, add, sub, mul, norm, len } = G;

/** 두 원의 차로 만든 초승달 윤곽(바깥 원 O,ro 에서 안쪽 원 I,ri 를 뺀 것). */
function crescentSub(O, ro, I, ri) {
  const d = len(sub(I, O));
  const a = (ro * ro - ri * ri + d * d) / (2 * d);
  const h = Math.sqrt(ro * ro - a * a);
  const base = add(O, mul(sub(I, O), a / d));
  const perp = mul([-(I[1] - O[1]) / d, (I[0] - O[0]) / d], h);
  const P1 = add(base, perp);
  const P2 = sub(base, perp);
  const ang = (c, p) => (Math.atan2(p[1] - c[1], p[0] - c[0]) * 180) / Math.PI;
  const s = new Sub(P1);
  let a1 = ang(O, P1);
  let a2 = ang(O, P2);
  while (a2 <= a1) a2 += 360;
  s.arc(O, ro, a1, a2);
  let b1 = ang(I, P2);
  let b2 = ang(I, P1);
  while (b2 >= b1) b2 -= 360;
  s.arc(I, ri, b1, b2);
  return s;
}

/** 초승달 — 가장 기본 배지. 살짝 기울여 웃는 입꼬리처럼. */
function crescent() {
  return path(crescentSub([0.48, 0.52], 0.4, [0.66, 0.38], 0.33));
}

/** 작은 반짝이 — 큰 네 꼭지 하나 + 작은 하나. */
function twinkle() {
  return path(sparkle([0.44, 0.56], 0.38, 0.13), sparkle([0.82, 0.2], 0.15, 0.13));
}

/** 고리 행성 — 행성 + 기운 고리(고리 안쪽은 구멍, 행성과 겹치는 곳은 행성이 채운다). */
function ringedPlanet() {
  const c = [0.5, 0.5];
  return path(
    circle(c, 0.25),
    ellipse(c, 0.47, 0.15, -22),
    ellipse(c, 0.36, 0.085, -22).reversed(),
  );
}

/** 깃털 — 휘어진 깃 + 가운데 깃대(깃대는 깃 끝 밖으로 조금 나온다). 한쪽 가장자리에 결이 갈라진 홈 둘. */
function feather() {
  const s = new Sub([0.22, 0.82]);
  s.C([0.2, 0.55], [0.42, 0.22], [0.8, 0.1]);
  s.C([0.86, 0.3], [0.78, 0.45], [0.66, 0.52]);
  s.L([0.6, 0.5]); // 결 갈라짐
  s.C([0.62, 0.58], [0.55, 0.66], [0.46, 0.7]);
  s.L([0.4, 0.67]);
  s.C([0.4, 0.74], [0.32, 0.79], [0.22, 0.82]);
  const shaft = taper([0.24, 0.8], [0.08, 0.95], 0.05, 0);
  const groove = taper([0.27, 0.76], [0.74, 0.16], 0.03, 0.06).reversed(); // 깃대 홈(크게 볼 때만 보이는 결)
  return path(s, shaft, groove);
}

/** 유성 — 오른쪽 위의 별(둥근 5꼭지) + 왼쪽 아래로 길이가 다른 꼬리 셋. */
function shootingStar() {
  const head = roundedPoly(starPts([0.68, 0.33], 5, 0.27, 0.12, -80), 0.18);
  const t1 = taper([0.55, 0.47], [0.07, 0.93], 0.13, 0.01);
  const t2 = taper([0.5, 0.36], [0.12, 0.62], 0.08, 0.01);
  const t3 = taper([0.66, 0.53], [0.4, 0.92], 0.08, -0.01);
  return path(t1, t2, t3, head);
}

/** 불사조 깃털 — 불꽃처럼 일렁이는 깃털: 굽이치는 큰 혀 셋이 깃대에서 솟는다. */
function phoenixPlume() {
  // 바깥 가장자리는 매끈하게 위로 솟고, 안쪽 가장자리에서 불꽃 혀 셋이 왼쪽 위로 날린다.
  const s = new Sub([0.2, 0.96]);
  s.C([0.44, 0.86], [0.72, 0.6], [0.82, 0.04]); // 바깥 가장자리 → 깃 끝
  s.C([0.72, 0.13], [0.63, 0.2], [0.58, 0.29]);
  s.C([0.53, 0.22], [0.47, 0.17], [0.37, 0.15]); // 첫째 혀
  s.C([0.45, 0.25], [0.5, 0.33], [0.5, 0.42]);
  s.C([0.42, 0.38], [0.33, 0.38], [0.22, 0.41]); // 둘째 혀
  s.C([0.32, 0.47], [0.39, 0.54], [0.39, 0.61]);
  s.C([0.31, 0.61], [0.22, 0.64], [0.12, 0.7]); // 셋째 혀
  s.C([0.23, 0.74], [0.27, 0.82], [0.2, 0.96]);
  const eye = ellipse([0.62, 0.42], 0.085, 0.055, -62).reversed(); // 공작 깃의 눈
  const pupil = ellipse([0.62, 0.42], 0.038, 0.025, -62);
  const shaft = taper([0.21, 0.94], [0.1, 0.995], 0.05, 0);
  // 윤곽을 반대(바깥 가장자리부터 위로 = 반시계)로 그렸으므로 뒤집어 시계 방향으로 — 눈 구멍이 뚫리고 깃대가 합쳐지게.
  return path(s.reversed(), eye, pupil, shaft);
}

// ── 천체 망원경 아이콘 ────────────────────────────────────────────────

/** 굴절 망원경 — 길고 가는 경통(앞쪽 후드가 약간 넓다) + 삼각대. */
function refractor() {
  const c = [0.5, 0.5];
  const deg = -32;
  const R = (p) => rotate(p, c, deg);
  const tube = poly([[0.12, 0.45], [0.8, 0.43], [0.8, 0.57], [0.12, 0.55]].map(R));
  const hood = poly([[0.78, 0.4], [0.95, 0.38], [0.95, 0.62], [0.78, 0.6]].map(R));
  const eyepiece = poly([[0.04, 0.47], [0.13, 0.47], [0.13, 0.53], [0.04, 0.53]].map(R));
  const mount = R([0.45, 0.56]);
  const legs = [capsule(mount, [0.3, 0.96], 0.04), capsule(mount, [0.62, 0.96], 0.04), capsule(mount, [0.47, 0.98], 0.035)];
  return path(tube, hood, eyepiece, circle(mount, 0.05), ...legs);
}

/** 반사 망원경 — 짧고 굵은 경통(옆에 접안부), 상자형 받침(돕소니언). */
function reflector() {
  const c = [0.5, 0.42];
  const deg = -40;
  const R = (p) => rotate(p, c, deg);
  const tube = roundedPoly([[0.18, 0.3], [0.86, 0.3], [0.86, 0.56], [0.18, 0.56]].map(R), 0.08);
  const ring = poly([[0.84, 0.27], [0.9, 0.27], [0.9, 0.59], [0.84, 0.59]].map(R));
  const focuser = poly([[0.66, 0.2], [0.74, 0.2], [0.74, 0.3], [0.66, 0.3]].map(R));
  const base = roundedPoly([[0.24, 0.66], [0.76, 0.66], [0.84, 0.95], [0.16, 0.95]], 0.06);
  const notch = poly([[0.38, 0.66], [0.62, 0.66], [0.56, 0.78], [0.44, 0.78]]).reversed();
  return path(tube, ring, focuser, base, notch);
}

/** 전파 망원경 — 하늘을 향한 접시(오목한 초승달 단면) + 가운데 수신기 + 받침대. */
function radioDish() {
  const dish = new Sub([0.08, 0.3]);
  dish.C([0.16, 0.66], [0.52, 0.8], [0.86, 0.62]); // 접시 바깥(아래) 곡선
  dish.L([0.92, 0.53]);
  dish.C([0.62, 0.58], [0.32, 0.46], [0.18, 0.22]); // 접시 안쪽(위) 곡선 — 두께가 남게 덜 파낸다
  const feedArm = capsule([0.48, 0.58], [0.66, 0.2], 0.035);
  const feed = circle([0.67, 0.18], 0.06);
  const signal1 = crescentSub([0.78, 0.1], 0.1, [0.72, 0.16], 0.085);
  const pedestal = poly([[0.44, 0.66], [0.56, 0.66], [0.66, 0.94], [0.34, 0.94]]);
  // 접시는 아래 곡선부터(반시계) 그렸으므로 뒤집어 다른 부품과 같은 방향으로 — 겹친 곳이 비지 않게.
  return path(dish.reversed(), feedArm, feed, signal1, pedestal);
}

module.exports = { crescent, twinkle, ringedPlanet, feather, shootingStar, phoenixPlume, refractor, reflector, radioDish };
