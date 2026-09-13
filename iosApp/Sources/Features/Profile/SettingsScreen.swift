import SwiftUI

/// 설정 화면 — 배경음악/효과음 볼륨, 알림 팝업 on/off, 언어 변경. (Android SettingsScreen 패리티)
/// 값은 [MusicManager]/[AppSettings]/[LocaleManager] 에 즉시 저장되어 전 화면에 반영된다.
struct SettingsScreen: View {
    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var blocks: BlockStore
    @ObservedObject private var music = MusicManager.shared
    @ObservedObject private var settings = AppSettings.shared
    @ObservedObject private var locale = LocaleManager.shared
    @State private var showLanguagePicker = false
    @State private var showDeleteConfirm = false
    @State private var showDeleteFailed = false
    @State private var deleting = false

    var body: some View {
        ZStack {
            // Android SettingsScreen 배경 — mydiary_bg + 검정 0.84 틴트.
            ScreenBackground(name: "mydiary_bg", darken: 0.84)
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
                        divider
                        toggleRow(icon: "iphone.radiowaves.left.and.right",
                                  label: locale.t(.settingsHaptics),
                                  description: locale.t(.settingsHapticsDesc),
                                  isOn: settings.hapticsEnabled) { settings.updateHapticsEnabled($0) }
                    }

                    // ── 알림 ──
                    sectionLabel(locale.t(.settingsNotification), "bell.fill")
                    glassCard {
                        toggleRow(icon: settings.notificationsEnabled ? "bell.fill" : "bell.slash.fill",
                                  label: locale.t(.settingsNotifPopup),
                                  description: locale.t(.settingsNotifPopupDesc),
                                  isOn: settings.notificationsEnabled) { settings.updateNotificationsEnabled($0) }
                        divider
                        toggleRow(icon: "calendar.badge.clock",
                                  label: locale.t(.settingsDailyReminder),
                                  description: locale.t(.settingsDailyReminderDesc),
                                  isOn: settings.dailyReminderEnabled) { settings.updateDailyReminderEnabled($0) }
                    }

                    // ── 언어 ──
                    sectionLabel(locale.t(.settingsLanguage), "globe")
                    glassCard {
                        Button { showLanguagePicker = true } label: {
                            HStack(spacing: 14) {
                                iconBadge("globe", active: true)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(locale.t(.settingsLanguage))
                                        .font(.minSans(17)).foregroundStyle(Theme.textPrimary)
                                    Text(locale.t(.settingsLanguageDesc))
                                        .font(.minSans(12)).foregroundStyle(Theme.textSecondary)
                                }
                                Spacer()
                                Text(languageLabel(locale.language))
                                    .font(.minSans(15)).foregroundStyle(Theme.navyAccent)
                                Image(systemName: "chevron.right")
                                    .font(.caption).foregroundStyle(Theme.textFaint)
                            }
                            .padding(.vertical, 12)
                        }
                        .buttonStyle(.plain)
                    }

                    // ── 도움말 ── 처음 보여줬던 코치마크(주요 컨트롤 안내)를 다시 재생.
                    sectionLabel(locale.t(.settingsHelp), "questionmark.circle")
                    glassCard {
                        Button { OnboardingReplayState.shared.request() } label: {
                            HStack(spacing: 14) {
                                iconBadge("questionmark.circle", active: true)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(locale.t(.settingsHelpReplay))
                                        .font(.minSans(17)).foregroundStyle(Theme.textPrimary)
                                    Text(locale.t(.settingsHelpReplayDesc))
                                        .font(.minSans(12)).foregroundStyle(Theme.textSecondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.caption).foregroundStyle(Theme.textFaint)
                            }
                            .padding(.vertical, 12)
                        }
                        .buttonStyle(.plain)
                    }

