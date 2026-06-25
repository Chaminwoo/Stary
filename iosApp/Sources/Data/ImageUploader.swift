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
