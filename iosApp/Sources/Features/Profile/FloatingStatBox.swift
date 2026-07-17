import SwiftUI

/// 떠다니는 통계 아이콘 한 개. (Android `feature.profile.screen.FloatingStatBox.StatBubble` 패리티)
struct StatBubble: Identifiable {
    let id = UUID()
    /// SF Symbol 이름(별 모양이면 무시).
    let systemImage: String
    let count: Int
    let color: Color
    let label: String
    /// 탭하면 작은 아이콘이 파티클처럼 퍼졌다 사라짐(좋아요/업적).
    var burstOnTap = false
    /// false 면 잡았을 때 수 대신 라벨만 표시.
    var showCount = true
    /// >=0 이면 SF Symbol 대신 StarStyle 별 모양으로 그린다(핀한 내 다이어리).
    var starType: Int = -1
    var starColorIndex: Int = -1
    /// nil 이 아니면 히든 업적 아이콘 — 오라 파티클 + 클릭/드래그 시 화려한 버스트·잔상.
    var hiddenEffect: ParticleEffect? = nil
}

/// 통계(좋아요/친구/다이어리/업적)와 핀한 별을 **떠다니는 아이콘**으로 보여주고 물리적으로 다룬다.
/// - 잡으면 커지고 그 위에 수/라벨이 뜨며, 놓으면 던진 방향으로 미끄러지다 벽/서로에 튕긴다.
/// - 빠른 탭은 해당 동작(친구/내 별/업적/지도)으로 연결.
/// Android FloatingStatBox 의 Compose 물리(부유/잡기/던지기/충돌/버스트)를 SwiftUI 로 포팅.
struct FloatingStatBox: View {
    let items: [StatBubble]
    /// 빠른 탭 시 인덱스 콜백.
    var onTap: (Int) -> Void = { _ in }
    /// 이 세로 비율(프로필 사진/이름)을 가리지 않게 그 주변을 피해 배치.
    var avoidCenterYFraction: CGFloat = 0.5

    @StateObject private var engine = FloatingEngine()

    var body: some View {
        GeometryReader { geo in
            TimelineView(.animation) { timeline in
                ZStack(alignment: .topLeading) {
                    // 렌더링만(후광/회전/별) — 히트테스트는 끄고, 버블별 투명 뷰가 제스처를 받는다.
                    // (전체 캔버스가 터치를 먹으면 아래의 아바타/로그아웃이 안 눌리므로 분리.)
                    Canvas { context, _ in
                        engine.step(date: timeline.date)
                        engine.draw(into: context)
                    } symbols: {
                        ForEach(Array(items.enumerated()), id: \.element.id) { idx, item in
                            symbolView(item, seed: idx).tag(idx)
                        }
                    }
                    .allowsHitTesting(false)

                    // 버블별 투명 히트 영역(아이콘 위에서만 잡기/탭). 본체는 Canvas 가 그린다.
                    ForEach(Array(items.enumerated()), id: \.element.id) { idx, _ in
                        Color.clear
                            .frame(width: engine.hitDiameter(idx), height: engine.hitDiameter(idx))
                            .contentShape(Circle())
                            .position(engine.bodyPos(idx))
                            .gesture(
                                DragGesture(minimumDistance: 0)
                                    .onChanged { v in engine.onDrag(index: idx, start: v.startLocation, current: v.location, time: v.time.timeIntervalSinceReferenceDate) }
                                    .onEnded { v in engine.onDragEnd(index: idx, time: v.time.timeIntervalSinceReferenceDate) }
                            )
                    }

                    // 잡은 아이콘 위 수/라벨 오버레이 — 손가락 위쪽.
                    if let gi = engine.scaledIdx, gi < items.count {
                        countOverlay(items[gi])
                            .position(x: engine.dragPos.x,
                                      y: engine.dragPos.y - engine.overlayLift)
                            .opacity(engine.countAlpha)
                            .allowsHitTesting(false)
                    }
                }
                .onAppear { engine.configure(items: items, size: geo.size, avoidY: avoidCenterYFraction) }
                .onChange(of: geo.size) { newSize in engine.configure(items: items, size: newSize, avoidY: avoidCenterYFraction) }
                .onChange(of: items.map(\.count)) { _ in engine.updateItems(items) }
                .onChange(of: items.count) { _ in engine.configure(items: items, size: geo.size, avoidY: avoidCenterYFraction) }
            }
        }
        .onAppear { engine.onTap = onTap }
    }

