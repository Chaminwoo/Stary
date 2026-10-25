import FirebaseFirestore
import FirebaseStorage
import Foundation
import UIKit

/// 다이어리 사진을 Firebase Storage 에 업로드. Android ImageUploadHelper 와 동일 경로(diary_images/).
enum ImageUploader {
    /// 업로드 재시도는 1분까지(기본 10분) — 오프라인이면 '올리는 중'이 10분 동안 끝나지 않았다(Android StaryApplication 과 같다).
    private static let storage: Storage = {
        let s = Storage.storage()
        s.maxUploadRetryTime = 60
        s.maxOperationRetryTime = 30
        return s
    }()

    /// JPEG 로 압축 후 업로드하고 다운로드 URL 문자열을 반환.
    static func upload(_ data: Data) async throws -> String {
        let jpeg = UIImage(data: data)?.jpegData(compressionQuality: 0.8) ?? data
        let ref = storage.reference().child("diary_images/\(UUID().uuidString).jpg")
        let meta = StorageMetadata()
        meta.contentType = "image/jpeg"
        _ = try await ref.putDataAsync(jpeg, metadata: meta)
        let url = try await ref.downloadURL()
        return url.absoluteString
    }

    /// 3초 이내 짧은 영상 업로드(diary_videos/). 길이 검증은 호출부(UploadScreen)가 하고,
    /// 여기서는 원본 파일 데이터를 그대로 올린다. Android ImageUploadHelper.uploadVideoResult 패리티.
    static func uploadVideo(_ data: Data, contentType: String) async throws -> String {
        let ext = contentType.contains("quicktime") || contentType.contains("mov") ? "mov" : "mp4"
        let ref = storage.reference().child("diary_videos/\(UUID().uuidString).\(ext)")
        let meta = StorageMetadata()
        meta.contentType = contentType
        _ = try await ref.putDataAsync(data, metadata: meta)
        let url = try await ref.downloadURL()
        return url.absoluteString
    }

    /// 부메랑(3초 움짤) GIF 업로드. contentType=image/gif 라 diary_images 규칙(이미지 타입)을 그대로 통과.
    /// URL 은 diary.videoUrl 에 저장해 스키마 유지(.gif 로 판별). Android uploadGifResult 패리티.
    static func uploadGif(_ data: Data) async throws -> String {
        let ref = storage.reference().child("diary_images/\(UUID().uuidString).gif")
        let meta = StorageMetadata()
        meta.contentType = "image/gif"
        _ = try await ref.putDataAsync(data, metadata: meta)
        let url = try await ref.downloadURL()
        return url.absoluteString
    }

    /// 프로필 사진 교체(2026-10-19, Android UserRepository.uploadProfileImage 와 같은 순서) —
    /// **새 사진 업로드 → users/{uid}.profileImageUrl 연결 → 예전 사진(기존 URL)을 Storage 에서 삭제**.
    /// - 파일명은 매번 새로 `profile_images/{uid}_{시각}.jpg`(storage.rules 가 본인 것만 허용). URL 이 바뀌어 옛 사진이 캐시로 남지 않는다.
    /// - 먼저 지우고 올리다 업로드가 실패하면 프로필 URL 이 없는 파일을 가리키므로, 새 사진을 연결한 **뒤에** 지운다.
    /// - 예전 URL 은 서버에서 읽는다. 우리 Storage 의 내 프로필 파일일 때만 지운다(구글 사진 URL 등은 그대로). 삭제 실패는 무시.
    static func uploadProfile(uid: String, data: Data) async throws -> String {
        let jpeg = UIImage(data: data)?.jpegData(compressionQuality: 0.8) ?? data
        let userRef = FirestoreService.users.document(uid)
        // 못 읽으면 지우지 않는다(잘못 지우는 것보다 남는 게 낫다)
        let oldUrl = (try? await userRef.getDocument(source: .server))?.get("profileImageUrl") as? String

        let millis = Int64(Date().timeIntervalSince1970 * 1000)
        let ref = storage.reference().child("profile_images/\(uid)_\(millis).jpg")
        let meta = StorageMetadata()
        meta.contentType = "image/jpeg"
        _ = try await ref.putDataAsync(jpeg, metadata: meta)
        let url = try await ref.downloadURL().absoluteString
        try await userRef.setData(["profileImageUrl": url], merge: true)

        if let oldUrl, !oldUrl.isEmpty, oldUrl != url, let old = myProfileRef(oldUrl, uid: uid) {
            try? await old.delete()
        }
        return url
    }

    /// url 이 우리 Storage 의 **내** 프로필 파일(profile_images/{uid}.jpg 또는 {uid}_{시각}.jpg)이면 그 참조. 아니면 nil.
    /// ⚠️ `Storage.reference(forURL:)` 는 다른 버킷 URL 이면 앱을 죽이므로 쓰지 않고 직접 푼다
    ///    (https://firebasestorage.googleapis.com/v0/b/{버킷}/o/{인코딩된 경로}?alt=media&token=…).
    private static func myProfileRef(_ url: String, uid: String) -> StorageReference? {
        guard let comps = URLComponents(string: url), comps.host == "firebasestorage.googleapis.com" else { return nil } // 구글 사진 등
        let parts = comps.percentEncodedPath.components(separatedBy: "/o/")
        guard parts.count == 2,
              parts[0].hasSuffix("/b/\(storage.reference().bucket)"),
              let path = parts[1].removingPercentEncoding else { return nil }
        let mine = path == "profile_images/\(uid).jpg"
            || (path.hasPrefix("profile_images/\(uid)_") && path.hasSuffix(".jpg"))
        return mine ? storage.reference().child(path) : nil
    }
}
