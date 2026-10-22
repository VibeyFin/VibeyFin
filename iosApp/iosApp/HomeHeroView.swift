import SwiftUI
import Shared

/// The one featured item, above Continue watching: its backdrop full width as a 4:5 crop under a scrim, the Logo as the title, a
/// line of facts and Play, the one aqua button. Tapping the picture opens the item's page. A movie plays at once; a show opens its
/// page, where Play knows which episode is next.
struct HomeHeroView: View {
    let item: MediaItem
    let onPlay: (String) -> Void
    let onOpen: (String) -> Void

    var body: some View {
        Button { onOpen(item.id) } label: {
            Artwork(url: item.backdropUrl, title: item.title, aspectRatio: 4.0 / 5.0, blurHash: item.backdropBlurHash)
                .overlay {
                    LinearGradient(
                        stops: [.init(color: .clear, location: 0), .init(color: .clear, location: 0.45), .init(color: Brand.scrim, location: 0.8), .init(color: Brand.scrimStrong, location: 1)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                }
                .overlay(alignment: .bottom) { details }
                .clipped()
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Strings.shared.featured(title: item.title))
    }

    private var details: some View {
        VStack(spacing: Space.s3) {
            if let logo = item.logoUrl, let url = URL(string: logo) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFit()
                } placeholder: {
                    Color.clear
                }
                .frame(maxWidth: 240, maxHeight: 88)
            }
            let facts = HomeHeroKt.getHeroFacts(item)
            if !facts.isEmpty {
                Text(facts.joined(separator: "  \u{00B7}  "))
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.white)
            }
            PrimaryButton(action: { item.kind == ItemKind.movie ? onPlay(item.id) : onOpen(item.id) }) {
                Label(Strings.shared.play.titleCased(), systemImage: "play.fill")
            }
            .frame(width: 200)
        }
        .padding(.horizontal, Space.s4)
        .padding(.bottom, Space.s4)
    }
}
