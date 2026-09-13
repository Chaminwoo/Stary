import FirebaseFirestore
import SwiftUI
import UIKit

/// 공유 카드 편집 화면 — Android `feature/diary/screen/ShareCardEditor.kt` 패리티(전체 화면).
///
/// 미리보기에서 **별(+지도 무대)·제목·위치·날짜·추가 별을 각각 드래그**해 배치하고, 내 다이어리의 별을
/// 가져와 장식으로 얹는다(개별 크기 조절/삭제). 자산(동네 이름·지역 지도)은 1회 로드 후 재사용.
///
/// ⚠️ 렉 방지(Android 와 같은 설계): 1080×1920 렌더는 무겁다 — 드래그 **중엔** 재렌더를 건너뛰고
///    점선 링만 움직이며, 손을 떼는 순간에만 실제 카드에 반영한다. 렌더는 백그라운드(Task.detached).
struct ShareCardEditorView: View {
    let diary: Diary
    /// 내 별 가져오기 조회용(sheet/fullScreenCover 로 뜨는 화면이라 환경 객체 대신 값으로 받는다).
    let myUid: String?

    @Environment(\.dismiss) private var dismiss
    @State private var assets: ShareCardAssets?
    @State private var options: ShareCardOptions
    @State private var preview: UIImage?
    @State private var busy = false
    @State private var selectedExtra: Int?
    @State private var showPicker = false
    /// 지금 드래그 중인 요소 — 드래그 동안은 무거운 재렌더를 건너뛰고 이 값으로 점선 링만 옮긴다.
    @State private var liveDrag: ShareDragTarget?
    /// 직전 드래그 이동량(누적 translation 에서 델타를 뽑기 위함).
    @State private var lastTranslation: CGSize = .zero
    /// 내 다이어리의 별 스타일(모양, 색) — 피커 최초 오픈 시 1회 로드. nil = 로딩 중.
    @State private var myStars: [ShareStarStyle]?

    /// 새 추가 별의 기본 배치 위치(겹치지 않게 순환) — Android EXTRA_STAR_PRESETS.
    private static let extraPresets: [(CGFloat, CGFloat)] = [
        (0.30, 0.22), (0.72, 0.30), (0.24, 0.52), (0.76, 0.58), (0.50, 0.18), (0.34, 0.68),
    ]
    /// 미리보기 높이(Android 420dp) — 폭은 카드 비율(1080:1920)로.
    private let previewHeight: CGFloat = 420
    private var previewWidth: CGFloat { previewHeight * ShareCard.width / ShareCard.height }

    private var accent: Color { StarStyle.color(diary.starColor) }

    init(diary: Diary, myUid: String?) {
        self.diary = diary
        self.myUid = myUid
        _options = State(initialValue: ShareCardOptions(title: diary.title))
    }

