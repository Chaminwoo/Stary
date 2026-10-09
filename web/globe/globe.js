/*
 * Stary 글로브(웹) — 앱의 3D 지구본(Android `feature/globe/GlobeRenderer.kt` / iOS `GlobeRenderer.swift`)을 WebGL1 로 옮긴 것.
 *
 * 셰이더 식·상수는 앱과 **같은 값**이다(GLSL ES 1.00 이라 문법 그대로 쓸 수 있다):
 *   ① 은하수 성운 하늘 구(SKY_*)        ② 유리 지구(EARTH_*, 실제 UTC 태양 방향 조명)
 *   ③ 푸른 대기광 셸(ATMO_*)            ④ 궤도 링(RING_*)
 *   ⑤ 별밭/다이어리 별빛(SPRITE_VS + SPRITE_FS/PIN_FS, 반짝임 uSparkle)
 * 앱과 다른 점(웹 전용 단순화): 별자리 선·은하수 띠 스프라이트·유성·태양 원반은 생략했고,
 * 다이어리 별은 실제 데이터(로그인 필요) 대신 **장식용 도시 별**을 띄운다.
 *
 * ⚠️ 앱의 글로브 셰이더/상수를 바꾸면(docs/code/04-globe.md) 여기 EARTH_FS/ATMO_FS 도 같이 맞출 것.
 *    육지 마스크/성운 텍스처는 `androidApp/src/main/assets/globe_*.jpg` 의 **복사본**(web/globe/) —
 *    tools/globe 로 다시 구우면 이쪽도 다시 복사한다.
 */
