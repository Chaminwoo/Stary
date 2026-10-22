/**
 * 성운 상자 별 모양 — 하나씩 손으로 설계한 0~1 단위 실루엣(내장 0~20 과 겹치지 않는 주제).
 * 등급은 제작 난이도: 일반 = 한 덩어리 윤곽, 희귀 = 여러 부품, 영웅 = 구멍·대칭 세부, 전설 = 겹친 고리·구멍·장식까지.
 * 미리보기: node items.js --json > o.json && python preview.py o.json shapes.png shape
 */
const G = require("./geom");
const { Sub, circle, ellipse, roundedPoly, poly, capsule, starPts, sparkle, path, fit, polar, lerp, add, sub, mul, norm, len } = G;

/** 물방울 — 위가 뾰족하고 아래가 둥근 한 방울. 옆선을 살짝 부풀려 딱딱한 원뿔처럼 보이지 않게. */
function droplet() {
  const T = [0.5, 0.05];
  const C = [0.5, 0.635];
  const r = 0.305;
  const phi = 90 - (Math.asin(r / (C[1] - T[1])) * 180) / Math.PI; // 접점 각(위 방향 기준)
  const right = polar(C, r, -90 + phi);
  const left = polar(C, r, -90 - phi);
  const bulge = (a, b, k) => {
    const m = lerp(a, b, 0.5);
    const d = norm(sub(b, a));
    return add(m, mul([d[1], -d[0]], -k)); // 바깥쪽으로
  };
  const s = new Sub(T);
  s.Q(bulge(T, right, 0.035), right);
  s.arc(C, r, -90 + phi, 270 - phi);
  s.Q(bulge(left, T, 0.035), T);
  return path(...fit([s], 0.89));
}

/** 몽글 별 — 통통한 5꼭지 별, 꼭지와 안쪽 골을 모두 둥글게(내장 별의 날카로운 스파이크와 다른 말랑한 인상). */
function softStar() {
  const pts = starPts([0.5, 0.54], 5, 0.47, 0.235);
  return path(...fit([roundedPoly(pts, pts.map((_, i) => (i % 2 === 0 ? 0.24 : 0.32)))], 0.91));
}

/** 나뭇잎 — 45° 기운 잎, 양쪽 볼륨이 살짝 다르고(자연스러움) 잎자루가 휘어 붙는다. */
function leaf() {
  const B = [0.24, 0.76];
  const T = [0.9, 0.1];
  const L = len(sub(T, B));
  const u = norm(sub(T, B));
  const n = [u[1], -u[0]]; // 위(오른쪽 아래가 아닌 쪽)
  const at = (s, w) => add(add(B, mul(u, s * L)), mul(n, w * L));
  const body = new Sub(B);
  body.C(at(0.12, 0.42), at(0.62, 0.36), T);
  body.C(at(0.6, -0.27), at(0.16, -0.32), B);
  const stemEnd = add(B, mul(add(mul(u, -1), mul(n, -0.35)), 0.15));
  const stem = G.taper(add(B, mul(u, 0.06)), stemEnd, 0.075, 0.025);
  return path(...fit([body, stem], 0.93));
}

/** 나비 — 큰 윗날개 + 작은 아랫날개(오른쪽을 그려 좌우 반전), 몸통, 끝이 동그란 더듬이. */
function butterfly() {
  const up = new Sub([0.525, 0.46]);
  up.C([0.58, 0.2], [0.79, 0.04], [0.925, 0.15]);
  up.C([1.0, 0.22], [0.95, 0.43], [0.79, 0.49]);
  up.C([0.69, 0.53], [0.585, 0.515], [0.525, 0.5]);
  const low = new Sub([0.525, 0.53]);
  low.C([0.66, 0.52], [0.84, 0.58], [0.81, 0.74]);
  low.C([0.785, 0.88], [0.64, 0.9], [0.585, 0.79]);
  low.C([0.55, 0.72], [0.525, 0.63], [0.525, 0.57]);
  const body = ellipse([0.5, 0.56], 0.04, 0.22);
  const head = circle([0.5, 0.315], 0.048);
  const antR = G.taper([0.515, 0.29], [0.64, 0.1], 0.026, -0.03);
  const antL = antR.mirrorX();
  const knobR = circle([0.645, 0.095], 0.032);
  return path(...fit([up, up.mirrorX(), low, low.mirrorX(), body, head, antR, antL, knobR, knobR.mirrorX()], 0.8));
}

