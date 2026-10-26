import AVFoundation
import CoreLocation
import PhotosUI
import SwiftUI
import UIKit

/// 올리기 탭 — 현재 위치에 별(다이어리)을 남긴다(사진 첨부 포함).
/// 화면 순서(Android UploadScreen 패리티): 쓰던 글 띠 → 첨부(없으면 얇은 한 줄) → 별 도장 + 제목 → 본문
/// → 별 모양/색 휠(이름 옆 '모두 보기' 격자 시트) → 공개 범위 → 아래 고정 저장 버튼.
/// 칸마다 테두리 상자를 두르지 않고 면·밑줄·여백으로만 나눈다.
struct UploadScreen: View {
    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var store: DiaryStore
    @EnvironmentObject var location: LocationManager
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    /// 배경 시차 스크롤(관찰하지 않는다 — 배경만 다시 그린다).

    @State private var bgScroll = BackgroundScroll()

    @State private var title = ""
    @State private var content = ""
    @State private var visibility: Visibility = .publicAll
    @State private var starType = 1
    @State private var starColor = 9
    /// 별 고리(우주 항해, 2026-10-26) — 이 별에만 두른다. 빈 값 = 미착용. 칸은 고리를 가진 사람에게만 보인다.
    @State private var ring = ""
    /// 우주 항해에서 찾은 별 모양·색 — 휠 끝에 덧붙인다. 고리 칸도 여기서 가진 고리를 읽는다.
    @ObservedObject private var starlight = StarlightStore.shared
    /// 가진 것만 보기 — 잠긴 칸을 휠에서 뺀다(한 번 켜 두면 기억). 지금 고른 칸은 잠겨 있어도 남긴다.
    @AppStorage(StarPickerPrefs.ownedOnlyKey) private var ownedOnly = false
    /// '모두 보기' 시트 — 0 = 모양, 1 = 색.
    @State private var pickerOpen = false
    @State private var pickerTab = 0
    /// 쓰다 나간 글 — 열 때 한 번 읽어 맨 위에 "이어 쓰기" 띠를 띄운다(고르기 전엔 아무것도 덮어쓰지 않는다).
    @State private var pendingDraft: UploadDraft?
    @State private var draftChecked = false
    @State private var showLeaveConfirm = false
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
    /// 업로드 위치 실패 이유 — 안내 팝업 내용(팝업이 닫히는 동안 문구가 바뀌지 않게 표시 여부와 따로 둔다).
    @State private var locationProblem: LocationManager.UploadFix = .notFound
    @State private var showLocationProblem = false
    /// 권한 팝업에서 "허용하기" → 시스템 창에서 허용되면 바로 다시 올린다(Android 권한 요청 후 submit() 패리티).
    @State private var retryAfterAuth = false
    /// 키보드 내리기용 포커스 — 제목/내용 입력 후 키보드가 안 내려가던 문제(#3) 대응.
    @FocusState private var focusedField: Field?

    private enum Field: Hashable { case title, content }

    /// 전체 화면으로 띄우는 촬영 화면 종류.
    private enum CaptureSheet: String, Identifiable {
        case camera, boomerang
        var id: String { rawValue }
    }

    /// 입력칸·첨부 줄의 바탕 — 테두리 없이 반투명 남색 면(별하늘 배경이 은은히 비친다). Android FieldFill.
    private let fieldFill = Color(hex: 0x181E34, alpha: 0.6)
    private let placeholder = Theme.textSecondary.opacity(0.7)

    /// 현재 해금된 업적 id 집합(내 다이어리 + 친구 수 기반).
    /// ⚠️ 전체 별을 훑는 계산이라 본문에서 **한 번만** 구해 넘긴다(예전엔 휠 칸마다 다시 계산했다).
    private var unlocked: Set<String> {
        Achievements.unlockedIds(
            Achievements.computeStats(diaries: store.mine(uid: auth.uid),
                                      friendsCount: friendsCount, viewedCount: 0,
                                      uid: auth.uid, allDiaries: store.diaries, unlockedIds: Set(DiaryUnlockStore.shared.unlockedAt.keys))
        )
    }

    private var hasText: Bool { draftNow.hasText }
    private var hasMedia: Bool { !crop.isEmpty || loadingMedia }
    /// 쓴 글이 있거나 시트가 떠 있으면 뒤로가기를 직접 받는다(시스템 버튼·밀어서 뒤로는 묻지 않고 나가 버린다).
    private var guardsBack: Bool { pickerOpen || (hasText && !saving) }

