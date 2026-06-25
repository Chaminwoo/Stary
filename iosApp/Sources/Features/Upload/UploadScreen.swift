import SwiftUI

/// 올리기 탭 — 현재 위치에 별(다이어리)을 남긴다.
/// (사진 첨부는 추후: Storage 업로드 + PhotosPicker(iOS16+) 가용성 처리 필요 — IOS_RELEASE 6절)
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

    private var starPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            label("모양")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(0..<StarStyle.typeCount, id: \.self) { t in
                        StarView(type: t, colorIndex: starColor, size: 34, glow: false)
                            .padding(8)
                            .background(starType == t ? Theme.mint.opacity(0.2) : Color.clear,
                                        in: Circle())
                            .onTapGesture { starType = t }
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
                        Circle()
                            .fill(StarStyle.fill(c))
                            .frame(width: 28, height: 28)
                            .overlay(Circle().stroke(Theme.mint, lineWidth: starColor == c ? 3 : 0))
                            .onTapGesture { starColor = c }
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
        let coord = location.coordinateOrDefault
        let diary = Diary(
            userId: uid,
            userName: auth.displayName,
            isAnonymous: isAnonymous,
            title: title,
            content: content,
            latitude: coord.latitude,
            longitude: coord.longitude,
            createdAt: FirestoreService.nowMillis,
            starType: starType,
            starColor: starColor,
            visibilityType: visibility.rawValue
        )
        do {
            try await store.save(diary)
            title = ""; content = ""
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