    /// 캔버스 심볼(아이콘 또는 별) — 후광 없이 본체만. 후광/회전/확대는 Canvas 가 그린다.
    /// 별은 지도 마커/내 다이어리와 같은 크리스탈 채움(Android `drawStarBubble` 패리티).
    @ViewBuilder
    private func symbolView(_ item: StatBubble, seed: Int) -> some View {
        if item.starType >= 0 {
            // ⚠️ 여기는 `Canvas(symbols:)` 의 심볼이라 Canvas 를 중첩하면 렌더가 불안정하다.
            //    → 크리스탈 별을 비트맵으로 구워(캐시) Image 로 넘긴다.
            Image(uiImage: StarCrystal.image(
                type: item.starType,
                colorIndex: item.starColorIndex,
                size: FloatingEngine.iconBase
            ))
            .frame(width: FloatingEngine.iconBase, height: FloatingEngine.iconBase)
        } else {
            // 벡터 틴트 대신 별과 같은 크리스탈 파편 채움(Android `bakeCrystalIcon` 패리티).
            // 굽는 해상도는 표시 크기의 2배 — 잡기 확대에도 뭉개지지 않게(Android 80dp 베이크 대응).
            Image(uiImage: StarCrystal.iconImage(
                systemName: item.systemImage,
                color: UIColor(item.color),
                seed: seed,
                size: FloatingEngine.iconBase * 2
            ))
            .resizable().scaledToFit()
            .frame(width: FloatingEngine.iconBase * 0.78, height: FloatingEngine.iconBase * 0.78)
        }
    }

    private func countOverlay(_ item: StatBubble) -> some View {
        VStack(spacing: 1) {
            if item.showCount {
                Text("\(item.count)")
                    .font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(item.color)
                    .shadow(color: item.color.opacity(0.95), radius: 14)
                Text(item.label)
                    .font(.system(size: 12, weight: .regular))
                    .foregroundStyle(item.color.opacity(0.85))
            } else {
                Text(item.label)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(item.color)
                    .shadow(color: item.color.opacity(0.95), radius: 13)
            }
        }
        .fixedSize()
    }
}

// MARK: - 물리 엔진

/// 떠다니는 아이콘의 물리 상태(부유/던지기/충돌). TimelineView 가 매 프레임 step 을 돌린다.
/// (관찰은 TimelineView 가 담당 — @Published 불필요. @StateObject 로 인스턴스만 유지.)
final class FloatingEngine: ObservableObject {
    static let iconBase: CGFloat = 40

    // 물리 상수 (Android 동일).
    private let flingDamp: CGFloat = 1.7
    private let wallRest: CGFloat = 0.72
    private let ballRest: CGFloat = 0.82
    private let stopSpeed: CGFloat = 10
    private let maxFling: CGFloat = 3600
    private let slop: CGFloat = 10

    /// 한 아이콘의 물리 상태.
    final class Body {
        var pos: CGPoint
        var vel: CGPoint = .zero
        var anchorBase: CGPoint
        let phase: CGFloat
        let floatAmp: CGFloat
        let floatSpeed: CGFloat
        var rot: CGFloat
        let rotIdleSpeed: CGFloat
        var floatT0: CGFloat = 0
        /// 히든 아이콘 잔상(afterimage) 최근 위치들.
        var trail: [CGPoint] = []
        init(pos: CGPoint, phase: CGFloat, floatAmp: CGFloat, floatSpeed: CGFloat, rot: CGFloat, rotIdleSpeed: CGFloat) {
            self.pos = pos; self.anchorBase = pos
            self.phase = phase; self.floatAmp = floatAmp; self.floatSpeed = floatSpeed
            self.rot = rot; self.rotIdleSpeed = rotIdleSpeed
        }
    }

