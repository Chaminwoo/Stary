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
    // 전송 실패 안내 토스트(권한/네트워크) — 조용히 사라지지 않게.
    @State private var toast: String?

    init(friend: Friend, myUid: String) {
        self.init(friendId: friend.userId, friendName: friend.userName, myUid: myUid)
    }

    init(friendId: String, friendName: String, myUid: String) {
        self.friendId = friendId
        self.friendName = friendName
        _vm = StateObject(wrappedValue: ChatViewModel(myUid: myUid, friendUid: friendId))
    }

    var body: some View {
        // ⚠️ 배경을 ZStack 형제로 두면 안 된다 — ScreenBackground 의 ignoresSafeArea 가 ZStack 을
        //    키보드 영역까지 키워서, 입력 바가 키보드 **뒤**에 깔린 채 올라오지 않았다(#5).
        //    .background 로 넣으면 배경은 화면 끝까지 그려지되 레이아웃(키보드 회피)에는 관여하지 않는다.
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
                .scrollDismissesKeyboard(.interactively)
                .onChange(of: vm.messages.count) { _ in
                    if let last = vm.messages.last?.id {
                        withAnimation { proxy.scrollTo(last, anchor: .bottom) }
                    }
                }
            }
            inputBar
        }
        // Android ChatScreen 배경 — mydiary_bg + 검정 0.85 틴트.
        .background { ScreenBackground(name: "mydiary_bg", darken: 0.85) }
        .overlay(alignment: .bottom) {
            if let toast { ToastView(text: toast) }
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
                Task {
                    let ok = await vm.send(senderId: auth.uid ?? "", senderName: auth.displayName, text: t)
                    if !ok {
                        text = t   // 실패하면 입력 내용을 되돌려 준다(다시 보낼 수 있게)
                        showToast(locale.t(.chatSendFailed))
                    }
                }
            } label: {
                Image(systemName: "paperplane.fill")
                    .foregroundStyle(text.trimmingCharacters(in: .whitespaces).isEmpty ? Theme.textFaint : Theme.mint)
            }
            .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .padding(12)
        // 배경만 하단 안전영역까지 늘린다(홈 인디케이터 아래로 이어지게).
        // ⚠️ regions 를 .container 로 한정 — .all 로 두면 키보드 영역까지 무시해 입력 바가 다시 안 올라온다.
        .background(Theme.background.ignoresSafeArea(.container, edges: .bottom))
    }

    private func showToast(_ text: String) {
        toast = text
        Task { try? await Task.sleep(nanoseconds: 1_800_000_000); toast = nil }
    }
}