/** 셋잇단… 아니, 두 개 묶인 16분음표(♬) — 기운 타원 머리 둘, 기둥 둘, 두 줄 빔. 배경음악 기능과 이어지는 모양. */
function notes() {
  const head1 = ellipse([0.3, 0.79], 0.135, 0.098, -24);
  const head2 = ellipse([0.735, 0.7], 0.135, 0.098, -24);
  const x1 = 0.418;
  const x2 = 0.853;
  const w = 0.052;
  const stem1 = poly([[x1 - w, 0.24], [x1, 0.235], [x1, 0.77], [x1 - w, 0.79]]);
  const stem2 = poly([[x2 - w, 0.155], [x2, 0.15], [x2, 0.685], [x2 - w, 0.705]]);
  const beam = (y1, y2, th) => poly([[x1 - w, y1], [x2, y2], [x2, y2 + th], [x1 - w, y1 + th]]);
  return path(...fit([head1, head2, stem1, stem2, beam(0.215, 0.13, 0.085), beam(0.355, 0.27, 0.075)], 1.08));
}

/** 고래 — 왼쪽을 보는 옆모습, 꼬리지느러미가 위로 휘고, 머리 위로 물을 뿜는다(물기둥 + 물방울 셋). */
function whale() {
  const s = new Sub([0.06, 0.6]);
  s.C([0.05, 0.43], [0.2, 0.33], [0.42, 0.34]); // 머리 → 등
  s.C([0.58, 0.35], [0.7, 0.44], [0.78, 0.5]); // 등 → 꼬리자루
  s.C([0.82, 0.45], [0.83, 0.38], [0.81, 0.31]); // 꼬리자루가 들린다
  s.C([0.75, 0.29], [0.7, 0.24], [0.68, 0.16]); // 왼쪽 꼬리날개 끝
  s.C([0.76, 0.17], [0.82, 0.21], [0.86, 0.26]); // 가운데 홈
  s.C([0.88, 0.2], [0.93, 0.14], [0.99, 0.13]); // 오른쪽 꼬리날개 끝
  s.C([0.97, 0.26], [0.92, 0.35], [0.87, 0.44]); // 꼬리 아래로
  s.C([0.82, 0.62], [0.62, 0.76], [0.4, 0.76]); // 배
  s.C([0.21, 0.76], [0.07, 0.73], [0.06, 0.6]); // 턱
  const fin = new Sub([0.36, 0.7]);
  fin.C([0.33, 0.8], [0.27, 0.86], [0.2, 0.88]);
  fin.C([0.28, 0.8], [0.33, 0.74], [0.44, 0.71]);
  const eye = circle([0.2, 0.55], 0.024).reversed();
  const spout = G.taper([0.27, 0.33], [0.27, 0.19], 0.03, 0);
  const drops = [circle([0.17, 0.17], 0.033), circle([0.27, 0.11], 0.038), circle([0.37, 0.17], 0.033)];
  return path(...fit([s, fin, eye, spout, ...drops], 0.92));
}

