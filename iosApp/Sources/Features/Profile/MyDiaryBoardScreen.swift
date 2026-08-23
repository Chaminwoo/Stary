import SwiftUI
import UIKit

// ─── 내 다이어리 — 별자리 배경 + 바나나 다이얼 + 떠다니는 별 보드 ───
// Android `MyDiaryScreen.kt`(DiaryStarsBoard/BananaDial/ConstellationBackground) 이식.
// 별자리 좌표/연결선·다이얼 기하(포물선)·색은 Android 와 동일 값.
// 별: 부유 + 탭(상세) + 드래그 물리(잡아끌면 따라오고 놓으면 탄성 복귀 — Android DiaryStarBox 대응).

/// 정렬 모드 — 최신/인기/거리. (Android DiarySort 대응)
enum DiarySort: CaseIterable {
    case latest, popular, distance

    /// 정렬별 테마색 — 최신=푸른색, 인기=분홍색, 거리=보라색(별자리·다이얼 공용, Android 동일).
    var color: Color {
        switch self {
        case .latest: return Color(hex: 0x7FB7FF)
        case .popular: return Color(hex: 0xFF9CC6)
        case .distance: return Color(hex: 0xB89BFF)
        }
    }

    /// 다이얼 버튼 별 모양(StarStyle 타입) — Android dialStarType 동일.
    var dialStarType: Int {
        switch self {
        case .latest: return 0
        case .popular: return 2
        case .distance: return 4
        }
    }

    var label: L10n {
        switch self {
        case .latest: return .sortLatest
        case .popular: return .sortPopular
        case .distance: return .sortDistance
        }
    }
}

/// 내 다이어리 화면(드로어 "내 다이어리" / 프로필 다이어리 아이콘 진입).
struct MyStarsScreen: View {
    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var location: LocationManager
    @ObservedObject private var locale = LocaleManager.shared

    @State private var sortMode: DiarySort = .latest
    /// 같은 정렬을 다시 골라도 번쩍임/재정렬이 보이도록 하는 토큰(Android sortNonce).
    @State private var sortNonce = 0
    /// false = 떠다니는 별 보드(기본), true = 1열 리스트.
    @State private var listMode = false

    private var mine: [Diary] { store.mine(uid: auth.uid) }

    private var sorted: [Diary] {
        switch sortMode {
        case .latest: return mine.sorted { $0.createdAt > $1.createdAt }
        case .popular: return mine.sorted { $0.likeCount > $1.likeCount }
        case .distance:
            guard let me = location.coordinate else { return mine }
            return mine.sorted {
                Geo.distanceMeters(lat1: me.latitude, lng1: me.longitude, lat2: $0.latitude, lng2: $0.longitude)
                    < Geo.distanceMeters(lat1: me.latitude, lng1: me.longitude, lat2: $1.latitude, lng2: $1.longitude)
            }
        }
    }

