import ImageIO
import SwiftUI
import UIKit

/// 별 상세 — 본문/작성자 + 좋아요·댓글. 가까이 있으면 본문 열람, 멀면 거리 게이팅.
struct DetailScreen: View {
    let diary: Diary
    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var location: LocationManager
    @StateObject private var vm: DetailViewModel
    @ObservedObject private var focus = MapFocusStore.shared
    // 작성자/댓글 이름·프사를 users/{uid} 의 "현재" 값으로 표시(스냅샷 아님) — Android UserDirectory 패리티.
    @ObservedObject private var directory = UserDirectory.shared
    @ObservedObject private var unlockStore = DiaryUnlockStore.shared
    @ObservedObject private var ads = AdsManager.shared
    /// 광고 결과·댓글 100m 안내 토스트(Android StaryToast 대응 — FriendsScreen 과 같은 ToastView 패턴).
    @State private var toast: String?
    @State private var didCountView = false
    @State private var commentText = ""
    @State private var profileTarget: ProfileTarget?
    @State private var blockedIds: Set<String> = []
    @State private var showReportDialog = false
    @State private var showReportedConfirm = false
    @State private var showLoginRequired = false
    /// 사진/움짤/영상 전체화면 보기.
    @State private var showFullMedia = false
    /// 공유 카드 편집 화면.
    @State private var showShareEditor = false
    // 내 글 수정/삭제(Android 인라인 수정·삭제 대응). 수정 결과는 로컬 오버라이드로 즉시 반영.
    @Environment(\.dismiss) private var dismiss
    @State private var showEditDialog = false
    @State private var showDeleteConfirm = false
    @State private var editTitle = ""
    @State private var editContent = ""
    @State private var editedTitle: String?
    @State private var editedContent: String?
    /// 헤더 미디어(사진/영상/움짤) 로딩 완료 여부 — MediaLoadingFrame 페이드인 트리거.
    @State private var mediaLoaded = false

    init(diary: Diary) {
        self.diary = diary
        _vm = StateObject(wrappedValue: DetailViewModel(diary: diary))
    }

    /// 타인 프로필 진입 대상.
    struct ProfileTarget: Identifiable {
        let userId: String
        let userName: String
        var id: String { userId }
    }

    /// 익명/빈 userId 가 아니면 그 작성자의 프로필을 띄운다.
    private func openProfile(_ userId: String, _ userName: String) {
        guard !userId.isEmpty else { return }
        profileTarget = ProfileTarget(userId: userId, userName: userName)
    }

    private var distanceM: Double {
        let me = location.coordinateOrDefault
        return Geo.distanceMeters(lat1: me.latitude, lng1: me.longitude,
                                  lat2: diary.latitude, lng2: diary.longitude)
    }

