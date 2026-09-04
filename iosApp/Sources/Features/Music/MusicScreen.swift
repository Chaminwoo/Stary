import SwiftUI

// ─── 트랙별 별자리 데이터 ──────────────────────────────────────────────────────
// 좌표는 0..1 비율(x 오른쪽 / y 아래쪽), mag 는 별 크기/밝기 가중치. (Android MUSIC_CONSTELLATIONS 와 동일)
private struct MStar { let x: Double; let y: Double; let mag: Double }
private struct MConstel { let stars: [MStar]; let edges: [(Int, Int)] }

private let musicConstellations: [String: MConstel] = [
    // 별의 속삭임 — 리라(거문고)
    "star_whisper": MConstel(
        stars: [
            MStar(x: 0.50, y: 0.15, mag: 2.10), MStar(x: 0.34, y: 0.34, mag: 1.40),
            MStar(x: 0.66, y: 0.34, mag: 1.45), MStar(x: 0.30, y: 0.60, mag: 1.30),
            MStar(x: 0.70, y: 0.60, mag: 1.35), MStar(x: 0.42, y: 0.80, mag: 1.60),
            MStar(x: 0.58, y: 0.82, mag: 1.55),
        ],
        edges: [(0, 1), (0, 2), (1, 3), (2, 4), (3, 5), (4, 6), (5, 6)]
    ),
    // 작은 탐험가 — 작은 국자(북두칠성)
    "tiny_explorer": MConstel(
        stars: [
            MStar(x: 0.18, y: 0.30, mag: 1.80), MStar(x: 0.36, y: 0.24, mag: 1.40),
            MStar(x: 0.54, y: 0.30, mag: 1.45), MStar(x: 0.70, y: 0.40, mag: 1.50),
            MStar(x: 0.74, y: 0.58, mag: 1.35), MStar(x: 0.58, y: 0.66, mag: 1.40),
            MStar(x: 0.42, y: 0.58, mag: 1.95),
        ],
        edges: [(0, 1), (1, 2), (2, 3), (3, 4), (4, 5), (5, 6), (6, 3)]
    ),
    // 천상의 표류 — 물결
    "celestial_drift": MConstel(
        stars: [
            MStar(x: 0.14, y: 0.40, mag: 1.50), MStar(x: 0.30, y: 0.26, mag: 1.35),
            MStar(x: 0.44, y: 0.46, mag: 1.70), MStar(x: 0.58, y: 0.66, mag: 1.35),
            MStar(x: 0.72, y: 0.46, mag: 1.45), MStar(x: 0.86, y: 0.30, mag: 2.00),
            MStar(x: 0.50, y: 0.84, mag: 1.30),
        ],
        edges: [(0, 1), (1, 2), (2, 3), (3, 4), (4, 5), (3, 6)]
    ),
    // 코스믹 펑크 — 번개 지그재그
    "cosmic_funk": MConstel(
        stars: [
            MStar(x: 0.32, y: 0.16, mag: 1.80), MStar(x: 0.58, y: 0.30, mag: 1.35),
            MStar(x: 0.38, y: 0.46, mag: 1.40), MStar(x: 0.64, y: 0.60, mag: 1.45),
            MStar(x: 0.44, y: 0.74, mag: 1.35), MStar(x: 0.70, y: 0.86, mag: 1.95),
        ],
        edges: [(0, 1), (1, 2), (2, 3), (3, 4), (4, 5)]
    ),
    // 잊혀진 은하 — 나선
    "forgotten_galaxy": MConstel(
        stars: [
            MStar(x: 0.50, y: 0.50, mag: 2.10), MStar(x: 0.64, y: 0.46, mag: 1.30),
            MStar(x: 0.70, y: 0.62, mag: 1.35), MStar(x: 0.54, y: 0.74, mag: 1.30),
            MStar(x: 0.34, y: 0.68, mag: 1.40), MStar(x: 0.26, y: 0.44, mag: 1.35),
            MStar(x: 0.42, y: 0.26, mag: 1.45), MStar(x: 0.72, y: 0.24, mag: 1.60),
        ],
        edges: [(0, 1), (1, 2), (2, 3), (3, 4), (4, 5), (5, 6), (6, 7)]
    ),
    // 성운의 정원 — 꽃
    "nebula_garden": MConstel(
        stars: [
            MStar(x: 0.50, y: 0.50, mag: 1.90), MStar(x: 0.50, y: 0.22, mag: 1.40),
            MStar(x: 0.74, y: 0.36, mag: 1.35), MStar(x: 0.74, y: 0.64, mag: 1.40),
            MStar(x: 0.50, y: 0.80, mag: 1.35), MStar(x: 0.26, y: 0.64, mag: 1.40),
            MStar(x: 0.26, y: 0.36, mag: 1.35),
        ],
        edges: [(0, 1), (0, 2), (0, 3), (0, 4), (0, 5), (0, 6)]
    ),
]

