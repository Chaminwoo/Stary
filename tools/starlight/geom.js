/**
 * 별빛 공방 아이템용 0~1 단위 SVG 경로 빌더.
 *
 * 앱 파서(Android PathParser / iOS StarCatalog.parse)가 읽는 규칙: M L C Q Z 만 쓴다(A 호 금지 — 원·호는 C 로 근사),
 * 채움 non-zero. 구멍은 **반대 방향**으로 감은 하위 경로(cw 바깥 + ccw 안쪽). 같은 방향끼리 겹치면 합쳐진다.
 * 좌표계: 좌상단 원점, y 아래로. 각도는 (cos a, sin a) — 각도가 커지면 화면에서 시계 방향.
 */

const K = 0.5522847498; // 원을 4개의 3차 베지어로 그릴 때 제어점 비율(90°)

const r4 = (n) => {
  const v = Math.round(n * 10000) / 10000;
  return Object.is(v, -0) ? 0 : v;
};
const pt = (p) => `${r4(p[0])} ${r4(p[1])}`;
const rad = (deg) => (deg * Math.PI) / 180;
const add = (a, b) => [a[0] + b[0], a[1] + b[1]];
const sub = (a, b) => [a[0] - b[0], a[1] - b[1]];
const mul = (a, k) => [a[0] * k, a[1] * k];
const lerp = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
const len = (a) => Math.hypot(a[0], a[1]);
const norm = (a) => mul(a, 1 / (len(a) || 1));
const polar = (c, r, deg) => [c[0] + r * Math.cos(rad(deg)), c[1] + r * Math.sin(rad(deg))];
const rotate = (p, c, deg) => {
  const a = rad(deg);
  const d = sub(p, c);
  return [c[0] + d[0] * Math.cos(a) - d[1] * Math.sin(a), c[1] + d[0] * Math.sin(a) + d[1] * Math.cos(a)];
};

/** 하위 경로 하나 — 시작점 + 선분 목록. 뒤집기(구멍 만들기)·좌우 반전·변환을 위해 절대 좌표로 들고 있는다. */
class Sub {
  constructor(start) {
    this.start = start;
    this.segs = []; // { t:'L', to } | { t:'C', c1, c2, to } | { t:'Q', c, to }
  }
  get end() {
    return this.segs.length ? this.segs[this.segs.length - 1].to : this.start;
  }
  L(to) { this.segs.push({ t: "L", to }); return this; }
  C(c1, c2, to) { this.segs.push({ t: "C", c1, c2, to }); return this; }
  Q(c, to) { this.segs.push({ t: "Q", c, to }); return this; }

  /** 중심 c, 반지름 r 의 원호 a0→a1(도) — 90° 이하 조각의 C 로. 현재 점이 호 시작점이 아니면 L 로 잇는다. */
  arc(c, r, a0, a1) { return this.ellipseArc(c, r, r, 0, a0, a1); }

  ellipseArc(c, rx, ry, rot, a0, a1) {
    const s = [c[0] + rx * Math.cos(rad(a0)), c[1] + ry * Math.sin(rad(a0))];
    const startP = rotate(s, c, rot);
    if (len(sub(startP, this.end)) > 1e-6) this.L(startP);
    const n = Math.max(1, Math.ceil(Math.abs(a1 - a0) / 90));
    const step = (a1 - a0) / n;
    for (let i = 0; i < n; i++) {
      const t0 = rad(a0 + step * i);
      const t1 = rad(a0 + step * (i + 1));
      const k = (4 / 3) * Math.tan((t1 - t0) / 4);
      const p0 = [Math.cos(t0), Math.sin(t0)];
      const p1 = [Math.cos(t1), Math.sin(t1)];
      const c1 = [p0[0] - k * p0[1], p0[1] + k * p0[0]];
      const c2 = [p1[0] + k * p1[1], p1[1] - k * p1[0]];
      const map = (q) => rotate([c[0] + rx * q[0], c[1] + ry * q[1]], c, rot);
      this.C(map(c1), map(c2), map(p1));
    }
    return this;
  }

  map(fn) {
    const s = new Sub(fn(this.start));
    for (const g of this.segs) {
      if (g.t === "L") s.L(fn(g.to));
      else if (g.t === "C") s.C(fn(g.c1), fn(g.c2), fn(g.to));
      else s.Q(fn(g.c), fn(g.to));
    }
    return s;
  }

  /** 같은 모양을 반대 방향으로(구멍용, 또는 좌우 반전 뒤 방향 되돌리기). */
  reversed() {
    const pts = [this.start, ...this.segs.map((g) => g.to)];
    const s = new Sub(pts[pts.length - 1]);
    for (let i = this.segs.length - 1; i >= 0; i--) {
      const g = this.segs[i];
      const to = pts[i];
      if (g.t === "L") s.L(to);
      else if (g.t === "C") s.C(g.c2, g.c1, to);
      else s.Q(g.c, to);
    }
    return s;
  }

