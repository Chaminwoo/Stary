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
    @State private var didCountView = false
    @State private var commentText = ""
    @State private var profileTarget: ProfileTarget?
    @State private var blockedIds: Set<String> = []
    @State private var showReportDialog = false
    @State private var showReportedConfirm = false
    @State private var showLoginRequired = false
    /// 사진/움짤/영상 전체화면 보기.
    @State private var showFullMedia = false
    // 내 글 수정/삭제(Android 인라인 수정·삭제 대응). 수정 결과는 로컬 오버라이드로 즉시 반영.
    @Environment(\.dismiss) private var dismiss
    @State private var showEditDialog = false
    @State private var showDeleteConfirm = false
    @State private var editTitle = ""
    @State private var editContent = ""
    @State private var editedTitle: String?
    @State private var editedContent: String?

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
    /// 실제 위치 fix(coordinate != nil) 전에는 열람 불가 — 기본좌표/저장좌표로 100m 판정하면
    /// 이동·조작으로 우회될 수 있다(체크리스트 29, Android DiaryMap 게이팅 패리티).
    private var canOpen: Bool { isOwner || (location.coordinate != nil && distanceM <= AppConfig.diaryOpenRadiusM) }
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
                            .font(.poorStory(24))
                            .foregroundStyle(Theme.textPrimary)
                        Spacer().frame(height: 16)
                        bodyCard
                        Spacer().frame(height: 20)
                        if canOpen {
                            interactionRow
                            Divider().overlay(Theme.outline)
                            Spacer().frame(height: 16)
                            commentsSection
                        } else {
                            lockedNotice
                        }
                        Spacer().frame(height: 40)
                    }
                    .padding(.horizontal, 20)
                }
            }

            // 열람의 여운(34-2) — 파장(별 열람 연출)이 남긴 잔향처럼 화면 상단에
            // 그 별 색의 오로라가 아주 옅게 드리운다(스크롤 무관 고정 레이어, 터치 통과).
            DetailAuroraVeil(accent: StarStyle.color(diary.starColor))
        }
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
        .reportDialog(title: LocaleManager.shared.t(.reportDiary), isPresented: $showReportDialog) { reason in
            guard let myUid = auth.uid, let id = diary.id else { return }
            Task {
                // 관리자가 Console 에서 바로 검토하도록 다이어리 스냅샷을 함께 등록(체크리스트 28).
                await ModerationRepository.report(
                    reporterId: myUid, type: "diary",
                    targetId: id, targetOwnerId: diary.userId, reason: reason,
                    extra: [
                        "targetTitle": diary.title,
                        "targetContent": String(diary.content.prefix(280)),
                        "targetOwnerName": diary.userName,
                        "targetImageUrl": diary.imageUrl.isEmpty ? diary.videoUrl : diary.imageUrl,
                    ])
                showReportedConfirm = true
            }
        }
        .alert(LocaleManager.shared.t(.toastReported), isPresented: $showReportedConfirm) {
            Button("OK", role: .cancel) {}
        }
        // 비로그인 상호작용(좋아요/댓글) 시 로그인 안내. (Android requireLogin 토스트 패리티)
        .alert(LocaleManager.shared.t(.commonLoginRequired), isPresented: $showLoginRequired) {
            Button("OK", role: .cancel) {}
        }
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
        // 내 글 수정 — 제목/내용(글자수 제한 선차단). (Android 수정 다이얼로그 대응)
        .alert(LocaleManager.shared.t(.commonEdit), isPresented: $showEditDialog) {
            TextField("", text: $editTitle)
            TextField("", text: $editContent)
            Button(LocaleManager.shared.t(.commonSave)) {
                let t = String(editTitle.prefix(AppConfig.diaryTitleMaxLen))
                let c = String(editContent.prefix(AppConfig.diaryContentMaxLen))
                var d = diary
                d.title = t
                d.content = c
                editedTitle = t
                editedContent = c
                Task { try? await store.save(d) }
            }
            Button(LocaleManager.shared.t(.commonCancel), role: .cancel) {}
        }
        // 내 글 삭제 — 확인 후 삭제하고 pop. (Android 삭제 다이얼로그 대응)
        .alert(LocaleManager.shared.t(.commonDelete), isPresented: $showDeleteConfirm) {
            Button(LocaleManager.shared.t(.commonDelete), role: .destructive) {
                guard let id = diary.id else { return }
                Task {
                    try? await store.delete(id)
                    dismiss()
                }
            }
            Button(LocaleManager.shared.t(.commonCancel), role: .cancel) {}
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

    /// 실제 미디어(사진/영상/움짤)가 있고 열람 가능한가 — 없으면 기본 템플릿(image_frame)만.
    private var hasMedia: Bool {
        canOpen && !(diary.imageUrl.isEmpty && diary.videoUrl.isEmpty)
    }

    private var heroHeader: some View {
        Color.clear
            .aspectRatio(4.0 / 3.0, contentMode: .fit)
            .overlay { headerMedia }
            .clipped()
            .overlay(
                // 하단 가독성 스크림은 **실제 미디어가 있을 때만** — 기본 템플릿 위에 덧씌우면
                // 필터처럼 보여서(사용자 피드백 #5) 미디어 없을 땐 하단만 배경색으로 자연스럽게 잇는다.
                Group {
                    if hasMedia {
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
        // ⚠️ 미디어는 게이팅 없이 표시(Android 헤더 동일) — 100m 게이트는 지도 탭 진입에서,
        // 본문/상호작용 게이트는 canOpen 으로 각각 적용된다.
        if !diary.videoUrl.isEmpty, isGifUrl(diary.videoUrl) {
            // 부메랑 움짤(GIF) — 무한 루프 재생. (구버전 mp4 는 아래 플레이어)
            RemoteGifView(urlString: diary.videoUrl)
        } else if !diary.videoUrl.isEmpty, let vurl = URL(string: diary.videoUrl) {
            LoopingVideoPlayer(url: vurl, muted: true)
        } else if !diary.imageUrl.isEmpty {
            AsyncImage(url: URL(string: diary.imageUrl)) { image in
                image.resizable().scaledToFill()
            } placeholder: { Theme.surfaceAlt }
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
                        .font(.poorStory(13))
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
            Text("  ·  ").font(.poorStory(13)).foregroundStyle(Theme.textSecondary)
            Text(Self.createdFmt.string(from: diary.createdDate))
                .font(.poorStory(13)).foregroundStyle(Theme.textSecondary)
        }
        .task(id: diary.userId) {
            if canOpenProfile { directory.ensureWatching(diary.userId) }
        }
    }

    // ── 본문 카드 — Android: 0xCC14181C 배경 + accent 그라데이션 테두리 ──

    private var bodyCard: some View {
        Text(editedContent ?? diary.content)
            .font(.poorStory(16))
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
            Button {
                // 비로그인 시 좋아요 잠금 — 로그인 안내만.
                guard auth.uid != nil else { showLoginRequired = true; return }
                Task { await vm.toggleLike(uid: auth.uid, userName: auth.displayName) }
            } label: {
                Image(systemName: vm.isLiked ? "heart.fill" : "heart")
                    .font(.system(size: 20))
                    .foregroundStyle(vm.isLiked ? Theme.accentRed : Theme.textSecondary)
                    .frame(width: 40, height: 40)
            }
            Text("\(vm.likeCount)")
                .font(.poorStory(14)).foregroundStyle(Theme.textSecondary)
            // 공유 — 밤하늘 카드 이미지 + 웹 랜딩 링크(체크리스트 30). Android 위치(좋아요 옆) 동일.
            Button {
                Task { await ShareCard.share(diary: diary) }
            } label: {
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 18))
                    .foregroundStyle(Theme.textSecondary)
                    .frame(width: 40, height: 40)
            }
            Spacer()
            if isOwner {
                Button(LocaleManager.shared.t(.commonEdit)) {
                    editTitle = editedTitle ?? diary.title
                    editContent = editedContent ?? diary.content
                    showEditDialog = true
                }
                .font(.poorStory(13)).foregroundStyle(Theme.textSecondary)
                Spacer().frame(width: 12)
                Button(LocaleManager.shared.t(.commonDelete)) { showDeleteConfirm = true }
                    .font(.poorStory(13)).foregroundStyle(Theme.accentRed)
            } else {
                Button(LocaleManager.shared.t(.reportDiary)) {
                    if auth.uid == nil { showLoginRequired = true } else { showReportDialog = true }
                }
                .font(.poorStory(13)).foregroundStyle(Theme.textSecondary)
            }
        }
        .padding(.bottom, 8)
    }

    /// 100m 밖 상호작용 잠금 안내 — Android 잠금 pill 대응.
    private var lockedNotice: some View {
        HStack(spacing: 8) {
            Image(systemName: "location")
                .font(.system(size: 15)).foregroundStyle(Theme.textSecondary)
            Text(location.coordinate == nil
                 ? LocaleManager.shared.t(.detailLocating)
                 : String(format: LocaleManager.shared.t(.mapOpenRange),
                          Int(AppConfig.diaryOpenRadiusM), Int(distanceM)))
                .font(.poorStory(13)).foregroundStyle(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(14)
        .background(Theme.surface.opacity(0.6), in: RoundedRectangle(cornerRadius: 12))
    }

    private var commentsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            // "댓글 N" 헤더 — Android detail_comments_count 대응.
            Text(String(format: LocaleManager.shared.t(.detailCommentsCount), visibleComments.count))
                .font(.poorStory(14))
                .foregroundStyle(Theme.textPrimary)
            HStack {
                TextField(LocaleManager.shared.t(.commentPlaceholder), text: $commentText, axis: .vertical)
                    .lineLimit(1...4)
                    .onChange(of: commentText) { v in
                        if v.count > AppConfig.commentMaxLen { commentText = String(v.prefix(AppConfig.commentMaxLen)) }
                    }
                    .padding(10)
                    .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
                    .foregroundStyle(Theme.textPrimary)
                    .disabled(auth.uid == nil) // 비로그인 시 입력 잠금
                    .overlay {
                        if auth.uid == nil {
                            // 비활성 필드는 터치를 안 받으므로 투명 오버레이로 로그인 안내.
                            Color.clear
                                .contentShape(Rectangle())
                                .onTapGesture { showLoginRequired = true }
                        }
                    }
                Button {
                    guard auth.uid != nil else { showLoginRequired = true; return }
                    let t = commentText
                    commentText = ""
                    Task { await vm.addComment(uid: auth.uid, userName: auth.displayName, text: t) }
                } label: {
                    Image(systemName: "paperplane.fill")
                        .foregroundStyle(commentText.isEmpty ? Theme.textFaint : Theme.mint)
                }
                .disabled(auth.uid == nil || commentText.trimmingCharacters(in: .whitespaces).isEmpty)
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
                                        .font(.poorStory(12)).foregroundStyle(Theme.textSecondary)
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
                        Text(c.content).font(.poorStory(15)).foregroundStyle(Theme.textPrimary)
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

/// 상세 화면 상단 오로라(34-2) — 별색을 아주 옅게(≤0.16) 드리우고 좌우로 느리게 흐른다.
/// 장식 전용: 히트테스트에 참여하지 않는다(아래 콘텐츠 탭 그대로 동작). (Android DetailAuroraVeil 패리티)
private struct DetailAuroraVeil: View {
    let accent: Color

    var body: some View {
        VStack(spacing: 0) {
            TimelineView(.animation(minimumInterval: 1.0 / 20)) { tl in
                // 15s 왕복 드리프트(Android Reverse 트윈 대응 — 삼각파 0→1→0).
                let cycle = tl.date.timeIntervalSinceReferenceDate / 15
                let drift = abs(cycle.truncatingRemainder(dividingBy: 2) - 1)
                Canvas { ctx, size in
                    let cx = size.width * (0.28 + 0.44 * drift)
                    let grad = Gradient(stops: [
                        .init(color: accent.opacity(0.16), location: 0),
                        .init(color: accent.opacity(0.06), location: 0.5),
                        .init(color: .clear, location: 1),
                    ])
                    ctx.fill(
                        Path(CGRect(origin: .zero, size: size)),
                        with: .radialGradient(grad, center: CGPoint(x: cx, y: 0),
                                              startRadius: 0, endRadius: size.width * 0.95)
                    )
                }
            }
            .frame(height: 220)
            Spacer(minLength: 0)
        }
        .ignoresSafeArea(edges: .top)
        .allowsHitTesting(false)
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
                        .font(.poorStory(13))
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
