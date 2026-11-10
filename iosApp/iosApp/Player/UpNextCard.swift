import SwiftUI
import Shared

/// Offers the next episode in the last seconds of this one: its picture, "S1 E2 · title", how many
/// seconds until it starts on its own, "Play now" and
/// "Watch credits", a glass capsule that dismisses it. The card is Liquid
/// Glass on iOS 26 and the scrim before that, over the video.
struct UpNextCard: View {
    let upNext: UpNext
    let onPlayNow: () -> Void
    let onWatchCredits: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            HStack(spacing: Space.s3) {
                Artwork(url: upNext.imageUrl, title: upNext.title, aspectRatio: 16.0 / 9.0, blurHash: upNext.imageBlurHash)
                    .frame(width: 112)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(Strings.shared.upNext.titleCased())
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                    Text(Strings.shared.taggedTitle(tag: upNext.label, title: upNext.title))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(2)
                    Text(Strings.shared.upNextIn(seconds: upNext.secondsLeft))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            HStack(spacing: Space.s2) {
                Button(action: onPlayNow) {
                    Label(Strings.shared.playNow.titleCased(), systemImage: "play.fill")
                        .font(.headline)
                        .foregroundStyle(Color(.systemBackground))
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(Capsule().fill(Color.primary))
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                Button(action: onWatchCredits) {
                    Text(Strings.shared.watchCredits.titleCased())
                        .font(.headline)
                        .foregroundStyle(.primary)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .contentShape(Capsule())
                }
                .modifier(SkipButtonStyle())
            }
        }
        .padding(Space.s3)
        .frame(width: 340)
        .modifier(UpNextBackground())
    }
}

/// Real Liquid Glass on iOS 26, the strong scrim before that.
private struct UpNextBackground: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.glassEffect(in: .rect(cornerRadius: Radius.card))
        } else {
            content.background(Brand.scrimStrong, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        }
    }
}
