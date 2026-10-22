/**
 * 앱 내장 별 모양(StarStyle.kt 0~20) 중 비교용 몇 개를 0~1 SVG 로 근사 — 성운 상자 모양 크기를 내장 별과 맞출 때 기준으로 쓴다.
 *   node builtins.js > b.json && python preview.py --compare o.json b.json sheet.png
 * (앱 렌더와 픽셀 단위로 같지는 않다 — 윤곽·크기 비교용.)
 */
const G = require("./geom");
const { Sub, circle, ellipse, path, polar } = G;

/** 곡선 스파이크 별(StarStyle buildStarPath 와 같은 식): 꼭지 → 안쪽 제어점 quad → 다음 꼭지. */
function curvedStar(spikes, innerRatio, rotDeg, c = [0.5, 0.5], outer = 0.475) {
  const total = spikes * 2;
  const at = (i, r) => polar(c, r, (i * 360) / total + rotDeg);
  const s = new Sub(at(0, outer));
  for (let i = 0; i < spikes; i++) s.Q(at(i * 2 + 1, outer * innerRatio), at(((i + 1) % spikes) * 2, outer));
  return s;
}

function scaleSub(sub, k, c = [0.5, 0.5]) {
  return sub.map((p) => [c[0] + (p[0] - c[0]) * k, c[1] + (p[1] - c[1]) * k]);
}

function heart(k = 0.82) {
  const s = new Sub([0.5, 0.84]);
  s.C([0.14, 0.62], [0.06, 0.42], [0.14, 0.28]);
  s.C([0.23, 0.12], [0.43, 0.13], [0.5, 0.3]);
  s.C([0.57, 0.13], [0.77, 0.12], [0.86, 0.28]);
  s.C([0.94, 0.42], [0.86, 0.62], [0.5, 0.84]);
  return path(scaleSub(s, k));
}

function crescent() {
  // 큰 원(0.45,0.5,r.42) − 원(0.66,0.46,r.37) 을 -22° 회전 — 근사: 초승달 경로를 직접.
  const O = [0.45, 0.5], ro = 0.42, I = [0.66, 0.46], ri = 0.37;
  const d = G.len(G.sub(I, O));
  const a = (ro * ro - ri * ri + d * d) / (2 * d);
  const h = Math.sqrt(ro * ro - a * a);
  const base = G.add(O, G.mul(G.sub(I, O), a / d));
  const perp = G.mul([-(I[1] - O[1]) / d, (I[0] - O[0]) / d], h);
  const P1 = G.add(base, perp), P2 = G.sub(base, perp);
  const ang = (c, p) => (Math.atan2(p[1] - c[1], p[0] - c[0]) * 180) / Math.PI;
  const s = new Sub(P1);
  let a1 = ang(O, P1), a2 = ang(O, P2);
  while (a2 <= a1) a2 += 360;
  s.arc(O, ro, a1, a2);
  let b1 = ang(I, P2), b2 = ang(I, P1);
  while (b2 >= b1) b2 -= 360;
  s.arc(I, ri, b1, b2);
  return path(s.map((p) => G.rotate(p, [0.5, 0.5], -22)));
}

function planet() {
  const c = [0.5, 0.52];
  return path(circle(c, 0.26), ellipse(c, 0.46, 0.15, -20), ellipse(c, 0.37, 0.105, -20).reversed());
}

function crown() {
  const k = 0.92;
  const body = G.poly([[0.16, 0.72], [0.12, 0.3], [0.33, 0.5], [0.5, 0.22], [0.67, 0.5], [0.88, 0.3], [0.84, 0.72]]);
  const band = G.poly([[0.16, 0.7], [0.84, 0.7], [0.84, 0.82], [0.16, 0.82]]);
  const subs = [body, band, circle([0.12, 0.27], 0.055), circle([0.5, 0.19], 0.06), circle([0.88, 0.27], 0.055)];
  return path(...subs.map((s) => scaleSub(s, k)));
}

function cat() {
  const k = 0.95;
  const head = ellipse([0.5, 0.6], 0.34, 0.27);
  const e1 = G.poly([[0.2, 0.5], [0.19, 0.2], [0.25, 0.17], [0.45, 0.36]]);
  const e2 = G.poly([[0.8, 0.5], [0.81, 0.2], [0.75, 0.17], [0.55, 0.36]]).reversed();
  const eye1 = ellipse([0.38, 0.6], 0.045, 0.065).reversed();
  const eye2 = ellipse([0.62, 0.6], 0.045, 0.065).reversed();
  return path(...[head, e1, e2.reversed(), eye1, eye2].map((s) => scaleSub(s, k)));
}

function sun() {
  const subs = [circle([0.5, 0.5], 0.21)];
  for (let i = 0; i < 8; i++) {
    const r = (p) => G.rotate([0.5 + p[0], 0.5 + p[1]], [0.5, 0.5], i * 45);
    const s = new Sub(r([-0.055, -0.28]));
    s.Q(r([-0.02, -0.38]), r([0, -0.47]));
    s.Q(r([0.02, -0.38]), r([0.055, -0.28]));
    subs.push(s);
  }
  return path(...subs);
}

function plane() {
  return path(G.poly([[0.92, 0.14], [0.07, 0.47], [0.37, 0.57], [0.45, 0.88], [0.56, 0.66], [0.78, 0.77]]));
}

const t = (ko) => ({ ko, en: ko, ja: ko });
const items = [
  ["1 별", path(curvedStar(5, 0.14, -90))],
  ["0 스파클", path(curvedStar(4, 0.085, 0))],
  ["7 초승달", crescent()],
  ["8 행성", planet()],
  ["9 하트", heart()],
  ["13 태양", sun()],
  ["17 왕관", crown()],
  ["19 고양이", cat()],
  ["20 종이비행기", plane()],
].map(([name, p], i) => ({ id: "builtin_" + i, kind: "shape", rarity: "common", name: t(name), payload: { type: i, path: p } }));

process.stdout.write(JSON.stringify({ items }, null, 1));
