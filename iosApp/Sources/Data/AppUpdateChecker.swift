import Foundation
import SwiftUI
import UIKit

/// 새 버전 안내 — 앱을 열었을 때 **App Store 에 더 새 버전이 올라와 있으면** 팝업으로 알리고
/// "업데이트하러 가기"로 스토어 페이지를 바로 연다. Android `core.util.AppUpdateChecker` 패리티(2026-09-25).
///
/// - Android 는 Play 인앱 업데이트 API 를 쓰지만 iOS 엔 그런 API 가 없어 공개 **iTunes lookup API**
///   (`https://itunes.apple.com/lookup?bundleId=…&country=…`)의 `version` 을 설치본
///   `CFBundleShortVersionString` 과 숫자 단위로 비교한다.
/// - `country` 는 기기 지역(없으면 kr) — 그 나라 스토어에 앱이 없으면 결과 0건 → 조용히 넘어간다.
/// - lookup 결과는 애플 CDN 캐시라 출시 직후 몇 시간은 이전 버전으로 보일 수 있다(정상).
/// - 프로세스당 1회만 확인. "나중에"를 누르면 이번 실행 동안 다시 안 뜬다.
@MainActor
final class AppUpdateChecker: ObservableObject {
    static let shared = AppUpdateChecker()

    /// 안내 팝업 표시 여부.
    @Published private(set) var showPrompt = false
    /// 스토어 앱으로 바로 여는 주소(itms-apps://…/id{trackId}).
    private(set) var storeURL: URL?

    private var checked = false

    /// 앱 시작 직후 권한 요청·로그인 영상과 겹치지 않도록 잠깐 뒤에 띄운다(Android PROMPT_DELAY_MS 와 같은 값).
    private let promptDelay: UInt64 = 1_500_000_000

    private struct Lookup: Decodable {
        struct Item: Decodable {
            let version: String
            let trackId: Int
        }
        let results: [Item]
    }

    func checkOnce() async {
        guard !checked else { return }
        checked = true
        guard let bundleId = Bundle.main.bundleIdentifier,
              let current = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
        else { return }
        let country = (Locale.current.region?.identifier ?? "KR").lowercased()
        var comps = URLComponents(string: "https://itunes.apple.com/lookup")
        comps?.queryItems = [
            URLQueryItem(name: "bundleId", value: bundleId),
            URLQueryItem(name: "country", value: country),
        ]
        guard let url = comps?.url,
              let response = try? await URLSession.shared.data(from: url),
              let result = try? JSONDecoder().decode(Lookup.self, from: response.0),
              let item = result.results.first,
              Self.isNewer(item.version, than: current)
        else { return }
        storeURL = URL(string: "itms-apps://itunes.apple.com/app/id\(item.trackId)")
        try? await Task.sleep(nanoseconds: promptDelay)
        withAnimation(.easeOut(duration: 0.2)) { showPrompt = true }
    }

    func dismiss() {
        withAnimation(.easeOut(duration: 0.2)) { showPrompt = false }
    }

    func openStore() {
        guard let url = storeURL else { return }
        UIApplication.shared.open(url)
    }

    /// "1.5.10" > "1.5.9" 처럼 점 단위 숫자로 비교(자리수가 다르면 없는 자리는 0).
    static func isNewer(_ store: String, than installed: String) -> Bool {
        let a = store.split(separator: ".").map { Int($0) ?? 0 }
        let b = installed.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(a.count, b.count) {
            let x = i < a.count ? a[i] : 0
            let y = i < b.count ? b[i] : 0
            if x != y { return x > y }
        }
        return false
    }
}

/// 새 버전 안내 카드 — 화면 첫 진입 설명창(FirstVisitInfoCard)과 같은 모양 + "나중에". Android AppUpdatePromptHost 패리티.
struct AppUpdatePromptOverlay: View {
    @ObservedObject private var checker = AppUpdateChecker.shared
    @ObservedObject private var locale = LocaleManager.shared

    private var accent: LinearGradient {
        LinearGradient(colors: [Theme.mint, Color(hex: 0x3B82F6)], startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    var body: some View {
        if checker.showPrompt {
            ZStack {
                Color.black.opacity(0.6).ignoresSafeArea()
                    .onTapGesture { checker.dismiss() }
                VStack(spacing: 16) {
                    ZStack {
                        Circle().fill(accent).frame(width: 64, height: 64)
                        Image(systemName: "arrow.down.circle.fill")
                            .font(.system(size: 30, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                    Text(locale.t(.updateTitle))
                        .font(.minSans(20, .semibold))
                        .foregroundStyle(Theme.textPrimary)
                        .multilineTextAlignment(.center)
                    Text(locale.t(.updateMsg))
                        .font(.minSans(14))
                        .foregroundStyle(Color(hex: 0xB8C0CC))
                        .multilineTextAlignment(.center)
                        .lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                    VStack(spacing: 6) {
                        Button {
                            checker.openStore()
                            checker.dismiss()
                        } label: {
                            Text(locale.t(.updateGo))
                                .font(.minSans(15, .medium))
                                .foregroundStyle(Color(hex: 0x06121E))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 13)
                                .background(accent, in: RoundedRectangle(cornerRadius: 14))
                        }
                        Button { checker.dismiss() } label: {
                            Text(locale.t(.updateLater))
                                .font(.minSans(14))
                                .foregroundStyle(Theme.textSecondary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 11)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.top, 4)
                }
                .padding(24)
                .background(Color(hex: 0x121821), in: RoundedRectangle(cornerRadius: 24))
                .overlay(
                    RoundedRectangle(cornerRadius: 24)
                        .strokeBorder(LinearGradient(colors: [Theme.mint.opacity(0.6), Color(hex: 0x3B82F6).opacity(0.45)],
                                                     startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 1)
                )
                .padding(24)
            }
            .transition(.opacity)
        }
    }
}