    private var isOwner: Bool { diary.userId == auth.uid }
    /// 100m 이내인가. 실제 위치 fix(coordinate != nil) 전에는 거리 판정을 하지 않는다 — 기본좌표/저장좌표로
    /// 100m 을 재면 이동·조작으로 우회될 수 있다. (Android DetailScreen 패리티)
    private var isNear: Bool {
        location.coordinate != nil && distanceM <= AppConfig.diaryOpenRadiusM
    }
    /// ── 열람 잠금(2026-09-21, 09-22 영구 해금으로 개편) ─────────────────────────
    /// 예전엔 100m 밖이면 지도에서 **진입 자체가 막혔다**. 이제는 누구나 들어와서 **제목까지** 보고,
    /// 사진/본문/댓글은 ① 100m 이내 접근 ② 보상형 광고 시청 중 하나로 연다(내 글은 항상 열림).
    /// 한 번 열린 글은 `DiaryUnlockStore` 에 남아 **계속 열려 있다**(멀어져도). 잠금 UI 는 `DiaryLockViews.swift`.
    /// ⚠️ 댓글 **작성**만은 해금과 무관하게 항상 100m 이내(`isNear`)에서만.
    private var canOpen: Bool {
        isOwner || isNear || (diary.id.map { unlockStore.isUnlocked($0) } ?? false)
    }
    /// 댓글을 쓸 수 있는가 — 로그인 && **무조건 100m 이내**(2026-09-22 사용자 지시, 내 글 포함).
    private var canComment: Bool { auth.uid != nil && isNear }
    /// 100m 이내에 들어온 순간 그 글을 영구 해금한다(내 글은 기록 불필요).
    private func recordProximityUnlock() {
        guard isNear, !isOwner, let id = diary.id else { return }
        unlockStore.unlock(id)
    }
    /// 차단한 사용자의 댓글은 숨긴다. (Android DetailScreen 패리티)
    private var visibleComments: [Comment] { vm.comments.filter { !blockedIds.contains($0.userId) } }

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 0) {
                    // ── 헤더: 4:3 미디어(없으면 image_frame) + 하단 스크림 + 별/작성자/날짜 오버레이 ──
                    // (Android DetailScreen 헤더와 동일 구조 — 제목은 본문 영역으로 분리)
                    heroHeader

                    VStack(alignment: .leading, spacing: 0) {
                        Spacer().frame(height: 18)
                        Text(displayTitle)
                            .font(.minSans(24, .semibold))
                            .foregroundStyle(Theme.textPrimary)
                        Spacer().frame(height: 16)
                        if canOpen { bodyCard } else { lockedContentCard }
                        Spacer().frame(height: 20)
                        if canOpen {
                            interactionRow
                            Divider().overlay(Theme.outline)
                            Spacer().frame(height: 16)
                            commentsSection
                        }
                        Spacer().frame(height: 40)
                    }
                    .padding(.horizontal, 20)
                }
            }
            if let t = toast {
                ToastView(text: t)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .allowsHitTesting(false)
            }
        }
        // 100m 이내에 들어오면 영구 해금 기록(Android LaunchedEffect(isNear) 패리티).
        .onAppear { recordProximityUnlock() }
        .onChange(of: isNear) { _ in recordProximityUnlock() }
        .fullScreenCover(isPresented: $showFullMedia) {
            FullScreenMediaViewer(
                mediaUrl: diary.videoUrl.isEmpty ? diary.imageUrl : diary.videoUrl,
                // 움짤(GIF)은 이미지로 재생 — mp4(구버전 영상)만 플레이어가 필요하다.
                isVideo: !diary.videoUrl.isEmpty && !isGifUrl(diary.videoUrl)
            )
        }
        .navigationTitle(LocaleManager.shared.t(.navDetail))
        .navigationBarTitleDisplayMode(.inline)
        // (공유/신고/수정/삭제는 Android 처럼 좋아요 행 인라인 버튼 — 탑바 액션 없음)
        .reportDialog(title: LocaleManager.shared.t(.reportDiary), isPresented: $showReportDialog) { reason, detail in
            guard let myUid = auth.uid, let id = diary.id else { return }
            Task {
                // 관리자가 Console 에서 바로 검토하도록 다이어리 스냅샷을 함께 등록(체크리스트 28).
                // "기타" 사유는 신고자가 적은 설명(reasonDetail)도 같이 남긴다.
                var extra: [String: Any] = [
                    "targetTitle": diary.title,
                    "targetContent": String(diary.content.prefix(280)),
                    "targetOwnerName": diary.userName,
                    "targetImageUrl": diary.imageUrl.isEmpty ? diary.videoUrl : diary.imageUrl,
                ]
                if !detail.isEmpty { extra["reasonDetail"] = detail }
                await ModerationRepository.report(
                    reporterId: myUid, type: "diary",
                    targetId: id, targetOwnerId: diary.userId, reason: reason,
                    extra: extra)
                showReportedConfirm = true
            }
        }
        .staryInfoDialog(LocaleManager.shared.t(.toastReported), isPresented: $showReportedConfirm)
        // 비로그인 상호작용(좋아요/댓글) 시 로그인 안내. (Android requireLogin 토스트 패리티)
        .staryInfoDialog(LocaleManager.shared.t(.commonLoginRequired), isPresented: $showLoginRequired)
        .task {
            guard let uid = auth.uid else { return }
            if let snap = try? await FirestoreService.blocked(of: uid).getDocuments() {
                blockedIds = Set(snap.documents.map { $0.documentID })
            }
        }
        // 타인 프로필 — Android 처럼 전체 화면 push(NavRoute.UserProfile 대응).
        // DetailScreen 은 항상 루트 NavigationStack 안에서 push 되므로 스택 push 가 가능하다.
        .navigationDestination(isPresented: Binding(
            get: { profileTarget != nil }, set: { if !$0 { profileTarget = nil } }
        )) {
            if let t = profileTarget {
                UserProfileScreen(userId: t.userId, userName: t.userName)
            }
        }
        // 친구 프로필에서 "길찾기"를 누르면 이 상세/프로필 시트를 닫고 지도로 보낸다.
        .onChange(of: focus.pendingDiaryId) { id in
            if id != nil { profileTarget = nil }
        }
        .onAppear {
            vm.start(uid: auth.uid)
            // (열람 효과음은 Android 처럼 지도 파장(warp) 시작 시 재생 — 여기서 또 울리면 중복)
        }
        .onDisappear { vm.stop() }
        .task {
            guard !didCountView, !isOwner, let id = diary.id else { return }
            didCountView = true
            await store.incrementView(id)
        }
        .task {
            // 미조회 필터용 열람 기록(본인 글 포함 무조건 — Android markViewed 패리티).
            guard let uid = auth.uid, let id = diary.id else { return }
            await ViewedRepository.markViewed(uid: uid, diaryId: id)
        }
        // 내 글 수정 — Android 수정 다이얼로그(제목/내용 두 칸)와 같은 가운데 사각 팝업.
        .staryDialog(isPresented: $showEditDialog) {
            StaryDialogCard(title: LocaleManager.shared.t(.detailEditTitle)) {
                VStack(alignment: .leading, spacing: 12) {
                    dialogField(LocaleManager.shared.t(.fieldTitle), text: $editTitle, lines: 1...1)
                    dialogField(LocaleManager.shared.t(.fieldContent), text: $editContent, lines: 3...6)
                }
            } actions: {
                StaryDialogTextButton(LocaleManager.shared.t(.commonCancel), color: Theme.textSecondary) {
                    showEditDialog = false
                }
                StaryDialogTextButton(LocaleManager.shared.t(.commonSave), weight: .semibold) {
                    showEditDialog = false
                    let t = String(editTitle.prefix(AppConfig.diaryTitleMaxLen))
                    let c = String(editContent.prefix(AppConfig.diaryContentMaxLen))
                    var d = diary
                    d.title = t
                    d.content = c
                    editedTitle = t
                    editedContent = c
                    Task { try? await store.save(d) }
                }
            }
        }
        // 내 글 삭제 — 확인 후 삭제하고 pop. (Android 삭제 다이얼로그 대응)
        .staryConfirmDialog(LocaleManager.shared.t(.detailDeleteTitle),
                            isPresented: $showDeleteConfirm,
                            message: LocaleManager.shared.t(.detailDeleteConfirm),
                            confirmTitle: LocaleManager.shared.t(.commonDelete),
                            destructive: true) {
            guard let id = diary.id else { return }
            Task {
                try? await store.delete(id)
                dismiss()
            }
        }
    }

    /// 수정 팝업의 라벨 달린 입력칸(Android OutlinedTextField 톤).
    private func dialogField(_ label: String, text: Binding<String>,
                             lines: ClosedRange<Int>) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(label)
                .font(.minSans(12))
                .foregroundStyle(Theme.textSecondary)
            TextField("", text: text, axis: .vertical)
                .font(.minSans(15))
                .foregroundStyle(Theme.textPrimary)
                .lineLimit(lines)
                .padding(10)
                .background(Theme.surfaceAlt, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10)
                    .strokeBorder(Theme.outline, lineWidth: 1))
        }
    }

    /// 별색(그라데이션이면 시작색) — 본문 카드 테두리/전송 버튼 강조에 사용(Android accent).
    private var accent: Color { StarStyle.color(diary.starColor) }

    /// 표시 제목 — 수정 결과 로컬 오버라이드 우선, 비면 "(제목 없음)".
    private var displayTitle: String {
        let t = editedTitle ?? diary.title
        return t.isEmpty ? LocaleManager.shared.t(.shareCardUntitled) : t
    }

    private static let createdFmt: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy.MM.dd HH:mm"
        return f
    }()

    // ── 헤더: 4:3 미디어 + 하단 스크림 + 별/작성자/날짜 오버레이 (Android 헤더 Box 대응) ──

    /// 이 글에 미디어(사진/영상/움짤)가 있는가(열람 가능 여부와 무관).
    private var diaryHasMedia: Bool { !(diary.imageUrl.isEmpty && diary.videoUrl.isEmpty) }


    private var heroHeader: some View {
        Color.clear
            .aspectRatio(4.0 / 3.0, contentMode: .fit)
            .overlay { headerMedia }
            .clipped()
            .overlay(
                // 하단 가독성 스크림은 **미디어 자리(실제 미디어 또는 잠금 플레이스홀더)일 때만** — 기본 템플릿 위에
                // 덧씌우면 필터처럼 보여서(사용자 피드백 #5) 미디어 없을 땐 하단만 배경색으로 자연스럽게 잇는다.
                // (잠긴 글도 미디어가 있으면 loading_dipper 플레이스홀더라 Android 처럼 진한 스크림)
                Group {
                    if diaryHasMedia {
                        LinearGradient(stops: [
                            .init(color: .clear, location: 0),
                            .init(color: .black.opacity(0.4), location: 0.55),
                            .init(color: Theme.background, location: 1),
                        ], startPoint: .top, endPoint: .bottom)
                    } else {
                        LinearGradient(stops: [
                            .init(color: .clear, location: 0.6),
                            .init(color: Theme.background, location: 1),
                        ], startPoint: .top, endPoint: .bottom)
                    }
                }
            )
            .overlay(alignment: .bottomLeading) {
                headerOverlay.padding(20)
            }
            .contentShape(Rectangle())
            .onTapGesture {
                // 미디어가 있고 열람 가능할 때만 전체화면 뷰어.
                if canOpen, !(diary.imageUrl.isEmpty && diary.videoUrl.isEmpty) { showFullMedia = true }
            }
    }

    @ViewBuilder
    private var headerMedia: some View {
        // ⚠️ 잠겨 있으면 원본 URL 을 **로드조차 하지 않는다** — 가리기만 하면 캐시/전체화면으로 새어나간다.
        // 잠김 + 미디어 없음이면 잠금 표시 없이 아래 image_frame 분기(가릴 게 없다).
        if !canOpen && diaryHasMedia {
            lockedHero
        } else if !diary.videoUrl.isEmpty, isGifUrl(diary.videoUrl) {
            // 부메랑 움짤(GIF) — 무한 루프 재생. (구버전 mp4 는 아래 플레이어)
            MediaLoadingFrame(loaded: mediaLoaded) {
                RemoteGifView(
                    urlString: diary.videoUrl,
                    suppressOwnPlaceholder: true,
                    onLoaded: { mediaLoaded = true }
                )
            }
        } else if !diary.videoUrl.isEmpty, let vurl = URL(string: diary.videoUrl) {
            MediaLoadingFrame(loaded: mediaLoaded) {
                LoopingVideoPlayer(url: vurl, muted: true, onFirstFrameRendered: { mediaLoaded = true })
            }
        } else if !diary.imageUrl.isEmpty {
            MediaLoadingFrame(loaded: mediaLoaded) {
                AsyncImage(url: URL(string: diary.imageUrl)) { phase in
                    if case .success(let image) = phase {
                        image.resizable().scaledToFill()
                            .onAppear { mediaLoaded = true }
                    } else {
                        Color.clear
                    }
                }
            }
        } else if let frame = BundleImage.named("image_frame") {
            // 사진/영상이 없으면 템플릿 이미지 — Android image_frame 대응.
            Image(uiImage: frame).resizable().scaledToFill()
        } else {
            Theme.surfaceAlt
        }
    }

    private var headerOverlay: some View {
        let canOpenProfile = !diary.userId.isEmpty
        let authorName = canOpenProfile
            ? directory.name(diary.userId, fallback: diary.userName)
            : diary.userName
        return HStack(spacing: 0) {
            Button {
                if canOpenProfile { openProfile(diary.userId, authorName) }
            } label: {
                HStack(spacing: 0) {
                    StarView(type: diary.starType, colorIndex: diary.starColor, size: 18, glow: false)
                    Spacer().frame(width: 8)
                    Text(authorName.isEmpty ? LocaleManager.shared.t(.commonAnonymous) : authorName)
                        .font(.minSans(13))
                        .foregroundStyle(Theme.textPrimary.opacity(0.85))
                    if canOpenProfile {
                        // 히든 업적 배지 — 익명 글에는 붙이지 않는다(작성자 은닉 유지).
                        HiddenStarBadges(userId: diary.userId, size: 13)
                            .padding(.leading, 5)
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12))
                            .foregroundStyle(Theme.textPrimary.opacity(0.6))
                    }
                }
            }
            .buttonStyle(.plain)
            .disabled(!canOpenProfile)
            Text("  ·  ").font(.minSans(13)).foregroundStyle(Theme.textSecondary)
            Text(Self.createdFmt.string(from: diary.createdDate))
                .font(.minSans(13)).foregroundStyle(Theme.textSecondary)
        }
        .task(id: diary.userId) {
            if canOpenProfile { directory.ensureWatching(diary.userId) }
        }
    }

    // ── 본문 카드 — Android: 0xCC14181C 배경 + accent 그라데이션 테두리 ──

    private var bodyCard: some View {
        Text((editedContent ?? diary.content).hangulWordWrapped)
            .font(.minSans(16))
            .lineSpacing(8)
            .foregroundStyle(Theme.textPrimary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
            .background(Color(hex: 0x14181C).opacity(0.8), in: RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16).strokeBorder(
                    LinearGradient(colors: [accent.opacity(0.45), accent.opacity(0.15)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing),
                    lineWidth: 1
                )
            )
    }

    // ── 좋아요/공유 + (내 글) 수정·삭제 / (남의 글) 신고 — Android 인라인 행 대응 ──

    private var interactionRow: some View {
        HStack(spacing: 2) {
            // 하트 pop + 크리스탈 파편 버스트 + 숫자 롤링(LikeButton). 파편 색은 그 별의 색.
            LikeButton(isLiked: vm.isLiked, count: vm.likeCount, accent: accent) {
                // 비로그인 시 좋아요 잠금 — 로그인 안내만.
                guard auth.uid != nil else { showLoginRequired = true; return }
                Task { await vm.toggleLike(uid: auth.uid, userName: auth.displayName) }
            }
            // 공유 → 공유 카드 편집 화면(Android ShareDiaryButton → ShareCardEditorDialog 패리티).
            // (2026-07-19 에 한 번 제거했다가 2026-09 사용자 결정으로 편집기까지 포함해 재구현)
            Button { showShareEditor = true } label: {
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 18))
                    .foregroundStyle(Theme.textSecondary)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(LocaleManager.shared.t(.shareDiary))
            .fullScreenCover(isPresented: $showShareEditor) {
                ShareCardEditorView(diary: diary, myUid: auth.uid)
            }
            Spacer()
            if isOwner {
                Button(LocaleManager.shared.t(.commonEdit)) {
                    editTitle = editedTitle ?? diary.title
                    editContent = editedContent ?? diary.content
                    showEditDialog = true
                }
                .font(.minSans(13)).foregroundStyle(Theme.textSecondary)
                // 수정/삭제는 붙여 둔다(Android CompactTextAction 좌우 8dp 와 같은 간격).
                Spacer().frame(width: 16)
                Button(LocaleManager.shared.t(.commonDelete)) { showDeleteConfirm = true }
                    .font(.minSans(13)).foregroundStyle(Theme.accentRed)
            } else {
                Button(LocaleManager.shared.t(.reportDiary)) {
                    if auth.uid == nil { showLoginRequired = true } else { showReportDialog = true }
                }
                .font(.minSans(13)).foregroundStyle(Theme.textSecondary)
            }
        }
        .padding(.bottom, 8)
    }

    /// 잠긴 글의 히어로(4:3) — **미디어가 있는 글에서만**. 미디어 로딩 플레이스홀더(loading_dipper) +
    /// 우하단(작성자 줄 바로 위) 작은 캡션 "이 사진/영상은 잠겨 있어요" — 별자리 선의 청보라를 밝힌 색 + 옅은 번짐.
    /// 별자리는 영상의 가로 14~88% · 세로 23~60% 라 겹치지 않는다. (Android `DiaryLock.kt` `LockedHero` 패리티)
    private var lockedHero: some View {
        ZStack(alignment: .bottomTrailing) {
            // loaded=false 고정 → 콘텐츠 없이 플레이스홀더만.
            MediaLoadingFrame(loaded: false) { Color.clear }
            Text(LocaleManager.shared.t(isGifOrVideo ? .detailLockedVideo : .detailLockedPhoto))
                .font(.minSans(12))
                .tracking(0.4)
                .foregroundStyle(DiaryLock.captionColor)
                .shadow(color: DiaryLock.captionGlow, radius: 5)
                // 아래 20(헤더 오버레이 여백) + 작성자 줄 ≈ 20 + 간격 12.
                .padding(.trailing, 20)
                .padding(.bottom, 52)
        }
    }

    /// 잠긴 글의 미디어가 영상(움짤/mp4)인가 — 캡션 "영상/사진" 구분용.
    private var isGifOrVideo: Bool { !diary.videoUrl.isEmpty }

    /// 잠긴 글의 본문 자리 — 좌상단·우하단 **십자 코너**(`CornerCrossFrame`) 안에 크리스탈 재생 로고(탭 = 광고)
    /// + 여는 방법 + 현재 위치로부터의 거리. 카드 배경·테두리는 없다(2026-09-22 레퍼런스 재해석).
    /// 광고 SDK 가 아직 안 붙은 동안(`AdsManager.isConfigured == false`)에는 탭하면 "광고를 불러올 수 없어요" 안내.
    /// (Android `DiaryLock.kt` `LockedContentCard` 패리티 — 수치 동일)
    private var lockedContentCard: some View {
        VStack(spacing: 0) {
            CrystalPullIcon(
                image: DiaryLock.playLogoImage(color: accent, seed: DiaryLock.seed(diary.id, slot: 1), size: 54),
                color: accent,
                iconSize: 54,
                accessibilityText: LocaleManager.shared.t(.detailWatchAd),
                onTap: watchAdToUnlock
            )
            Spacer().frame(height: 6)
            Text(String(format: LocaleManager.shared.t(.detailLockedTitle), Int(AppConfig.diaryOpenRadiusM)))
                .font(.minSans(14.5, .medium))
                .lineSpacing(7)
                .multilineTextAlignment(.center)
                .foregroundStyle(Theme.textPrimary.opacity(0.94))
            Spacer().frame(height: 12)
            HStack(spacing: 5) {
                Image(systemName: "location")
                    .font(.system(size: 11)).foregroundStyle(Theme.textSecondary)
                Text(location.coordinate == nil
                     ? LocaleManager.shared.t(.detailLocating)
                     : String(format: LocaleManager.shared.t(.detailLockedDistance),
                              Geo.formatDistance(distanceM)))
                    .font(.minSans(12))
                    .tracking(0.2)
                    .monospacedDigit()
                    .foregroundStyle(Theme.textSecondary)
            }
        }
        .frame(maxWidth: .infinity)
        // 위쪽은 아이콘 터치 영역(후광 여백)이 이미 넉넉해서 얇게(Android 와 같은 값).
        .padding(.horizontal, 24)
        .padding(.top, 12)
        .padding(.bottom, 30)
        .background(CornerCrossFrame(color: accent.blended(with: .white, fraction: 0.18)))
        .padding(.vertical, 8)
        .onAppear { ads.preload() }
    }

    /// 보상형 광고 → 끝까지 보면 이 글을 **영구 해금**(`DiaryUnlockStore`). (Android `watchAdToUnlock` 패리티)
    private func watchAdToUnlock() {
        guard let id = diary.id, !ads.showing else { return }
        guard ads.isConfigured else {
            showToast(LocaleManager.shared.t(.detailAdUnavailable))
            return
        }
        ads.showRewarded { rewarded in
            if rewarded {
                DiaryUnlockStore.shared.unlock(id)
                Haptics.celebrate()
                showToast(LocaleManager.shared.t(.detailAdUnlocked))
            } else {
                showToast(LocaleManager.shared.t(.detailAdNotFinished))
            }
        }
    }

    private func showToast(_ text: String) {
        toast = text
        Task {
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            if toast == text { toast = nil }
        }
    }

    /// 댓글을 못 쓰는 이유 안내 — 비로그인이면 로그인 팝업, 100m 밖이면 토스트.
    private func explainCommentBlocked() {
        if auth.uid == nil {
            showLoginRequired = true
        } else if !isNear {
            showToast(String(format: LocaleManager.shared.t(.detailCommentNearOnly), Int(AppConfig.diaryOpenRadiusM)))
        }
    }

    private var commentsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            // "댓글 N" 헤더 — Android detail_comments_count 대응.
            Text(String(format: LocaleManager.shared.t(.detailCommentsCount), visibleComments.count))
                .font(.minSans(14))
                .foregroundStyle(Theme.textPrimary)
            // 댓글 입력 — 로그인 && **100m 이내**일 때만(해금과 무관, 2026-09-22). 못 쓰면 잠그고 이유 안내.
            // (Android CommentInputRow 패리티)
            HStack {
                TextField(auth.uid != nil && !isNear
                          ? String(format: LocaleManager.shared.t(.detailCommentNearOnly), Int(AppConfig.diaryOpenRadiusM))
                          : LocaleManager.shared.t(.commentPlaceholder),
                          text: $commentText, axis: .vertical)
                    .lineLimit(1...4)
                    .onChange(of: commentText) { v in
                        if v.count > AppConfig.commentMaxLen { commentText = String(v.prefix(AppConfig.commentMaxLen)) }
                    }
                    .padding(10)
                    .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
                    .foregroundStyle(Theme.textPrimary)
                    .disabled(!canComment)
                    .overlay {
                        if !canComment {
                            // 비활성 필드는 터치를 안 받으므로 투명 오버레이로 이유 안내(로그인 / 100m).
                            Color.clear
                                .contentShape(Rectangle())
                                .onTapGesture { explainCommentBlocked() }
                        }
                    }
                Button {
                    guard canComment else { explainCommentBlocked(); return }
                    let t = commentText
                    commentText = ""
                    Task { await vm.addComment(uid: auth.uid, userName: auth.displayName, text: t) }
                } label: {
                    Image(systemName: "paperplane.fill")
                        .foregroundStyle(!canComment || commentText.isEmpty ? Theme.textFaint : Theme.mint)
                }
                .disabled(!canComment || commentText.trimmingCharacters(in: .whitespaces).isEmpty)
            }

            ForEach(visibleComments) { c in
                HStack(alignment: .top, spacing: 10) {
                    // 인스타식 프로필 아바타 (top 을 사용자 이름 top 에 맞춤) — 탭 시 작성자 프로필
                    Button { openProfile(c.userId, c.userName) } label: {
                        CommentAvatar(userId: c.userId, userName: c.userName)
                            .padding(.top, 2)
                    }
                    .buttonStyle(.plain)
                    VStack(alignment: .leading, spacing: 3) {
                        HStack {
                            Button { openProfile(c.userId, c.userName) } label: {
                                HStack(spacing: 4) {
                                    // 저장 시점 스냅샷이 아닌 현재 닉네임으로 표시
                                    Text(directory.name(c.userId, fallback: c.userName))
                                        .font(.minSans(12)).foregroundStyle(Theme.textSecondary)
                                    HiddenStarBadges(userId: c.userId, size: 10)
                                }
                            }
                            .buttonStyle(.plain)
                            Spacer()
                            if c.userId == auth.uid {
                                Button {
                                    Task { await vm.deleteComment(c) }
                                } label: {
                                    Image(systemName: "trash").font(.caption2).foregroundStyle(Theme.textFaint)
                                }
                            }
                        }
                        Text(c.content.hangulWordWrapped).font(.minSans(14)).foregroundStyle(Theme.textPrimary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 4)
                // 카드 대신 구분선 — Android 댓글 목록(HorizontalDivider 구분) 대응.
                Divider().overlay(Theme.outline).padding(.vertical, 8)
            }
        }
    }

    private func distanceLabel(_ m: Double) -> String {
        m < 1000 ? "\(Int(m))m" : String(format: "%.1fkm", m / 1000)
    }
}

