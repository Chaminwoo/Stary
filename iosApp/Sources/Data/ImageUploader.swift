import FirebaseFirestore
import FirebaseStorage
import Foundation
import UIKit

/// 다이어리 사진을 Firebase Storage 에 업로드. Android ImageUploadHelper 와 동일 경로(diary_images/).
enum ImageUploader {
    /// JPEG 로 압축 후 업로드하고 다운로드 URL 문자열을 반환.
    static func upload(_ data: Data) async throws -> String {
        let jpeg = UIImage(data: data)?.jpegData(compressionQuality: 0.8) ?? data
        let ref = Storage.storage().reference().child("diary_images/\(UUID().uuidString).jpg")
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
        let ref = Storage.storage().reference().child("diary_videos/\(UUID().uuidString).\(ext)")
        let meta = StorageMetadata()
        meta.contentType = contentType
        _ = try await ref.putDataAsync(data, metadata: meta)
        let url = try await ref.downloadURL()
        return url.absoluteString
    }

    /// 부메랑(3초 움짤) GIF 업로드. contentType=image/gif 라 diary_images 규칙(이미지 타입)을 그대로 통과.
    /// URL 은 diary.videoUrl 에 저장해 스키마 유지(.gif 로 판별). Android uploadGifResult 패리티.
    static func uploadGif(_ data: Data) async throws -> String {
        let ref = Storage.storage().reference().child("diary_images/\(UUID().uuidString).gif")
        let meta = StorageMetadata()
        meta.contentType = "image/gif"
        _ = try await ref.putDataAsync(data, metadata: meta)
        let url = try await ref.downloadURL()
        return url.absoluteString
    }

    /// 프로필 사진 업로드(항상 같은 경로 profile_images/{uid}.jpg) + users/{uid}.profileImageUrl 갱신.
    /// Android UserRepository.uploadProfileImage 와 동일.
    static func uploadProfile(uid: String, data: Data) async throws -> String {
        let jpeg = UIImage(data: data)?.jpegData(compressionQuality: 0.8) ?? data
        let ref = Storage.storage().reference().child("profile_images/\(uid).jpg")
        let meta = StorageMetadata()
        meta.contentType = "image/jpeg"
        _ = try await ref.putDataAsync(jpeg, metadata: meta)
        let url = try await ref.downloadURL()
        try await FirestoreService.users.document(uid).setData(["profileImageUrl": url.absoluteString], merge: true)
        return url.absoluteString
    }
}
