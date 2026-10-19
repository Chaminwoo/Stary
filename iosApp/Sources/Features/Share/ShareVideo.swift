import FirebaseFirestore
import Photos
import UIKit

/// 다이어리 공유 영상(스토리용 세로 8초) — 만들기 / SNS 로 보내기 / 사진 앱에 저장.
/// 예전 공유 카드(이미지 + 편집 화면 `ShareCard`/`ShareCardEditorView`)를 대체한다(2026-10-11).
/// Android `feature/share/ShareVideoHelper.kt` 대응. 영상 자체는 `ShareVideoRenderer` 가 그린다.
///
/// 링크(`AppConfig.shareLink`)를 열면 웹 랜딩이 앱 설치자는 stary://diary/{id} 로, 미설치자는 스토어로 보낸다.
enum ShareVideo {

    /// 영상을 만들어 임시 파일 URL 로 돌려준다(실패·취소면 nil). 수 초 걸리므로 `onProgress`(0..1)로 진행을 알린다.
    /// 호출한 Task 가 취소되면 다음 프레임에서 멈춘다.
    @MainActor
    static func generate(diary: Diary, myUid: String?, onProgress: @escaping (Float) -> Void) async -> URL? {
        let sky = await mySkyDiaries(uid: myUid)
        if Task.isCancelled { return nil }
        // ⚠️ `LocaleManager.t` 는 한글에 U+2060 결합자를 끼워 넣는다(화면 줄바꿈용) — 그림에 그릴 글은 원문 값을 쓴다.
        let lang = LocaleManager.shared.effectiveLanguage
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("share_video_\(diary.id ?? "star").mp4")
        let ok = await ShareVideoRenderer.render(diary: diary, sky: sky,
                                                 untitled: L10n.shareCardUntitled.value(for: lang),
                                                 cta: L10n.shareVideoCta.value(for: lang),
                                                 to: url, onProgress: onProgress)
        if ok { QuestStore.report(.share, key: diary.id) }
        return ok ? url : nil
    }

    /// 지구 위에 같이 띄울 내 다른 별들 — 못 읽으면(비로그인·느린 망) 주인공 별만 띄운다. 2.5초 넘게 걸리면 기다리지 않는다.
    private static func mySkyDiaries(uid: String?) async -> [Diary] {
        guard let uid, !uid.isEmpty else { return [] }
        return await withTaskGroup(of: [Diary]?.self) { group in
            group.addTask {
                let snap = try? await FirestoreService.diaries.whereField("userId", isEqualTo: uid).getDocuments()
                return (snap?.documents ?? []).compactMap { try? $0.data(as: Diary.self) }
            }
            group.addTask {
                try? await Task.sleep(nanoseconds: 2_500_000_000)
                return nil
            }
            let first = await group.next() ?? nil
            group.cancelAll()
            return first ?? []
        }
    }

    // MARK: - 공유

    /// 공유 본문(링크 포함) — 다른 앱으로 나가는 글이라 결합자 없는 원문 값.
    @MainActor
    private static func shareText(link: String) -> String {
        String(format: L10n.shareDiaryWithLink.value(for: LocaleManager.shared.effectiveLanguage), link)
    }

    /// SNS 공유 — 인스타그램이 있으면 스토리로 바로, 없으면 일반 공유 시트.
    @MainActor
    static func shareToSns(_ url: URL, diaryId: String) {
        if !shareToInstagramStory(url, diaryId: diaryId) { shareSheet(url, diaryId: diaryId) }
    }

    /// 일반 공유 시트 — 영상 파일 + 링크 본문.
    @MainActor
    private static func shareSheet(_ url: URL, diaryId: String) {
        let link = AppConfig.shareLink(diaryId: diaryId)
        let vc = UIActivityViewController(activityItems: [url, shareText(link: link)], applicationActivities: nil)
        guard let top = topViewController() else { return }
        // iPad 는 팝오버 앵커가 없으면 크래시.
        vc.popoverPresentationController?.sourceView = top.view
        vc.popoverPresentationController?.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.midY, width: 0, height: 0)
        top.present(vc, animated: true)
    }

    /// 인스타 스토리 직접 공유 — 영상을 스토리 배경으로. 인스타가 없으면 false(→ 공유 시트 폴백).
    ///
    /// 링크 스티커(contentURL)는 Meta 앱 ID(`source_application`)가 있어야만 붙는다 — 없으면 인스타가 무시하므로
    /// 링크를 같은 페이스트보드 항목의 텍스트로도 넣고 "링크 스티커로 붙여넣기" 안내를 띄운다(Android 와 같은 정책).
    @MainActor
    private static func shareToInstagramStory(_ url: URL, diaryId: String) -> Bool {
        let appId = ((Bundle.main.object(forInfoDictionaryKey: "INSTAGRAM_APP_ID") as? String) ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        var comps = URLComponents()
        comps.scheme = "instagram-stories"
        comps.host = "share"
        if !appId.isEmpty { comps.queryItems = [URLQueryItem(name: "source_application", value: appId)] }
        guard let story = comps.url, UIApplication.shared.canOpenURL(story), let video = try? Data(contentsOf: url) else { return false }

        let link = AppConfig.shareLink(diaryId: diaryId)
        var item: [String: Any] = [
            "com.instagram.sharedSticker.backgroundVideo": video,
            "public.utf8-plain-text": link,
        ]
        if !appId.isEmpty { item["com.instagram.sharedSticker.contentURL"] = link }
        UIPasteboard.general.setItems([item], options: [.expirationDate: Date().addingTimeInterval(300)])
        GlobalToast.shared.show(LocaleManager.shared.t(.shareStoryLinkCopied), seconds: 3.5)
        UIApplication.shared.open(story)
        return true
    }

    /// 공유 시트를 띄울 최상단 뷰컨트롤러(공유 화면 fullScreenCover 위).
    @MainActor
    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }

    // MARK: - 저장

    /// 사진 앱에 저장(추가 전용 권한만 요청). 성공 여부를 돌려준다.
    static func saveToPhotos(_ url: URL) async -> Bool {
        let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        guard status == .authorized || status == .limited else { return false }
        do {
            try await PHPhotoLibrary.shared().performChanges {
                PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: url)
            }
            return true
        } catch {
            return false
        }
    }
}