    /// 탭 버스트 파티클.
    final class Particle {
        var pos: CGPoint; var vel: CGPoint; var life: CGFloat
        let bubbleIdx: Int; let color: Color; let spin: CGFloat
        init(pos: CGPoint, vel: CGPoint, life: CGFloat, bubbleIdx: Int, color: Color, spin: CGFloat) {
            self.pos = pos; self.vel = vel; self.life = life
            self.bubbleIdx = bubbleIdx; self.color = color; self.spin = spin
        }
    }

    private(set) var bodies: [Body] = []
    private var particles: [Particle] = []
    private var items: [StatBubble] = []
    private var size: CGSize = .zero
    private var avoidY: CGFloat = 0.5

    // 입력/상호작용 상태.
    private var grabbed: Int?
    var dragPos: CGPoint = .zero
    private var physicsActive = false
    private(set) var scaledIdx: Int?
    private var grabScale: CGFloat = 1
    private var grabScaleTarget: CGFloat = 1
    private(set) var countAlpha: CGFloat = 0
    private var velPos: CGPoint = .zero
    private var velTime: TimeInterval = 0
    private var throwVel: CGPoint = .zero

    private var lastDate: TimeInterval = 0
    private var elapsed: CGFloat = 0

    var onTap: (Int) -> Void = { _ in }

    /// 손가락 위 수/라벨이 떠야 할 높이.
    var overlayLift: CGFloat { FloatingEngine.iconBase / 2 * grabScale + 34 }

    private var rad: CGFloat { FloatingEngine.iconBase / 2 }
    private func richness(_ count: Int) -> CGFloat { 1 + 0.5 * (1 - exp(-CGFloat(count) / 90)) }

    // MARK: 구성

    func updateItems(_ items: [StatBubble]) { self.items = items }

    func configure(items: [StatBubble], size: CGSize, avoidY: CGFloat) {
        self.items = items
        self.avoidY = avoidY
        guard size.width > 1, size.height > 1 else { return }
        if self.size == size && bodies.count == items.count { return }
        self.size = size
        rebuild(n: items.count)
    }

    private func rebuild(n: Int) {
        var rng = SeededGenerator(seed: UInt64(n * 977 + 3))
        let w = size.width, h = size.height
        let cx = w * 0.5, cy = h * avoidY
        let avoidRx = w * 0.44, avoidRy = h * 0.20
        let top = rad + h * 0.03, bottom = h * 0.80
        var placed: [CGPoint] = []
        func frand() -> CGFloat { CGFloat(rng.nextUnit()) }
        func pick() -> CGPoint {
            for _ in 0..<40 {
                let x = rad + frand() * (w - 2 * rad)
                let y = top + frand() * (bottom - top)
                let ex = (x - cx) / avoidRx, ey = (y - cy) / avoidRy
                let inAvoid = ex * ex + ey * ey < 1
                let tooClose = placed.contains { hypot($0.x - x, $0.y - y) < FloatingEngine.iconBase * 1.6 }
                if !inAvoid && !tooClose { return CGPoint(x: x, y: y) }
            }
            return CGPoint(x: rad + frand() * (w - 2 * rad), y: top + frand() * (h * 0.1))
        }
        bodies = (0..<n).map { _ in
            let p = pick(); placed.append(p)
            return Body(
                pos: p,
                phase: frand() * 6.2832,
                floatAmp: 5 + frand() * 5,
                floatSpeed: 0.5 + frand() * 0.5,
                rot: frand() * 360,
                rotIdleSpeed: (frand() - 0.5) * 30
            )
        }
        grabbed = nil; scaledIdx = nil
        grabScale = 1; grabScaleTarget = 1; countAlpha = 0
        physicsActive = false
    }

