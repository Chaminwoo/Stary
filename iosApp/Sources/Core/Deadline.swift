import Foundation

/// [withDeadline] 결과 — 제때 끝남 / 실패(예외) / 시간 초과(작업은 뒤에서 계속 돈다).
enum DeadlineResult { case done, failed, timedOut }

/// 작업을 최대 [seconds] 까지만 기다린다. 시간이 지나도 작업을 멈추지 않고 먼저 돌아온다.
///
/// ⚠️ TaskGroup 경주로 대신하지 말 것 — Firestore 의 async 호출은 취소에 반응하지 않아, 그룹이 끝나려면
/// 느린 쪽까지 기다려야 한다(오프라인이면 영영). 로그아웃의 "1.5초만 기다리기"가 그래서 실제로는 끝없이 기다렸다(2026-10-25).
func withDeadline(seconds: Double, _ work: @escaping @Sendable () async throws -> Void) async -> DeadlineResult {
    await withCheckedContinuation { (cont: CheckedContinuation<DeadlineResult, Never>) in
        let once = ResumeOnce(cont)
        Task {
            do { try await work(); once.resume(.done) } catch { once.resume(.failed) }
        }
        Task {
            try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            once.resume(.timedOut)
        }
    }
}

/// 두 갈래 중 먼저 온 결과로 한 번만 깨운다.
private final class ResumeOnce: @unchecked Sendable {
    private let lock = NSLock()
    private var cont: CheckedContinuation<DeadlineResult, Never>?

    init(_ cont: CheckedContinuation<DeadlineResult, Never>) { self.cont = cont }

    func resume(_ result: DeadlineResult) {
        lock.lock()
        let c = cont
        cont = nil
        lock.unlock()
        c?.resume(returning: result)
    }
}
