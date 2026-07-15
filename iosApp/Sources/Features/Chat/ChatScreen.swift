import SwiftUI

/// 1:1 친구 채팅 화면.
struct ChatScreen: View {
    let friendId: String
    let friendName: String
    @EnvironmentObject var auth: AuthManager
    @ObservedObject private var locale = LocaleManager.shared
    @StateObject private var vm: ChatViewModel
    @State private var text = ""
    // 롱프레스한 내 메시지(1분 이내) — 완전 삭제 확인 대상. nil 이면 다이얼로그 숨김.
    @State private var pendingDelete: ChatMessage?

    init(friend: Friend, myUid: String) {
        self.init(friendId: friend.userId, friendName: friend.userName, myUid: myUid)
    }

    init(friendId: String, friendName: String, myUid: String) {
        self.friendId = friendId
        self.friendName = friendName
        _vm = StateObject(wrappedValue: ChatViewModel(myUid: myUid, friendUid: friendId))
    }

    var body: some View {
        ZStack {
            // Android ChatScreen 배경 — mydiary_bg + 검정 0.85 틴트.
            ScreenBackground(name: "mydiary_bg", darken: 0.85)
            // 별가루(34-7) — 메시지 뒤 배경에서 아주 옅게 떠다닌다(장식, 히트테스트 없음).
            ChatStardust()
                .ignoresSafeArea()
            VStack(spacing: 0) {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            ForEach(vm.messages) { msg in
                                bubble(msg)
                                    .id(msg.id)
                            }
                        }
                        .padding(12)
                    }
                    .onChange(of: vm.messages.count) { _ in
                        if let last = vm.messages.last?.id {
                            withAnimation { proxy.scrollTo(last, anchor: .bottom) }
                        }
                    }
                }
                inputBar
            }
        }
        .navigationTitle(friendName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // 상대 이름 + 히든 업적 배지(34-4) — 타이틀 자리를 커스텀 뷰로.
            ToolbarItem(placement: .principal) {
                HStack(spacing: 6) {
                    Text(friendName)
                        .font(.headline)
                        .foregroundStyle(Theme.textPrimary)
                        .lineLimit(1)
                    HiddenStarBadges(userId: friendId, size: 12)
                }
            }
        }
        // 내 메시지 완전 삭제 확인(1분 이내) — 상대방 쪽에서도 사라진다.
        .confirmationDialog(
            locale.t(.chatDeleteTitle),
            isPresented: Binding(get: { pendingDelete != nil },
                                 set: { if !$0 { pendingDelete = nil } }),
            titleVisibility: .visible
        ) {
            Button(locale.t(.commonDelete), role: .destructive) {
                if let target = pendingDelete {
                    Task { await vm.deleteMessage(target, myUid: auth.uid) }
                }
                pendingDelete = nil
            }
            Button(locale.t(.commonCancel), role: .cancel) { pendingDelete = nil }
        } message: {
            Text(locale.t(.chatDeleteConfirm))
        }
        .onAppear {
            vm.start()
            ChatPresence.shared.activeFriendId = friendId // 이 방 메시지는 배너 억제
            markRead()
        }
        // 이 방을 보는 동안 새 메시지가 와도 계속 읽음 처리(친구 목록의 미읽음 파란 점 해제).
        .onChange(of: vm.messages.count) { _ in markRead() }
        .onDisappear {
            vm.stop()
            if ChatPresence.shared.activeFriendId == friendId {
                ChatPresence.shared.activeFriendId = nil
            }
            markRead()
        }
    }

    private func markRead() {
        guard let uid = auth.uid else { return }
        ChatReadStore.shared.markRead(AppConfig.chatId(uid, friendId))
    }

    private func bubble(_ msg: ChatMessage) -> some View {
        let mine = msg.senderId == auth.uid
        return HStack {
            if mine { Spacer(minLength: 40) }
            Text(msg.text)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(mine ? Theme.mint.opacity(0.85) : Theme.surface,
                            in: RoundedRectangle(cornerRadius: 14))
                .foregroundStyle(mine ? Color.black : Theme.textPrimary)
                // 내 메시지 + 전송 후 1분 이내면 롱프레스로 완전 삭제(그 외엔 무반응)
                .onLongPressGesture {
                    if vm.canDelete(msg, myUid: auth.uid) { pendingDelete = msg }
                }
            if !mine { Spacer(minLength: 40) }
        }
        .frame(maxWidth: .infinity, alignment: mine ? .trailing : .leading)
    }

    private var inputBar: some View {
        HStack(spacing: 10) {
            TextField(LocaleManager.shared.t(.chatInputPlaceholder), text: $text, axis: .vertical)
                .lineLimit(1...4)
                .onChange(of: text) { v in
                    if v.count > AppConfig.chatMessageMaxLen { text = String(v.prefix(AppConfig.chatMessageMaxLen)) }
                }
                .padding(10)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 14))
                .foregroundStyle(Theme.textPrimary)
            Button {
                let t = text
                text = ""
                Task { await vm.send(senderId: auth.uid ?? "", senderName: auth.displayName, text: t) }
            } label: {
                Image(systemName: "paperplane.fill")
                    .foregroundStyle(text.trimmingCharacters(in: .whitespaces).isEmpty ? Theme.textFaint : Theme.mint)
            }
            .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .padding(12)
        .background(Theme.background)
    }
}

/// 채팅방 배경 별가루(34-7) — 미세한 입자 12개가 아주 느리게 떠다니며 반짝인다.
/// 배치는 인덱스 기반 고정 시드라 리컴포지션마다 흔들리지 않고, 히트테스트에 참여하지 않아
/// 메시지 롱프레스/입력창 조작을 방해하지 않는다. (Android ChatStardust 패리티 — 시드/주기/알파 동일)
private struct ChatStardust: View {
    private static let count = 12

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30)) { tl in
            // 24s 주기 위상(0..2π) — Android InfiniteTransition Restart 대응.
            let t = (tl.date.timeIntervalSinceReferenceDate / 24).truncatingRemainder(dividingBy: 1) * 6.28318
            Canvas { ctx, size in
                let w = size.width
                let h = size.height
                for i in 0..<Self.count {
                    let fx = Double(i * 37 % 100) / 100
                    let fy = Double(i * 71 % 100) / 100
                    let phase = Double(i) * 0.7
                    // 아주 느린 표류(가로 sin, 세로 cos — 서로 다른 주기로 겹치지 않게)
                    let px = w * (0.05 + 0.90 * fx) + sin(t * 0.6 + phase) * 10
                    let py = h * (0.06 + 0.88 * fy) + cos(t * 0.45 + phase * 1.3) * 14
                    let twinkle = 0.35 + 0.65 * (0.5 + 0.5 * sin(t * 2.1 + phase * 2))
                    let r = 1.5 + Double(i % 3) * 0.75
                    ctx.fill(
                        Path(ellipseIn: CGRect(x: px - r * 2.2, y: py - r * 2.2,
                                               width: r * 4.4, height: r * 4.4)),
                        with: .color(.white.opacity(0.10 * twinkle))
                    )
                    ctx.fill(
                        Path(ellipseIn: CGRect(x: px - r, y: py - r, width: r * 2, height: r * 2)),
                        with: .color(.white.opacity(0.35 * twinkle))
                    )
                }
            }
        }
        .allowsHitTesting(false)
    }
}
