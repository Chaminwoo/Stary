import SwiftUI

/// 설정 화면 — 배경음악/효과음 볼륨, 알림 팝업 on/off, 언어 변경. (Android SettingsScreen 패리티)
/// 값은 [MusicManager]/[AppSettings]/[LocaleManager] 에 즉시 저장되어 전 화면에 반영된다.
struct SettingsScreen: View {
    @EnvironmentObject var auth: AuthManager
    @ObservedObject private var music = MusicManager.shared
    @ObservedObject private var settings = AppSettings.shared
    @ObservedObject private var locale = LocaleManager.shared
    @State private var showLanguagePicker = false
    @State private var showDeleteConfirm = false
    @State private var showDeleteFailed = false
    @State private var deleting = false

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    // ── 사운드 ──
                    sectionLabel(locale.t(.settingsSound), "music.note")
                    glassCard {
                        toggleRow(icon: "music.note",
                                  label: locale.t(.settingsBgm1),
                                  isOn: music.enabled) { music.setActive($0) }
                        divider
                        volumeRow(label: locale.t(.settingsBgmVolume),
                                  value: music.musicVolume,
                                  enabled: music.enabled) { music.updateMusicVolume($0) }
                        divider
                        volumeRow(label: locale.t(.settingsSfxVolume),
                                  value: music.sfxVolume,
                                  enabled: true,
                                  iconOverride: "waveform") { music.updateSfxVolume($0) }
                    }

                    // ── 알림 ──
                    sectionLabel(locale.t(.settingsNotification), "bell.fill")
                    glassCard {
                        toggleRow(icon: settings.notificationsEnabled ? "bell.fill" : "bell.slash.fill",
                                  label: locale.t(.settingsNotifPopup),
                                  description: locale.t(.settingsNotifPopupDesc),
                                  isOn: settings.notificationsEnabled) { settings.updateNotificationsEnabled($0) }
                    }