/// userId → 프로필 사진 URL 캐시 — 댓글마다 같은 작성자를 반복 조회하지 않게.
@MainActor
final class ProfileImageCache {
    static let shared = ProfileImageCache()
    private var cache: [String: String] = [:]   // userId → url ("" = 사진 없음)

    /// 없으면 nil. 한 번 조회한 userId 는 캐시에서 즉시 반환.
    func url(for userId: String) async -> String? {
        guard !userId.isEmpty else { return nil }
        if let cached = cache[userId] { return cached.isEmpty ? nil : cached }
        let url = ((try? await FirestoreService.users.document(userId).getDocument())?
            .get("profileImageUrl") as? String) ?? ""
        cache[userId] = url
        return url.isEmpty ? nil : url
    }
}

/// 아바타/썸네일 캐시 — 원본을 요청 크기로 다운샘플(CGImageSource)해 빠르게 렌더링.
/// 네트워크는 [ImageCache.session](디스크 캐시 + returnCacheDataElseLoad)을 써서 재방문 시
/// 다시 받지 않는다. 캐시 키에 **크기를 포함**해 같은 URL 을 다른 크기로 써도 흐려지지 않는다.
@MainActor
final class AvatarThumbCache {
    static let shared = AvatarThumbCache()
    private var cache: [String: UIImage] = [:]

