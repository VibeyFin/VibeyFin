import SwiftUI
import Shared

// A skeleton is the real view drawn with `Placeholders` data and shaped by `.redacted`: text and shapes turn into grey bars, so it is
// exactly as tall as the real thing. It wears the one shimmer (`skeletonShimmer()`), which plays unless the system asks for Reduce Motion.
// Only the large pictures (the hero, the item's backdrop) are plain `SkeletonBlock`s. A placeholder never fetches and never navigates.
// A `ProgressView` is only for an action in progress (a button, the player, "loading more").

/// A grey block with the artwork corner radius, for a large picture that has no text to shape it.
struct SkeletonBlock: View {
    var cornerRadius: CGFloat = Radius.artwork

    var body: some View {
        Color(.tertiarySystemBackground)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .skeletonShimmer()
    }
}

private struct SkeletonStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .environment(\.tileMenu, nil)
            .redacted(reason: .placeholder)
            .textRenderer(SkeletonTextRenderer())
            .skeletonShimmer()
            .allowsHitTesting(false)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Strings.shared.loadingPage)
    }
}

private struct SkeletonHidden: ViewModifier {
    @Environment(\.redactionReasons) private var redactionReasons: RedactionReasons

    func body(content: Content) -> some View {
        content.opacity(redactionReasons.contains(.placeholder) ? 0 : 1)
    }
}

extension View {
    /// Draws the view as grey shapes with the shimmer: its text and pictures stand as bars and blocks, and it can't be tapped.
    func skeleton() -> some View {
        modifier(SkeletonStyle())
    }

    /// Takes the view out of a skeleton (a progress bar, a play disc, a badge) while keeping its room, as it is not a shape of the content.
    func skeletonHidden() -> some View {
        modifier(SkeletonHidden())
    }
}

extension EnvironmentValues {
    /// True while the view is drawn as a skeleton.
    var isSkeleton: Bool { redactionReasons.contains(.placeholder) }
}

/// How many placeholder tiles fill a row's visible width and peek past its edge, or the [known] count when the screen has it.
func skeletonTileCount(screenWidth: CGFloat?, tileWidth: CGFloat, spacing: CGFloat, known: Int?) -> Int {
    let visibleWidth: Float = Float((screenWidth ?? tileWidth * 4) - Space.s4)
    let knownCount: KotlinInt? = known.map { KotlinInt(value: Int32($0)) }

    return Int(PlaceholderCount.shared.forRow(visibleWidth: visibleWidth, tileWidth: Float(tileWidth), spacing: Float(spacing), known: knownCount))
}

/// How many placeholder tiles fill a grid's visible height in whole rows, plus one peeking row, as Android's `placeholderGridCount` does.
func skeletonGridCount(screenWidth: CGFloat?, screenHeight: CGFloat?, minimumWidth: CGFloat, spacing: CGFloat, rowSpacing: CGFloat, captionHeight: CGFloat) -> Int {
    let gridWidth: CGFloat   = (screenWidth ?? minimumWidth * 3) - Space.s4 * 2
    let columns: Int         = max(1, Int(((gridWidth + spacing) / (minimumWidth + spacing)).rounded(.down)))
    let tileWidth: CGFloat   = (gridWidth - CGFloat(columns - 1) * spacing) / CGFloat(columns)
    let rowHeight: CGFloat   = tileWidth / CGFloat(MediaAspect.shared.POSTER) + captionHeight
    let visibleHeight: Float = Float(screenHeight ?? rowHeight * 4)

    return columns * Int(PlaceholderCount.shared.forRow(visibleWidth: visibleHeight, tileWidth: Float(rowHeight), spacing: Float(rowSpacing), known: nil))
}

/// A row of [tile]s at [tileWidth] drawn as a skeleton, for a row of posters, cards or library tiles while it loads.
struct SkeletonRow<Tile: View>: View {
    let tileWidth: CGFloat
    var spacing: CGFloat = Space.s3
    var known: Int?
    @ViewBuilder let tile: () -> Tile

    @Environment(\.skeletonShimmerWidth) private var screenWidth: CGFloat?

    var body: some View {
        // In a scroll view that never scrolls: fixed-width tiles can be wider than a phone, and laid out on their
        // own they would stretch everything above them past the screen's edge.
        HorizontalRow(lifts: false) {
            HStack(alignment: .top, spacing: spacing) {
                ForEach(0..<skeletonTileCount(screenWidth: screenWidth, tileWidth: tileWidth, spacing: spacing, known: known), id: \.self) { _ in
                    tile().frame(width: tileWidth, alignment: .leading)
                }
            }
        }
        .scrollDisabled(true)
        .skeleton()
    }
}

/// An adaptive grid of [tile]s drawn as a skeleton, laid out as the real grid at the same [minimumWidth].
struct SkeletonGrid<Tile: View>: View {
    let minimumWidth: CGFloat
    var known: Int?
    @ViewBuilder let tile: () -> Tile

    @Environment(\.skeletonShimmerWidth) private var screenWidth: CGFloat?
    @Environment(\.skeletonScreenHeight) private var screenHeight: CGFloat?

    private let posterCaptionHeight: CGFloat = 48

    var body: some View {
        let count: Int = known ?? skeletonGridCount(
            screenWidth: screenWidth,
            screenHeight: screenHeight,
            minimumWidth: minimumWidth,
            spacing: Space.s3,
            rowSpacing: Space.s4,
            captionHeight: posterCaptionHeight
        )

        LazyVGrid(columns: [GridItem(.adaptive(minimum: minimumWidth), spacing: Space.s3, alignment: .top)], alignment: .leading, spacing: Space.s4) {
            ForEach(0..<count, id: \.self) { _ in tile() }
        }
        .skeleton()
    }
}

/// The poster tiles of a library row or grid, as the real tiles draw them.
struct PosterSkeletonTile: View {
    var captionLines: Int = 2

    var body: some View {
        ArtworkTile(item: Placeholders.shared.posterTile, captionLines: captionLines)
    }
}

/// A row's header and posters, for the moment before the rows are known.
struct RowPlaceholder: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            Text(Placeholders.shared.mediaRow(count: 0).title)
                .sectionTitle()
                .lineLimit(1)
                .screenMargin()
                .skeleton()
            SkeletonRow(tileWidth: 112) { PosterSkeletonTile() }
        }
    }
}