    /// 재렌더 트리거 — 옵션/자산 준비/드래그 여부가 바뀔 때.
    private struct RenderKey: Equatable {
        let options: ShareCardOptions
        let hasAssets: Bool
        let dragging: Bool
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header
                previewArea
                Text(LocaleManager.shared.t(.shareEditHint))
                    .font(.minSans(12))
                    .foregroundStyle(Color(hex: 0x8A93A6))
                    .frame(maxWidth: .infinity)
                    .padding(.top, 6)
                Spacer().frame(height: 14)
                titleField
                Spacer().frame(height: 12)
                HStack(spacing: 8) {
                    togglePill(LocaleManager.shared.t(.shareEditShowMap), active: options.showMap) { options.showMap.toggle() }
                    togglePill(LocaleManager.shared.t(.shareEditShowLocation), active: options.showLocation) { options.showLocation.toggle() }
                    togglePill(LocaleManager.shared.t(.shareEditShowDate), active: options.showDate) { options.showDate.toggle() }
                }
                Spacer().frame(height: 8)
                importStarsPill
                Spacer().frame(height: 10)
                starSizeRow
                selectedExtraRow
                Spacer().frame(height: 14)
                shareButtons
                Spacer().frame(height: 18)
            }
            .padding(.horizontal, 18)
        }
        .background(Color(hex: 0x090D16).opacity(0.97).ignoresSafeArea())
        .interactiveDismissDisabled(busy)
        .task {
            let lang = LocaleManager.shared.effectiveLanguage
            assets = await ShareCard.prepareAssets(diary: diary, locale: Locale(identifier: lang))
        }
        .task(id: RenderKey(options: options, hasAssets: assets != nil, dragging: liveDrag != nil)) {
            guard let a = assets, liveDrag == nil else { return }
            try? await Task.sleep(nanoseconds: 60_000_000) // 연속 변경 스로틀(재시작 시 직전 대기 취소)
            guard !Task.isCancelled else { return }
            let d = diary, o = options
            let untitled = L10n.shareCardUntitled.value(for: LocaleManager.shared.effectiveLanguage)
            let image = await Task.detached(priority: .userInitiated) {
                ShareCard.render(diary: d, assets: a, options: o, untitled: untitled)
            }.value
            guard !Task.isCancelled else { return }
            preview = image
        }
        .sheet(isPresented: $showPicker) { starPicker }
    }

    // MARK: - 헤더 / 미리보기

    private var header: some View {
        HStack {
            Text(LocaleManager.shared.t(.shareEditTitle))
                .font(.minSans(17, .semibold))
                .foregroundStyle(Theme.textPrimary)
            Spacer()
            Button { if !busy { dismiss() } } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
            }
        }
    }

    private var previewArea: some View {
        ZStack {
            if let preview {
                ZStack {
                    Image(uiImage: preview)
                        .resizable()
                        .frame(width: previewWidth, height: previewHeight)
                    activeRing
                }
                .frame(width: previewWidth, height: previewHeight)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(accent.opacity(0.35), lineWidth: 1))
                .contentShape(Rectangle())
                // 탭 = 추가 별 선택(다른 곳 탭하면 해제), 드래그 = 시작 지점 최근접 요소 이동.
                .gesture(SpatialTapGesture().onEnded { v in
                    let t = ShareDragTarget.hit(options, v.location.x / previewWidth, v.location.y / previewHeight)
                    if case .extra(let i) = t { selectedExtra = i } else { selectedExtra = nil }
                })
                .simultaneousGesture(dragGesture)
            } else {
                StarLoadingView(size: 30)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: previewHeight)
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 3)
            .onChanged { v in
                if liveDrag == nil {
                    let t = ShareDragTarget.hit(options, v.startLocation.x / previewWidth,
                                                v.startLocation.y / previewHeight)
                    liveDrag = t
                    lastTranslation = .zero
                    if case .extra(let i) = t { selectedExtra = i }
                }
                guard let target = liveDrag else { return }
                let dx = (v.translation.width - lastTranslation.width) / previewWidth
                let dy = (v.translation.height - lastTranslation.height) / previewHeight
                lastTranslation = v.translation
                options = target.move(options, dx: dx, dy: dy)
            }
            .onEnded { _ in
                liveDrag = nil
                lastTranslation = .zero
            }
    }

    /// 드래그 중인 요소(실시간) 또는 선택된 추가 별 — 점선 링.
    @ViewBuilder
    private var activeRing: some View {
        let target = liveDrag ?? selectedExtra.map { ShareDragTarget.extra($0) }
        if let target, let frac = target.frac(options) {
            let radius: CGFloat = {
                if case .extra(let i) = target, options.extraStars.indices.contains(i) {
                    return min(max(26 * options.extraStars[i].scale, 14), 64)
                }
                return 44 // 제목/위치/날짜/무대는 크기가 제각각이라 고정 링(Android 동일).
            }()
            Circle()
                .stroke(Color.white.opacity(0.75), style: StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                .frame(width: radius * 2, height: radius * 2)
                .position(x: previewWidth * frac.x, y: previewHeight * frac.y)
                .allowsHitTesting(false)
        }
    }

    // MARK: - 입력 / 토글 / 슬라이더

    private var titleField: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(LocaleManager.shared.t(.shareEditFieldTitle))
                .font(.minSans(12))
                .foregroundStyle(Color(hex: 0x8A93A6))
            TextField("", text: Binding(
                get: { options.title ?? "" },
                set: { options.title = String($0.prefix(AppConfig.diaryTitleMaxLen)) }
            ))
            .font(.minSans(15))
            .foregroundStyle(Theme.textPrimary)
            .padding(12)
            .background(Color.white.opacity(0.04), in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color.white.opacity(0.15), lineWidth: 1))
        }
    }

    private func togglePill(_ label: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.minSans(12))
                .foregroundStyle(active ? accent : Color(hex: 0x8A93A6))
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(active ? accent.opacity(0.16) : Color.white.opacity(0.05), in: Capsule())
                .overlay(Capsule().strokeBorder(active ? accent : Color.white.opacity(0.15),
                                                lineWidth: active ? 1.5 : 1))
        }
        .buttonStyle(.plain)
    }

    private var importStarsPill: some View {
        Button { showPicker = true } label: {
            HStack(spacing: 6) {
                Image(systemName: "sparkles").font(.system(size: 13))
                Text(LocaleManager.shared.t(.shareEditImportStars)).font(.minSans(12))
            }
            .foregroundStyle(accent)
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(accent.opacity(0.14), in: Capsule())
            .overlay(Capsule().strokeBorder(accent.opacity(0.55), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var starSizeRow: some View {
        HStack(spacing: 12) {
            Text(LocaleManager.shared.t(.shareEditStarSize))
                .font(.minSans(12))
                .foregroundStyle(Color(hex: 0x8A93A6))
            Slider(value: Binding(get: { Double(options.starScale) },
                                  set: { options.starScale = CGFloat($0) }),
                   in: 0.25...2.2)
                .tint(accent)
        }
    }

    @ViewBuilder
    private var selectedExtraRow: some View {
        if let idx = selectedExtra, options.extraStars.indices.contains(idx) {
            let star = options.extraStars[idx]
            HStack(spacing: 8) {
                StarView(type: star.type, colorIndex: star.colorIndex, size: 18, glow: false)
                Text(LocaleManager.shared.t(.shareEditExtraStarSize))
                    .font(.minSans(12))
                    .foregroundStyle(Color(hex: 0x8A93A6))
                Slider(value: Binding(
                    get: { Double(options.extraStars.indices.contains(idx) ? options.extraStars[idx].scale : 0.6) },
                    set: { v in
                        guard options.extraStars.indices.contains(idx) else { return }
                        options.extraStars[idx].scale = CGFloat(v)
                    }), in: 0.25...2.2)
                    .tint(accent)
                Button {
                    options.extraStars.remove(at: idx)
                    selectedExtra = nil
                } label: {
                    Image(systemName: "trash")
                        .font(.system(size: 16))
                        .foregroundStyle(Color(hex: 0xFF6B6B))
                        .frame(width: 40, height: 40)
                }
                .accessibilityLabel(LocaleManager.shared.t(.commonDelete))
            }
        }
    }

    // MARK: - 공유 실행

    private var shareButtons: some View {
        HStack(spacing: 10) {
            Button {
                share(toStory: true)
            } label: {
                ZStack {
                    if busy {
                        StarLoadingView(size: 18)
                    } else {
                        Text(LocaleManager.shared.t(.shareToStory)).font(.minSans(13, .light))
                    }
                }
                .foregroundStyle(Color(hex: 0x0B0F18))
                .frame(maxWidth: .infinity).frame(height: 48)
                .background(accent, in: RoundedRectangle(cornerRadius: 13))
            }
            Button {
                share(toStory: false)
            } label: {
                Text(LocaleManager.shared.t(.shareAsImage))
                    .font(.minSans(13, .light))
                    .foregroundStyle(Theme.textPrimary)
                    .frame(maxWidth: .infinity).frame(height: 48)
                    .background(Color.white.opacity(0.10), in: RoundedRectangle(cornerRadius: 13))
            }
        }
        .buttonStyle(.plain)
        .disabled(busy || assets == nil)
    }

    /// 최종 카드 렌더 → 인스타 스토리(없으면 일반 공유로 폴백) 또는 일반 공유 시트.
    private func share(toStory: Bool) {
        guard !busy, let a = assets, let diaryId = diary.id, !diaryId.isEmpty else {
            if diary.id == nil { GlobalToast.shared.show(LocaleManager.shared.t(.shareFailed)) }
            return
        }
        busy = true
        let d = diary, o = options
        let untitled = L10n.shareCardUntitled.value(for: LocaleManager.shared.effectiveLanguage)
        Task {
            let image = await Task.detached(priority: .userInitiated) {
                ShareCard.render(diary: d, assets: a, options: o, untitled: untitled)
            }.value
            if toStory, ShareCard.shareToInstagramStory(image, diaryId: diaryId) {
                busy = false
                dismiss()
            } else {
                // 일반 공유 시트 — 편집기 위에 띄우고, 시트가 닫히면 편집기도 닫는다(Android onDismiss 순서).
                ShareCard.shareAsImage(image, diaryId: diaryId) {
                    busy = false
                    dismiss()
                }
            }
        }
    }

    // MARK: - 내 별 피커

    private var starPicker: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(LocaleManager.shared.t(.shareEditPickStar))
                .font(.minSans(15, .light))
                .foregroundStyle(Theme.textPrimary)
            if let stars = myStars {
                if stars.isEmpty {
                    Text(LocaleManager.shared.t(.shareEditNoStars))
                        .font(.minSans(13))
                        .foregroundStyle(Color(hex: 0x8A93A6))
                        .padding(.vertical, 20)
                } else {
                    ScrollView {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 56), spacing: 6)], spacing: 6) {
                            ForEach(stars) { s in
                                Button { addExtraStar(s) } label: {
                                    StarView(type: s.type, colorIndex: s.colorIndex, size: 36)
                                        .frame(width: 56, height: 56)
                                        .background(Color.white.opacity(0.05), in: RoundedRectangle(cornerRadius: 12))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            } else {
                StarLoadingView(size: 24)
                    .frame(maxWidth: .infinity).frame(height: 90)
            }
            Spacer(minLength: 0)
        }
        .padding(18)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color(hex: 0x121826).ignoresSafeArea())
        .presentationDetents([.medium])
        .task { await loadMyStars() }
    }

    private func addExtraStar(_ s: ShareStarStyle) {
        let p = Self.extraPresets[options.extraStars.count % Self.extraPresets.count]
        let newIndex = options.extraStars.count
        options.extraStars.append(ShareExtraStar(type: s.type, colorIndex: s.colorIndex, xFrac: p.0, yFrac: p.1))
        selectedExtra = newIndex
        showPicker = false
    }

    /// 내 다이어리의 별 스타일(최신순, 모양×색 중복 제거) — Android observeMyDiaries().first() 대응.
    private func loadMyStars() async {
        guard myStars == nil else { return }
        guard let uid = myUid, !uid.isEmpty else { myStars = []; return }
        let snap = try? await FirestoreService.diaries.whereField("userId", isEqualTo: uid).getDocuments()
        let diaries = (snap?.documents ?? []).compactMap { try? $0.data(as: Diary.self) }
            .sorted { $0.createdAt > $1.createdAt }
        var seen = Set<ShareStarStyle>()
        var out: [ShareStarStyle] = []
        for d in diaries {
            let s = ShareStarStyle(type: d.starType, colorIndex: d.starColor)
            if seen.insert(s).inserted { out.append(s) }
        }
        myStars = out
    }
}

/// 별 스타일(모양, 색) — 피커 항목.
struct ShareStarStyle: Hashable, Identifiable {
    let type: Int
    let colorIndex: Int
    var id: String { "\(type)_\(colorIndex)" }
}

/// 미리보기 드래그 대상 — Android `DragTarget`(무대/제목/위치/날짜/추가 별).
enum ShareDragTarget: Equatable {
    case stage, title, location, date
    case extra(Int)

    /// 드래그/탭 지점(정규화 좌표)에서 임계 반경 안 최근접 요소, 없으면 무대 — Android hitTarget 과 같은 반경.
    static func hit(_ o: ShareCardOptions, _ fx: CGFloat, _ fy: CGFloat) -> ShareDragTarget {
        var best: ShareDragTarget = .stage
        var bestD = CGFloat.greatestFiniteMagnitude
        func consider(_ t: ShareDragTarget, _ x: CGFloat, _ y: CGFloat, _ threshold: CGFloat) {
            let d = (fx - x) * (fx - x) + (fy - y) * (fy - y)
            if d < bestD && d <= threshold * threshold {
                bestD = d
                best = t
            }
        }
        for (i, s) in o.extraStars.enumerated() { consider(.extra(i), s.xFrac, s.yFrac, 0.09) }
        consider(.title, o.titleXFrac, o.titleYFrac, 0.11)
        if o.showLocation { consider(.location, o.locationXFrac, o.locationYFrac, 0.09) }
        if o.showDate { consider(.date, o.dateXFrac, o.dateYFrac, 0.08) }
        consider(.stage, o.stageXFrac, o.stageYFrac, 0.30)
        return best
    }

    /// 요소의 현재 상대 좌표(0..1).
    func frac(_ o: ShareCardOptions) -> CGPoint? {
        switch self {
        case .stage: return CGPoint(x: o.stageXFrac, y: o.stageYFrac)
        case .title: return CGPoint(x: o.titleXFrac, y: o.titleYFrac)
        case .location: return CGPoint(x: o.locationXFrac, y: o.locationYFrac)
        case .date: return CGPoint(x: o.dateXFrac, y: o.dateYFrac)
        case .extra(let i):
            guard o.extraStars.indices.contains(i) else { return nil }
            return CGPoint(x: o.extraStars[i].xFrac, y: o.extraStars[i].yFrac)
        }
    }

    /// 드래그 델타(정규화)를 반영 — 렌더 클램프와 같은 범위(Android moveTarget).
    func move(_ o: ShareCardOptions, dx: CGFloat, dy: CGFloat) -> ShareCardOptions {
        func c(_ v: CGFloat, _ lo: CGFloat, _ hi: CGFloat) -> CGFloat { min(max(v, lo), hi) }
        var n = o
        switch self {
        case .stage:
            n.stageXFrac = c(o.stageXFrac + dx, 0.08, 0.92)
            n.stageYFrac = c(o.stageYFrac + dy, 0.08, 0.90)
        case .title:
            n.titleXFrac = c(o.titleXFrac + dx, 0.08, 0.92)
            n.titleYFrac = c(o.titleYFrac + dy, 0.05, 0.95)
        case .location:
            n.locationXFrac = c(o.locationXFrac + dx, 0.08, 0.92)
            n.locationYFrac = c(o.locationYFrac + dy, 0.05, 0.96)
        case .date:
            n.dateXFrac = c(o.dateXFrac + dx, 0.08, 0.92)
            n.dateYFrac = c(o.dateYFrac + dy, 0.05, 0.97)
        case .extra(let i):
            guard n.extraStars.indices.contains(i) else { return o }
            n.extraStars[i].xFrac = c(o.extraStars[i].xFrac + dx, 0.04, 0.96)
            n.extraStars[i].yFrac = c(o.extraStars[i].yFrac + dy, 0.03, 0.97)
        }
        return n
    }
}