    // MARK: 프레임 스텝

    func step(date: Date) {
        guard !bodies.isEmpty else { return }
        let now = date.timeIntervalSinceReferenceDate
        let dt = lastDate == 0 ? 0.016 : min(max(now - lastDate, 0.001), 0.05)
        lastDate = now
        elapsed += CGFloat(dt)
        let dtF = CGFloat(dt)
        let w = size.width, h = size.height
        let grab = grabbed

        // 잡기 애니메이션(확대/수 알파) 이징.
        grabScale += (grabScaleTarget - grabScale) * min(1, dtF * 12)
        let targetAlpha: CGFloat = (grabbed != nil) ? 1 : 0
        countAlpha += (targetAlpha - countAlpha) * min(1, dtF * 12)
        if grabbed == nil && grabScale < 1.03 { scaledIdx = nil }

        if physicsActive {
            var grabbedVel = CGPoint.zero
            for (i, b) in bodies.enumerated() {
                if i == grab {
                    grabbedVel = dtF > 0 ? CGPoint(x: (dragPos.x - b.pos.x) / dtF, y: (dragPos.y - b.pos.y) / dtF) : .zero
                    b.pos = dragPos
                    let tgt = (b.rot / 360).rounded() * 360
                    b.rot += (tgt - b.rot) * min(1, dtF * 10)
                } else {
                    let damp = exp(-flingDamp * dtF)
                    b.vel = CGPoint(x: b.vel.x * damp, y: b.vel.y * damp)
                    b.pos = CGPoint(x: b.pos.x + b.vel.x * dtF, y: b.pos.y + b.vel.y * dtF)
                    b.rot += b.rotIdleSpeed * dtF
                }
            }
            // 벽 튕김.
            for (i, b) in bodies.enumerated() {
                let r = rad * richness(count(i))
                if i == grab {
                    b.pos = CGPoint(x: min(max(b.pos.x, r), w - r), y: min(max(b.pos.y, r), h - r))
                    continue
                }
                if b.pos.x < r { b.pos.x = r; b.vel.x = -b.vel.x * wallRest }
                else if b.pos.x > w - r { b.pos.x = w - r; b.vel.x = -b.vel.x * wallRest }
                if b.pos.y < r { b.pos.y = r; b.vel.y = -b.vel.y * wallRest }
                else if b.pos.y > h - r { b.pos.y = h - r; b.vel.y = -b.vel.y * wallRest }
            }
            // 아이콘끼리 충돌.
            let n = bodies.count
            if n > 1 {
                for i in 0..<n {
                    for j in (i + 1)..<n {
                        collide(bodies[i], bodies[j], aGrab: i == grab, bGrab: j == grab,
                                grabbedVel: grabbedVel, ra: rad * richness(count(i)), rb: rad * richness(count(j)))
                    }
                }
            }
            // 정지 판정 → 부유 모드 복귀.
            if grab == nil && bodies.allSatisfy({ hypot($0.vel.x, $0.vel.y) < stopSpeed }) {
                for b in bodies {
                    b.vel = .zero
                    let off0 = CGPoint(x: sin(b.phase) * b.floatAmp, y: cos(b.phase) * b.floatAmp * 0.7)
                    b.anchorBase = CGPoint(x: b.pos.x - off0.x, y: b.pos.y - off0.y)
                    b.floatT0 = elapsed
                }
                physicsActive = false
            }
        } else {
            for b in bodies {
                let ft = elapsed - b.floatT0
                b.pos = CGPoint(
                    x: b.anchorBase.x + sin(ft * b.floatSpeed + b.phase) * b.floatAmp,
                    y: b.anchorBase.y + cos(ft * b.floatSpeed * 0.85 + b.phase) * b.floatAmp * 0.7
                )
                b.rot += b.rotIdleSpeed * dtF
            }
        }

        // 히든 아이콘 잔상(afterimage) — 잡았거나 빠르게 움직일 때 위치를 쌓고, 멈추면 서서히 지운다.
        for (i, b) in bodies.enumerated() where i < items.count && items[i].hiddenEffect != nil {
            let moving = i == grab || hypot(b.vel.x, b.vel.y) > 45
            if moving {
                b.trail.append(b.pos)
                if b.trail.count > 12 { b.trail.removeFirst() }
            } else if !b.trail.isEmpty {
                b.trail.removeFirst()
            }
        }

        // 파티클.
        if !particles.isEmpty {
            for p in particles {
                let damp = exp(-2.4 * dtF)
                p.vel = CGPoint(x: p.vel.x * damp, y: p.vel.y * damp + 220 * dtF)
                p.pos = CGPoint(x: p.pos.x + p.vel.x * dtF, y: p.pos.y + p.vel.y * dtF)
                p.life -= dtF / 0.7
            }
            particles.removeAll { $0.life <= 0 }
        }
    }

