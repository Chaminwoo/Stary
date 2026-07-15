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
                VStack(spacing: 18) {
                    StarView(type: diary.starType, colorIndex: diary.starColor, size: 84)
                        .padding(.top, 12)
                    Text(diary.title.isEmpty ? "(제목 없음)" : diary.title)
                        .font(.poorStory(22))
                        .foregroundStyle(Theme.textPrimary)
                    if diary.isAnonymous || diary.userId.isEmpty {
                        Text("익명")
                            .font(.poorStory(15))
                            .foregroundStyle(Theme.textSecondary)
                    } else {
                        let authorName = directory.name(diary.userId, fallback: diary.userName)
                        Button { openProfile(diary.userId, authorName) } label: {
                            HStack(spacing: 4) {
                                Text(authorName)
                                // 히든 업적 달성자 전용 크리스탈 배지(34-4) — 익명 분기가 아니므로 안전.
                                HiddenStarBadges(userId: diary.userId, size: 12)
                                Image(systemName: "chevron.right").font(.caption2)
                            }
                            .font(.poorStory(15))
                            .foregroundStyle(Theme.textSecondary)
                        }
                        .buttonStyle(.plain)
                        .task(id: diary.userId) { directory.ensureWatching(diary.userId) }
                    }

                    // 사진/움짤/영상 모두 탭하면 전체화면으로 크게 볼 수 있다(Android 패리티).
                    if canOpen, !diary.videoUrl.isEmpty, isGifUrl(diary.videoUrl) {
                        // 부메랑 움짤(GIF) — 무한 루프 재생. (구버전 mp4 는 아래 플레이어 유지)
                        RemoteGifView(urlString: diary.videoUrl)
                            .frame(height: 220)
                            .frame(maxWidth: .infinity)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .contentShape(Rectangle())
                            .onTapGesture { showFullMedia = true }
                    } else if canOpen, !diary.videoUrl.isEmpty, let vurl = URL(string: diary.videoUrl) {
                        // 짧은 영상(3초 이내) 음소거 루프 재생.
                        LoopingVideoPlayer(url: vurl, muted: true)
                            .frame(height: 220)
                            .frame(maxWidth: .infinity)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .contentShape(Rectangle())
                            .onTapGesture { showFullMedia = true }
                    } else if canOpen, !diary.imageUrl.isEmpty {
                        AsyncImage(url: URL(string: diary.imageUrl)) { image in
                            image.resizable().scaledToFit()
                        } placeholder: {
                            RoundedRectangle(cornerRadius: 14).fill(Theme.surfaceAlt).frame(height: 200)
                        }
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .contentShape(Rectangle())
                        .onTapGesture { showFullMedia = true }
                    } else if canOpen, let frame = BundleImage.named("image_frame") {
                        // 사진/영상이 없으면 템플릿 이미지(image_frame) — Android DetailScreen 패리티.
                        Image(uiImage: frame)
                            .resizable()
                            .scaledToFill()
                            .frame(height: 200)
                            .frame(maxWidth: .infinity)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                    bodyCard
                    likeBar
                    if canOpen { commentsSection }
                }
                .padding(16)
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
        .toolbar {
            // 공유 — 밤하늘 카드 이미지 + 웹 랜딩 링크를 공유 시트로(체크리스트 30, Android 패리티).
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    Task { await ShareCard.share(diary: diary) }
                } label: {
                    Image(systemName: "square.and.arrow.up")
                }
                .tint(Theme.mint)
            }
            if !isOwner, !diary.userId.isEmpty, auth.uid != nil {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button(role: .destructive) { showReportDialog = true } label: {
                            Label(LocaleManager.shared.t(.reportDiary), systemImage: "exclamationmark.bubble")
                        }
                    } label: {
                        Image(systemName: "ellipsis")
                    }
                    .tint(Theme.mint)
                }
            }
        }
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
            MusicManager.shared.playOpenDiary() // 별(다이어리) 열람 효과음
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
    }

    private var bodyCard: some View {
        VStack(spacing: 10) {
            if canOpen {
                Text(diary.content.isEmpty ? "내용이 없어요." : diary.content)
                    .font(.poorStory(17))
                    .foregroundStyle(Theme.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Image(systemName: "lock.fill").foregroundStyle(Theme.textFaint)
                Text("이 별 가까이(\(Int(AppConfig.diaryOpenRadiusM))m)로 가면 열람할 수 있어요.")
                    .font(.poorStory(15))
                    .foregroundStyle(Theme.textSecondary)
                    .multilineTextAlignment(.center)
                Text("현재 약 \(distanceLabel(distanceM)) 떨어져 있어요.")
                    .font(.poorStory(12))
                    .foregroundStyle(Theme.textFaint)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16))
    }

    private var likeBar: some View {
        HStack(spacing: 20) {
            Button {
                // 비로그인 시 좋아요 잠금 — 로그인 안내만.
                guard auth.uid != nil else { showLoginRequired = true; return }
                Task { await vm.toggleLike(uid: auth.uid, userName: auth.displayName) }
            } label: {
                Label("\(vm.likeCount)", systemImage: vm.isLiked ? "heart.fill" : "heart")
                    .foregroundStyle(vm.isLiked ? .pink : Theme.textSecondary)
            }
            Label("\(visibleComments.count)", systemImage: "bubble.right.fill")
                .foregroundStyle(Theme.textSecondary)
            Label("\(diary.viewCount)", systemImage: "eye.fill")
                .foregroundStyle(Theme.textSecondary)
            Spacer()
        }
        .font(.poorStory(17))
    }

    private var commentsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                TextField("댓글 달기…", text: $commentText, axis: .vertical)
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
                .padding(12)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
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
        .overlay(Circle().stroke(Theme.mint.opacity(0.30), lineWidth: 1))
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
