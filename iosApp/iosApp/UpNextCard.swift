import SwiftUI
import Shared

/// Offers the next episode in the last seconds of this one: its picture, "S1 E2 · title", how many
/// seconds until it starts on its own, "Play now" (the one aqua action while it is up) and "Watch
/// credits", which dismisses it. It sits on the scrim, over the video.
struct UpNextCard: View {
    let upNext: UpNext
    let onPlayNow: () -> Void
    let onWatchCredits: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            HStack(spacing: Space.s3) {
                Artwork(url: upNext.imageUrl, title: upNext.title, aspectRatio: 16.0 / 9.0)
                    .frame(width: 112)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(Strings.shared.upNext.titleCased())
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                    Text([upNext.label, upNext.title].compactMap { $0 }.joined(separator: " · "))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(2)
                    Text(Strings.shared.upNextIn(seconds: upNext.secondsLeft))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            HStack(spacing: Space.s2) {
                PrimaryButton(action: onPlayNow) {
                    Label(Strings.shared.playNow.titleCased(), systemImage: "play.fill")
                }
                SecondaryButton(title: Strings.shared.watchCredits, action: onWatchCredits)
            }
        }
        .padding(Space.s3)
        .frame(width: 340)
        .background(Brand.scrimStrong, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
    }
}
