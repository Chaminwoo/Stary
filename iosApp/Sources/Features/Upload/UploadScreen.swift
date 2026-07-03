import AVFoundation
import PhotosUI
import SwiftUI

/// 올리기 탭 — 현재 위치에 별(다이어리)을 남긴다(사진 첨부 포함).
struct UploadScreen: View {
    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var location: LocationManager

    @State private var title = ""
    @State private var content = ""
    @State private var isAnonymous = false
    @State private var visibility: Visibility = .publicAll
    @State private var starType = 1
    @State private var starColor = 9
    @State private var saving = false
    @State private var toast: String?
    @State private var photoItem: PhotosPickerItem?
    @State private var imageData: Data?
    // 3초 이내 짧은 영상(이미지와 배타). videoLocalURL 은 미리보기용 임시 파일.
    @State private var videoItem: PhotosPickerItem?
    @State private var videoData: Data?
    @State private var videoLocalURL: URL?
    @State private var videoContentType = "video/mp4"
    @State private var friendsCount = 0

    /// 현재 해금된 업적 id 집합(내 다이어리 + 친구 수 기반).
    private var unlocked: Set<String> {
        Achievements.unlockedIds(
            Achievements.computeStats(diaries: store.mine(uid: auth.uid),
                                      friendsCount: friendsCount, viewedCount: 0)
        )
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Theme.background.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 22) {
                        preview
                        field("제목") { TextField("", text: $title).textFieldStyle(.plain) }
                        field("내용") {
                            TextField("", text: $content, axis: .vertical)
                                .lineLimit(4...8)
                        }
                        photoSection
                        starPicker
                        colorPicker
                        visibilityPicker
                        Toggle("익명으로 남기기", isOn: $isAnonymous)
                            .tint(Theme.mint)
                            .foregroundStyle(Theme.textSecondary)
                        saveButton
                    }
                    .padding(16)
                }
            }
            .navigationTitle("별 남기기")
            .navigationBarTitleDisplayMode(.inline)
            .overlay(alignment: .bottom) {
                if let toast { ToastView(text: toast) }
            }
            .onChange(of: photoItem) { item in
                Task {
                    if let data = try? await item?.loadTransferable(type: Data.self) {
                        imageData = data
                        clearVideo() // 이미지 선택 시 영상과 배타
                    }
                }
            }
            .onChange(of: videoItem) { item in
                Task { await handlePickedVideo(item) }
            }
            .task {
                if let uid = auth.uid {
                    let snap = try? await FirestoreService.friends(of: uid).getDocuments()
                    friendsCount = snap?.documents.count ?? 0
                }
            }
        }
    }

    private var preview: some View {
        HStack {
            Spacer()
            VStack(spacing: 8) {
                StarView(type: starType, colorIndex: starColor, size: 72)
                Text("미리보기").font(.caption2).foregroundStyle(Theme.textFaint)
            }
            Spacer()
        }
        .padding(.vertical, 8)
    }

    private var photoSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            label("사진 · 영상 (선택)")
            if let videoLocalURL {
                ZStack(alignment: .topTrailing) {
                    LoopingVideoPlayer(url: videoLocalURL, muted: true)
                        .frame(height: 180)
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    clearMediaButton
                }
            } else if let imageData, let ui = UIImage(data: imageData) {
                ZStack(alignment: .topTrailing) {
                    Image(uiImage: ui)
                        .resizable()
                        .scaledToFill()
                        .frame(height: 180)
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    clearMediaButton
                }
            } else {
                HStack(spacing: 10) {
                    PhotosPicker(selection: $photoItem, matching: .images) {
                        mediaAddLabel(icon: "photo.on.rectangle.angled", text: "사진 추가")
                    }
                    PhotosPicker(selection: $videoItem, matching: .videos) {
                        mediaAddLabel(icon: "video.badge.plus", text: "동영상 (3초 이내)")
                    }
                }
            }
        }
    }

    private func mediaAddLabel(icon: String, text: String) -> some View {
        HStack {
            Image(systemName: icon)
            Text(text)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
        .foregroundStyle(Theme.textSecondary)
    }

    private var clearMediaButton: some View {
        Button { clearMedia() } label: {
            Image(systemName: "xmark.circle.fill")
                .foregroundStyle(.white, .black.opacity(0.5))
                .padding(8)
        }
    }

    /// 선택된 영상을 3초 이내인지 검증하고, 통과 시 보관(초과 시 안내 후 취소). 이미지와 배타.
    private func handlePickedVideo(_ item: PhotosPickerItem?) async {
        guard let item, let data = try? await item.loadTransferable(type: Data.self) else { return }
        let tmp = FileManager.default.temporaryDirectory
            .appendingPathComponent("upload_\(UUID().uuidString).mov")
        do { try data.write(to: tmp) } catch { return }
        let asset = AVURLAsset(url: tmp)
        let durSec = (try? await asset.load(.duration))?.seconds ?? 0
        if durSec * 1000 > Double(AppConfig.videoMaxDurationMs) {
            try? FileManager.default.removeItem(at: tmp)
            videoItem = nil
            showToast("3초 이내의 짧은 영상만 올릴 수 있어요")
            return
        }
        videoData = data
        videoLocalURL = tmp
        videoContentType = item.supportedContentTypes.first?.preferredMIMEType ?? "video/quicktime"
        imageData = nil; photoItem = nil // 이미지와 배타
    }

    private func clearMedia() {
        imageData = nil; photoItem = nil
        clearVideo()
    }

    private func clearVideo() {
        if let u = videoLocalURL { try? FileManager.default.removeItem(at: u) }
        videoData = nil; videoLocalURL = nil; videoItem = nil
    }

    private var starPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            label("모양")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(0..<StarStyle.typeCount, id: \.self) { t in
                        let lockedAch = StarUnlocks.lockedShapeAch(t, unlocked)
                        ZStack {
                            StarView(type: t, colorIndex: starColor, size: 34, glow: false)
                                .opacity(lockedAch == nil ? 1 : 0.25)
                            if lockedAch != nil {
                                Image(systemName: "lock.fill").font(.caption2).foregroundStyle(Theme.textSecondary)
                            }
                        }
                        .padding(8)
                        .background(starType == t ? Theme.mint.opacity(0.2) : Color.clear, in: Circle())
                        .onTapGesture {
                            if let a = lockedAch { showToast("‘\(a.name)’ 업적으로 해금돼요") }
                            else { starType = t }
                        }
                    }
                }
            }
        }
    }

    private var colorPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            label("색")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(0..<StarStyle.colorCount, id: \.self) { c in
                        let lockedAch = StarUnlocks.lockedColorAch(c, unlocked)
                        ZStack {
                            Circle()
                                .fill(StarStyle.fill(c))
                                .frame(width: 28, height: 28)
                                .opacity(lockedAch == nil ? 1 : 0.25)
                                .overlay(Circle().stroke(Theme.mint, lineWidth: starColor == c ? 3 : 0))
                            if lockedAch != nil {
                                Image(systemName: "lock.fill").font(.system(size: 10)).foregroundStyle(Theme.textPrimary)
                            }
                        }
                        .onTapGesture {
                            if let a = lockedAch { showToast("‘\(a.name)’ 업적으로 해금돼요") }
                            else { starColor = c }
                        }
                    }
                }
            }
        }
    }

    private var visibilityPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            label("공개 범위")
            Picker("", selection: $visibility) {
                ForEach(Visibility.allCases, id: \.self) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)
        }
    }

    private var saveButton: some View {
        Button {
            Task { await save() }
        } label: {
            Text(saving ? "남기는 중…" : "이 자리에 별 남기기")
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Theme.mint, in: RoundedRectangle(cornerRadius: 14))
                .foregroundStyle(Color.black)
                .font(.headline)
        }
        .disabled(saving || title.isEmpty)
        .opacity(title.isEmpty ? 0.5 : 1)
    }

    private func save() async {
        guard let uid = auth.uid else { return }
        // 하루 업로드 제한 — 오늘(로컬 자정 이후) 내가 올린 개수로 선차단.
        let startOfDay = Int64(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970 * 1000)
        let todayCount = store.mine(uid: uid).filter { $0.createdAt >= startOfDay }.count
        if todayCount >= AppConfig.dailyUploadLimit {
            showToast("오늘 올릴 수 있는 별 \(AppConfig.dailyUploadLimit)개를 모두 사용했어요")
            return
        }
        saving = true
        defer { saving = false }

        // 첨부는 영상/사진 중 하나(배타). 영상이 있으면 영상 우선 업로드.
        var imageUrl = ""
        var videoUrl = ""
        if let videoData {
            do { videoUrl = try await ImageUploader.uploadVideo(videoData, contentType: videoContentType) }
            catch { showToast("영상 업로드 실패: \(error.localizedDescription)"); return }
        } else if let imageData {
            do { imageUrl = try await ImageUploader.upload(imageData) }
            catch { showToast("사진 업로드 실패: \(error.localizedDescription)"); return }
        }

        let coord = location.coordinateOrDefault
        let diary = Diary(
            userId: uid,
            userName: auth.displayName,
            isAnonymous: isAnonymous,
            title: title,
            content: content,
            imageUrl: imageUrl,
            videoUrl: videoUrl,
            latitude: coord.latitude,
            longitude: coord.longitude,
            createdAt: FirestoreService.nowMillis,
            starType: starType,
            starColor: starColor,
            visibilityType: visibility.rawValue
        )
        do {
            let newId = try await store.save(diary)
            // 전체/친구 공개 글이면 친구들에게 알림(나만보기는 제외).
            if visibility != .privateOnly {
                await store.notifyFriends(uid: uid, name: auth.displayName, diaryId: newId, title: title)
            }
            title = ""; content = ""; clearMedia()
            showToast("별을 남겼어요 ✨")
        } catch {
            showToast("저장 실패: \(error.localizedDescription)")
        }
    }

    private func showToast(_ text: String) {
        toast = text
        Task { try? await Task.sleep(nanoseconds: 1_800_000_000); toast = nil }
    }

    // MARK: - 작은 헬퍼

    private func label(_ t: String) -> some View {
        Text(t).font(.caption).foregroundStyle(Theme.textSecondary)
    }

    private func field<Content: View>(_ t: String, @ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            label(t)
            content()
                .padding(12)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
                .foregroundStyle(Theme.textPrimary)
        }
    }
}

struct ToastView: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.subheadline)
            .padding(.horizontal, 16).padding(.vertical, 10)
            .background(.ultraThinMaterial, in: Capsule())
            .padding(.bottom, 24)
    }
}