    func image(for urlString: String, maxPixel: CGFloat = 96) async -> UIImage? {
        let key = "\(urlString)@\(Int(maxPixel))"
        if let hit = cache[key] { return hit }
        guard let url = URL(string: urlString),
              let (data, _) = try? await ImageCache.session.data(from: url) else { return nil }
        let opts: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel,
        ]
        guard let src = CGImageSourceCreateWithData(data as CFData, nil),
              let cg = CGImageSourceCreateThumbnailAtIndex(src, 0, opts as CFDictionary) else { return nil }
        let img = UIImage(cgImage: cg)
        cache[key] = img
        return img
    }
}

/// 인스타식 댓글 프로필 아바타 — users/{uid} 의 "현재" 사진/이름(실시간), 저해상도 썸네일로 빠르게. 없으면 이니셜 폴백.
private struct CommentAvatar: View {
    let userId: String
    let userName: String
    @ObservedObject private var directory = UserDirectory.shared
    @State private var thumb: UIImage?

    private var initial: String {
        let name = directory.name(userId, fallback: userName)
        let first = name.trimmingCharacters(in: .whitespaces).prefix(1).uppercased()
        return first.isEmpty ? "?" : first
    }

    var body: some View {
        Group {
            if let thumb {
                Image(uiImage: thumb).resizable().scaledToFill()
            } else {
                Theme.surfaceAlt.overlay(
                    Text(initial)
                        .font(.minSans(13))
                        .foregroundStyle(Theme.mint)
                )
            }
        }
        .frame(width: 32, height: 32)
        .clipShape(Circle())
        .task(id: userId) { directory.ensureWatching(userId) }
        .task(id: directory.photoUrl(userId)) {
            if let url = directory.photoUrl(userId), !url.isEmpty {
                thumb = await AvatarThumbCache.shared.image(for: url)
            } else {
                thumb = nil
            }
        }
    }
}