  /** x → 1-x 좌우 반전(방향이 뒤집히므로 되돌려서 같은 감김 유지). */
  mirrorX(axis = 0.5) { return this.map((p) => [2 * axis - p[0], p[1]]).reversed(); }

  toString() {
    let d = `M${pt(this.start)}`;
    for (const g of this.segs) {
      if (g.t === "L") d += `L${pt(g.to)}`;
      else if (g.t === "C") d += `C${pt(g.c1)} ${pt(g.c2)} ${pt(g.to)}`;
      else d += `Q${pt(g.c)} ${pt(g.to)}`;
    }
    return d + "Z";
  }
}

/** 시계 방향 원(구멍이면 .reversed()). */
function circle(c, r) {
  return ellipse(c, r, r, 0);
}

function ellipse(c, rx, ry, rot = 0) {
  const s = new Sub(rotate([c[0] + rx, c[1]], c, rot));
  s.ellipseArc(c, rx, ry, rot, 0, 360);
  return s;
}

/** 꼭짓점 목록(시계 방향) → 모서리를 Q 로 둥글린 다각형. t = 모서리에서 변 길이의 몇 %를 깎을지(꼭짓점마다 배열 가능). */
function roundedPoly(pts, t) {
  const n = pts.length;
  const ts = Array.isArray(t) ? t : pts.map(() => t);
  const cut = (i, toward, k) => lerp(pts[i], pts[toward], k);
  const s = new Sub(cut(0, 1, ts[0]));
  for (let j = 1; j <= n; j++) {
    const i = j % n;
    const prev = (i - 1 + n) % n;
    const next = (i + 1) % n;
    s.L(cut(i, prev, ts[i]));
    s.Q(pts[i], cut(i, next, ts[i]));
  }
  return s;
}

function poly(pts) {
  const s = new Sub(pts[0]);
  for (let i = 1; i < pts.length; i++) s.L(pts[i]);
  return s;
}

/** 두 끝이 둥근 막대(획) — a→b, 굵기 w. */
function capsule(a, b, w) {
  const d = norm(sub(b, a));
  const n = [-d[1], d[0]];
  const h = w / 2;
  const ang = (Math.atan2(d[1], d[0]) * 180) / Math.PI;
  const s = new Sub(add(a, mul(n, -h)));
  s.L(add(b, mul(n, -h)));
  s.arc(b, h, ang - 90, ang + 90);
  s.L(add(a, mul(n, h)));
  s.arc(a, h, ang + 90, ang + 270);
  return s;
}

/** 점점 가늘어지는 꼬리(유성·불꽃) — 굵은 머리 a(반지름 w/2)에서 b 의 뾰족 끝까지. */
function taper(a, b, w, bend = 0) {
  const d = norm(sub(b, a));
  const n = [-d[1], d[0]];
  const h = w / 2;
  const ang = (Math.atan2(d[1], d[0]) * 180) / Math.PI;
  const mid = add(lerp(a, b, 0.5), mul(n, bend));
  const s = new Sub(add(a, mul(n, -h)));
  s.Q(add(mid, mul(n, -h * 0.5)), b);
  s.Q(add(mid, mul(n, h * 0.5)), add(a, mul(n, h)));
  s.arc(a, h, ang + 90, ang + 270);
  return s;
}

/** 네 꼭지 반짝이(오목한 Q 변). */
function sparkle(c, r, pinch = 0.12) {
  const s = new Sub([c[0], c[1] - r]);
  const q = r * pinch;
  s.Q([c[0] + q, c[1] - q], [c[0] + r, c[1]]);
  s.Q([c[0] + q, c[1] + q], [c[0], c[1] + r]);
  s.Q([c[0] - q, c[1] + q], [c[0] - r, c[1]]);
  s.Q([c[0] - q, c[1] - q], [c[0], c[1] - r]);
  return s;
}

/** n 꼭지 별 꼭짓점(시계 방향) — 바깥 ro, 안쪽 ri, 첫 꼭지 각도 a0. */
function starPts(c, n, ro, ri, a0 = -90) {
  const out = [];
  for (let i = 0; i < n * 2; i++) out.push(polar(c, i % 2 === 0 ? ro : ri, a0 + (i * 180) / n));
  return out;
}

const path = (...subs) => subs.map(String).join("");

module.exports = {
  Sub, circle, ellipse, roundedPoly, poly, capsule, taper, sparkle, starPts, path,
  polar, rotate, lerp, add, sub, mul, norm, len, rad,
};