/// 원 안쪽 중앙에 그려지는 트랙별 별자리 — 내 다이어리 별자리 보드와 같은 렌더링
/// (선택 플래시 1.7→0.78, 그라데이션 후광, mag 가중 밝기 — Android MusicConstellationBackground 패리티).
private struct MusicConstellationView: View {
    let trackId: String
    let color: Color
    /// 선택할 때마다 바뀌는 키 — 바뀌면 전체가 번쩍 → 0.9s 에 걸쳐 가라앉음.
    let flashKey: Int

    @State private var flashStart = Date()

    var body: some View {
        TimelineView(.animation) { tl in
            Canvas { gc, size in
                guard let con = musicConstellations[trackId] else { return }
                let now = tl.date

                // flash 1.7 → 0.78 (easeOut 0.9s) — Android FastOutSlowIn 근사.
                let e = min(max(now.timeIntervalSince(flashStart) / 0.9, 0), 1)
                let ease = 1 - pow(1 - e, 3)
                let f = 1.7 - (1.7 - 0.78) * ease
                // 트윙클 위상(3.4s 주기 0..2π) — Android InfiniteTransition 동일.
                let t = (now.timeIntervalSinceReferenceDate / 3.4)
                    .truncatingRemainder(dividingBy: 1) * 2 * .pi

                let padX = size.width * 0.13, padY = size.height * 0.10
                let w = size.width - padX * 2, h = size.height - padY * 2
                func pos(_ s: MStar) -> CGPoint { CGPoint(x: padX + CGFloat(s.x) * w, y: padY + CGFloat(s.y) * h) }

                for (a, b) in con.edges {
                    var path = Path()
                    path.move(to: pos(con.stars[a]))
                    path.addLine(to: pos(con.stars[b]))
                    gc.stroke(path, with: .color(color.opacity(min(0.20 * f, 1))), lineWidth: 1.4)
                }
                // ⚠️ CGFloat·Double 혼합 '+' 는 모호성 에러 — Double 로 계산 후 마지막에 CGFloat.
                // ⚠️ 긴 곱셈 한 줄 수식은 Xcode 타입체커가 시간 초과로 빌드 에러 → 부분식으로 분해.
                for s in con.stars {
                    let c = pos(s)
                    let mag = Double(s.mag)
                    let phase = s.x * 11 + s.y * 7
                    let pulse = 0.5 + 0.5 * sin(t + phase)
                    let magN = min(max((mag - 1.0) / 1.2, 0), 1)

                    let magnitudePart = 7.0 + (16.0 * CGFloat(mag))
                    let pulsePart = 0.8 + (0.35 * CGFloat(pulse))
                    let frequencyPart = 0.85 + (0.25 * CGFloat(f))

                    let haloR = magnitudePart * pulsePart * frequencyPart
                    let haloA = (0.08 + 0.34 * pulse) * (0.45 + 0.55 * magN) * f
                    let grad = Gradient(colors: [color.opacity(min(haloA, 1)), .clear])
                    gc.fill(
                        Path(ellipseIn: CGRect(x: c.x - haloR, y: c.y - haloR, width: haloR * 2, height: haloR * 2)),
                        with: .radialGradient(grad, center: c, startRadius: 0, endRadius: haloR)
                    )
                    let coreMagnitudePart = 1.0 + (1.8 * CGFloat(mag))
                    let corePulsePart = 0.88 + (0.2 * CGFloat(pulse))

                    let coreR = coreMagnitudePart * corePulsePart
                    gc.fill(
                        Path(ellipseIn: CGRect(x: c.x - coreR, y: c.y - coreR, width: coreR * 2, height: coreR * 2)),
                        with: .color(.white.opacity(min((0.45 + 0.40 * pulse) * f, 1)))
                    )
                }
            }
        }
        .allowsHitTesting(false)
        .onChange(of: flashKey) { _ in flashStart = Date() }
        .onChange(of: trackId) { _ in flashStart = Date() }
    }
}

// ─── 음악 다이얼(원형 로터리) ──────────────────────────────────────────────────
// 별이 원 둘레에 놓이고, 드래그로 고리를 돌리면 위쪽(topAngle)에 온 트랙이 선택된다.
private struct MusicDial: View {
    let tracks: [MusicCatalog.Track]
    let isUnlocked: (MusicCatalog.Track) -> Bool
    let initialIndex: Int
    let onSelect: (Int) -> Void