/// 사진/움짤/영상 전체화면 뷰어 — 원본 비율 그대로(Fit) 보여주고 핀치 확대·드래그 이동을 지원한다.
/// 탭하면 닫힌다(Android DetailScreen.FullScreenMediaViewer 패리티).
private struct FullScreenMediaViewer: View {
    let mediaUrl: String
    let isVideo: Bool
    @Environment(\.dismiss) private var dismiss

    @State private var scale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var pinchStart: CGFloat?
    @State private var dragStart: CGSize?

    var body: some View {
        ZStack {
            Color.black.opacity(0.95).ignoresSafeArea()

            content
                .scaleEffect(scale)
                .offset(offset)
                .gesture(zoomGesture)

            VStack {
                HStack {
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 38, height: 38)
                            .background(.black.opacity(0.35), in: Circle())
                    }
                    .padding(.trailing, 14)
                }
                Spacer()
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { dismiss() }
    }

    @ViewBuilder
    private var content: some View {
        if isVideo, let url = URL(string: mediaUrl) {
            LoopingVideoPlayer(url: url, muted: false)
        } else if isGifUrl(mediaUrl) {
            RemoteGifFitView(urlString: mediaUrl)
        } else {
            AsyncImage(url: URL(string: mediaUrl)) { image in
                image.resizable().scaledToFit()
            } placeholder: {
                StarLoadingView(size: 36)
            }
        }
    }

