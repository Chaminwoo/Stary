import Foundation
import SwiftUI

/// 채팅방 읽음 시각 로컬 저장소 — 친구 목록의 "미읽음 파란 점" 판정에 사용.
/// chatId → 마지막으로 그 방을 본 시각(ms). 방 메타(updatedAt)가 이 시각보다 크고
/// 마지막 발신자가 내가 아니면 미읽음.
///
/// Android `core.util.ChatReadStore` 패리티 — 서버 스키마 변경 없는 **기기 로컬 기준**
/// (다른 기기에서 읽어도 이 기기엔 미읽음으로 남는다 — 허용 범위).
/// `ObservableObject` 라 [markRead] 시 관찰 중인 화면(친구 목록)이 즉시 갱신된다.
@MainActor
final class ChatReadStore: ObservableObject {
    static let shared = ChatReadStore()

    private static let prefsKey = "chat_read_store"

    /// chatId → 마지막 열람 시각(ms).
    @Published private(set) var lastReadAt: [String: Int64] = [:]

    private init() {
        let raw = UserDefaults.standard.dictionary(forKey: Self.prefsKey) as? [String: NSNumber] ?? [:]
        lastReadAt = raw.mapValues { $0.int64Value }
    }

    /// 그 방을 마지막으로 본 시각(없으면 0).
    func lastRead(_ chatId: String) -> Int64 { lastReadAt[chatId] ?? 0 }

    /// 방을 열람했음을 기록(기본 = 지금).
    func markRead(_ chatId: String, at: Int64 = FirestoreService.nowMillis) {
        lastReadAt[chatId] = at
        UserDefaults.standard.set(
            lastReadAt.mapValues { NSNumber(value: $0) },
            forKey: Self.prefsKey
        )
    }
}
