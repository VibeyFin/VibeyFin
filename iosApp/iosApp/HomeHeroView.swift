import SwiftUI
import Shared

/// The one featured item, above Continue watching, as a rounded card inside the page's margins: its backdrop under a scrim, the Logo
/// as the title, a line saying what it is and its genres, then Play (the one aqua button) beside My List. Tapping the picture opens the
/// item's page. Play plays: a movie, or the episode a show is up to.
struct HomeHeroView: View {
    let item: MediaItem
    let onPlay: () -> Void
    let onOpen: (String) -> Void
    let onMyList: () -> Void

    @Environment(\.horizontalSizeClass) private var sizeClass
    private var wide: Bool { sizeClass == .regular }

    var body: some View {
        // A banner rather than a full poster, so Continue watching stays close to the top: 4:3 on a phone, wider on an iPad.
        Artwork(url: item.backdropUrl, title: item.title, aspectRatio: wide ? 2.6 : 4.0 / 3.0, blurHash: item.backdropBlurHash)
            .overlay {
                LinearGradient(
                    stops: [.init(color: .clear, location: 0), .init(color: .clear, location: 0.4), .init(color: Brand.scrim, location: 0.75), .init(color: Brand.scrimStrong, location: 1)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .allowsHitTesting(false)
            }
            .overlay(alignment: wide ? .bottomLeading : .bottom) { details }
            .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous).stroke(Color(.tertiarySystemBackground), lineWidth: 1))
            .contentShape(Rectangle())
            .onTapGesture { onOpen(item.id) }
            .accessibilityElement(children: .contain)
            .accessibilityLabel(Strings.shared.featured(title: item.title))
    }

    private var details: some View {
        VStack(alignment: wide ? .leading : .center, spacing: Space.s2) {
            if let logo = item.logoUrl, let url = URL(string: logo) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFit()
                } placeholder: {
                    Color.clear
                }
                .frame(maxWidth: wide ? 240 : 200, maxHeight: wide ? 72 : 52, alignment: wide ? .leading : .center)
            }
            let facts = HomeHeroKt.getHeroFacts(item)
            if !facts.isEmpty {
                // "Show · Offbeat · Heartfelt · Comedy": what it is, then its genres.
                Text(facts.joined(separator: "  \u{00B7}  "))
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.white)
                    .lineLimit(1)
            }
            HStack(spacing: Space.s2) {
                PrimaryButton(action: onPlay) {
                    Label(Strings.shared.play.titleCased(), systemImage: "play.fill")
                }
                // A size smaller than the page's large buttons: they sit on a picture.
                .controlSize(.regular)
                // Not aqua: aqua is the one primary action. A soft, see-through button over the picture.
                Button(action: onMyList) {
                    Label(Strings.shared.myList.titleCased(), systemImage: item.isFavorite ? "checkmark" : "plus")
                        .font(.body.weight(.medium))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: 40)
                        .background(Capsule().fill(Color.white.opacity(0.2)))
                }
                .buttonStyle(PressableButtonStyle())
                .accessibilityLabel(Strings.shared.myListAction(onList: item.isFavorite))
            }
            .frame(maxWidth: wide ? 360 : .infinity)
        }
        .padding(Space.s3)
    }
}

/// The hero's place while it is being picked: the same rounded card at the same height, with grey stand-ins for the logo, the line
/// and the two buttons, so the hero does not just appear.
struct HomeHeroSkeleton: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    private var wide: Bool { sizeClass == .regular }

    var body: some View {
        Color(.tertiarySystemBackground)
            .aspectRatio(wide ? 2.6 : 4.0 / 3.0, contentMode: .fit)
            .overlay(alignment: wide ? .bottomLeading : .bottom) {
                VStack(alignment: wide ? .leading : .center, spacing: Space.s2) {
                    RoundedRectangle(cornerRadius: 6).fill(Color(.quaternarySystemFill)).frame(width: wide ? 160 : 180, height: 40)
                    RoundedRectangle(cornerRadius: 6).fill(Color(.quaternarySystemFill)).frame(width: 150, height: 16)
                    HStack(spacing: Space.s2) {
                        Capsule().fill(Color(.quaternarySystemFill)).frame(height: 40)
                        Capsule().fill(Color(.quaternarySystemFill)).frame(height: 40)
                    }
                    .frame(maxWidth: wide ? 360 : .infinity)
                }
                .padding(Space.s3)
            }
            .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous).stroke(Color(.tertiarySystemBackground), lineWidth: 1))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Strings.shared.loadingPage)
    }
}