    @State private var angleOffset: Double = 0
    @State private var lastDragAngle: Double?
    @State private var didInit = false
    /// 드래그 중 햅틱 눈금 중복 방지 — 마지막으로 딸깍한 트랙 인덱스.
    @State private var tickedIndex = -1
    /// 맷돌음(그라인딩) 전용 — 트랙 하나(step)보다 훨씬 촘촘한 눈금마다 울린다(Android FINE_DIVISIONS 패리티).
    @State private var tickedFine = 0

    private let ringRadius: CGFloat = 124
    private var n: Int { tracks.count }
    private var step: Double { 2 * .pi / Double(n) }
    /// 트랙 한 칸을 몇 등분해서 그라인딩음을 울릴지 — 클수록 더 촘촘하게 "드드드드".
    private let fineDivisions: Double = 5
    private var fineStepAngle: Double { step / fineDivisions }
    private let topAngle: Double = -.pi / 2

    var body: some View {
        GeometryReader { geo in
            let center = CGPoint(x: geo.size.width / 2, y: geo.size.height / 2)
            ZStack {
                Circle()
                    .stroke(Color.white.opacity(0.07), lineWidth: 1.2)
                    .frame(width: ringRadius * 2, height: ringRadius * 2)

                ForEach(tracks.indices, id: \.self) { i in
                    let ang = topAngle + Double(i) * step + angleOffset
                    let closeness = (cos(ang - topAngle) + 1) / 2
                    starItem(i: i, closeness: closeness)
                        .position(
                            x: center.x + CGFloat(cos(ang)) * ringRadius,
                            y: center.y + CGFloat(sin(ang)) * ringRadius
                        )
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { v in
                        let a = atan2(Double(v.location.y - center.y), Double(v.location.x - center.x))
                        if let last = lastDragAngle {
                            var d = a - last
                            if d > .pi { d -= 2 * .pi }
                            if d < -.pi { d += 2 * .pi }
                            angleOffset += d
                            // 눈금(트랙 하나)을 지날 때마다 딸깍(햅틱).
                            let idx = indexAt(angleOffset)
                            if idx != tickedIndex {
                                tickedIndex = idx
                                Haptics.tick()
                            }
                            // 맷돌 그라인딩음 — 실제로 지나간 촘촘한 눈금 수만큼만 호출(가만히 있으면 무음).
                            let fine = Int((-angleOffset / fineStepAngle).rounded())
                            if fine != tickedFine {
                                tickedFine = fine
                                MusicManager.shared.dialTick()
                            }
                        } else {
                            Haptics.prepare()
                        }
                        lastDragAngle = a
                    }
                    .onEnded { _ in
                        lastDragAngle = nil
                        MusicManager.shared.dialRelease()
                        settle()
                    }
            )
            .onAppear {
                if !didInit {
                    angleOffset = -Double(initialIndex) * step
                    tickedFine = Int((-angleOffset / fineStepAngle).rounded())
                    didInit = true
                }
            }
        }
    }

    @ViewBuilder
    private func starItem(i: Int, closeness: Double) -> some View {
        let track = tracks[i]
        let unlocked = isUnlocked(track)
        let col = track.color
        let starSize = CGFloat(16 + 14 * closeness)
        let glowR = CGFloat(18 + 22 * closeness)
        ZStack {
            Circle()
                .fill(RadialGradient(
                    gradient: Gradient(colors: [col.opacity((0.16 + 0.46 * closeness) * (unlocked ? 1 : 0.4)), .clear]),
                    center: .center, startRadius: 1, endRadius: glowR
                ))
                .frame(width: glowR * 2, height: glowR * 2)
            StarShape(type: track.starType)
                .fill(col.opacity(unlocked ? (0.45 + 0.55 * closeness) : 0.30), style: FillStyle(eoFill: true))
                .frame(width: starSize, height: starSize)
            if !unlocked {
                Image(systemName: "lock.fill")
                    .font(.system(size: CGFloat(10 + 5 * closeness)))
                    .foregroundStyle(.white.opacity(0.85))
            }
        }
        .frame(width: 54, height: 54)
        .contentShape(Circle())
        .onTapGesture { bringToTop(i) }
    }

    private func bringToTop(_ i: Int) {
        var t = -Double(i) * step
        while t - angleOffset > .pi { t -= 2 * .pi }
        while angleOffset - t > .pi { t += 2 * .pi }
        withAnimation(.easeInOut(duration: 0.32)) { angleOffset = t }
        onSelect(i)
    }

    /// 현재 회전량에서 위쪽(선택 위치)에 온 트랙 인덱스.
    private func indexAt(_ offset: Double) -> Int {
        let raw = Int((-offset / step).rounded())
        return ((raw % n) + n) % n
    }

    private func settle() {
        let idx = indexAt(angleOffset)
        tickedIndex = idx
        var t = -Double(idx) * step
        while t - angleOffset > .pi { t -= 2 * .pi }
        while angleOffset - t > .pi { t += 2 * .pi }
        withAnimation(.easeInOut(duration: 0.32)) { angleOffset = t }
        onSelect(idx)
    }
}

// ─── 배경음악 선택 화면 ────────────────────────────────────────────────────────
/// Android `MusicScreen` 의 Swift 포팅 — 원형 다이얼 + 중앙 별자리, 잠금/미리듣기/이어듣기.
/// (ProfileScreen 에서 push 되므로 자체 NavigationStack 없음)
struct MusicScreen: View {
    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var store: DiaryStore