    var body: some View {
        ZStack {
            // Android MyDiaryScreen 배경 — mydiary_bg + 검정 0.82 틴트.
            ScreenBackground(name: "mydiary_bg", darken: 0.82)
            ScrollView {
                VStack(spacing: 0) {
                    // 별자리(상단 260 고정) + 바나나 다이얼(박스 360 — 내려간 다이얼까지 터치)
                    ZStack(alignment: .top) {
                        ConstellationBackgroundView(sort: sortMode, flashKey: sortNonce)
                            .frame(maxWidth: .infinity)
                            .frame(height: 260)
                        BananaDialView(selected: sortMode) { s in
                            sortMode = s
                            sortNonce += 1
                            MusicManager.shared.playWind() // 정렬 반영 + 바람 효과음(Android 동일)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 360)
                    }
                    .frame(height: 360)

                    // 정렬·개수 라벨 + 보기 전환 토글(별 보드 ↔ 1열 리스트)
                    ZStack {
                        Text(String(format: locale.t(.mydiarySortCount), locale.t(sortMode.label), sorted.count))
                            .font(.minSans(14))
                            .foregroundStyle(sortMode.color)
                        HStack {
                            Spacer()
                            Button { listMode.toggle() } label: {
                                Image(systemName: listMode ? "sparkles" : "list.bullet")
                                    .font(.system(size: 17))
                                    .foregroundStyle(sortMode.color.opacity(0.95))
                                    .frame(width: 34, height: 34)
                            }
                            .padding(.trailing, 10)
                        }
                    }
                    .padding(.bottom, 4)

                    if sorted.isEmpty {
                        Text(locale.t(.mydiaryEmpty))
                            .font(.minSans(14))
                            .foregroundStyle(Theme.textSecondary)
                            .padding(.vertical, 48)
                    } else if listMode {
                        listColumn
                            .padding(.horizontal, 16)
                    } else {
                        FloatingStarBoard(diaries: sorted, sortNonce: sortNonce)
                            .padding(.horizontal, 12)
                    }

                    Spacer().frame(height: 32)
                }
            }
        }
        .navigationTitle(locale.t(.navMyDiary))
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(for: Diary.self) { DetailScreen(diary: $0) }
        .firstVisitInfo(key: "mydiary", systemImage: "book.fill",
                        title: locale.t(.onbMyDiaryTitle),
                        message: locale.t(.onbMyDiaryMsg))
    }

    /// 1열 리스트 보기 — 별 아이콘 + 제목 + 날짜. (Android DiaryListColumn 대응, 0x66161B22 행 배경)
    private var listColumn: some View {
        VStack(spacing: 8) {
            ForEach(sorted) { d in
                NavigationLink(value: d) {
                    HStack(spacing: 12) {
                        StarView(type: d.starType, colorIndex: d.starColor, size: 24)
                        Text(d.title.isEmpty ? locale.t(.commonUntitled) : d.title)
                            .font(.minSans(15))
                            .foregroundStyle(Color.white.opacity(0.92))
                            .lineLimit(1)
                        Spacer()
                        Text(Self.dateFmt.string(from: d.createdDate))
                            .font(.minSans(12))
                            .foregroundStyle(Theme.textSecondary)
                    }
                    .padding(.horizontal, 14).padding(.vertical, 12)
                    .background(Color(hex: 0x161B22).opacity(0.4), in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private static let dateFmt: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy.MM.dd"
        return f
    }()
}

// ─── 바나나(호) 다이얼 — Android BananaDial 이식 ───
// 3개 별 버튼이 아래로 휜 포물선에 놓이고, 맨 아래(가운데) 항목이 선택된다.
// 좌우 드래그 또는 버튼 탭으로 굴려서 선택. 기하 상수(dp)는 Android 와 동일(pt 1:1).

private let dialHSpacing: CGFloat = 90   // 항목 사이 수평 간격(드래그 감도 겸용)
private let dialCurve: CGFloat = 30      // 곡률(가장자리로 갈수록 위로: k²)
private let dialBottom: CGFloat = 100    // 선택(바닥) 항목 깊이(박스 중앙 기준)

private struct BananaDialView: View {
    let selected: DiarySort
    let onSelect: (DiarySort) -> Void

    private let items = DiarySort.allCases
    @State private var rot: CGFloat
    @State private var dragStartRot: CGFloat?

    init(selected: DiarySort, onSelect: @escaping (DiarySort) -> Void) {
        self.selected = selected
        self.onSelect = onSelect
        _rot = State(initialValue: CGFloat(DiarySort.allCases.firstIndex(of: selected) ?? 0))
    }

    var body: some View {
        let n = items.count
        ZStack {
            // 포물선 가이드 곡선 — 별들이 놓인 궤적을 따라 함께 이동.
            Canvas { ctx, size in
                let cx = size.width / 2
                let cy = size.height / 2
                func pt(_ k: CGFloat) -> CGPoint {
                    CGPoint(x: cx + dialHSpacing * k, y: cy + dialBottom - dialCurve * k * k)
                }
                var path = Path()
                let kL = (0 - rot) - 0.25
                let kR = (CGFloat(n - 1) - rot) + 0.25
                let steps = 48
                for s in 0...steps {
                    let k = kL + (kR - kL) * CGFloat(s) / CGFloat(steps)
                    let p = pt(k)
                    if s == 0 { path.move(to: p) } else { path.addLine(to: p) }
                }
                ctx.stroke(path, with: .color(.white.opacity(0.10)), lineWidth: 1.5)
            }
            .allowsHitTesting(false)

            ForEach(Array(items.enumerated()), id: \.offset) { i, sort in
                let k = CGFloat(i) - rot
                // 바닥에 가까울수록 1 → 별이 커지고 후광이 강해짐(Android closeness).
                let closeness = 1 - min(abs(k), 1)
                let col = sort.color
                Button {
                    withAnimation(.easeInOut(duration: 0.3)) { rot = CGFloat(i) }
                    onSelect(sort) // 같은 항목 재선택도 항상 반영(Android 동일)
                } label: {
                    ZStack {
                        Circle()
                            .fill(RadialGradient(
                                colors: [col.opacity(0.16 + 0.46 * closeness), .clear],
                                center: .center, startRadius: 0,
                                endRadius: (22 + 24 * closeness) / 2
                            ))
                            .frame(width: 22 + 24 * closeness, height: 22 + 24 * closeness)
                        TintedStar(type: sort.dialStarType,
                                   color: col.opacity(0.45 + 0.55 * closeness),
                                   size: 15 + 13 * closeness)
                    }
                    .frame(width: 54, height: 54)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .offset(x: dialHSpacing * k, y: dialBottom - dialCurve * k * k)
            }
        }
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 5)
                .onChanged { v in
                    if dragStartRot == nil { dragStartRot = rot }
                    // 미는 방향으로 별들이 따라오도록 부호 반전(Android 동일).
                    let base = dragStartRot ?? rot
                    rot = min(max(base - v.translation.width / dialHSpacing, 0), CGFloat(items.count - 1))
                }
                .onEnded { _ in
                    dragStartRot = nil
                    let idx = Int(rot.rounded())
                    withAnimation(.easeInOut(duration: 0.3)) { rot = CGFloat(idx) }
                    onSelect(items[idx])
                }
        )
        .onChange(of: selected) { s in
            // 외부 요인으로 selected 가 바뀐 경우 회전 보정(다이얼 조작분은 이미 애니메이션 중).
            let t = CGFloat(items.firstIndex(of: s) ?? 0)
            if dragStartRot == nil, rot.rounded() != t {
                withAnimation(.easeInOut(duration: 0.3)) { rot = t }
            }
        }
    }
}

/// 팔레트 밖 색으로 그리는 크리스탈 별(다이얼 전용 — StarView 는 colorIndex 만 받는다).
private struct TintedStar: View {
    let type: Int
    let color: Color
    let size: CGFloat

    var body: some View {
        Canvas { ctx, sz in
            ctx.withCGContext { cg in
                StarCrystal.draw(in: cg, type: type, colors: [UIColor(color)],
                                 rect: CGRect(origin: .zero, size: sz))
            }
        }
        .frame(width: size, height: size)
    }
}

// ─── 별자리 배경 — Android ConstellationBackground 이식(좌표/수식 동일) ───

private struct CStar {
    let x: CGFloat
    let y: CGFloat
    let mag: CGFloat
}

private struct Constel {
    let stars: [CStar]
    let edges: [(Int, Int)]
}

/// 정렬별 별자리 문양 — Android CONSTELLATIONS 와 동일 좌표(레퍼런스 픽셀 분석값).
private let constellations: [DiarySort: Constel] = [
    .latest: Constel(
        stars: [
            CStar(x: 0.496, y: 0.187, mag: 1.49), CStar(x: 0.622, y: 0.319, mag: 1.10),
            CStar(x: 0.572, y: 0.344, mag: 1.14), CStar(x: 0.373, y: 0.364, mag: 1.52),
            CStar(x: 0.214, y: 0.407, mag: 2.20), CStar(x: 0.853, y: 0.406, mag: 1.12),
            CStar(x: 0.520, y: 0.455, mag: 1.10), CStar(x: 0.587, y: 0.456, mag: 1.10),
            CStar(x: 0.734, y: 0.480, mag: 1.10), CStar(x: 0.633, y: 0.493, mag: 1.14),
            CStar(x: 0.531, y: 0.535, mag: 1.16), CStar(x: 0.734, y: 0.609, mag: 1.53),
            CStar(x: 0.910, y: 0.644, mag: 1.12), CStar(x: 0.799, y: 0.660, mag: 1.14),
            CStar(x: 0.677, y: 0.713, mag: 1.16), CStar(x: 0.362, y: 0.786, mag: 1.08),
            CStar(x: 0.211, y: 0.791, mag: 1.10), CStar(x: 0.684, y: 0.794, mag: 1.03),
            CStar(x: 0.342, y: 0.885, mag: 1.14),
        ],
        edges: [
            (0, 2), (1, 2), (2, 7), (3, 4), (3, 6), (3, 9), (4, 16), (5, 8), (6, 7),
            (6, 9), (6, 10), (7, 9), (8, 9), (8, 10), (8, 11), (8, 17), (9, 10),
            (11, 13), (11, 14), (12, 13), (14, 17), (15, 16), (16, 18),
        ]
    ),
    .popular: Constel(
        stars: [
            CStar(x: 0.808, y: 0.111, mag: 1.76), CStar(x: 0.261, y: 0.413, mag: 2.20),
            CStar(x: 0.694, y: 0.472, mag: 1.41), CStar(x: 0.229, y: 0.521, mag: 1.15),
            CStar(x: 0.168, y: 0.544, mag: 1.18), CStar(x: 0.152, y: 0.590, mag: 1.41),
            CStar(x: 0.416, y: 0.701, mag: 1.39), CStar(x: 0.590, y: 0.699, mag: 1.15),
            CStar(x: 0.770, y: 0.796, mag: 1.28), CStar(x: 0.370, y: 0.826, mag: 2.16),
        ],
        edges: [(0, 1), (1, 3), (1, 5), (3, 4), (3, 5), (4, 5), (5, 9), (6, 7), (6, 9), (7, 8)]
    ),
    .distance: Constel(
        stars: [
            CStar(x: 0.840, y: 0.122, mag: 1.64), CStar(x: 0.859, y: 0.232, mag: 1.61),
            CStar(x: 0.661, y: 0.333, mag: 1.66), CStar(x: 0.874, y: 0.359, mag: 1.64),
            CStar(x: 0.622, y: 0.387, mag: 1.59), CStar(x: 0.515, y: 0.605, mag: 2.02),
            CStar(x: 0.269, y: 0.706, mag: 1.39), CStar(x: 0.220, y: 0.770, mag: 1.36),
            CStar(x: 0.195, y: 0.815, mag: 1.70), CStar(x: 0.504, y: 0.866, mag: 2.20),
            CStar(x: 0.419, y: 0.907, mag: 1.99), CStar(x: 0.254, y: 0.909, mag: 1.66),
        ],
        edges: [(0, 2), (0, 4), (1, 2), (2, 3), (2, 4), (2, 5), (4, 5), (5, 9),
                (6, 7), (6, 8), (7, 8), (8, 11), (9, 10), (10, 11)]
    ),
]

private struct ConstellationBackgroundView: View {
    let sort: DiarySort
    let flashKey: Int

    /// 번쩍임 시작 시각 — 선택할 때마다(같은 정렬 재선택 포함) 전체가 번쩍 → 0.9s 에 걸쳐 가라앉음.
    @State private var flashStart = Date()

    var body: some View {
        TimelineView(.animation) { tl in
            Canvas { ctx, size in
                guard let constel = constellations[sort] else { return }
                let color = sort.color
                let now = tl.date

                // flash 1.7 → 0.78 (easeOut 0.9s) — Android FastOutSlowIn 근사.
                let e = min(max(now.timeIntervalSince(flashStart) / 0.9, 0), 1)
                let ease = 1 - pow(1 - e, 3)
                let f = 1.7 - (1.7 - 0.78) * ease
                // 트윙클 위상(3.4s 주기 0..2π) — Android InfiniteTransition 동일.
                let t = (now.timeIntervalSinceReferenceDate / 3.4)
                    .truncatingRemainder(dividingBy: 1) * 2 * .pi

                let padX = size.width * 0.13
                let padY = size.height * 0.10
                let w = size.width - padX * 2
                let h = size.height - padY * 2
                func pos(_ s: CStar) -> CGPoint { CGPoint(x: padX + s.x * w, y: padY + s.y * h) }

                // 연결선
                for (a, b) in constel.edges {
                    var line = Path()
                    line.move(to: pos(constel.stars[a]))
                    line.addLine(to: pos(constel.stars[b]))
                    ctx.stroke(line, with: .color(color.opacity(min(0.20 * f, 1))), lineWidth: 1.4)
                }
                // 별 + 후광(펄스) — mag 클수록 크고 밝게(Android 수식 동일).
                // CGFloat(mag)·Double(pulse/f) 혼합은 '+' 모호성 에러 → Double 로 통일 후 마지막에 CGFloat.
                for s in constel.stars {
                    let c = pos(s)
                    let mag = Double(s.mag)
                    let phase = Double(s.x * 11 + s.y * 7)
                    let pulse = 0.5 + 0.5 * sin(t + phase)
                    let magN = min(max((mag - 1.0) / 1.2, 0), 1)

                    let magnitudePart = 7.0 + (16.0 * CGFloat(mag))
                    let pulsePart = 0.8 + (0.35 * CGFloat(pulse))
                    let frequencyPart = 0.85 + (0.25 * CGFloat(f))

                    let haloR = magnitudePart * pulsePart * frequencyPart
                    let haloA = (0.08 + 0.34 * pulse) * (0.45 + 0.55 * magN) * f
                    let grad = Gradient(colors: [color.opacity(min(haloA, 1)), .clear])
                    ctx.fill(
                        Path(ellipseIn: CGRect(x: c.x - haloR, y: c.y - haloR,
                                               width: haloR * 2, height: haloR * 2)),
                        with: .radialGradient(grad, center: c, startRadius: 0, endRadius: haloR)
                    )
                    let coreMagnitudePart = 1.0 + (1.8 * CGFloat(mag))
                    let corePulsePart = 0.88 + (0.2 * CGFloat(pulse))

                    let coreR = coreMagnitudePart * corePulsePart
                    ctx.fill(
                        Path(ellipseIn: CGRect(x: c.x - coreR, y: c.y - coreR,
                                               width: coreR * 2, height: coreR * 2)),
                        with: .color(.white.opacity(min((0.45 + 0.40 * pulse) * f, 1)))
                    )
                }
            }
        }
        .allowsHitTesting(false)
        .onChange(of: flashKey) { _ in flashStart = Date() }
        .onChange(of: sort) { _ in flashStart = Date() }
    }
}

// ─── 떠다니는 별 보드(간이) — Android DiaryStarBox 의 시각 대응 ───
// 결정론적 배치(id 해시 지터) + 개별 위상 부유 + 탭 → 상세 + 드래그 물리(잡기·관성·탄성 복귀).

private struct FloatingStarBoard: View {
    let diaries: [Diary]
    let sortNonce: Int

    private let columns = 4
    private let rowHeight: CGFloat = 96

    var body: some View {
        let rows = max((diaries.count + columns - 1) / columns, 1)
        let boardHeight = CGFloat(rows) * rowHeight + 24
        return GeometryReader { geo in
            ZStack(alignment: .topLeading) {
                ForEach(Array(diaries.enumerated()), id: \.element.id) { idx, d in
                    let seed = Self.hash01(d.id ?? "\(idx)")
                    let seed2 = Self.hash01((d.id ?? "") + "y")
                    let col = idx % columns
                    let row = idx / columns
                    let x = geo.size.width * ((CGFloat(col) + 0.5) / CGFloat(columns))
                        + (seed - 0.5) * 28
                    let y = CGFloat(row) * rowHeight + rowHeight / 2
                        + (seed2 - 0.5) * 32
                    // 정렬 순위가 높을수록 큰 별(1위 ≈ 34pt → 최소 18pt).
                    let size = max(34 - CGFloat(idx) * 1.4, 18)

                    NavigationLink(value: d) {
                        FloatingStarItem(diary: d, size: size,
                                         duration: 2.4 + Double(seed) * 1.4)
                    }
                    .buttonStyle(.plain)
                    .position(x: x, y: y)
                }
            }
        }
        .frame(height: boardHeight)
        // 재정렬 시 배치가 새 순서로 다시 그려지도록(부유 위상도 리셋).
        .id(sortNonce)
    }

    /// 0..1 결정론적 해시(id → 배치 지터). 리컴포지션마다 흔들리지 않는다.
    private static func hash01(_ s: String) -> CGFloat {
        var h: UInt32 = 2166136261
        for b in s.utf8 {
            h = (h ^ UInt32(b)) &* 16777619
        }
        return CGFloat(h % 1000) / 1000
    }
}

/// 부유하는 별 1개 — 개별 주기의 상하 float + **드래그 물리**(잡아끌면 따라오고, 놓으면 탄성으로 제자리 복귀).
/// (Android DiaryStarBox 드래그 대응. 탭은 상위 NavigationLink 가 상세로 — 10pt 미만 이동이면 드래그 미발동.)
private struct FloatingStarItem: View {
    let diary: Diary
    let size: CGFloat
    let duration: Double

    @State private var up = false
    @State private var drag: CGSize = .zero
    @State private var dragging = false

    var body: some View {
        StarView(type: diary.starType, colorIndex: diary.starColor, size: size)
            .frame(width: 54, height: 54)
            .contentShape(Rectangle())
            .overlay {
                if dragging {
                    Text(diary.title.isEmpty ? LocaleManager.shared.t(.commonUntitled) : diary.title)
                        .font(.minSans(11))
                        .foregroundStyle(.white)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(Color.black.opacity(0.75), in: RoundedRectangle(cornerRadius: 6))
                        .frame(maxWidth: 80)
                        .offset(y: 36) // star center +27(half frame) +9(gap)
                }
            }
            .scaleEffect(dragging ? 1.18 : 1)      // 잡는 순간 살짝 커져 "들어올린" 느낌
            .offset(y: up ? -4 : 4)
            .offset(drag)
            .zIndex(dragging ? 1 : 0)
            .animation(.easeInOut(duration: duration).repeatForever(autoreverses: true), value: up)
            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: dragging)
            .onAppear { up = true }
            .simultaneousGesture(
                DragGesture(minimumDistance: 10)
                    .onChanged { v in
                        dragging = true
                        drag = v.translation
                    }
                    .onEnded { v in
                        dragging = false
                        // 놓은 방향으로 관성만큼 살짝 더 밀렸다가, 낮은 damping 스프링으로 오버슈트하며 제자리 복귀.
                        let fling = CGSize(
                            width: v.translation.width + (v.predictedEndTranslation.width - v.translation.width) * 0.18,
                            height: v.translation.height + (v.predictedEndTranslation.height - v.translation.height) * 0.18
                        )
                        drag = fling
                        withAnimation(.interpolatingSpring(stiffness: 140, damping: 8)) {
                            drag = .zero
                        }
                    }
            )
    }
}