                    // ── 안전 ── (차단 목록: 차단한 사용자의 별/댓글은 앱 전체에서 숨겨진다)
                    sectionLabel(locale.t(.settingsSafety), "shield.lefthalf.filled")
                    glassCard {
                        NavigationLink {
                            BlockedUsersScreen()
                        } label: {
                            HStack(spacing: 14) {
                                iconBadge("person.slash", active: true)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(locale.t(.settingsBlockedUsers))
                                        .font(.minSans(17)).foregroundStyle(Theme.textPrimary)
                                    Text(locale.t(.settingsBlockedUsersDesc))
                                        .font(.minSans(12)).foregroundStyle(Theme.textSecondary)
                                }
                                Spacer()
                                if !blocks.blockedIds.isEmpty {
                                    Text("\(blocks.blockedIds.count)")
                                        .font(.minSans(15)).foregroundStyle(Theme.navyAccent)
                                }
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
                                        .font(.minSans(17)).foregroundStyle(SoftRed)
                                    Text(locale.t(.settingsDeleteAccountDesc))
                                        .font(.minSans(12)).foregroundStyle(Theme.textSecondary)
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
        .staryChoiceDialog(locale.t(.languageDialogTitle), isPresented: $showLanguagePicker,
                           options: LocaleManager.supported.map { tag in
                               StaryDialogOption(languageLabel(tag), action: { locale.setLanguage(tag) })
                           })
        .staryConfirmDialog(locale.t(.settingsDeleteAccount), isPresented: $showDeleteConfirm,
                            message: locale.t(.settingsDeleteConfirmMsg),
                            confirmTitle: locale.t(.settingsDeleteAccount),
                            destructive: true) { performDelete() }
        .staryInfoDialog(locale.t(.settingsDeleteFailed), isPresented: $showDeleteFailed)
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
    /// 카드 배경 — 검정에 가까운 어두운 남색(Android SettingsScreen CardBg 0xE6080D1A 패리티, 2026-07-18).
    private let cardBg = Color(hex: 0x080D1A).opacity(0.9)

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
            Image(systemName: icon).font(.system(size: 13)).foregroundStyle(Theme.navyAccent)
            Text(text).font(.minSans(15)).foregroundStyle(Theme.navyAccent)
        }
    }

    private func glassCard<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(spacing: 0) { content() }
            .padding(.horizontal, 16)
            .background(cardBg, in: RoundedRectangle(cornerRadius: 20))
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .strokeBorder(
                        LinearGradient(colors: [Color.blue.opacity(0.5), Theme.navyDeep.opacity(0.4)],
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
            .foregroundStyle(active ? Theme.navyAccent : Theme.textFaint)
            .frame(width: 40, height: 40)
            .background(active ? Theme.navyAccent.opacity(0.14) : Color.white.opacity(0.05), in: Circle())
            .overlay(Circle().strokeBorder(active ? Theme.navyAccent.opacity(0.35) : Color.white.opacity(0.08), lineWidth: 1))
    }

    private func toggleRow(icon: String, label: String, description: String? = nil,
                           isOn: Bool, onChange: @escaping (Bool) -> Void) -> some View {
        HStack(spacing: 14) {
            iconBadge(icon, active: isOn)
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.minSans(17)).foregroundStyle(Theme.textPrimary)
                if let description {
                    Text(description).font(.minSans(12)).foregroundStyle(Theme.textSecondary)
                }
            }
            Spacer()
            Toggle("", isOn: Binding(get: { isOn }, set: { onChange($0) }))
                .labelsHidden()
                .tint(Theme.navyAccent)
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
                Text(label).font(.minSans(17)).foregroundStyle(enabled ? Theme.textPrimary : Theme.textFaint)
                Spacer()
                Text("\(Int(value * 100))%")
                    .font(.minSans(12))
                    .foregroundStyle(enabled ? Theme.navyAccent : Theme.textFaint)
                    .padding(.horizontal, 12).padding(.vertical, 4)
                    .background(enabled ? Theme.navyAccent.opacity(0.14) : Color.white.opacity(0.05), in: Capsule())
            }
            StarVolumeSlider(value: value, enabled: enabled, onChange: onChange)
        }
        .padding(.vertical, 12)
    }
}

