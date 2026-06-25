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
                    imageData = try? await item?.loadTransferable(type: Data.self)
                }
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
            label("사진 (선택)")
            if let imageData, let ui = UIImage(data: imageData) {
                ZStack(alignment: .topTrailing) {
                    Image(uiImage: ui)
                        .resizable()
                        .scaledToFill()
                        .frame(height: 180)
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    Button {
                        self.imageData = nil
                        self.photoItem = nil
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.white, .black.opacity(0.5))
                            .padding(8)
                    }
                }
            } else {
                PhotosPicker(selection: $photoItem, matching: .images) {
                    HStack {
                        Image(systemName: "photo.on.rectangle.angled")
                        Text("사진 추가")
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
                    .foregroundStyle(Theme.textSecondary)
                }
            }
        }
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
        saving = true
        defer { saving = false }

        // 사진이 있으면 먼저 Storage 업로드.
        var imageUrl = ""
        if let imageData {
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
            title = ""; content = ""; imageData = nil; photoItem = nil
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
