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
            Theme.background.ignoresSafeArea()
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
        }
        .onDisappear {
            vm.stop()
            if ChatPresence.shared.activeFriendId == friendId {
                ChatPresence.shared.activeFriendId = nil
            }
        }
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
            TextField("메시지", text: $text, axis: .vertical)
                .lineLimit(1...4)
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