    private func count(_ i: Int) -> Int { i < items.count ? items[i].count : 0 }

    /// 원형 두 아이콘 충돌 해소(분리 + 법선 속도 반발). 잡힌 건 무한질량.
    private func collide(_ a: Body, _ b: Body, aGrab: Bool, bGrab: Bool, grabbedVel: CGPoint, ra: CGFloat, rb: CGFloat) {
        var delta = CGPoint(x: b.pos.x - a.pos.x, y: b.pos.y - a.pos.y)
        var dist = hypot(delta.x, delta.y)
        let minDist = ra + rb
        if dist >= minDist { return }
        if dist < 0.001 { dist = 0.001; delta = CGPoint(x: 0.001, y: 0) }
        let nrm = CGPoint(x: delta.x / dist, y: delta.y / dist)
        let overlap = minDist - dist
        if aGrab {
            b.pos = CGPoint(x: b.pos.x + nrm.x * overlap, y: b.pos.y + nrm.y * overlap)
        } else if bGrab {
            a.pos = CGPoint(x: a.pos.x - nrm.x * overlap, y: a.pos.y - nrm.y * overlap)
        } else {
            a.pos = CGPoint(x: a.pos.x - nrm.x * overlap * 0.5, y: a.pos.y - nrm.y * overlap * 0.5)
            b.pos = CGPoint(x: b.pos.x + nrm.x * overlap * 0.5, y: b.pos.y + nrm.y * overlap * 0.5)
        }
        let va = a.vel.x * nrm.x + a.vel.y * nrm.y
        let vb = b.vel.x * nrm.x + b.vel.y * nrm.y
        if aGrab {
            let gvn = grabbedVel.x * nrm.x + grabbedVel.y * nrm.y
            if gvn - vb > 0 { let k = (1 + ballRest) * (gvn - vb); b.vel = CGPoint(x: b.vel.x + nrm.x * k, y: b.vel.y + nrm.y * k) }
        } else if bGrab {
            let gvn = grabbedVel.x * nrm.x + grabbedVel.y * nrm.y
            if va - gvn > 0 { let k = (1 + ballRest) * (va - gvn); a.vel = CGPoint(x: a.vel.x - nrm.x * k, y: a.vel.y - nrm.y * k) }
        } else if va - vb > 0 {
            let k = (va - vb) * 0.5 * (1 + ballRest)
            a.vel = CGPoint(x: a.vel.x - nrm.x * k, y: a.vel.y - nrm.y * k)
            b.vel = CGPoint(x: b.vel.x + nrm.x * k, y: b.vel.y + nrm.y * k)
        }
    }

    // MARK: 제스처 (버블별 — index 로 직접 받음)

