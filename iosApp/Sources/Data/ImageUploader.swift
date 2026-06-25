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
}
