import SwiftUI
import UIKit

/// `.sheet(item:)` 용 래퍼 — 고른 사진을 조절 화면으로 넘긴다.
struct PickedPhoto: Identifiable {
    let image: UIImage
    let id = UUID()
}

/// 프로필 사진 조절 화면 — Android `core.ui.ProfilePhotoCropDialog` 패리티.
///
/// 고른 사진을 **정사각(원형 표시) 프레임 안에서 드래그로 위치, 두 손가락으로 확대/축소**해
/// 잘라낸 뒤 [onConfirm] 에 JPEG 데이터를 넘긴다. 좌표 모델은 [ImageCrop] 참고.
struct ProfilePhotoCropView: View {
    let image: UIImage
    var onConfirm: (Data) -> Void
    var onCancel: () -> Void

    @ObservedObject private var locale = LocaleManager.shared

    @State private var scale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var pinchStart: CGFloat?
    @State private var dragStart: CGSize?
    /// 크롭 프레임의 실제 표시 크기(pt) — 제스처와 확정이 같은 기준을 쓰도록 보관.
    @State private var frameSize: CGSize = .zero

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()

            VStack(spacing: 16) {
                Text(locale.t(.profilePhotoAdjust))
                    .font(.minSans(17, .semibold))
                    .foregroundStyle(Theme.textPrimary)
                Text(locale.t(.profilePhotoAdjustHint))
                    .font(.minSans(12))
                    .foregroundStyle(Theme.textSecondary)

                // 정사각 프레임 — 실제 프로필은 원형으로 보이므로 원형 마스크로 그대로 미리 본다.
                GeometryReader { geo in
                    let side = min(geo.size.width, geo.size.height)
                    ZStack {
                        Color(hex: 0x0D0D0D)
                        let bw = image.size.width
                        let bh = image.size.height
                        let disp = max(side / bw, side / bh) * scale
                        Image(uiImage: image)
                            .resizable()
                            .frame(width: bw * disp, height: bh * disp)
                            .offset(offset)
                    }
                    .frame(width: side, height: side)
                    .clipShape(Circle())
                    .contentShape(Circle())
                    .gesture(cropGesture(side: side))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .onAppear { frameSize = CGSize(width: side, height: side) }
                    .onChange(of: side) { frameSize = CGSize(width: $0, height: $0) }
                }
                .aspectRatio(1, contentMode: .fit)
                .padding(.horizontal, 24)

                HStack(spacing: 14) {
                    Button(action: onCancel) {
                        Text(locale.t(.commonCancel))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .foregroundStyle(Theme.textSecondary)
                            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(.white.opacity(0.25)))
                    }
                    Button { confirm() } label: {
                        Text(locale.t(.commonSave))
                            .fontWeight(.medium)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Theme.navyAccent, in: RoundedRectangle(cornerRadius: 14))
                            .foregroundStyle(.black)
                    }
                }
                .font(.minSans(16))
                .padding(.horizontal, 24)
                .padding(.bottom, 12)
            }
            .padding(.top, 24)
        }
    }

    /// 드래그 + 핀치 동시 제스처 — 프레임 밖 빈자리가 안 생기게 클램프.
    private func cropGesture(side: CGFloat) -> some Gesture {
        let frame = CGSize(width: side, height: side)
        let drag = DragGesture()
            .onChanged { v in
                if dragStart == nil { dragStart = offset }
                let base = dragStart ?? offset
                offset = ImageCrop.clampedOffset(
                    CGSize(width: base.width + v.translation.width,
                           height: base.height + v.translation.height),
                    image: image.size, frame: frame, scale: scale)
            }
            .onEnded { _ in dragStart = nil }
        let pinch = MagnificationGesture()
            .onChanged { m in
                if pinchStart == nil { pinchStart = scale }
                scale = min(max((pinchStart ?? scale) * m, 1), 4)
                offset = ImageCrop.clampedOffset(offset, image: image.size, frame: frame, scale: scale)
            }
            .onEnded { _ in pinchStart = nil }
        return drag.simultaneously(with: pinch)
    }

    private func confirm() {
        let frame = frameSize.width > 0 ? frameSize : CGSize(width: 300, height: 300)
        // 크롭 실패(디코딩 오류)면 원본을 그대로 올려 흐름이 끊기지 않게 한다.
        let data = ImageCrop.crop(image, frame: frame, scale: scale, offset: offset,
                                  outWidth: ImageCrop.profileOutPixels)
            ?? image.jpegData(compressionQuality: 0.9)
        guard let data else { onCancel(); return }
        onConfirm(data)
    }
}
