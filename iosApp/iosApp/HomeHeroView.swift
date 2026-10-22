import SwiftUI
import Shared

/// The one featured item, above Continue watching, as a rounded card inside the page's margins: its backdrop under a scrim, the Logo
/// as the title, a line saying what it is and its genres, then Play (the one aqua button) beside My List. Tapping the picture opens the
/// item's page. A movie plays at once; a show opens its page, where Play knows which episode is next.
struct HomeHeroView: View {
    let item: MediaItem
    let onPlay: (String) -> Void
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
                PrimaryButton(action: { item.kind == ItemKind.movie ? onPlay(item.id) : onOpen(item.id) }) {
                    Label(Strings.shared.play.titleCased(), systemImage: "play.fill")
                }
                // Not aqua: aqua is the one primary action. A soft, see-through button over the picture.
                Button(action: onMyList) {
                    Label(Strings.shared.myList.titleCased(), systemImage: item.isFavorite ? "checkmark" : "plus")
                        .font(.body.weight(.medium))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous).fill(Color.white.opacity(0.2)))
                }
                .buttonStyle(PressableButtonStyle())
                .accessibilityLabel(Strings.shared.myListAction(onList: item.isFavorite))
            }
            .frame(maxWidth: wide ? 360 : .infinity)
        }
        .padding(Space.s3)
    }
}