/** 여우 — 정면 얼굴을 각진 면으로(로우폴리), 귀 안쪽·눈은 구멍. 오른쪽 절반을 그려 반전. */
function fox() {
  const half = [
    [0.5, 0.37], // 이마(귀 사이)
    [0.6, 0.33], // 오른쪽 귀 안쪽 뿌리
    [0.8, 0.07], // 귀 끝
    [0.86, 0.38], // 귀 바깥 뿌리
    [0.96, 0.57], // 뺨 바깥
    [0.71, 0.7], // 턱
    [0.565, 0.87], // 주둥이 옆
  ];
  const outline = [...half, [0.5, 0.94], ...half.slice(1).reverse().map(([x, y]) => [1 - x, y])];
  const t = outline.map((p) => (p[1] < 0.1 ? 0.1 : 0.14));
  const head = roundedPoly(outline, t);
  const earR = roundedPoly([[0.64, 0.33], [0.785, 0.15], [0.815, 0.36]], 0.16).reversed();
  const eyeR = (() => {
    const e = new Sub([0.575, 0.56]);
    e.Q([0.64, 0.49], [0.71, 0.52]);
    e.Q([0.66, 0.6], [0.575, 0.56]);
    return e.reversed();
  })();
  const nose = ellipse([0.5, 0.86], 0.045, 0.03).reversed();
  return path(...fit([head, earR, earR.mirrorX(), eyeR, eyeR.mirrorX(), nose], 0.89));
}

/**
 * 달토끼 — 보름달 속 토끼(옛이야기의 "달에 사는 토끼"). 둥근 달에 앉아 있는 토끼 옆모습을 **구멍**으로 뚫어,
 * 작게 보여도 달 + 토끼 실루엣이 한눈에 읽힌다(2026-10-22 리디자인 — 예전 초승달 + 토끼 덩어리는 작게 보면 뭉개졌다).
 * 토끼 윤곽은 겹치는 부품 없이 한 붓 그리기(구멍끼리 겹치면 non-zero 채움에서 다시 채워지므로).
 */
function moonRabbit() {
  const moon = circle([0.5, 0.5], 0.43);
  const r = new Sub([0.31, 0.75]); // 엉덩이 아래
  r.C([0.2, 0.72], [0.18, 0.56], [0.27, 0.5]); // 둥근 엉덩이 → 등
  r.C([0.24, 0.47], [0.24, 0.42], [0.29, 0.43]); // 꼬리 몽실
  r.C([0.36, 0.42], [0.45, 0.42], [0.51, 0.38]); // 등 → 뒤통수
  r.C([0.47, 0.3], [0.44, 0.17], [0.48, 0.14]); // 뒷귀(뒤로 눕는다)
  r.C([0.53, 0.12], [0.56, 0.24], [0.56, 0.33]);
  r.C([0.59, 0.24], [0.64, 0.15], [0.68, 0.17]); // 앞귀(쫑긋)
  r.C([0.72, 0.19], [0.68, 0.3], [0.62, 0.37]);
  r.C([0.69, 0.38], [0.75, 0.43], [0.74, 0.48]); // 이마 → 코끝
  r.C([0.73, 0.52], [0.67, 0.53], [0.62, 0.53]); // 턱
  r.C([0.61, 0.6], [0.64, 0.66], [0.67, 0.71]); // 가슴 → 앞발
  r.C([0.69, 0.75], [0.64, 0.77], [0.58, 0.76]);
  r.C([0.49, 0.78], [0.39, 0.78], [0.31, 0.75]); // 배 → 엉덩이
  const eye = circle([0.655, 0.44], 0.022); // 눈은 구멍 속의 달빛(구멍 안에서 다시 채움)
  return path(...fit([moon, r.reversed(), eye], 0.86));
}

/** 북극성 나침반 — 동서남북 긴 꼭지 + 대각 짧은 꼭지, 둘레 고리(고리 안쪽은 구멍), 한가운데 작은 구멍, 오른쪽 위에 북극성. */
function compass() {
  const c = [0.5, 0.52];
  const pts = [];
  for (let i = 0; i < 16; i++) {
    const deg = -90 + i * 22.5;
    const r = i % 4 === 0 ? 0.44 : i % 2 === 0 ? 0.27 : 0.075;
    pts.push(polar(c, r, deg));
  }
  const rose = poly(pts);
  const ringOut = circle(c, 0.315);
  const ringIn = circle(c, 0.28).reversed();
  const hole = circle(c, 0.035).reversed();
  const polaris = sparkle([0.86, 0.13], 0.1, 0.1);
  return path(rose, ringOut, ringIn, hole, polaris);
}

module.exports = { droplet, softStar, leaf, butterfly, notes, whale, fox, moonRabbit, compass };