                    // ── 언어 ──
                    sectionLabel(locale.t(.settingsLanguage), "globe")
                    glassCard {
                        Button { showLanguagePicker = true } label: {
                            HStack(spacing: 14) {
                                iconBadge("globe", active: true)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(locale.t(.settingsLanguage))
                                        .foregroundStyle(Theme.textPrimary)
                                    Text(locale.t(.settingsLanguageDesc))
                                        .font(.poorStory(11)).foregroundStyle(Theme.textSecondary)
                                }
                                Spacer()
                                Text(languageLabel(locale.language))
                                    .font(.poorStory(15)).foregroundStyle(Theme.mint)
                                Image(systemName: "chevron.right")
                                    .font(.caption).foregroundStyle(Theme.textFaint)
                            }
                            .padding(.vertical, 12)
                        }
                        .buttonStyle(.plain)
                    }

                    // ── 계정 ──
                    sectionLabel(locale.t(.settingsAccount), "person.crop.circle")
                    glassCard {
                        Button(role: .destructive) { showDeleteConfirm = true } label: {
                            HStack(spacing: 14) {
                                Image(systemName: "trash.fill")
                                    .font(.system(size: 18))
                                    .foregroundStyle(SoftRed)
                                    .frame(width: 40, height: 40)
                                    .background(SoftRed.opacity(0.14), in: Circle())
                                    .overlay(Circle().strokeBorder(SoftRed.opacity(0.35), lineWidth: 1))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(locale.t(.settingsDeleteAccount))
                                        .foregroundStyle(SoftRed)
                                    Text(locale.t(.settingsDeleteAccountDesc))
                                        .font(.caption2).foregroundStyle(Theme.textSecondary)
                                }
                                Spacer()
                                if deleting { StarLoadingView(size: 20, color: SoftRed) }
                            }
                            .padding(.vertical, 12)
                        }
                        .buttonStyle(.plain)
                        .disabled(deleting)
                    }
                }
                .padding(20)
            }
        }
        .navigationTitle(locale.t(.navSettings))
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog(locale.t(.languageDialogTitle), isPresented: $showLanguagePicker, titleVisibility: .visible) {
            ForEach(LocaleManager.supported, id: \.self) { tag in
                Button(languageLabel(tag)) { locale.setLanguage(tag) }
            }
        }
        .alert(locale.t(.settingsDeleteAccount), isPresented: $showDeleteConfirm) {
            Button(locale.t(.commonCancel), role: .cancel) {}
            Button(locale.t(.settingsDeleteAccount), role: .destructive) { performDelete() }
        } message: {
            Text(locale.t(.settingsDeleteConfirmMsg))
        }
        .alert(locale.t(.settingsDeleteFailed), isPresented: $showDeleteFailed) {
            Button("OK", role: .cancel) {}
        }
    }

    private func performDelete() {
        deleting = true
        Task {
            let ok = await auth.requestDeletion()
            deleting = false
            // 성공 시 삭제 "예약" + 로그아웃 → auth.uid 가 nil 이 되어 RootView 가 로그인 화면으로 전환된다.
            // 7일 유예 안에 다시 로그인하면 예약이 취소된다.
            if !ok { showDeleteFailed = true }
        }
    }

    private let SoftRed = Color(red: 1.0, green: 0.42, blue: 0.42) // 0xFFFF6B6B

    private func languageLabel(_ tag: String) -> String {
        switch tag {
        case "ko": return locale.t(.languageKo)
        case "en": return locale.t(.languageEn)
        case "ja": return locale.t(.languageJa)
        default: return locale.t(.languageSystem)
        }
    }

    // MARK: - Building blocks

    private func sectionLabel(_ text: String, _ icon: String) -> some View {
        HStack(spacing: 7) {
            Image(systemName: icon).font(.system(size: 13)).foregroundStyle(Theme.mint)
            Text(text).font(.poorStory(15)).foregroundStyle(Theme.mint)
        }
    }

    private func glassCard<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(spacing: 0) { content() }
            .padding(.horizontal, 16)
            .background(Theme.surface, in: RoundedRectangle(cornerRadius: 20))
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .strokeBorder(
                        LinearGradient(colors: [Theme.mint.opacity(0.5), Color.blue.opacity(0.4)],
                                       startPoint: .topLeading, endPoint: .bottomTrailing),
                        lineWidth: 1)
            )
    }

    private var divider: some View {
        Rectangle().fill(Color.white.opacity(0.06)).frame(height: 1)
    }

    private func iconBadge(_ icon: String, active: Bool) -> some View {
        Image(systemName: icon)
            .font(.system(size: 18))
            .foregroundStyle(active ? Theme.mint : Theme.textFaint)
            .frame(width: 40, height: 40)
            .background(active ? Theme.mint.opacity(0.14) : Color.white.opacity(0.05), in: Circle())
            .overlay(Circle().strokeBorder(active ? Theme.mint.opacity(0.35) : Color.white.opacity(0.08), lineWidth: 1))
    }

    private func toggleRow(icon: String, label: String, description: String? = nil,
                           isOn: Bool, onChange: @escaping (Bool) -> Void) -> some View {
        HStack(spacing: 14) {
            iconBadge(icon, active: isOn)
            VStack(alignment: .leading, spacing: 2) {
                Text(label).foregroundStyle(Theme.textPrimary)
                if let description {
                    Text(description).font(.poorStory(11)).foregroundStyle(Theme.textSecondary)
                }
            }
            Spacer()
            Toggle("", isOn: Binding(get: { isOn }, set: { onChange($0) }))
                .labelsHidden()
                .tint(Theme.mint)
        }
        .padding(.vertical, 14)
    }

    private func volumeRow(label: String, value: Float, enabled: Bool,
                           iconOverride: String? = nil,
                           onChange: @escaping (Float) -> Void) -> some View {
        let icon = iconOverride ?? (value <= 0.01 ? "speaker.slash.fill" : "speaker.wave.2.fill")
        return VStack(spacing: 6) {
            HStack(spacing: 14) {
                iconBadge(icon, active: enabled)
                Text(label).foregroundStyle(enabled ? Theme.textPrimary : Theme.textFaint)
                Spacer()
                Text("\(Int(value * 100))%")
                    .font(.poorStory(12))
                    .foregroundStyle(enabled ? Theme.mint : Theme.textFaint)
                    .padding(.horizontal, 12).padding(.vertical, 4)
                    .background(enabled ? Theme.mint.opacity(0.14) : Color.white.opacity(0.05), in: Capsule())
            }
            Slider(value: Binding(get: { Double(value) }, set: { onChange(Float($0)) }), in: 0...1)
                .tint(Theme.mint)
                .disabled(!enabled)
        }
        .padding(.vertical, 12)
    }
}