    @State private var friendsCount = 0
    @State private var selectedIndex = MusicCatalog.index(of: MusicManager.shared.selectedTrackId)
    @State private var pendingId = MusicManager.shared.selectedTrackId
    @State private var firstChange = true

    private let originalId = MusicManager.shared.selectedTrackId
    private let tracks = MusicCatalog.tracks

    private var mine: [Diary] { store.mine(uid: auth.uid) }
    private var stats: UserStats { Achievements.computeStats(diaries: mine, friendsCount: friendsCount, viewedCount: 0) }
    private var unlocked: Set<String> { Achievements.unlockedIds(stats) }
    private func isUnlocked(_ t: MusicCatalog.Track) -> Bool {
        guard let id = t.unlockAchievementId else { return true }
        return unlocked.contains(id)
    }

    private var selected: MusicCatalog.Track { tracks[selectedIndex] }

    private var subtitle: String {
        if isUnlocked(selected) { return LocaleManager.shared.t(.musicDragHint) }
        // 해금 업적명은 언어 전환에 맞춰 표시(로케일 해석)
        let ach = Achievements.byId(selected.unlockAchievementId)
        let name = ach.flatMap { LocalizedNames.title($0.id, fallback: $0.name) }
            ?? LocaleManager.shared.t(.commonSecret)
        return String(format: LocaleManager.shared.t(.musicLockedHint), name)
    }

    var body: some View {
        ZStack {
            // Android MusicScreen 배경 — mydiary_bg + 검정 0.82 틴트.
            ScreenBackground(name: "mydiary_bg", darken: 0.82)
            VStack(spacing: 14) {
                ZStack {
                    MusicConstellationView(trackId: selected.id, color: selected.color, flashKey: selectedIndex)
                        .frame(width: 186, height: 186)
                    MusicDial(
                        tracks: tracks,
                        isUnlocked: isUnlocked,
                        initialIndex: selectedIndex,
                        onSelect: { selectedIndex = $0 }
                    )
                    .frame(width: 320, height: 320)
                }
                .frame(height: 360)

                // 트랙명은 언어 전환에 맞춰 표시(로케일 해석)
                Text(LocalizedNames.music(selected.id, fallback: selected.displayName))
                    .font(.minSans(20))
                    .foregroundStyle(isUnlocked(selected) ? selected.color : Theme.textSecondary)
                Text(subtitle)
                    .font(.minSans(13))
                    .foregroundStyle(Theme.textSecondary)
                Spacer()
            }
            .padding(.top, 16)
        }
        .navigationTitle(LocaleManager.shared.t(.navMusic))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            guard let uid = auth.uid else { return }
            let snap = try? await FirestoreService.friends(of: uid).getDocuments()
            friendsCount = snap?.documents.count ?? 0
        }
        .onChange(of: selectedIndex) { _ in previewSelected() }
        .onDisappear {
            // 바꿨으면 확정(미리듣기 위치 그대로 이어짐), 안 바꿨으면 현재 재생 무간섭.
            if pendingId != originalId { MusicManager.shared.commitSelectedTrack(pendingId) }
        }
        .firstVisitInfo(key: "music", systemImage: "music.note",
                        title: LocaleManager.shared.t(.onbMusicTitle),
                        message: LocaleManager.shared.t(.onbMusicMsg))
    }

    private func previewSelected() {
        if firstChange { firstChange = false; return }
        let t = tracks[selectedIndex]
        guard isUnlocked(t) else { return } // 잠긴 트랙은 미리듣기/확정 안 함
        pendingId = t.id
        // 다른 음악으로 바꾸면 그 곡의 처음부터 재생.
        MusicManager.shared.playTrack(t.id, at: 0)
    }
}
