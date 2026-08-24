import SwiftUI

/// 프로필 사진 등 단일 이미지를 화면 가득(원본 비율 Fit) 띄우는 뷰어 — Android `core.ui.PhotoViewer` 패리티.
/// 탭하면 닫히고, 핀치 확대(1..5배) + 확대 상태에서 드래그 이동, 더블탭 확대/복귀.
/// (`fullScreenCover` 로 띄운다 — 상세 화면의 FullScreenMediaViewer 와 같은 조작감.)
struct PhotoViewer: View {
    let imageUrl: String
    var onClose: () -> Void

    @State private var scale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var pinchStart: CGFloat?
    @State private var dragStart: CGSize?

    var body: some View {
        ZStack {
            Color.black.opacity(0.95).ignoresSafeArea()

            AsyncImage(url: URL(string: imageUrl)) { image in
                image.resizable().scaledToFit()
            } placeholder: {
                StarLoadingView(size: 36)
            }
            .scaleEffect(scale)
            .offset(offset)
            .gesture(zoomGesture)

            VStack {
                HStack {
                    Spacer()
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 38, height: 38)
                            .background(.black.opacity(0.35), in: Circle())
                    }
                    .padding(.trailing, 14)
                }
                Spacer()
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(count: 2) {
            // 더블탭 — 확대/원배율 토글.
            if scale > 1 { scale = 1; offset = .zero } else { scale = 2.5 }
        }
        .onTapGesture { onClose() }
    }

    /// 핀치 확대(1..5) + 확대 상태에서만 드래그 이동. 원배율로 돌아오면 위치 리셋.
    private var zoomGesture: some Gesture {
        let pinch = MagnificationGesture()
            .onChanged { m in
                if pinchStart == nil { pinchStart = scale }
                scale = min(max((pinchStart ?? scale) * m, 1), 5)
                if scale <= 1 { offset = .zero }
            }
            .onEnded { _ in pinchStart = nil }
        let drag = DragGesture()
            .onChanged { v in
                guard scale > 1 else { return }
                if dragStart == nil { dragStart = offset }
                let base = dragStart ?? offset
                offset = CGSize(width: base.width + v.translation.width,
                                height: base.height + v.translation.height)
            }
            .onEnded { _ in dragStart = nil }
        return pinch.simultaneously(with: drag)
    }
}
