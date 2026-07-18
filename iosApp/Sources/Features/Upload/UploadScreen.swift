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
    @State private var visibility: Visibility = .publicAll
    @State private var starType = 1
    @State private var starColor = 9
    @State private var saving = false
    @State private var toast: String?
    @State private var photoItem: PhotosPickerItem?
    @State private var imageData: Data?
    // 부메랑(3초 움짤) GIF — 커스텀 촬영 화면에서 생성(이미지와 배타).
    @State private var boomerangGif: Data?
    @State private var showBoomerangCapture = false
    @State private var friendsCount = 0

    /// 현재 해금된 업적 id 집합(내 다이어리 + 친구 수 기반).
    private var unlocked: Set<String> {
        Achievements.unlockedIds(
            Achievements.computeStats(diaries: store.mine(uid: auth.uid),
                                      friendsCount: friendsCount, viewedCount: 0)
        )
    }

    // 루트(MainTabView)의 단일 NavigationStack 에 push 되므로 자체 스택은 두지 않는다(Android 단일 NavHost 대응).
    var body: some View {
        ZStack {
            // 업로드 화면 배경 — Android upload_bg 이미지 + 검정 0.82 틴트 대응.
            ScreenBackground(name: "upload_bg", darken: 0.82)
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    preview
                    field(LocaleManager.shared.t(.fieldTitle)) {
                        TextField("", text: $title).textFieldStyle(.plain)
                            .onChange(of: title) { v in
                                if v.count > AppConfig.diaryTitleMaxLen { title = String(v.prefix(AppConfig.diaryTitleMaxLen)) }
                            }
                    }
                    field(LocaleManager.shared.t(.uploadContentLabel)) {
                        TextField("", text: $content, axis: .vertical)
                            .lineLimit(4...8)
                            .onChange(of: content) { v in
                                if v.count > AppConfig.diaryContentMaxLen { content = String(v.prefix(AppConfig.diaryContentMaxLen)) }
                            }
                    }
                    photoSection
                    starPicker
                    colorPicker
                    visibilityPicker
                    saveButton
                }
                .padding(16)
            }
        }
        .navigationTitle(LocaleManager.shared.t(.navUpload))
        .navigationBarTitleDisplayMode(.inline)
        .overlay(alignment: .bottom) {
            if let toast { ToastView(text: toast) }
        }
        .onChange(of: photoItem) { item in
            Task {
                if let data = try? await item?.loadTransferable(type: Data.self) {
                    imageData = data
                    boomerangGif = nil // 이미지 선택 시 움짤과 배타
                }
            }
        }
        // 부메랑(3초 움짤) 커스텀 촬영 — 전체 화면
        .fullScreenCover(isPresented: $showBoomerangCapture) {
            BoomerangCaptureView { data in
                boomerangGif = data
                imageData = nil; photoItem = nil // 이미지와 배타
                showBoomerangCapture = false
            }
        }
        .task {
            if let uid = auth.uid {
                let snap = try? await FirestoreService.friends(of: uid).getDocuments()
                friendsCount = snap?.documents.count ?? 0
            }
        }
    }

    private var preview: some View {
        HStack {
            Spacer()
            VStack(spacing: 8) {
                StarView(type: starType, colorIndex: starColor, size: 72)
                Text(LocaleManager.shared.t(.uploadPreview)).font(.poorStory(11)).foregroundStyle(Theme.textFaint)
            }
            Spacer()
        }
        .padding(.vertical, 8)
    }

    private var photoSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            label(LocaleManager.shared.t(.uploadPhotoSection))
            if let boomerangGif {
                ZStack(alignment: .topTrailing) {
                    GifImageView(data: boomerangGif)
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
                        mediaAddLabel(icon: "photo.on.rectangle.angled",
                                      text: LocaleManager.shared.t(.uploadAddPhoto))
                    }
                    // 파일 선택 대신 커스텀 촬영 화면으로(부메랑 3초 움짤)
                    Button { showBoomerangCapture = true } label: {
                        mediaAddLabel(icon: "infinity",
                                      text: LocaleManager.shared.t(.uploadCaptureBoomerang))
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

    private func clearMedia() {
        imageData = nil; photoItem = nil
        boomerangGif = nil
    }

    private var starPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            label(LocaleManager.shared.t(.uploadStarShape))
            WheelPicker(
                count: StarStyle.typeCount,
                selection: $starType,
                itemSize: 40,
                isLocked: { StarUnlocks.lockedShapeAch($0, unlocked) != nil },
                onLocked: { t in
                    if let a = StarUnlocks.lockedShapeAch(t, unlocked) {
                        showToast(String(format: LocaleManager.shared.t(.toastUnlockAchievement), a.name))
                    }
                }
            ) { t in
                ZStack {
                    StarView(type: t, colorIndex: starColor, size: 40, glow: false)
                        .opacity(StarUnlocks.lockedShapeAch(t, unlocked) == nil ? 1 : 0.25)
                    if StarUnlocks.lockedShapeAch(t, unlocked) != nil {
                        Image(systemName: "lock.fill").font(.caption2).foregroundStyle(Theme.textSecondary)
                    }
                }
            }
        }
    }

    private var colorPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            label(LocaleManager.shared.t(.uploadStarColor))
            WheelPicker(
                count: StarStyle.colorCount,
                selection: $starColor,
                itemSize: 32,
                isLocked: { StarUnlocks.lockedColorAch($0, unlocked) != nil },
                onLocked: { c in
                    if let a = StarUnlocks.lockedColorAch(c, unlocked) {
                        showToast(String(format: LocaleManager.shared.t(.toastUnlockAchievement), a.name))
                    }
                }
            ) { c in
                ZStack {
                    Circle()
                        .fill(StarStyle.fill(c))
                        .frame(width: 32, height: 32)
                        .opacity(StarUnlocks.lockedColorAch(c, unlocked) == nil ? 1 : 0.25)
                    if StarUnlocks.lockedColorAch(c, unlocked) != nil {
                        Image(systemName: "lock.fill").font(.system(size: 11)).foregroundStyle(Theme.textPrimary)
                    }
                }
            }
        }
    }

    private var visibilityPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            label(LocaleManager.shared.t(.uploadVisibility))
            Picker("", selection: $visibility) {
                ForEach(Visibility.allCases, id: \.self) { Text(visLabel($0)).tag($0) }
            }
            .pickerStyle(.segmented)
        }
    }

    /// 공개 범위 라벨(현재 언어) — Android upload_vis_* 대응.
    private func visLabel(_ v: Visibility) -> String {
        switch v {
        case .publicAll: return LocaleManager.shared.t(.uploadVisPublic)
        case .friends: return LocaleManager.shared.t(.uploadVisFriends)
        case .privateOnly: return LocaleManager.shared.t(.uploadVisPrivate)
        }
    }

    private var saveButton: some View {
        Button {
            Task { await save() }
        } label: {
            Group {
                if saving {
                    StarLoadingView(size: 20, color: .black)
                } else {
                    Text(LocaleManager.shared.t(.commonSave))
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(Theme.navyAccent, in: RoundedRectangle(cornerRadius: 14))
            .foregroundStyle(Color.black)
            .font(.poorStory(17))
        }
        .disabled(saving || title.isEmpty)
        .opacity(title.isEmpty ? 0.5 : 1)
    }

    private func save() async {
        guard let uid = auth.uid else { return }
        // 잠긴 별 모양/색이 다이얼 중앙에 온 채 저장되지 않도록 차단(#7).
        if let a = StarUnlocks.lockedShapeAch(starType, unlocked) {
            showToast(String(format: LocaleManager.shared.t(.toastUnlockAchievement), a.name)); return
        }
        if let a = StarUnlocks.lockedColorAch(starColor, unlocked) {
            showToast(String(format: LocaleManager.shared.t(.toastUnlockAchievement), a.name)); return
        }
        // 하루 업로드 제한 — 오늘(로컬 자정 이후) 내가 올린 개수로 선차단.
        let startOfDay = Int64(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970 * 1000)
        let todayCount = store.mine(uid: uid).filter { $0.createdAt >= startOfDay }.count
        if todayCount >= AppConfig.dailyUploadLimit {
            showToast(String(format: LocaleManager.shared.t(.uploadDailyLimit), AppConfig.dailyUploadLimit))
            return
        }
        saving = true
        defer { saving = false }

        // 첨부는 움짤/사진 중 하나(배타). 움짤(GIF)은 videoUrl 필드에 저장(스키마 유지, .gif 로 판별).
        var imageUrl = ""
        var videoUrl = ""
        if let boomerangGif {
            do { videoUrl = try await ImageUploader.uploadGif(boomerangGif) }
            catch { showToast(String(format: LocaleManager.shared.t(.toastImageUploadFailed), error.localizedDescription)); return }
        } else if let imageData {
            do { imageUrl = try await ImageUploader.upload(imageData) }
            catch { showToast(String(format: LocaleManager.shared.t(.toastImageUploadFailed), error.localizedDescription)); return }
        }

        let coord = location.coordinateOrDefault
        let diary = Diary(
            userId: uid,
            userName: auth.displayName,
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
            showToast(LocaleManager.shared.t(.uploadDone))
            // 별 탄생 연출(34-8) — 지도 탭으로 돌아가 방금 심은 별이 태어나는 연출을 지도 위에서 재생.
            // (Android 는 업로드 화면이 pop 되며 지도 위에서 재생 — 같은 동선.) 실패 경로에선 호출하지 않는다.
            StarBirthStore.shared.trigger(starType: starType, starColor: starColor)
            TabRouter.shared.go(TabRouter.map)
            // 개척 퀘스트 선점 시도(체크리스트 32) — 화면 이탈과 무관하게 진행, 실패는 무시.
            let myName = auth.displayName
            Task.detached {
                await PioneerStore.attemptClaim(
                    lat: diary.latitude, lng: diary.longitude, uid: uid, name: myName
                )
            }
        } catch {
            showToast(String(format: LocaleManager.shared.t(.toastImageUploadFailed), error.localizedDescription))
        }
    }

    private func showToast(_ text: String) {
        toast = text
        Task { try? await Task.sleep(nanoseconds: 1_800_000_000); toast = nil }
    }

    // MARK: - 작은 헬퍼

    private func label(_ t: String) -> some View {
        Text(t).font(.poorStory(12)).foregroundStyle(Theme.textSecondary)
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

/// 별 모양/색 선택용 무한 회전 휠(#13) — 선택 항목이 항상 정중앙, 좌우로 5개 정도 노출,
/// 끝까지 돌리면 멈추지 않고 처음 항목이 다시 나온다(modulo 순환). (Android 미참고, iOS 자체 설계)
private struct WheelPicker<Content: View>: View {
    let count: Int
    @Binding var selection: Int
    let itemSize: CGFloat
    var isLocked: (Int) -> Bool = { _ in false }
    var onLocked: (Int) -> Void = { _ in }
    @ViewBuilder let item: (Int) -> Content

    /// 슬롯 간격 — 5개(중앙 ±2)가 보이도록 넉넉히.
    private var slot: CGFloat { itemSize + 28 }
    @State private var drag: CGFloat = 0
    @State private var settling = false

    private func wrap(_ i: Int) -> Int { ((i % count) + count) % count }

    /// 화면에 그릴 슬롯 반경 — 기본 3(±3=중앙+양옆) + 현재 드래그 이동량만큼 여유.
    private var visibleSpan: Int { 3 + Int((abs(drag) / slot).rounded(.up)) }

    var body: some View {
        ZStack {
            // 중앙 강조 링(선택 자리).
            Circle()
                .stroke(Theme.navyAccent.opacity(0.9), lineWidth: 2)
                .frame(width: itemSize + 18, height: itemSize + 18)

            // 많이 드래그해도 빈 공간이 안 생기도록, 현재 드래그 양만큼 슬롯 수를 늘려 그린다.
            // (아이콘은 modulo 순환이라 계속 돌려도 항상 채워진다.)
            ForEach(-visibleSpan...visibleSpan, id: \.self) { off in
                let idx = wrap(selection + off)
                let x = CGFloat(off) * slot + drag
                let dist = abs(x)
                let scale = max(0.55, 1.25 - dist / slot * 0.35)
                item(idx)
                    .scaleEffect(scale)
                    .opacity(Double(max(0.3, 1 - dist / (slot * 2.2))))
                    .offset(x: x)
                    .onTapGesture { commit(steps: off) }
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: itemSize * 1.7)
        .contentShape(Rectangle())
        .clipped()
        .gesture(
            DragGesture()
                .onChanged { g in
                    guard !settling else { return }
                    drag = g.translation.width
                }
                .onEnded { g in
                    let steps = Int((-g.translation.width / slot).rounded())
                    commit(steps: steps)
                }
        )
    }

    /// 놓은(또는 탭한) 지점의 항목을 **놓은 자리에서 그대로 중앙으로** 부드럽게 스냅한다.
    /// selection 은 애니메이션이 끝난 뒤 한 번에 갱신 → 좌표계 불연속(되돌아가는 현상)이 없다.
    /// 잠긴 항목도 중앙에 고정되며, 저장은 UploadScreen 에서 별도로 막는다.
    private func commit(steps: Int) {
        guard steps != 0 else { return }
        settling = true
        let target = wrap(selection + steps)
        // 현재 위치에서 목표 슬롯이 중앙(x=0)에 오도록 drag 를 이동.
        withAnimation(.easeOut(duration: 0.24)) { drag = -CGFloat(steps) * slot }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.24) {
            selection = target      // off=steps 였던 항목이 이제 off=0
            drag = 0                // 좌표 동일 → 시각적 점프 없음
            settling = false
            if isLocked(target) { onLocked(target) }
        }
    }
}

struct ToastView: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.poorStory(15))
            .padding(.horizontal, 16).padding(.vertical, 10)
            .background(.ultraThinMaterial, in: Capsule())
            .padding(.bottom, 24)
    }
}