(function (global) {
  "use strict";

  const DEG = Math.PI / 180;
  const FOV = 42, NEAR = 0.3, FAR = 100;
  const SKY_RADIUS = 80.0, ATMO_SCALE = 1.16;

  // ───────────────────────── 셰이더 (앱과 동일) ─────────────────────────
  const EARTH_VS = `
    uniform mat4 uMVP;
    attribute vec3 aPos; attribute vec2 aUV;
    varying vec2 vUV; varying vec3 vN;
    void main() { vUV = aUV; vN = aPos; gl_Position = uMVP * vec4(aPos, 1.0); }`;

  const EARTH_FS = `
    #ifdef GL_FRAGMENT_PRECISION_HIGH
    precision highp float;
    #else
    precision mediump float;
    #endif
    uniform sampler2D uTex; uniform float uFade;
    uniform vec3 uSunDir;
    uniform vec3 uCamObj;
    varying vec2 vUV; varying vec3 vN;
    float grain(vec2 p) {
      vec3 p3 = fract(vec3(p.xyx) * 0.1031);
      p3 += dot(p3, p3.yzx + 33.33);
      return fract((p3.x + p3.y) * p3.z);
    }
    float hash3(vec3 p) { return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
    float vnoise(vec3 p) {
      vec3 i = floor(p);
      vec3 f = fract(p);
      f = f * f * (3.0 - 2.0 * f);
      return mix(mix(mix(hash3(i), hash3(i + vec3(1.0, 0.0, 0.0)), f.x),
                     mix(hash3(i + vec3(0.0, 1.0, 0.0)), hash3(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
                 mix(mix(hash3(i + vec3(0.0, 0.0, 1.0)), hash3(i + vec3(1.0, 0.0, 1.0)), f.x),
                     mix(hash3(i + vec3(0.0, 1.0, 1.0)), hash3(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
    }
    float fbm(vec3 p) { return 0.55 * vnoise(p) + 0.30 * vnoise(p * 2.07 + 3.1) + 0.15 * vnoise(p * 4.3 + 7.7); }
    void main() {
      vec3 n = normalize(vN);
      vec3 V = normalize(uCamObj - vN);
      float ndv = max(dot(n, V), 0.0);
      float edge = 1.0 - ndv;
      float sunD = dot(n, uSunDir);
      float day = smoothstep(-0.18, 0.55, sunD);
      float m = texture2D(uTex, vUV).r;
      float fill = smoothstep(0.46, 0.54, m);
      float coast = smoothstep(0.18, 0.5, m) * (1.0 - smoothstep(0.5, 0.82, m));
      float shelf = (0.55 * smoothstep(0.03, 0.40, texture2D(uTex, vUV, 4.0).r)
                   + 0.45 * smoothstep(0.02, 0.30, texture2D(uTex, vUV, 6.0).r)) * (1.0 - fill);
      float depth = pow(ndv, 0.55);
      vec3 body = mix(vec3(0.026, 0.078, 0.205), vec3(0.008, 0.024, 0.085), depth);
      body *= 0.28 + 0.72 * day;
      body += shelf * (0.30 + 0.70 * day) * 1.1 * vec3(0.050, 0.170, 0.340);
      body += pow(edge, 2.2) * (0.25 + 0.75 * day) * 0.5 * vec3(0.02, 0.06, 0.15);
      vec3 landC = mix(vec3(0.034, 0.064, 0.17), vec3(0.13, 0.25, 0.52), day) * (0.88 + 0.24 * fbm(n * 14.0));
      vec3 c = mix(body, landC, fill * (0.70 + 0.25 * day));
      c += coast * (0.22 + 0.78 * day) * 0.62 * vec3(0.40, 0.66, 1.0);
      float gloss = smoothstep(0.38, 0.49, m) * (1.0 - smoothstep(0.50, 0.60, m));
      c += gloss * (0.30 + 0.70 * smoothstep(-0.3, 0.7, sunD)) * 0.50 * vec3(0.55, 0.78, 1.0);
      vec3 nz = vec3(vnoise(n * 24.0 + 1.3), vnoise(n * 24.0 + 5.1), vnoise(n * 24.0 + 9.7)) - 0.5;
      vec3 n2 = normalize(n + 0.30 * nz);
      vec3 Rv = reflect(-V, n2);
      float refl = pow(clamp(dot(Rv, uSunDir), 0.0, 1.0), 2.6) * (0.30 + 0.70 * vnoise(n * 46.0 + 2.2)) * smoothstep(-0.1, 0.4, sunD);
      c += fill * refl * 0.48 * vec3(0.60, 0.80, 1.0);
      c += fill * pow(edge, 1.8) * (0.30 + 0.70 * day) * 0.42 * vec3(0.45, 0.68, 1.0);
      float rimLit = 0.30 + 0.70 * smoothstep(-0.35, 0.65, sunD);
      c += pow(edge, 2.6) * rimLit * 1.5 * vec3(0.20, 0.38, 0.90);
      c += pow(smoothstep(0.72, 1.0, edge), 2.0) * 1.05 * rimLit * vec3(0.50, 0.75, 1.0);
      c += (grain(gl_FragCoord.xy) - 0.5) * (1.5 / 255.0);
      gl_FragColor = vec4(max(c, vec3(0.0)) * uFade, 1.0);
    }`;

  const SKY_VS = `
    uniform mat4 uMVP;
    attribute vec3 aPos; attribute vec2 aUV;
    varying vec2 vUV;
    void main() { vUV = aUV; gl_Position = uMVP * vec4(aPos * ${SKY_RADIUS.toFixed(1)}, 1.0); }`;

  const SKY_FS = `
    #ifdef GL_FRAGMENT_PRECISION_HIGH
    precision highp float;
    #else
    precision mediump float;
    #endif
    uniform sampler2D uTex; uniform float uFade;
    varying vec2 vUV;
    float grain(vec2 p) {
      vec3 p3 = fract(vec3(p.xyx) * 0.1031);
      p3 += dot(p3, p3.yzx + 33.33);
      return fract((p3.x + p3.y) * p3.z);
    }
    void main() {
      vec3 c = texture2D(uTex, vUV).rgb * uFade + (grain(gl_FragCoord.xy) - 0.5) / 255.0;
      gl_FragColor = vec4(max(c, vec3(0.0)), 1.0);
    }`;

  const ATMO_VS = `
    uniform mat4 uMVP;
    attribute vec3 aPos;
    varying vec3 vN; varying vec3 vP;
    void main() { vN = aPos; vP = aPos * ${ATMO_SCALE.toFixed(2)}; gl_Position = uMVP * vec4(vP, 1.0); }`;

  const ATMO_FS = `
    precision mediump float;
    uniform vec3 uCamObj; uniform vec3 uSunDir; uniform float uFade;
    varying vec3 vN; varying vec3 vP;
    void main() {
      float d = -dot(normalize(vN), normalize(uCamObj - vP));
      if (d <= 0.0) discard;
      float i = pow(clamp(d / 0.5, 0.0, 1.0), 3.2);
      float side = 0.30 + 0.70 * smoothstep(-0.2, 0.8, dot(normalize(vN), uSunDir));
      gl_FragColor = vec4(vec3(0.42, 0.62, 1.0) * i * 0.34 * side * uFade, 1.0);
    }`;

  const SPRITE_VS = `
    uniform mat4 uVP; uniform mat4 uModel;
    uniform vec3 uCamPos; uniform vec3 uCamRight; uniform vec3 uCamUp;
    uniform float uTime; uniform float uSparkle;
    attribute vec3 aCenter; attribute vec2 aCorner; attribute vec4 aColor;
    attribute float aSize; attribute float aPhase; attribute float aMode;
    varying vec4 vColor; varying vec2 vUV;
    void main() {
      vec3 wc = (uModel * vec4(aCenter, 1.0)).xyz;
      float tw = 0.82 + 0.28 * sin(uTime * (1.1 + aPhase * 2.3) + aPhase * 6.2831);
      if (aMode > 1.5) tw = 1.0;
      float glint = 0.0;
      if (uSparkle > 0.5 && aMode < 0.5) {
        tw = 0.74 + 0.26 * sin(uTime * (1.9 + aPhase * 2.7) + aPhase * 6.2831);
        glint = pow(max(0.0, sin(uTime * (0.7 + aPhase * 0.9) + aPhase * 41.0)), 12.0);
        tw += 0.55 * glint;
      }
      float vis = 1.0;
      if (aMode < 0.5) {
        vec3 n = normalize(wc);
        vec3 toCam = normalize(uCamPos - wc);
        vis = smoothstep(-0.02, 0.22, dot(n, toCam));
      }
      vColor = aColor * (tw * vis);
      vUV = aCorner * 0.5 + 0.5;
      vec2 cr = aCorner;
      if (uSparkle > 0.5 && aMode < 0.5) {
        float ca = cos(aPhase * 3.14159); float sa = sin(aPhase * 3.14159);
        cr = vec2(ca * aCorner.x - sa * aCorner.y, sa * aCorner.x + ca * aCorner.y);
      }
      vec3 pos = wc + (uCamRight * cr.x + uCamUp * cr.y) * aSize * (0.88 + 0.22 * tw + 0.30 * glint);
      gl_Position = uVP * vec4(pos, 1.0);
    }`;

  const SPRITE_FS = `
    precision mediump float;
    uniform sampler2D uTex; uniform float uFade;
    varying vec4 vColor; varying vec2 vUV;
    void main() { gl_FragColor = texture2D(uTex, vUV) * vColor * uFade; }`;

  const PIN_FS = `
    precision mediump float;
    uniform sampler2D uTex; uniform float uFade; uniform float uGain;
    varying vec4 vColor; varying vec2 vUV;
    void main() {
      float cov = texture2D(uTex, vUV).a;
      float mx = max(max(vColor.r, vColor.g), max(vColor.b, 0.001));
      vec3 tint = vColor.rgb / mx;
      float a = clamp(pow(cov, 1.6) * vColor.a * uGain, 0.0, 1.0) * uFade;
      vec3 rgb = mix(tint, vec3(1.0), smoothstep(0.75, 1.0, cov) * 0.7);
      gl_FragColor = vec4(rgb * a, a);
    }`;

  const RING_VS = `
    uniform mat4 uMVP;
    attribute vec3 aPos; attribute vec2 aUV;
    varying vec2 vUV;
    void main() { vUV = aUV; gl_Position = uMVP * vec4(aPos, 1.0); }`;

  const RING_FS = `
    precision mediump float;
    uniform float uTime; uniform float uSpeed; uniform float uFade; uniform float uPhase;
    uniform float uIntensity;
    uniform vec3 uColorA; uniform vec3 uColorB;
    varying vec2 vUV;
    void main() {
      float across = sin(vUV.y * 3.14159);
      float glow = pow(across, 2.0) * 0.07;
      float core = pow(across, 14.0);
      float ends = smoothstep(0.0, 0.20, vUV.x) * smoothstep(1.0, 0.80, vUV.x);
      float t = uTime * uSpeed;
      float w1 = 0.5 + 0.5 * sin((vUV.x - t) * 6.2831 + uPhase);
      float w2 = 0.5 + 0.5 * sin((vUV.x * 2.7 + t * 0.7) * 6.2831 + uPhase * 2.3);
      float flow = 0.45 + 0.55 * (0.6 * w1 + 0.4 * w2);
      float head = fract(t * 2.2 + uPhase * 0.159);
      float d1 = vUV.x - head;
      float d2 = vUV.x - fract(head + 0.47);
      float pulse = exp(-d1 * d1 * 220.0) + 0.45 * exp(-d2 * d2 * 300.0);
      vec3 col = mix(uColorA, uColorB, 0.5 + 0.5 * sin(vUV.x * 6.2831 + uTime * 0.15 + uPhase));
      vec3 c = col * (glow * flow + core * (0.10 + 0.10 * flow)) + vec3(1.0) * core * pulse * 0.30;
      gl_FragColor = vec4(c * ends * uFade * uIntensity, 1.0);
    }`;

  // ───────────────────────── 수학(열 우선 4x4, GL 과 같은 규약) ─────────────────────────
  const mul = (a, b) => {
    const o = new Float32Array(16);
    for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) {
      let s = 0;
      for (let k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k];
      o[c * 4 + r] = s;
    }
    return o;
  };
  const persp = (fovy, aspect, n, f) => {
    const t = 1 / Math.tan(fovy * DEG / 2), o = new Float32Array(16);
    o[0] = t / aspect; o[5] = t; o[10] = (f + n) / (n - f); o[11] = -1; o[14] = 2 * f * n / (n - f);
    return o;
  };
  const rotX = (d) => { const c = Math.cos(d * DEG), s = Math.sin(d * DEG); return new Float32Array([1, 0, 0, 0, 0, c, s, 0, 0, -s, c, 0, 0, 0, 0, 1]); };
  const rotY = (d) => { const c = Math.cos(d * DEG), s = Math.sin(d * DEG); return new Float32Array([c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, 0, 0, 0, 1]); };
  const rotZ = (d) => { const c = Math.cos(d * DEG), s = Math.sin(d * DEG); return new Float32Array([c, s, 0, 0, -s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]); };
  const transl = (x, y, z) => new Float32Array([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1]);
  /** 클립 좌표 시프트: x' = x + sx·w, y' = y + sy·w  → 화면에서 지구 위치를 NDC 단위로 옮긴다(투영 후라 크기 불변). */
  const shear = (sx, sy) => new Float32Array([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, sx, sy, 0, 1]);
  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

  const mulberry32 = (seed) => {
    let a = seed >>> 0;
    return () => {
      a = (a + 0x6D2B79F5) >>> 0;
      let t = a;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  };

  /** 위경도 → 단위구 좌표. 텍스처 UV 와 같은 규약(λ=0 이 +Z). */
  const latLng = (lat, lng, r) => {
    const p = lat * DEG, l = lng * DEG;
    return [Math.cos(p) * Math.sin(l) * r, Math.sin(p) * r, Math.cos(p) * Math.cos(l) * r];
  };

  /** 태양 방향 — UTC 하루에 360°(UTC 정오에 경도 0 상공). 앱 `sunDirection()` 과 같은 식. */
  const sunLng = () => 180 - ((Date.now() % 86400000) / 86400000) * 360;
  const sunDir = () => { const lam = sunLng() * DEG; return [Math.sin(lam), 0, Math.cos(lam)]; };

  // ───────────────────────── 텍스처(절차 생성 — 앱 makeSparkBitmap/makeGlowBitmap 과 같은 식) ─────────────────────────
  const sparkStreak = (d, p, len, weight, thick) => {
    const ad = Math.abs(d);
    const e = clamp((ad - 0.55) / 0.43, 0, 1);
    const edge = 1 - e * e * (3 - 2 * e);
    const t = thick * (1 - 0.6 * Math.min(ad, 1));
    return weight * Math.exp(-ad / len) * Math.exp(-(p / t) * (p / t)) * edge;
  };
  /** 프리멀티플라이드 흰색(rgb = 알파) — 앱의 비트맵 업로드와 같은 형태. */
  const makeSpark = (mainLen, crossLen, diagWeight) => {
    const s = 128, px = new Uint8Array(s * s * 4);
    for (let j = 0; j < s; j++) for (let i = 0; i < s; i++) {
      const x = (i + 0.5) / s * 2 - 1, y = (j + 0.5) / s * 2 - 1, r2 = x * x + y * y;
      let a = Math.exp(-r2 / 0.012) + 0.20 * Math.exp(-r2 / 0.07);
      a += sparkStreak(x, y, mainLen, 1.0, 0.030) + sparkStreak(y, x, crossLen, 0.75, 0.026);
      if (diagWeight > 0) {
        const u = (x + y) * 0.70710678, v = (y - x) * 0.70710678;
        a += sparkStreak(u, v, 0.16, diagWeight, 0.022) + sparkStreak(v, u, 0.16, diagWeight, 0.022);
      }
      const ai = Math.round(clamp(a, 0, 1) * 255), o = (j * s + i) * 4;
      px[o] = px[o + 1] = px[o + 2] = ai; px[o + 3] = ai;
    }
    return { data: px, size: s };
  };
  const makeGlow = () => {
    const s = 64, px = new Uint8Array(s * s * 4);
    for (let j = 0; j < s; j++) for (let i = 0; i < s; i++) {
      const x = (i + 0.5 - s / 2) / (s / 2), y = (j + 0.5 - s / 2) / (s / 2), r = Math.sqrt(x * x + y * y);
      const a = r >= 1 ? 0 : r < 0.35 ? 1 - 0.6 * (r / 0.35) : 0.4 * (1 - (r - 0.35) / 0.65);
      const ai = Math.round(clamp(a, 0, 1) * 255), o = (j * s + i) * 4;
      px[o] = px[o + 1] = px[o + 2] = ai; px[o + 3] = ai;
    }
    return { data: px, size: s };
  };

  // 장식용 "다이어리 별" — 실제 데이터는 로그인해야 읽을 수 있어서 대표 도시에 별을 띄운다. [lat, lng, 큰별?]
  const CITIES = [
    [37.57, 126.98, 1], [35.68, 139.69, 0], [40.71, -74.0, 1], [51.51, -0.13, 0], [48.86, 2.35, 1], [-33.87, 151.21, 0],
    [-23.55, -46.63, 0], [30.04, 31.24, 0], [19.08, 72.88, 1], [34.05, -118.24, 0], [52.52, 13.4, 0], [1.35, 103.82, 0],
    [-33.92, 18.42, 0], [19.43, -99.13, 0], [43.65, -79.38, 0], [13.76, 100.5, 0], [41.01, 28.98, 0], [55.75, 37.62, 0],
    [25.2, 55.27, 0], [-34.6, -58.38, 0], [31.23, 121.47, 1], [22.32, 114.17, 0], [-1.29, 36.82, 0], [64.15, -21.94, 0],
    [37.77, -122.42, 0], [35.01, 135.77, 0], [45.5, -73.57, 0], [-37.81, 144.96, 0], [28.61, 77.21, 0], [6.52, 3.38, 0],
  ];
  const PIN_COLORS = [[1.0, 0.84, 0.42], [0.45, 0.85, 1.0], [1.0, 0.55, 0.78], [0.72, 0.60, 1.0], [0.50, 1.0, 0.80], [1.0, 0.70, 0.45]];
  const TRAIL_COLORS = [[0.55, 0.75, 1.0], [1.0, 0.62, 0.42], [0.72, 0.55, 1.0], [0.45, 1.0, 0.80], [1.0, 0.80, 0.45]];
  const CORNERS = [-1, -1, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1];

  /**
   * 글로브 시작. opts = { land, nebula, box, onLost }
   *  - box() → { cx, cy, r } : 지구 중심·반지름(CSS px)
   * WebGL 불가/셰이더 실패면 throw — 호출부가 폴백 UI 를 유지한다.
   */
  function start(canvas, opts) {
    const gl = canvas.getContext("webgl", { antialias: true, alpha: false, powerPreference: "high-performance" });
    if (!gl) throw new Error("webgl unavailable");
    const reduceMotion = global.matchMedia && global.matchMedia("(prefers-reduced-motion: reduce)").matches;

    const compile = (type, src) => {
      const sh = gl.createShader(type);
      gl.shaderSource(sh, src); gl.compileShader(sh);
      if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) throw new Error("shader: " + gl.getShaderInfoLog(sh));
      return sh;
    };
    const locs = new Map();
    const program = (vs, fs) => {
      const p = gl.createProgram();
      gl.attachShader(p, compile(gl.VERTEX_SHADER, vs));
      gl.attachShader(p, compile(gl.FRAGMENT_SHADER, fs));
      gl.linkProgram(p);
      if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error("link: " + gl.getProgramInfoLog(p));
      locs.set(p, { u: {}, a: {} });
      return p;
    };
    const U = (p, n) => { const c = locs.get(p).u; return n in c ? c[n] : (c[n] = gl.getUniformLocation(p, n)); };
    const A = (p, n) => { const c = locs.get(p).a; return n in c ? c[n] : (c[n] = gl.getAttribLocation(p, n)); };

    const earthP = program(EARTH_VS, EARTH_FS);
    const skyP = program(SKY_VS, SKY_FS);
    const atmoP = program(ATMO_VS, ATMO_FS);
    const spriteP = program(SPRITE_VS, SPRITE_FS);
    const pinP = program(SPRITE_VS, PIN_FS);
    const ringP = program(RING_VS, RING_FS);

    // ── 구 메쉬(지구/하늘/대기광 공용) ──
    const stacks = 96, slices = 192;
    const verts = new Float32Array((stacks + 1) * (slices + 1) * 5);
    let vi = 0;
    for (let i = 0; i <= stacks; i++) {
      const v = i / stacks, phi = (90 - 180 * v) * DEG;
      for (let j = 0; j <= slices; j++) {
        const u = j / slices, lam = (-180 + 360 * u) * DEG;
        verts[vi++] = Math.cos(phi) * Math.sin(lam); verts[vi++] = Math.sin(phi); verts[vi++] = Math.cos(phi) * Math.cos(lam);
        verts[vi++] = u; verts[vi++] = v;
      }
    }
    const idx = new Uint16Array(stacks * slices * 6);
    let ii = 0;
    for (let i = 0; i < stacks; i++) for (let j = 0; j < slices; j++) {
      const a = i * (slices + 1) + j, b = a + slices + 1;
      idx[ii++] = a; idx[ii++] = b; idx[ii++] = a + 1; idx[ii++] = a + 1; idx[ii++] = b; idx[ii++] = b + 1;
    }
    const sphereVbo = gl.createBuffer(), sphereIbo = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, sphereVbo); gl.bufferData(gl.ARRAY_BUFFER, verts, gl.STATIC_DRAW);
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, sphereIbo); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, idx, gl.STATIC_DRAW);
    const drawSphere = (p) => {
      gl.bindBuffer(gl.ARRAY_BUFFER, sphereVbo);
      const aPos = A(p, "aPos"), aUV = A(p, "aUV");
      gl.enableVertexAttribArray(aPos); gl.vertexAttribPointer(aPos, 3, gl.FLOAT, false, 20, 0);
      if (aUV >= 0) { gl.enableVertexAttribArray(aUV); gl.vertexAttribPointer(aUV, 2, gl.FLOAT, false, 20, 12); }
      gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, sphereIbo);
      gl.drawElements(gl.TRIANGLES, idx.length, gl.UNSIGNED_SHORT, 0);
      gl.disableVertexAttribArray(aPos); if (aUV >= 0) gl.disableVertexAttribArray(aUV);
    };

    // ── 스프라이트 버퍼(앱과 같은 12 float 정점 레이아웃) ──
    const addSprite = (out, p, r, g, b, a, size, phase, mode) => {
      for (let c = 0; c < 6; c++) out.push(p[0], p[1], p[2], CORNERS[c * 2], CORNERS[c * 2 + 1], r, g, b, a, size, phase, mode);
    };
    const makeVbo = (arr) => {
      const vbo = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, vbo); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(arr), gl.STATIC_DRAW);
      return { vbo, count: arr.length / 12 };
    };
    const rnd = mulberry32(7);
    const starArr = [];
    const randOnSphere = (R) => { const z = rnd() * 2 - 1, a = rnd() * 6.2832, r = Math.sqrt(1 - z * z); return [r * Math.cos(a) * R, z * R, r * Math.sin(a) * R]; };
    const shell = (R, count, sizeBase, sizeVar, bm) => {
      for (let i = 0; i < count; i++) {
        const p = randOnSphere(R), warm = rnd(), bright = (0.15 + rnd() * 0.68) * bm, big = rnd();
        addSprite(starArr, p, bright * (0.85 + 0.15 * warm), bright * (0.85 + 0.10 * warm), bright * (0.95 - 0.15 * warm), 1,
          sizeBase + big * big * sizeVar, rnd(), 1);
      }
    };
    shell(12, 460, 0.022, 0.070, 1.00); shell(22, 900, 0.026, 0.088, 0.76); shell(38, 1400, 0.032, 0.105, 0.56);
    const stars = makeVbo(starArr);

    // 장식용 다이어리 별 — 작은 점광(sparkTex) + 큰 별빛(starTex)
    const smallArr = [], bigArr = [];
    CITIES.forEach((c, i) => {
      const col = PIN_COLORS[i % PIN_COLORS.length], phase = ((c[0] * 7 + c[1] * 13) % 1 + 1) % 1;
      if (c[2]) addSprite(bigArr, latLng(c[0], c[1], 1.03), col[0], col[1], col[2], 1, 0.105, phase, 0);
      else addSprite(smallArr, latLng(c[0], c[1], 1.008), col[0], col[1], col[2], 1, 0.072, phase, 0);
    });
    const smallPins = makeVbo(smallArr), bigPins = makeVbo(bigArr);

    // ── 궤도 링(앱 buildTrails 와 같은 분포 — 난수열만 다르다) ──
    const rr = mulberry32(11), trails = [];
    for (let i = 0; i < 5; i++) {
      const radius = 1.28 + rr() * 0.50, halfW = 0.030 + rr() * 0.020;
      const tiltX = -38 + rr() * 76, tiltZ = -45 + rr() * 90, start = rr() * 360, sweep = 130 + rr() * 150;
      const m = mul(rotZ(tiltZ), rotX(tiltX)), arr = [];
      for (let s = 0; s <= 192; s++) {
        const u = s / 192, ang = (start + u * sweep) * DEG;
        for (let k = 0; k < 2; k++) {
          const r = radius + (k === 0 ? -halfW : halfW), x = Math.cos(ang) * r, z = Math.sin(ang) * r;
          arr.push(m[0] * x + m[8] * z, m[1] * x + m[9] * z, m[2] * x + m[10] * z, u, k);
        }
      }
      const vbo = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, vbo); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(arr), gl.STATIC_DRAW);
      const dir = rr() < 0.5 ? 1 : -1;
      trails.push({
        vbo, count: arr.length / 5, a: TRAIL_COLORS[i % 5], b: TRAIL_COLORS[(i + 2) % 5],
        speed: dir * (0.030 + rr() * 0.040), phase: rr() * 6.2832, intensity: i < 2 ? 1 : 0.35 + rr() * 0.25,
      });
    }

    // ── 텍스처 ──
    const texParams = (wrapS) => {
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, wrapS);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    };
    const rawTex = (t) => {
      const tex = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, t.size, t.size, 0, gl.RGBA, gl.UNSIGNED_BYTE, t.data);
      gl.generateMipmap(gl.TEXTURE_2D); texParams(gl.CLAMP_TO_EDGE);
      return tex;
    };
    const glowTex = rawTex(makeGlow());
    const sparkTex = rawTex(makeSpark(0.36, 0.26, 0));
    const starTex = rawTex(makeSpark(0.46, 0.34, 0.50));
    const maxTex = gl.getParameter(gl.MAX_TEXTURE_SIZE);
    // 작은 화면에선 4096 육지 마스크가 과하다(메모리 ~85MB) — 폰은 2048 로 줄여 올린다.
    const smallScreen = Math.min(global.screen.width, global.screen.height) < 700;
    const imgTex = (img, wrapS, cap) => {
      const limit = Math.min(maxTex, cap);
      let src = img;
      if (img.width > limit) {
        const c = document.createElement("canvas");
        c.width = limit; c.height = limit / 2;
        const cx = c.getContext("2d"); cx.imageSmoothingQuality = "high"; cx.drawImage(img, 0, 0, c.width, c.height);
        src = c;
      }
      const tex = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, src);
      gl.generateMipmap(gl.TEXTURE_2D); texParams(wrapS);
      return tex;
    };
    const loadImg = (url) => new Promise((res, rej) => {
      const im = new Image(); im.onload = () => res(im); im.onerror = () => rej(new Error("img " + url)); im.src = url;
    });

    return Promise.all([loadImg(opts.land), loadImg(opts.nebula)]).then((imgs) => {
      const earthTex = imgTex(imgs[0], gl.REPEAT, smallScreen ? 2048 : 4096);
      const skyTex = imgTex(imgs[1], gl.REPEAT, 2048);

      // ── 상태 ──
      const sl = sunLng();
      let yaw = -(sl - 26), pitch = 22;           // 햇빛 받는 면이 오른쪽에 걸리게 시작(레퍼런스 구도)
      let yawVel = 0, pitchVel = 0, lastTouch = -1e9, dragging = false, lastX = 0, lastY = 0, lastT = 0;
      let zoom = 1, fade = 0, startMs = performance.now(), lastMs = 0, dist = 6, rPx = 150;
      let W = 1, H = 1, lost = false, projBase = null, shearM = null;

      /** 지구의 화면 위치/크기(opts.box)로 카메라 거리·투영을 다시 계산한다(캔버스 크기는 건드리지 않는다). */
      const recompute = () => {
        const b = opts.box();
        rPx = b.r * zoom;
        // 지구 반지름이 화면에서 rPx(css px) 가 되는 카메라 거리: r_px = (H/2) / (tan(fov/2)·√(d²−1))
        const s = (H / 2) / (Math.tan(FOV * DEG / 2) * rPx);
        dist = Math.sqrt(1 + s * s);
        // 지구 중심을 (cx, cy) 로 — 투영 후 NDC 시프트(크기 불변).
        const sx = (b.cx / W) * 2 - 1, sy = 1 - (b.cy / H) * 2;
        projBase = persp(FOV, W / H, NEAR, FAR); shearM = shear(sx, sy);
      };
      const layout = () => {
        const dpr = Math.min(global.devicePixelRatio || 1, 2);
        W = Math.max(1, canvas.clientWidth); H = Math.max(1, canvas.clientHeight);
        canvas.width = Math.max(1, Math.round(W * dpr)); canvas.height = Math.max(1, Math.round(H * dpr));
        gl.viewport(0, 0, canvas.width, canvas.height);
        recompute();
      };
      const onResize = () => layout();
      global.addEventListener("resize", onResize);
      layout();

      // ── 입력 ──
      const degPerPx = () => (180 / Math.PI) / (rPx * 1.0);
      canvas.style.touchAction = "none";
      canvas.addEventListener("pointerdown", (e) => {
        dragging = true; lastX = e.clientX; lastY = e.clientY; lastT = performance.now(); lastTouch = lastT;
        yawVel = pitchVel = 0;
        try { canvas.setPointerCapture(e.pointerId); } catch (_) {}
      });
      canvas.addEventListener("pointermove", (e) => {
        if (!dragging) return;
        const now = performance.now(), dt = Math.max(0.008, (now - lastT) / 1000), k = degPerPx();
        const dx = (e.clientX - lastX) * k, dy = (e.clientY - lastY) * k;
        yaw += dx; pitch = clamp(pitch + dy, -75, 75);
        // 관성 속도는 상한을 둔다(자동화/빠른 플릭에서 한 번에 크게 끌면 수백 °/s 로 튀어 휙 도는 것 방지). 앱도 속도에 ×0.55 를 곱한다.
        yawVel = clamp(0.6 * yawVel + 0.4 * (dx / dt), -140, 140); pitchVel = clamp(0.6 * pitchVel + 0.4 * (dy / dt), -90, 90);
        lastX = e.clientX; lastY = e.clientY; lastT = now; lastTouch = now;
      });
      const endDrag = () => { dragging = false; lastTouch = performance.now(); };
      canvas.addEventListener("pointerup", endDrag);
      canvas.addEventListener("pointercancel", endDrag);
      canvas.addEventListener("wheel", (e) => {
        e.preventDefault();
        zoom = clamp(zoom * Math.exp(-e.deltaY * 0.0012), 0.75, 1.5);
        recompute();
      }, { passive: false });
      canvas.addEventListener("webglcontextlost", (e) => { e.preventDefault(); lost = true; if (opts.onLost) opts.onLost(); });

      // ── 그리기 ──
      const spriteDraw = (prog, buf, tex, view, t, sparkle, gain) => {
        if (!buf.count) return;
        gl.useProgram(prog);
        gl.uniformMatrix4fv(U(prog, "uVP"), false, view.vp);
        gl.uniformMatrix4fv(U(prog, "uModel"), false, view.model);
        gl.uniform3f(U(prog, "uCamPos"), 0, 0, view.dist);
        gl.uniform3f(U(prog, "uCamRight"), 1, 0, 0); gl.uniform3f(U(prog, "uCamUp"), 0, 1, 0);
        gl.uniform1f(U(prog, "uTime"), t); gl.uniform1f(U(prog, "uSparkle"), sparkle); gl.uniform1f(U(prog, "uFade"), fade);
        if (gain != null) gl.uniform1f(U(prog, "uGain"), gain);
        gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, tex); gl.uniform1i(U(prog, "uTex"), 0);
        gl.bindBuffer(gl.ARRAY_BUFFER, buf.vbo);
        const spec = [["aCenter", 3, 0], ["aCorner", 2, 12], ["aColor", 4, 20], ["aSize", 1, 36], ["aPhase", 1, 40], ["aMode", 1, 44]];
        spec.forEach((s) => { const l = A(prog, s[0]); gl.enableVertexAttribArray(l); gl.vertexAttribPointer(l, s[1], gl.FLOAT, false, 48, s[2]); });
        gl.drawArrays(gl.TRIANGLES, 0, buf.count);
        spec.forEach((s) => gl.disableVertexAttribArray(A(prog, s[0])));
      };

      const frame = (now) => {
        if (lost) return;
        global.requestAnimationFrame(frame);
        if (document.hidden) { lastMs = 0; return; }
        const dt = lastMs ? clamp((now - lastMs) / 1000, 0.001, 0.05) : 0.016;
        lastMs = now;
        const t = (now - startMs) / 1000;

        fade = Math.min(1, fade + dt / 1.1);
        if (!dragging) {
          const since = now - lastTouch;
          if (since > 60) {
            yaw += yawVel * dt; pitch = clamp(pitch + pitchVel * dt, -75, 75);
            const decay = Math.exp(-2.6 * dt); yawVel *= decay; pitchVel *= decay;
          }
          if (!reduceMotion && since > 1200) yaw += dt * 1.7; // 앱과 같은 느린 자동 회전
        }
        // 진입 돌리-인(부드럽게 다가온다)
        const ease = reduceMotion ? 1 : 1 - Math.pow(1 - Math.min(1, t / 1.6), 3);
        const d = dist * (1 + 0.22 * (1 - ease));
        const model = mul(rotX(pitch), rotY(yaw));
        const vpM = mul(shearM, mul(projBase, transl(0, 0, -d)));
        const mvp = mul(vpM, model);
        // 카메라 위치(지구 좌표계) = R^T·(0,0,d) — 모델은 순수 회전이라 역행렬 = 전치. (R^T v)_i = Σ_j m[i*4+j]·v_j
        const camObj = [model[2] * d, model[6] * d, model[10] * d];
        const sun = sunDir();
        const view = { vp: vpM, model, dist: d };

        gl.clearColor(0, 0, 0, 1); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
        gl.disable(gl.CULL_FACE);

        // 0) 성운 하늘 — 가장 먼 배경(깊이 무시, 모델 회전을 따라 별밭과 같이 돈다)
        gl.disable(gl.DEPTH_TEST); gl.depthMask(false); gl.disable(gl.BLEND);
        gl.useProgram(skyP);
        gl.uniformMatrix4fv(U(skyP, "uMVP"), false, mvp); gl.uniform1f(U(skyP, "uFade"), fade);
        gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, skyTex); gl.uniform1i(U(skyP, "uTex"), 0);
        drawSphere(skyP);

        // 1) 배경 별밭(additive, 깊이 무시)
        gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE);
        spriteDraw(spriteP, stars, glowTex, view, t, 0, null);

        // 2) 지구 본체(불투명, 깊이 기록)
        gl.disable(gl.BLEND); gl.depthMask(true); gl.enable(gl.DEPTH_TEST); gl.depthFunc(gl.LEQUAL);
        gl.useProgram(earthP);
        gl.uniformMatrix4fv(U(earthP, "uMVP"), false, mvp); gl.uniform1f(U(earthP, "uFade"), fade);
        gl.uniform3f(U(earthP, "uSunDir"), sun[0], sun[1], sun[2]); gl.uniform3f(U(earthP, "uCamObj"), camObj[0], camObj[1], camObj[2]);
        gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, earthTex); gl.uniform1i(U(earthP, "uTex"), 0);
        drawSphere(earthP);

        // 이하 additive, 깊이 테스트만(기록 X) — 행성 뒤로 가려진다
        gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE); gl.depthMask(false);
        // 3) 대기광
        gl.useProgram(atmoP);
        gl.uniformMatrix4fv(U(atmoP, "uMVP"), false, mvp);
        gl.uniform3f(U(atmoP, "uCamObj"), camObj[0], camObj[1], camObj[2]); gl.uniform3f(U(atmoP, "uSunDir"), sun[0], sun[1], sun[2]);
        gl.uniform1f(U(atmoP, "uFade"), fade);
        drawSphere(atmoP);
        // 4) 궤도 링
        gl.useProgram(ringP);
        gl.uniformMatrix4fv(U(ringP, "uMVP"), false, mvp); gl.uniform1f(U(ringP, "uTime"), t); gl.uniform1f(U(ringP, "uFade"), fade);
        trails.forEach((tr) => {
          gl.uniform1f(U(ringP, "uSpeed"), tr.speed); gl.uniform1f(U(ringP, "uPhase"), tr.phase); gl.uniform1f(U(ringP, "uIntensity"), tr.intensity);
          gl.uniform3f(U(ringP, "uColorA"), tr.a[0], tr.a[1], tr.a[2]); gl.uniform3f(U(ringP, "uColorB"), tr.b[0], tr.b[1], tr.b[2]);
          gl.bindBuffer(gl.ARRAY_BUFFER, tr.vbo);
          const aPos = A(ringP, "aPos"), aUV = A(ringP, "aUV");
          gl.enableVertexAttribArray(aPos); gl.vertexAttribPointer(aPos, 3, gl.FLOAT, false, 20, 0);
          gl.enableVertexAttribArray(aUV); gl.vertexAttribPointer(aUV, 2, gl.FLOAT, false, 20, 12);
          gl.drawArrays(gl.TRIANGLE_STRIP, 0, tr.count);
          gl.disableVertexAttribArray(aPos); gl.disableVertexAttribArray(aUV);
        });
        // 5) 다이어리 별 — 프리멀티플라이드 덮어 그리기, 깊이 무시(뒷면은 셰이더 지평선 컷)
        gl.disable(gl.DEPTH_TEST); gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
        spriteDraw(pinP, smallPins, sparkTex, view, t, 1, 1.0);
        spriteDraw(pinP, bigPins, starTex, view, t, 1, 1.0);
        gl.depthMask(true); gl.disable(gl.BLEND);
      };
      global.requestAnimationFrame(frame);
      return {
        stop() { lost = true; global.removeEventListener("resize", onResize); },
        relayout: layout,
      };
    });
  }

  global.StaryGlobe = { start: start };
})(window);
