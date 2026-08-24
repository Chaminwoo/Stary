import AVFoundation
import PhotosUI
import SwiftUI
import UIKit

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
    /// 첨부(사진 1장 / 3초 움짤) 크롭 상태 — 고른 직후부터 프레임 안에서 위치·확대를 조절한다.
    /// 사진과 움짤은 배타(하나만 첨부)이며 같은 프레임/같은 조작을 쓴다.
    @StateObject private var crop = MediaCropState()
    /// 사진을 고르는 중(디코딩 대기) — 빈 프레임에 로딩을 띄우기 위한 플래그.
    @State private var loadingMedia = false
    // 첨부 소스 선택(촬영/갤러리/3초 영상) — Android showImageSourceDialog 대응.
    @State private var showMediaSourceSheet = false
    @State private var showPhotosPicker = false
    /// 전체 화면 촬영(사진/움짤) — nil 이면 닫힘.
    @State private var captureSheet: CaptureSheet?
    @State private var friendsCount = 0
    /// 키보드 내리기용 포커스 — 제목/내용 입력 후 키보드가 안 내려가던 문제(#3) 대응.
    @FocusState private var focusedField: Field?

    private enum Field: Hashable { case title, content }

    /// 전체 화면으로 띄우는 촬영 화면 종류.
    private enum CaptureSheet: String, Identifiable {
        case camera, boomerang
        var id: String { rawValue }
    }

    /// 현재 해금된 업적 id 집합(내 다이어리 + 친구 수 기반).
    private var unlocked: Set<String> {
        Achievements.unlockedIds(
            Achievements.computeStats(diaries: store.mine(uid: auth.uid),
                                      friendsCount: friendsCount, viewedCount: 0)
        )
    }

    // 루트(MainTabView)의 단일 NavigationStack 에 push 되므로 자체 스택은 두지 않는다(Android 단일 NavHost 대응).
    var body: some View {
        // ⚠️ 배경은 ZStack 형제가 아니라 .background 로 — ScreenBackground 의 ignoresSafeArea 가
        //    ZStack(=스크롤 영역)을 키보드 영역까지 늘리면, 키보드가 올라와도 입력칸이 안 밀려 가려진다.
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                preview
                field(LocaleManager.shared.t(.fieldTitle)) {
                    TextField("", text: $title).textFieldStyle(.plain)
                        .focused($focusedField, equals: .title)
                        .submitLabel(.done)                      // 한 줄 입력 → 리턴키로 바로 닫기
                        .onSubmit { focusedField = nil }
                        .onChange(of: title) { v in
                            if v.count > AppConfig.diaryTitleMaxLen { title = String(v.prefix(AppConfig.diaryTitleMaxLen)) }
                        }
                }
                field(LocaleManager.shared.t(.uploadContentLabel)) {
                    TextField("", text: $content, axis: .vertical)
                        .lineLimit(4...8)
                        .focused($focusedField, equals: .content)
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
        // 스크롤(드래그)로도 키보드가 내려가게 — 긴 본문 입력 후 탈출구(#3).
        .scrollDismissesKeyboard(.interactively)
        // 업로드 화면 배경 — Android upload_bg 이미지 + 검정 0.82 틴트 대응.
        .background { ScreenBackground(name: "upload_bg", darken: 0.82) }
        .navigationTitle(LocaleManager.shared.t(.navUpload))
        .navigationBarTitleDisplayMode(.inline)
        // 키보드 위 "완료" — 여러 줄 입력(본문)은 리턴키가 줄바꿈이라 이 버튼이 유일한 닫기 수단(#3).
        // 작아서 누르기 어렵다는 피드백(2026-08-15) → 캡슐 버튼으로 키우고 키보드/가장자리에서 살짝 띄운다.
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button { focusedField = nil } label: {
                    Text(LocaleManager.shared.t(.commonDone))
                        .font(.minSans(17, .semibold))
                        .foregroundStyle(Color(hex: 0x0D0D0D))
                        .padding(.horizontal, 24)
                        .padding(.vertical, 9)
                        .background(Theme.mint, in: Capsule())
                }
                .buttonStyle(.plain)
                .padding(.trailing, 8)
                .padding(.bottom, 6)
            }
        }
        .overlay(alignment: .bottom) {
            if let toast { ToastView(text: toast) }
        }
        .onChange(of: photoItem) { item in
            guard item != nil else { return }
            loadingMedia = true
            Task {
                defer { loadingMedia = false }
                guard let data = try? await item?.loadTransferable(type: Data.self),
                      let ui = UIImage(data: data) else { return }
                crop.setPhoto(ui)   // 사진 선택 시 움짤과 배타(같은 상태를 덮어쓴다)
            }
        }
        // 첨부 소스 선택 — Android 의 촬영/갤러리/3초 영상 3지선다 다이얼로그 패리티(#1).
        .confirmationDialog(LocaleManager.shared.t(.uploadAddPhoto),
                            isPresented: $showMediaSourceSheet, titleVisibility: .visible) {
            Button(LocaleManager.shared.t(.uploadTakePhoto)) { launchCamera() }
            Button(LocaleManager.shared.t(.uploadPickGallery)) { showPhotosPicker = true }
            Button(LocaleManager.shared.t(.uploadCaptureBoomerang)) { captureSheet = .boomerang }
            Button(LocaleManager.shared.t(.commonCancel), role: .cancel) {}
        }
        .photosPicker(isPresented: $showPhotosPicker, selection: $photoItem, matching: .images)
        // 촬영 화면(사진 1장 / 3초 움짤)은 **하나의** fullScreenCover 로 — 같은 뷰에 커버를 두 개 달면
        // 한쪽이 안 열리는 사례가 있어 item 방식으로 합쳤다.
        .fullScreenCover(item: $captureSheet) { which in
            switch which {
            case .camera:
                CameraPicker { data in
                    if let data, let ui = UIImage(data: data) {
                        crop.setPhoto(ui)
                        photoItem = nil // 움짤과 배타
                    }
                    captureSheet = nil
                }
                .ignoresSafeArea()
            case .boomerang:
                // 촬영 화면은 GIF 가 아니라 **원본 프레임 + 크롭 상태**를 넘긴다 —
                // 여기서도 위치·확대를 더 조절할 수 있고, GIF 는 저장 직전에 한 번만 굽는다.
                BoomerangCaptureView { frames, startScale, offsetNorm in
                    crop.setBoomerang(frames, scale: startScale, offsetNorm: offsetNorm)
                    photoItem = nil // 이미지와 배타
                    captureSheet = nil
                }
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
                Text(LocaleManager.shared.t(.uploadPreview)).font(.minSans(11)).foregroundStyle(Theme.textFaint)
            }
            Spacer()
        }
        .padding(.vertical, 8)
    }

    private var photoSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            label(LocaleManager.shared.t(.uploadPhotoSection))
            if !crop.isEmpty || loadingMedia {
                // 사진도 3초 움짤도 **같은 4:3 프레임**(Android 와 동일 크기/비율).
                // 이 프레임이 곧 잘릴 영역 — 드래그로 위치, 두 손가락으로 확대/축소.
                ZStack(alignment: .topTrailing) {
                    MediaCropFrame(state: crop)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    clearMediaButton
                }
                Text(LocaleManager.shared.t(.uploadCropHint))
                    .font(.minSans(11))
                    .foregroundStyle(Theme.textFaint)
                reselectButton
            } else {
                // 한 버튼 → 촬영/갤러리/3초 영상 선택 시트(Android 와 동일한 3지선다).
                Button { openMediaSourceSheet() } label: {
                    mediaAddLabel(icon: "camera.fill",
                                  text: LocaleManager.shared.t(.uploadAddPhoto))
                }
            }
        }
    }

    /// 첨부 다시 고르기 — Android upload_reselect 대응.
    private var reselectButton: some View {
        Button { openMediaSourceSheet() } label: {
            Text(LocaleManager.shared.t(.uploadReselect))
                .font(.minSans(13))
                .foregroundStyle(Theme.textPrimary)
        }
    }

    /// 첨부 소스 시트 열기 — 열기 전에 키보드를 내려 시트가 키보드 위로 겹치지 않게 한다.
    private func openMediaSourceSheet() {
        focusedField = nil
        showMediaSourceSheet = true
    }

    /// 카메라 촬영 — 기기 지원/권한 확인 후 촬영 화면. (Android launchCamera + 권한 토스트 패리티)
    private func launchCamera() {
        guard CameraPicker.isAvailable else {
            showToast(LocaleManager.shared.t(.toastCameraUnavailable)); return
        }
        CameraPicker.requestPermission { granted in
            if granted { captureSheet = .camera }
            else { showToast(LocaleManager.shared.t(.toastCameraPermission)) }
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
        photoItem = nil
        crop.clear()
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
            .font(.minSans(17))
        }
        .disabled(saving || title.isEmpty)
        .opacity(title.isEmpty ? 0.5 : 1)
    }

    private func save() async {
        // 둘러보기(게스트)는 작성 불가 — Android common_login_required 안내와 동일.
        guard let uid = auth.uid else {
            showToast(LocaleManager.shared.t(.commonLoginRequired)); return
        }
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
        // 올리기 성공 순간의 축하 진동(StarBirthStore.trigger → Haptics.celebrate)이 확실히 울리도록
        // 지금 탭틱 엔진을 깨워 둔다 — 예열 없이 부르면 첫 진동이 씹힌다(사용자 요청 #5).
        Haptics.prepare()

        // 첨부는 움짤/사진 중 하나(배타). 둘 다 **지금 프레임에 보이는 그대로** 잘라서 올린다.
        // 움짤(GIF)은 videoUrl 필드에 저장(스키마 유지, .gif 로 판별).
        var imageUrl = ""
        var videoUrl = ""
        if crop.isAnimated {
            guard let data = await encodeBoomerangGif() else {
                showToast(LocaleManager.shared.t(.boomerFailed)); return
            }
            do { videoUrl = try await ImageUploader.uploadGif(data) }
            catch { showToast(String(format: LocaleManager.shared.t(.toastImageUploadFailed), error.localizedDescription)); return }
        } else if let image = crop.first {
            // 크롭 실패(디코딩 오류)면 원본을 그대로 올려 흐름이 끊기지 않게 한다.
            let data = ImageCrop.crop(image, frame: crop.frameSize, scale: crop.scale, offset: crop.offset,
                                      outWidth: ImageCrop.diaryOutPixels)
                ?? image.jpegData(compressionQuality: 0.9)
            guard let data else {
                showToast(String(format: LocaleManager.shared.t(.toastImageUploadFailed), "")); return
            }
            do { imageUrl = try await ImageUploader.upload(data) }
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

    /// 3초 움짤을 지금 크롭 상태 그대로 잘라 GIF 로 굽는다(백그라운드). 실패하면 nil.
    private func encodeBoomerangGif() async -> Data? {
        let frames = crop.frames
        let fw = crop.frameSize.width > 0 ? crop.frameSize.width : UIScreen.main.bounds.width
        let fh = crop.frameSize.height > 0 ? crop.frameSize.height : fw / BoomerangConfig.aspect
        let scale = crop.scale
        let offset = crop.offset
        return await Task.detached(priority: .userInitiated) {
            let cropped = BoomerangConfig.cropFrames(frames, frameW: fw, frameH: fh, scale: scale, offset: offset)
            let seq = BoomerangConfig.boomerangSequence(cropped)
            return BoomerangConfig.encodeGif(frames: seq, delay: BoomerangConfig.frameDelay)
        }.value
    }

    private func showToast(_ text: String) {
        toast = text
        Task { try? await Task.sleep(nanoseconds: 1_800_000_000); toast = nil }
    }

    // MARK: - 작은 헬퍼

    private func label(_ t: String) -> some View {
        Text(t).font(.minSans(12)).foregroundStyle(Theme.textSecondary)
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
    /// 드래그 중 슬롯을 지날 때마다 딸깍(햅틱) — 중복 방지용 마지막 눈금.
    @State private var tickedStep = 0

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
                    let step = Int((-drag / slot).rounded())
                    if step != tickedStep {
                        tickedStep = step
                        Haptics.tick()
                    }
                }
                .onEnded { g in
                    let steps = Int((-g.translation.width / slot).rounded())
                    tickedStep = 0
                    commit(steps: steps)
                }
        )
    }

    /// 놓은(또는 탭한) 지점의 항목을 **놓은 자리에서 그대로 중앙으로** 부드럽게 스냅한다.
    /// selection 은 애니메이션이 끝난 뒤 한 번에 갱신 → 좌표계 불연속(되돌아가는 현상)이 없다.
    /// 잠긴 항목도 중앙에 고정되며, 저장은 UploadScreen 에서 별도로 막는다.
    private func commit(steps: Int) {
        // 반 슬롯을 못 넘긴 드래그(steps=0)도 반드시 원위치로 되돌린다 —
        // 그냥 return 하면 drag 가 남아 항목과 항목 **사이**에 멈춘 채 고정된다(#2).
        guard steps != 0 else {
            if drag != 0 { withAnimation(.easeOut(duration: 0.18)) { drag = 0 } }
            return
        }
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
            .font(.minSans(15))
            .padding(.horizontal, 16).padding(.vertical, 10)
            .background(.ultraThinMaterial, in: Capsule())
            .padding(.bottom, 24)
    }
}