    /// 핀치 확대(1..5) + 확대 상태에서만 드래그 이동. 원배율로 돌아오면 위치 리셋.
    private var zoomGesture: some Gesture {
        let pinch = MagnificationGesture()
            .onChanged { m in
                if pinchStart == nil { pinchStart = scale }
                scale = min(max((pinchStart ?? scale) * m, 1), 5)
                if scale <= 1 { offset = .zero }
            }
            .onEnded { _ in pinchStart = nil }
        let drag = DragGesture()
            .onChanged { v in
                guard scale > 1 else { return }
                if dragStart == nil { dragStart = offset }
                let base = dragStart ?? offset
                offset = CGSize(width: base.width + v.translation.width,
                                height: base.height + v.translation.height)
            }
            .onEnded { _ in dragStart = nil }
        return pinch.simultaneously(with: drag)
    }
}

/// 전체화면용 GIF — 잘라내지 않고(scaleAspectFit) 원본 비율 그대로 보여준다.
private struct RemoteGifFitView: View {
    let urlString: String
    @State private var data: Data?

    var body: some View {
        Group {
            if let data {
                GifFitImageView(data: data)
            } else {
                StarLoadingView(size: 36)
            }
        }
        .task(id: urlString) {
            guard let url = URL(string: urlString) else { return }
            data = try? await URLSession.shared.data(from: url).0
        }
    }
}

private struct GifFitImageView: UIViewRepresentable {
    let data: Data

    func makeUIView(context: Context) -> UIImageView {
        let iv = UIImageView()
        iv.contentMode = .scaleAspectFit
        iv.image = GifImageView.animatedImage(from: data)
        return iv
    }

    func updateUIView(_ uiView: UIImageView, context: Context) {}
}