    /// 버블 [i] 의 현재 화면 위치(투명 히트 뷰 배치용).
    func bodyPos(_ i: Int) -> CGPoint { i < bodies.count ? bodies[i].pos : .zero }
    /// 버블 [i] 의 히트 영역 지름(아이콘보다 넉넉하게).
    func hitDiameter(_ i: Int) -> CGFloat { max(56, FloatingEngine.iconBase * richness(count(i)) * 1.8) }

    func onDrag(index: Int, start: CGPoint, current: CGPoint, time: Double) {
        guard index < bodies.count else { return }
        if grabbed == nil {
            if hypot(current.x - start.x, current.y - start.y) > slop {
                grabbed = index; scaledIdx = index; physicsActive = true
                bodies[index].vel = .zero
                grabScaleTarget = 1.7
                dragPos = current; velPos = current; velTime = time; throwVel = .zero
            }
        } else if grabbed == index {
            dragPos = current
            let dt = time - velTime
            if dt > 0.004 {
                throwVel = CGPoint(x: (current.x - velPos.x) / CGFloat(dt), y: (current.y - velPos.y) / CGFloat(dt))
                velPos = current; velTime = time
            }
        }
    }

    func onDragEnd(index: Int, time: Double) {
        guard index < bodies.count else { return }
        if grabbed == index {
            bodies[index].vel = clampLen(throwVel, maxFling)
            grabbed = nil
            grabScaleTarget = 1
        } else if grabbed == nil {
            // 움직임 없이 뗌 = 탭.
            if items[index].burstOnTap { spawnBurst(index) }
            onTap(index)
        }
    }

    private func clampLen(_ v: CGPoint, _ maxLen: CGFloat) -> CGPoint {
        let d = hypot(v.x, v.y)
        return d > maxLen && d > 0 ? CGPoint(x: v.x * maxLen / d, y: v.y * maxLen / d) : v
    }

    private func spawnBurst(_ i: Int) {
        guard i < bodies.count else { return }
        let src = bodies[i].pos
        let hidden = i < items.count && items[i].hiddenEffect != nil
        var rng = SeededGenerator(seed: UInt64(bitPattern: Int64(elapsed * 1000)) &+ UInt64(i))
        let cnt = hidden ? 24 : 12
        for k in 0..<cnt {
            let ang = CGFloat(rng.nextUnit()) * 6.2832
            let base: CGFloat = hidden ? 90 : 60
            let span: CGFloat = hidden ? 170 : 120
            let spd = base + CGFloat(rng.nextUnit()) * span
            particles.append(Particle(
                pos: src,
                vel: CGPoint(x: cos(ang) * spd, y: sin(ang) * spd - spd * 0.3),
                life: 1,
                bubbleIdx: i,
                // 히든은 색 + 흰 스파클을 섞어 더 화려하게.
                color: (hidden && k % 3 == 0) ? .white : items[i].color,
                spin: (CGFloat(rng.nextUnit()) - 0.5) * (hidden ? 560 : 360)
            ))
        }
    }

    // MARK: 그리기

    func draw(into context: GraphicsContext) {
        // 히든 아이콘: 잔상(맨 뒤) + 궤도 스파클 오라.
        for (i, b) in bodies.enumerated() where i < items.count && items[i].hiddenEffect != nil {
            let col = itemColor(i)
            let sc = richness(count(i))
            let ts = b.trail.count
            if ts > 0 {
                for (k, tp) in b.trail.enumerated() {
                    let a = CGFloat(k + 1) / CGFloat(ts) * 0.32
                    drawSymbol(context, idx: i, color: col, pos: tp, scale: sc * 0.92, rot: b.rot, glow: 0, alpha: a)
                }
            }
            drawAura(context, color: col, pos: b.pos, r: rad * sc * 0.5, t: elapsed, seed: i)
        }
        // 파티클(아이콘 아래).
        for p in particles {
            let a = max(0, min(1, p.life))
            drawSymbol(context, idx: p.bubbleIdx, color: p.color, pos: p.pos,
                       scale: 0.42, rot: p.spin * (1 - a), glow: 0.5 * a, alpha: a)
        }
        for (i, b) in bodies.enumerated() {
            if i == scaledIdx { continue }
            drawSymbol(context, idx: i, color: itemColor(i), pos: b.pos,
                       scale: richness(count(i)), rot: b.rot, glow: -1, alpha: 1)
        }
        if let s = scaledIdx, s < bodies.count {
            drawSymbol(context, idx: s, color: itemColor(s), pos: bodies[s].pos,
                       scale: richness(count(s)) * grabScale, rot: bodies[s].rot, glow: -1, alpha: 1)
        }
    }