/// 음량 슬라이더 — Android SettingsScreen `Slider(thumb = StarThumb, track = 그라데이션)` 패리티.
/// SwiftUI `Slider` 는 thumb 를 바꿀 수 없어 직접 그린다(트랙/색/크기 값은 Android 와 동일).
private struct StarVolumeSlider: View {
    let value: Float
    let enabled: Bool
    let onChange: (Float) -> Void

    @State private var dragging = false

    /// Android StarThumb Box 40dp / 트랙 6dp.
    private let thumbSize: CGFloat = 40
    private let trackHeight: CGFloat = 6

    var body: some View {
        GeometryReader { geo in
            // thumb 중심이 트랙 양 끝(반지름만큼 안쪽) 사이를 오간다.
            let usable = max(geo.size.width - thumbSize, 1)
            let frac = CGFloat(min(max(value, 0), 1))
            let thumbX = thumbSize / 2 + usable * frac
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(enabled ? Color(hex: 0x111726) : Color(hex: 0x0D1220))
                    .frame(width: usable, height: trackHeight)
                    .offset(x: thumbSize / 2)
                if frac > 0 {
                    Capsule()
                        .fill(Self.activeFill(enabled))
                        .frame(width: usable * frac, height: trackHeight)
                        .offset(x: thumbSize / 2)
                }
                StarThumb(enabled: enabled, active: dragging)
                    .position(x: thumbX, y: geo.size.height / 2)
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { g in
                        guard enabled else { return }
                        if !dragging { dragging = true }
                        let f = (g.location.x - thumbSize / 2) / usable
                        onChange(Float(min(max(f, 0), 1)))
                    }
                    .onEnded { _ in dragging = false }
            )
        }
        .frame(height: thumbSize)
        .accessibilityElement()
        .accessibilityValue("\(Int(value * 100))%")
        .accessibilityAdjustableAction { direction in
            guard enabled else { return }
            switch direction {
            case .increment: onChange(min(value + 0.1, 1))
            case .decrement: onChange(max(value - 0.1, 0))
            @unknown default: break
            }
        }
    }

    /// 활성 트랙 — 파랑→남색 그라데이션(Android AccentBrush), 비활성은 회남색 단색.
    /// 그라데이션과 단색은 타입이 달라 `AnyShapeStyle` 로 지운다(ChatScreen bubbleFill 과 같은 이유).
    private static func activeFill(_ enabled: Bool) -> AnyShapeStyle {
        enabled
            ? AnyShapeStyle(LinearGradient(colors: [Color(hex: 0x3B82F6), Color(hex: 0x1E3A8A)],
                                           startPoint: .leading, endPoint: .trailing))
            : AnyShapeStyle(Color(hex: 0x3A434F))
    }
}

/// 슬라이더 핸들 — 5꼭지 크리스탈 별 + 후광. 누르거나 끄는 동안 1.3배 확대·발광(Android StarThumb 패리티).
private struct StarThumb: View {
    let enabled: Bool
    let active: Bool

    var body: some View {
        let accent = Theme.navyAccent
        // Android glowAlpha: 비활성 0 / 누름·드래그 0.9 / 평상시 0.4.
        let glow: Double = !enabled ? 0 : (active ? 0.9 : 0.4)
        let starColor = enabled ? accent : Color(hex: 0x555555)
        ZStack {
            Circle()
                .fill(RadialGradient(colors: [accent.opacity(glow), accent.opacity(glow * 0.35), .clear],
                                     center: .center, startRadius: 0, endRadius: 20))
            Canvas { ctx, size in
                ctx.withCGContext { cg in
                    StarCrystal.draw(in: cg, type: 1, colors: [UIColor(starColor)],
                                     rect: CGRect(origin: .zero, size: size))
                }
            }
            .frame(width: 22, height: 22)
        }
        .frame(width: 40, height: 40)
        .scaleEffect(enabled && active ? 1.3 : 1)
        .animation(.spring(response: 0.25, dampingFraction: 0.6), value: active)
        .animation(.easeOut(duration: 0.2), value: enabled)
        .allowsHitTesting(false)
    }
}