    // 루트(MainTabView)의 단일 NavigationStack 에 push 되므로 자체 스택은 두지 않는다(Android 단일 NavHost 대응).
    var body: some View {
        let unlocked = self.unlocked
        // ⚠️ 배경은 ZStack 형제가 아니라 .background 로 — ScreenBackground 의 ignoresSafeArea 가
        //    ZStack(=스크롤 영역)을 키보드 영역까지 늘리면, 키보드가 올라와도 입력칸이 안 밀려 가려진다.
        return ScrollViewReader { proxy in
            ScrollView {
                form(unlocked)
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 8)
            }
            .backgroundScrollSource(bgScroll)
            // 스크롤(드래그)로도 키보드가 내려가게 — 긴 본문 입력 후 탈출구(#3).
            .scrollDismissesKeyboard(.interactively)
            // 제목/본문을 누르면 키보드가 다 올라온 뒤 그 칸을 보이는 영역 가운데로(키보드에 가려지던 문제).
            .scrollToFocused(focusedField, proxy: proxy)
        }
        // 토스트는 저장 버튼 위에 뜨게 버튼 자리(safeAreaInset)보다 안쪽에 단다.
        .overlay(alignment: .bottom) {
            if let toast { ToastView(text: toast) }
        }
        // 저장 — 화면 아래 고정(스크롤해도 늘 보이고, 키보드가 올라오면 그 위로).
        .safeAreaInset(edge: .bottom, spacing: 0) { saveBar }
        // 업로드 화면 배경 — Android upload_bg 이미지 + 검정 0.82 틴트 대응.
        .background { ScreenBackground(name: "upload_bg", darken: 0.82, scroll: bgScroll) }
        .overlay {
            StarPickerSheet(isPresented: $pickerOpen, tab: $pickerTab,
                            starType: starType, starColor: starColor,
                            shapeIds: allShapeIds, colorIds: allColorIds,
                            unlocked: unlocked, ownedOnly: $ownedOnly,
                            onPickShape: { starType = $0 }, onPickColor: { starColor = $0 })
        }
        .navigationTitle(LocaleManager.shared.t(.navUpload))
        .navigationBarTitleDisplayMode(.inline)
        // (키보드 위 "완료" 버튼은 삭제 — 2026-10-02 사용자 결정. 본문은 리턴키가 줄바꿈이라
        //  닫기는 바깥 영역 탭(KeyboardDismissOnTap) 또는 스크롤 드래그로 한다.)
        .navigationBarBackButtonHidden(guardsBack)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) { backButton }
        }
        .staryDialog(isPresented: $showLeaveConfirm) { leaveDialog }
        // 업로드 위치 실패 안내 — 이유별 행동(권한 요청·설정 / 위치 설정 / 다시 시도).
        .staryConfirmDialog(locationProblemText.title,
                            isPresented: $showLocationProblem,
                            message: locationProblemText.message,
                            confirmTitle: locationProblemText.action) {
            handleLocationProblem()
        }
        .onChange(of: location.authorized) { ok in
            guard ok, retryAfterAuth else { return }
            retryAfterAuth = false
            Task { await save() }
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
        // 첨부 소스 선택 — Android 의 촬영/갤러리/3초 영상 3지선다 다이얼로그 패리티(아이콘까지 동일).
        .staryChoiceDialog(LocaleManager.shared.t(.uploadAddPhoto),
                           isPresented: $showMediaSourceSheet,
                           options: [
                               StaryDialogOption(LocaleManager.shared.t(.uploadTakePhoto),
                                                 systemImage: "camera.fill",
                                                 action: { launchCamera() }),
                               StaryDialogOption(LocaleManager.shared.t(.uploadPickGallery),
                                                 systemImage: "photo.fill",
                                                 action: { showPhotosPicker = true }),
                               StaryDialogOption(LocaleManager.shared.t(.uploadCaptureBoomerang),
                                                 systemImage: "video.fill",
                                                 action: { captureSheet = .boomerang }),
                           ])
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
        .onAppear {
            StaryAnalytics.log("upload_open", ["logged_in": auth.uid != nil])
            if !draftChecked {
                draftChecked = true
                pendingDraft = UploadDraftStore.load(uid: draftUid)
            }
        }
        // 뒤로·알림으로 이동·앱 전환 어느 쪽으로 나가도 쓴 글은 기기에 남긴다(Android ON_STOP/onDispose 패리티).
        .onDisappear { keepDraft() }
        .onChange(of: scenePhase) { phase in
            if phase == .background { keepDraft() }
        }
        .task {
            if let uid = auth.uid {
                let snap = try? await FirestoreService.friends(of: uid).getDocuments()
                friendsCount = snap?.documents.count ?? 0
            }
        }
    }

    private func form(_ unlocked: Set<String>) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            // 새로 쓰기 시작하면 숨긴다 — 그 상태에서 눌러 지금 쓴 글이 옛 글로 덮이지 않게.
            if let draft = pendingDraft, !hasText {
                draftStrip(draft)
                    .transition(.opacity)
            }
            // 사진 추가/다시 선택/삭제 + 크롭 드래그까지 한 번에 막는다.
            // (크롭은 커스텀 DragGesture 라 .disabled() 로는 안 막혀 allowsHitTesting 을 쓴다 — WheelPicker 와 동일.)
            mediaSection
                .allowsHitTesting(!saving)
            // 업로드(저장) 진행 중에는 제목·본문·첨부를 모두 잠근다 — 저장에 쓰인 값과
            // 화면에 보이는 값이 어긋나지 않게(공개 범위·별 모양/색과 동일한 정책).
            titleField
                .padding(.top, 26)
                .id(Field.title)
            bodyField
                .padding(.top, 14)
                .id(Field.content)
            hairline
                .padding(.top, 18)
                .padding(.bottom, 20)
            sectionHeader(LocaleManager.shared.t(.uploadStarShape)) { openPicker(0) }
            starPicker(unlocked)
                .padding(.top, 6)
            sectionHeader(LocaleManager.shared.t(.uploadStarColor)) { openPicker(1) }
                .padding(.top, 14)
            colorPicker(unlocked)
                .padding(.top, 6)
            OwnedOnlyToggle(isOn: $ownedOnly)
                .frame(maxWidth: .infinity)
                .padding(.top, 6)
            // ── 별 고리 — 우주 항해에서 찾은 고리가 있을 때만. 고른 고리는 이 별에만 둘린다(Android 패리티) ──
            let rings = starlight.ownedOf("aura")
            if !rings.isEmpty {
                sectionHeader(LocaleManager.shared.t(.uploadRing))
                    .padding(.top, 14)
                RingPicker(rings: rings, selected: $ring, starType: starType, starColor: starColor, enabled: !saving)
                    .padding(.top, 8)
            }
            hairline
                .padding(.top, 16)
                .padding(.bottom, 20)
            sectionHeader(LocaleManager.shared.t(.uploadVisibility))
            // 업로드(저장) 진행 중에는 공개 범위 변경 불가 — Android UploadScreen 과 동일.
            VisibilityChooser(selection: $visibility, enabled: !saving)
                .padding(.top, 10)
        }
        .animation(.easeOut(duration: 0.2), value: pendingDraft != nil && !hasText)
    }

    // MARK: - 첨부

    /// 첨부 — 없으면 얇은 한 줄, 고르면 상세 화면과 같은 4:3 크롭 프레임(사진·3초 움짤 공용).
    @ViewBuilder private var mediaSection: some View {
        if hasMedia {
            VStack(alignment: .leading, spacing: 4) {
                // 이 프레임이 곧 잘릴 영역 — 드래그로 위치, 두 손가락으로 확대/축소.
                ZStack(alignment: .topTrailing) {
                    MediaCropFrame(state: crop)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    clearMediaButton
                }
                HStack(spacing: 8) {
                    Text(LocaleManager.shared.t(.uploadCropHint))
                        .font(.minSans(11))
                        .foregroundStyle(Theme.textSecondary)
                    Spacer(minLength: 0)
                    // 첨부 다시 고르기 — Android upload_reselect 대응. 업로드 중에는 흐려진다(잠금은 mediaSection 에서).
                    Button { openMediaSourceSheet() } label: {
                        Text(LocaleManager.shared.t(.uploadReselect))
                            .font(.minSans(13))
                            .foregroundStyle(Theme.navyAccent.opacity(saving ? 0.5 : 1))
                            .padding(.vertical, 8)
                            .padding(.leading, 8)
                    }
                    .buttonStyle(.plain)
                }
            }
        } else {
            addMediaRow
        }
    }

    /// 첨부가 없을 때의 얇은 한 줄 — 누르면 촬영/갤러리/3초 영상 고르기. 오른쪽 작은 아이콘이 세 가지를 알려 준다.
    private var addMediaRow: some View {
        Button { openMediaSourceSheet() } label: {
            HStack(spacing: 0) {
                Image(systemName: "photo.badge.plus")
                    .font(.system(size: 18))
                    .foregroundStyle(Theme.navyAccent)
                Text(LocaleManager.shared.t(.uploadAddPhoto))
                    .font(.minSans(14))
                    .foregroundStyle(Theme.textPrimary)
                    .padding(.leading, 12)
                Spacer(minLength: 8)
                ForEach(["camera.fill", "photo.fill", "video.fill"], id: \.self) { name in
                    Image(systemName: name)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.textSecondary.opacity(0.7))
                        .padding(.leading, 8)
                }
            }
            .padding(.horizontal, 16)
            .frame(height: 56)
            .background(fieldFill, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
        .opacity(saving ? 0.5 : 1)
    }

    private var clearMediaButton: some View {
        Button { clearMedia() } label: {
            Image(systemName: "xmark")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 30, height: 30)
                .background(Color.black.opacity(0.45), in: Circle())
        }
        .buttonStyle(.plain)
        .padding(8)
        .accessibilityLabel(LocaleManager.shared.t(.uploadClearMedia))
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

    private func clearMedia() {
        photoItem = nil
        crop.clear()
    }

    // MARK: - 제목·본문

    /// 별 도장 + 제목 한 줄 + 밑줄(누르고 있으면 별빛색). 도장은 휠을 돌리면 같이 바뀐다.
    private var titleField: some View {
        VStack(alignment: .trailing, spacing: 0) {
            HStack(spacing: 12) {
                StarView(type: starType, colorIndex: starColor, size: 26, glow: false)
                TextField("", text: $title,
                          prompt: Text(LocaleManager.shared.t(.fieldTitle)).foregroundColor(placeholder))
                    .textFieldStyle(.plain)
                    .font(.minSans(20, .semibold))
                    .foregroundStyle(Theme.textPrimary)
                    .tint(Theme.navyAccent)
                    .focused($focusedField, equals: .title)
                    .submitLabel(.next)                       // 한 줄 입력 → 리턴키로 본문으로
                    .onSubmit { focusedField = .content }
                    .onChange(of: title) { v in
                        if v.count > AppConfig.diaryTitleMaxLen { title = String(v.prefix(AppConfig.diaryTitleMaxLen)) }
                    }
                    .padding(.vertical, 10)
            }
            Rectangle()
                .fill(focusedField == .title ? Theme.navyAccent.opacity(0.7) : Theme.outline)
                .frame(height: 1)
                .animation(.easeOut(duration: 0.2), value: focusedField == .title)
            counter(title.count, AppConfig.diaryTitleMaxLen)
        }
        .disabled(saving)
        .opacity(saving ? 0.5 : 1)
    }

    /// 본문 — 바탕 없이 글만(편지지처럼). 빈 곳을 눌러도 입력이 시작되게 최소 높이를 둔다.
    private var bodyField: some View {
        VStack(alignment: .trailing, spacing: 0) {
            TextField("", text: $content,
                      prompt: Text(LocaleManager.shared.t(.uploadContentLabel)).foregroundColor(placeholder),
                      axis: .vertical)
                .textFieldStyle(.plain)
                .font(.minSans(15))
                .lineSpacing(6)
                .foregroundStyle(Theme.textPrimary)
                .tint(Theme.navyAccent)
                .lineLimit(5...)
                .focused($focusedField, equals: .content)
                .onChange(of: content) { v in
                    if v.count > AppConfig.diaryContentMaxLen { content = String(v.prefix(AppConfig.diaryContentMaxLen)) }
                }
                .frame(maxWidth: .infinity, minHeight: 132, alignment: .topLeading)
            counter(content.count, AppConfig.diaryContentMaxLen)
        }
        .disabled(saving)
        .opacity(saving ? 0.5 : 1)
    }

    private func counter(_ length: Int, _ max: Int) -> some View {
        Text("\(length)/\(max)")
            .font(.minSans(11))
            .foregroundStyle(placeholder)
            .padding(.top, 4)
    }

    // MARK: - 별 모양/색

    /// 휠에 놓을 별 모양 id — 기본 모양 + 성운 상자에서 얻은 새 모양(Android `allShapeIds` 패리티).
    private var allShapeIds: [Int] {
        Array(0..<StarStyle.typeCount) + starlight.ownedShapeTypes.filter { $0 >= StarStyle.typeCount }
    }

    /// 휠에 놓을 별 색 id — 기본 색 + 성운 상자에서 얻은 새 색.
    private var allColorIds: [Int] {
        Array(0..<StarStyle.colorCount) + starlight.ownedColorIndices.filter { $0 >= StarStyle.colorCount }
    }

    /// 휠은 자리(0..<count)로 돌고, 자리 ↔ 실제 id 는 여기서 바꾼다.
    private func positionBinding(_ ids: [Int], _ value: Binding<Int>) -> Binding<Int> {
        Binding(
            get: { ids.firstIndex(of: value.wrappedValue) ?? 0 },
            set: { p in if ids.indices.contains(p) { value.wrappedValue = ids[p] } }
        )
    }

    private func openPicker(_ tab: Int) {
        focusedField = nil
        pickerTab = tab
        pickerOpen = true
    }

    private func toastLocked(_ a: Achievement) {
        showToast(String(format: LocaleManager.shared.t(.toastUnlockAchievement), LocalizedNames.title(a.id, fallback: a.name) ?? a.name))
    }

    private func starPicker(_ unlocked: Set<String>) -> some View {
        let ids = ownedOnly
            ? allShapeIds.filter { $0 == starType || StarUnlocks.lockedShapeAch($0, unlocked) == nil }
            : allShapeIds
        return WheelPicker(
            count: ids.count,
            selection: positionBinding(ids, $starType),
            itemSize: 40,
            isLocked: { StarUnlocks.lockedShapeAch(ids[$0], unlocked) != nil },
            onLocked: { p in
                if let a = StarUnlocks.lockedShapeAch(ids[p], unlocked) { toastLocked(a) }
            },
            // 업로드(저장) 진행 중에는 별 모양도 공개 범위와 마찬가지로 바꿀 수 없게 잠근다.
            enabled: !saving
        ) { p in
            let t = ids[p]
            let locked = StarUnlocks.lockedShapeAch(t, unlocked) != nil
            ZStack {
                StarView(type: t, colorIndex: starColor, size: 40, glow: false)
                    .opacity(locked ? 0.25 : 1)
                if locked {
                    Image(systemName: "lock.fill").font(.caption2).foregroundStyle(Theme.textSecondary)
                }
            }
        }
    }

    private func colorPicker(_ unlocked: Set<String>) -> some View {
        let ids = ownedOnly
            ? allColorIds.filter { $0 == starColor || StarUnlocks.lockedColorAch($0, unlocked) == nil }
            : allColorIds
        return WheelPicker(
            count: ids.count,
            selection: positionBinding(ids, $starColor),
            itemSize: 32,
            isLocked: { StarUnlocks.lockedColorAch(ids[$0], unlocked) != nil },
            onLocked: { p in
                if let a = StarUnlocks.lockedColorAch(ids[p], unlocked) { toastLocked(a) }
            },
            // 업로드(저장) 진행 중에는 별 색도 바꿀 수 없게 잠근다.
            enabled: !saving
        ) { p in
            let c = ids[p]
            let locked = StarUnlocks.lockedColorAch(c, unlocked) != nil
            ZStack {
                Circle()
                    .fill(StarStyle.fill(c))
                    .frame(width: 32, height: 32)
                    .opacity(locked ? 0.25 : 1)
                if locked {
                    Image(systemName: "lock.fill").font(.system(size: 11)).foregroundStyle(Theme.textPrimary)
                }
            }
        }
    }

    // MARK: - 저장 버튼 · 뒤로가기 · 쓰던 글

    /// 위쪽 여백은 글이 버튼 밑으로 녹아 사라지는 자리 — 칼같이 잘려 보이지 않게(Android fadeBottomEdge 대응).
    private var saveBar: some View {
        StaryButton(LocaleManager.shared.t(.commonSave), fullWidth: true, enabled: !title.isEmpty, loading: saving) {
            Task { await save() }
        }
        .padding(.horizontal, 20)
        .padding(.top, 24)
        .padding(.bottom, 12)
        .background {
            LinearGradient(stops: [.init(color: .black.opacity(0), location: 0),
                                   .init(color: .black.opacity(0.78), location: 0.4)],
                           startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea(edges: .bottom)
                .allowsHitTesting(false)
        }
    }

    /// 쓴 글이 있을 때만 대신 그리는 뒤로가기(시스템 버튼과 같은 모양·색) — 누르면 시트를 닫거나 한 번 묻는다.
    @ViewBuilder private var backButton: some View {
        if guardsBack {
            Button { goBack() } label: {
                Image(systemName: "chevron.backward")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color(hex: 0xF0F0F0))
                    .padding(.vertical, 6)
                    .padding(.trailing, 12)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel(LocaleManager.shared.t(.cdBack))
        }
    }

    private func goBack() {
        if pickerOpen { pickerOpen = false; return }
        focusedField = nil
        showLeaveConfirm = true
    }

    private var leaveDialog: some View {
        StaryDialogCard(title: LocaleManager.shared.t(.uploadLeaveTitle),
                        message: LocaleManager.shared.t(.uploadLeaveMsg)) {
            EmptyView()
        } actions: {
            StaryButton(LocaleManager.shared.t(.uploadLeaveExit), kind: .ghost, compact: true) {
                showLeaveConfirm = false
                keepDraft()
                // 팝업(전체 화면 커버)이 다 닫힌 뒤에 화면을 닫는다 — 닫히는 중에 pop 하면 무시되는 경우가 있다.
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { dismiss() }
            }
            StaryButton(LocaleManager.shared.t(.uploadLeaveStay), compact: true) {
                showLeaveConfirm = false
            }
        }
    }

    /// 쓰다 나간 글이 있을 때 맨 위 한 줄 — 이어 쓰기 / 지우기.
    private func draftStrip(_ draft: UploadDraft) -> some View {
        HStack(spacing: 0) {
            Image(systemName: "square.and.pencil")
                .font(.system(size: 15))
                .foregroundStyle(Theme.navyAccent)
            Text(LocaleManager.shared.t(.uploadDraftFound))
                .font(.minSans(13))
                .foregroundStyle(Theme.textPrimary)
                .padding(.leading, 10)
            Spacer(minLength: 6)
            Button {
                UploadDraftStore.clear(uid: draftUid)
                pendingDraft = nil
            } label: {
                Text(LocaleManager.shared.t(.uploadDraftDiscard))
                    .font(.minSans(13))
                    .foregroundStyle(Theme.textSecondary)
                    .padding(10)
            }
            Button { restore(draft) } label: {
                Text(LocaleManager.shared.t(.uploadDraftRestore))
                    .font(.minSans(13, .semibold))
                    .foregroundStyle(Theme.navyAccent)
                    .padding(10)
            }
        }
        .buttonStyle(.plain)
        .padding(.leading, 14)
        .padding(.trailing, 4)
        .background(Theme.navyAccent.opacity(0.10), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .padding(.bottom, 14)
    }

    private var draftUid: String { auth.uid ?? "guest" }

    private var draftNow: UploadDraft {
        UploadDraft(title: title, content: content, starType: starType, starColor: starColor,
                    visibility: visibility.rawValue, ring: ring.isEmpty ? nil : ring)
    }

    /// 쓴 글이 있으면 기기에 남긴다. 저장에 성공하면 제목·본문을 비우므로 그 뒤엔 남기지 않는다.
    private func keepDraft() {
        let d = draftNow
        if d.hasText { UploadDraftStore.save(d, uid: draftUid) }
    }

    private func restore(_ d: UploadDraft) {
        title = d.title
        content = d.content
        starType = d.starType
        starColor = d.starColor
        visibility = Visibility(type: d.visibility)
        ring = d.ring ?? ""
        pendingDraft = nil
    }

    // MARK: - 올리기

    private func save() async {
        // 둘러보기(게스트)는 작성 불가 — Android common_login_required 안내와 동일.
        guard let uid = auth.uid else {
            showToast(LocaleManager.shared.t(.commonLoginRequired)); return
        }
        // 본문이 비면 올리지 않는다(최소 1자) — 공백·줄바꿈만 있는 것도 빈 것으로 본다. Android 패리티.
        if content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            uploadFail("no_content"); showToast(LocaleManager.shared.t(.toastContentRequired)); return
        }
        // 부적절한 표현 필터(App Store 1.2) — 제목·본문 중 하나라도 걸리면 올리지 않는다. Android 패리티.
        if ContentFilter.anyObjectionable(title, content) {
            uploadFail("filter"); showToast(LocaleManager.shared.t(.contentBlocked)); return
        }
        // 잠긴 별 모양/색이 다이얼 중앙에 온 채 저장되지 않도록 차단(#7).
        let unlocked = self.unlocked
        if let a = StarUnlocks.lockedShapeAch(starType, unlocked) {
            toastLocked(a); return
        }
        if let a = StarUnlocks.lockedColorAch(starColor, unlocked) {
            toastLocked(a); return
        }
        // 하루 업로드 제한 — 오늘(로컬 자정 이후) 내가 올린 개수로 선차단.
        let startOfDay = Int64(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970 * 1000)
        let todayCount = store.mine(uid: uid).filter { $0.createdAt >= startOfDay }.count
        if todayCount >= AppConfig.dailyUploadLimit {
            uploadFail("daily_limit")
            showToast(String(format: LocaleManager.shared.t(.uploadDailyLimit), AppConfig.dailyUploadLimit))
            return
        }
        saving = true
        defer { saving = false }
        // 실제 위치(fix)가 없으면 올리지 않는다 — 예전엔 기본좌표(서울)로 저장돼 엉뚱한 곳에 별이 생겼다.
        // 첨부 업로드 전에 막아 Storage 에 주인 없는 파일이 남지 않게. 2026-10-23 완화: 최근 위치도 쓰고 잠시 기다리며,
        // 실패하면 토스트 대신 이유별 안내(권한 허용 / 위치 켜기 / 다시 시도)를 띄운다. (Android UploadScreen 패리티)
        let fix = await location.locateForUpload()
        guard case let .ok(coord) = fix else {
            switch fix {
            case .noPermission: uploadFail("no_location_permission")
            case .locationOff: uploadFail("no_location_off")
            case .mock: uploadFail("no_location_mock")
            default: uploadFail("no_location")
            }
            locationProblem = fix
            showLocationProblem = true
            return
        }
        // 올리기 성공 순간의 축하 진동(StarBirthStore.trigger → Haptics.celebrate)이 확실히 울리도록
        // 지금 탭틱 엔진을 깨워 둔다 — 예열 없이 부르면 첫 진동이 씹힌다(사용자 요청 #5).
        Haptics.prepare()
        // 작성자 이름은 서버에서 확인한 닉네임 — 기기 첫 실행 직후 구글 기본 이름이 박히지 않게(CLAUDE.md §2.6).
        // 첨부 업로드 전에 확인해 Storage 에 주인 없는 파일이 남지 않게 한다. (Android requireConfirmedName 패리티)
        guard let authorName = await auth.confirmedNameOrWait() else {
            uploadFail("profile_not_ready"); showToast(LocaleManager.shared.t(.profileNotReady)); return
        }

        // 첨부는 움짤/사진 중 하나(배타). 둘 다 **지금 프레임에 보이는 그대로** 잘라서 올린다.
        // 움짤(GIF)은 videoUrl 필드에 저장(스키마 유지, .gif 로 판별).
        var imageUrl = ""
        var videoUrl = ""
        if crop.isAnimated {
            guard let data = await encodeBoomerangGif() else {
                uploadFail("gif_encode"); showToast(LocaleManager.shared.t(.boomerFailed)); return
            }
            do { videoUrl = try await ImageUploader.uploadGif(data) }
            catch { uploadFail("media_upload"); showToast(String(format: LocaleManager.shared.t(.toastImageUploadFailed), error.localizedDescription)); return }
        } else if let image = crop.first {
            // 크롭 실패(디코딩 오류)면 원본을 그대로 올려 흐름이 끊기지 않게 한다.
            let data = ImageCrop.crop(image, frame: crop.frameSize, scale: crop.scale, offset: crop.offset,
                                      outWidth: ImageCrop.diaryOutPixels)
                ?? image.jpegData(compressionQuality: 0.9)
            guard let data else {
                uploadFail("media_upload"); showToast(String(format: LocaleManager.shared.t(.toastImageUploadFailed), "")); return
            }
            do { imageUrl = try await ImageUploader.upload(data) }
            catch { uploadFail("media_upload"); showToast(String(format: LocaleManager.shared.t(.toastImageUploadFailed), error.localizedDescription)); return }
        }

        // 겹쳐 올리기 퀘스트 — 저장 전에 판정(저장 뒤엔 방금 올린 별이 목록에 섞인다). Android UploadScreen 패리티.
        let stacked = store.diaries.contains { d in
            Geo.distanceMeters(coord, CLLocationCoordinate2D(latitude: d.latitude, longitude: d.longitude)) <= AppConfig.starMergeRadiusM
        }
        let diary = Diary(
            userId: uid,
            userName: authorName,
            title: title,
            content: content,
            imageUrl: imageUrl,
            videoUrl: videoUrl,
            latitude: coord.latitude,
            longitude: coord.longitude,
            createdAt: FirestoreService.nowMillis,
            starType: starType,
            starColor: starColor,
            visibilityType: visibility.rawValue,
            // 지금 가진 고리만(서버 checkDiaryRing 도 소유를 다시 확인한다).
            ring: starlight.ownedOf("aura").contains { $0.id == ring } ? ring : ""
        )
        do {
            let newId = try await store.save(diary)
            StaryAnalytics.log("upload_success", ["media": !diary.imageUrl.isEmpty || !diary.videoUrl.isEmpty])
            // 올렸으니 쓰다 둔 글은 지운다(제목·본문도 비워 화면이 닫힐 때 다시 남기지 않는다).
            UploadDraftStore.clear(uid: uid)
            pendingDraft = nil
            // 전체/친구 공개 글이면 친구들에게 알림(나만보기는 제외).
            if visibility != .privateOnly {
                await store.notifyFriends(uid: uid, name: authorName, diaryId: newId, title: title)
            }
            title = ""; content = ""; clearMedia()
            showToast(LocaleManager.shared.t(.uploadDone))
            // 별 탄생 연출(34-8) — 지도 탭으로 돌아가 방금 심은 별이 태어나는 연출을 지도 위에서 재생.
            // (Android 는 업로드 화면이 pop 되며 지도 위에서 재생 — 같은 동선.) 실패 경로에선 호출하지 않는다.
            StarBirthStore.shared.trigger(starType: starType, starColor: starColor)
            // 내 별에 반응이 올 수 있으니 알림을 켤지 물을 차례(연출이 끝난 뒤 지도에서 — PermissionAsk).
            PermissionAsk.shared.requestNotificationAsk()
            // 일일 퀘스트 — 올리기(시간대 퀘스트도 이 이벤트, 올린 시각으로 판정) + 사진/영상(Android 와 같은 규칙).
            QuestStore.report(.upload)
            if !diary.imageUrl.isEmpty || !diary.videoUrl.isEmpty { QuestStore.report(.uploadMedia) }
            if stacked { QuestStore.report(.uploadStack) }
            TabRouter.shared.go(TabRouter.map)
            // 개척 퀘스트 선점 시도(체크리스트 32) — 화면 이탈과 무관하게 진행, 실패는 무시.
            Task.detached {
                await PioneerStore.attemptClaim(
                    lat: diary.latitude, lng: diary.longitude, uid: uid, name: authorName
                )
            }
        } catch {
            uploadFail("save")
            showToast(String(format: LocaleManager.shared.t(.toastImageUploadFailed), error.localizedDescription))
        }
    }

    private var locationProblemText: (title: String, message: String, action: String) {
        let l = LocaleManager.shared
        switch locationProblem {
        case .noPermission: return (l.t(.uploadLocPermTitle), l.t(.uploadLocPermMsg), l.t(.uploadLocPermBtn))
        case .locationOff: return (l.t(.uploadLocOffTitle), l.t(.uploadLocOffMsg), l.t(.uploadLocOffBtn))
        case .mock: return (l.t(.uploadLocMockTitle), l.t(.uploadLocMockMsg), l.t(.uploadLocRetry))
        default: return (l.t(.uploadLocNoneTitle), l.t(.uploadLocNoneMsg), l.t(.uploadLocRetry))
        }
    }

    private func handleLocationProblem() {
        switch locationProblem {
        case .noPermission where location.permissionNotDetermined:
            retryAfterAuth = true
            location.requestPermission()
        case .noPermission, .locationOff:
            // 이미 거부했으면 시스템 창이 다시 안 뜬다 → 설정 앱(이 앱 화면)에서 위치를 허용하게.
            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
        default:
            Task { await save() }
        }
    }

    /// 올리기 실패 이벤트(이유별) — 첫 별을 못 올리고 나가는 지점을 찾으려고(Android `uploadFail` 패리티).
    private func uploadFail(_ reason: String) {
        StaryAnalytics.log("upload_fail", ["reason": reason])
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

    /// 묶음 이름(왼쪽) + '모두 보기'(오른쪽, 있을 때만).
    private func sectionHeader(_ label: String, onSeeAll: (() -> Void)? = nil) -> some View {
        HStack(spacing: 0) {
            Text(label)
                .font(.minSans(12, .medium))
                .foregroundStyle(Theme.textSecondary)
            Spacer(minLength: 8)
            if let onSeeAll {
                Button(action: onSeeAll) {
                    Text(LocaleManager.shared.t(.uploadSeeAll))
                        .font(.minSans(13, .medium))
                        .foregroundStyle(Theme.navyAccent.opacity(saving ? 0.5 : 1))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 4)
                }
                .buttonStyle(.plain)
                .disabled(saving)
            }
        }
    }

    private var hairline: some View {
        Rectangle()
            .fill(Theme.outline.opacity(0.7))
            .frame(height: 1)
    }
}

/// 별 고리 고르기 — 맨 앞 "미착용", 뒤로 가진 고리(지금 고른 모양·색 별에 둘러 미리 보여 준다).
/// 고른 칸은 고리 등급 빛 테두리. 지도에선 이 별에만 같은 고리가 돈다(MapStyleEffects 고리 스프라이트). Android `RingPicker` 패리티.
private struct RingPicker: View {
    let rings: [StarlightStore.Item]
    @Binding var selected: String
    let starType: Int
    let starColor: Int
    let enabled: Bool
    @ObservedObject private var locale = LocaleManager.shared

    var body: some View {
        let chosen = rings.contains { $0.id == selected } ? selected : ""
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                cell(locale.t(.uploadRingNone), chosen.isEmpty, Theme.navyAccent, "") {
                    StarView(type: starType, colorIndex: starColor, size: 26, glow: false)
                }
                ForEach(rings) { item in
                    cell(item.name(in: locale.effectiveLanguage), chosen == item.id, StarlightArt.rarityColor(item.rarity), item.id) {
                        AuraArt(item: item, starType: starType, starColor: starColor)
                    }
                }
            }
        }
        .opacity(enabled ? 1 : 0.5)
        .disabled(!enabled)
    }

    private func cell<Art: View>(_ label: String, _ on: Bool, _ accent: Color, _ id: String,
                                 @ViewBuilder art: () -> Art) -> some View {
        let picture = art()
        let shape = RoundedRectangle(cornerRadius: 16)
        return Button {
            guard selected != id else { return }
            Haptics.tick()
            selected = id
        } label: {
            VStack(spacing: 4) {
                picture.frame(width: 56, height: 56)
                Text(label)
                    .font(.minSans(11, on ? .semibold : .normal))
                    .foregroundStyle(on ? Theme.textPrimary : Theme.textSecondary)
                    .lineLimit(1)
            }
            .padding(.vertical, 8).padding(.horizontal, 4)
            .frame(width: 72)
            .background(on ? accent.opacity(0.12) : Color(hex: 0x181E34, alpha: 0.6), in: shape)
            .overlay(shape.strokeBorder(on ? accent.opacity(0.8) : .clear, lineWidth: 1.5))
            .contentShape(shape)
        }
        .buttonStyle(.plain)
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