    private func itemColor(_ i: Int) -> Color { i < items.count ? items[i].color : .white }

    /// 후광 + (회전/확대된) 심볼 한 개.
    private func drawSymbol(_ context: GraphicsContext, idx: Int, color: Color, pos: CGPoint,
                            scale: CGFloat, rot: CGFloat, glow: CGFloat, alpha: CGFloat) {
        let r = rad * scale
        let glowAlpha = glow >= 0 ? glow : min(0.34 * scale, 0.62)
        let glowR = r * 2.6
        let glowRect = CGRect(x: pos.x - glowR, y: pos.y - glowR, width: glowR * 2, height: glowR * 2)
        context.fill(
            Path(ellipseIn: glowRect),
            with: .radialGradient(Gradient(colors: [color.opacity(Double(glowAlpha)), .clear]),
                                  center: pos, startRadius: 0, endRadius: glowR)
        )
        guard let sym = context.resolveSymbol(id: idx) else { return }
        var ctx = context
        ctx.translateBy(x: pos.x, y: pos.y)
        ctx.rotate(by: .degrees(Double(rot)))
        ctx.scaleBy(x: scale, y: scale)
        ctx.opacity = Double(alpha)
        ctx.draw(sym, at: .zero, anchor: .center)
    }

    /// 히든 아이콘 주변을 도는 스파클 오라(궤도 + 반짝임 + 흰 하이라이트). 떠 있을 때도 화려하게.
    private func drawAura(_ context: GraphicsContext, color: Color, pos: CGPoint, r: CGFloat, t: CGFloat, seed: Int) {
        let count = 7
        for k in 0..<count {
            let ph = CGFloat(seed) * 1.7 + CGFloat(k) * (6.2832 / CGFloat(count))
            let ang = t * 0.9 + ph
            let rr = r * (1.4 + 0.25 * sin(t * 1.6 + ph))
            let p = CGPoint(x: pos.x + cos(ang) * rr, y: pos.y + sin(ang) * rr)
            let a = 0.22 + 0.34 * (0.5 + 0.5 * sin(t * 2.4 + ph))
            let dot = max(0.5, r * 0.14 * (0.7 + 0.5 * sin(t * 3 + ph)))
            context.fill(Path(ellipseIn: CGRect(x: p.x - dot, y: p.y - dot, width: dot * 2, height: dot * 2)),
                         with: .color(color.opacity(Double(a))))
            let hr = max(0.4, dot * 0.42)
            context.fill(Path(ellipseIn: CGRect(x: p.x - hr, y: p.y - hr, width: hr * 2, height: hr * 2)),
                         with: .color(Color.white.opacity(Double(a * 0.5))))
        }
    }
}

/// 결정적 배치를 위한 작은 시드 RNG(레이아웃 안정화용).
private struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64
    init(seed: UInt64) { state = seed == 0 ? 0x9E3779B97F4A7C15 : seed }
    mutating func next() -> UInt64 {
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return state
    }
    /// 0..<1 실수.
    mutating func nextUnit() -> Double { Double(next() >> 11) * (1.0 / 9_007_199_254_740_992.0) }
}
