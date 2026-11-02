import SwiftUI
import Shared

// Grey stand-ins for content that is still loading: the same shapes as the real thing, so nothing moves
// when it arrives. Not animated; looping motion is an opt-in setting (`brand.md`). A `ProgressView` is
// only for an action in progress (a button, the player, "loading more").

/// A grey block with the artwork corner radius.
struct SkeletonBlock: View {
    var cornerRadius: CGFloat = Radius.artwork

    var body: some View {
        Color(.tertiarySystemBackground)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

/// A grey bar as tall as one line of text in [font]: a redacted word, so it is exactly as tall as the real thing. With no
/// [width] it runs the full width of its row, like a line of a paragraph.
struct SkeletonLine: View {
    var font: Font = .subheadline
    var width: CGFloat? = 96

    var body: some View {
        if let width {
            Text(Strings.shared.loadingPage)
                .font(font)
                .redacted(reason: .placeholder)
                .frame(width: width, alignment: .leading)
                .clipped()
        } else {
            Text(Strings.shared.loadingPage)
                .font(font)
                .hidden()
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(SkeletonBlock(cornerRadius: Radius.artwork))
        }
    }
}

/// How many placeholders a row or grid draws: [phone] on a compact width, twice as many on a regular one (iPad, a big iPhone
/// in landscape), where the same count would leave the row looking nearly empty. A row that scrolls never shows the extras.
func skeletonCount(_ phone: Int, regularWidth: Bool) -> Int {
    regularWidth ? phone * 2 : phone
}

/// Poster tiles while a grid loads: [count] grey posters with a title line under each.
struct PosterSkeletonGrid: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    var count: Int = 6
    let minimumWidth: CGFloat
    var aspectRatio: CGFloat = 2.0 / 3.0

    var body: some View {
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: minimumWidth), spacing: Space.s3, alignment: .top)],
            alignment: .leading,
            spacing: Space.s4
        ) {
            ForEach(0..<skeletonCount(count, regularWidth: sizeClass == .regular), id: \.self) { _ in
                VStack(alignment: .leading, spacing: Space.s2) {
                    SkeletonBlock().aspectRatio(aspectRatio, contentMode: .fit)
                    SkeletonLine()
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Strings.shared.loadingPage)
    }
}

/// The item page while it loads, laid out like `ItemView`: on a phone the picture pinned at the top (2:3) with the title, facts,
/// genres, overview, Play and the four actions centred at the bottom of it, then a row of posters under it; on a regular width the
/// backdrop fills the screen with the same details in a column on the left, and the row under the first screen.
struct ItemSkeleton: View {
    let wide: Bool
    /// The page's side margin, as `ItemView` sets it.
    let margin: CGFloat

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .top) {
                picture(width: proxy.size.width, height: proxy.size.height)
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        if wide {
                            Color.clear.frame(height: proxy.size.height * 0.3)
                            HeroDetailsSkeleton(centered: false)
                                .frame(maxWidth: min(proxy.size.width * 0.46, 520), alignment: .leading)
                                .padding(.horizontal, margin)
                        } else {
                            HeroDetailsSkeleton(centered: true)
                                .padding(.horizontal, Space.s4)
                                .padding(.bottom, Space.s4)
                                .frame(maxWidth: .infinity, minHeight: proxy.size.width * 1.5, alignment: .bottom)
                        }
                        VStack(alignment: .leading, spacing: Space.s3) {
                            SkeletonLine(font: .title3.bold(), width: 160)
                                .padding(.horizontal, margin)
                            PosterSkeletonRow(tileWidth: wide ? 168 : 112)
                        }
                        .padding(.vertical, Space.s5)
                    }
                }
                .scrollDisabled(true)
            }
            .frame(height: min(proxy.size.width * 1.5, proxy.size.height))
        }
        .environment(\.screenMarginOverride, wide ? Space.s6 : nil)
        .ignoresSafeArea(edges: wide ? .all : .top)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Strings.shared.loadingPage)
    }

    /// The backdrop's stand-in: 2:3 at the top of a phone, the whole screen on a regular width, fading into the page's colour
    /// where the text starts.
    private func picture(width: CGFloat, height: CGFloat) -> some View {
        ZStack {
            Color(.tertiarySystemBackground)
            LinearGradient(
                stops: wide
                    ? [.init(color: .clear, location: 0.6), .init(color: Color(.systemBackground), location: 1)]
                    : [.init(color: .clear, location: 0.4), .init(color: Color(.systemBackground), location: 1)],
                startPoint: .top,
                endPoint: .bottom
            )
        }
        .frame(width: width, height: wide ? height : width * 1.5)
    }
}

/// `HeroDetailsView`'s content in grey: the title, the facts, the genres, three lines of the overview, the Play capsule and the
/// four actions (an icon over a label). Centred on a phone, at the start on a regular width.
private struct HeroDetailsSkeleton: View {
    let centered: Bool

    var body: some View {
        VStack(alignment: centered ? .center : .leading, spacing: Space.s3) {
            SkeletonLine(font: .brandDisplay, width: centered ? 240 : 320)
            SkeletonLine(font: .subheadline, width: 180)
            SkeletonLine(font: .subheadline, width: 140)
            VStack(spacing: 0) {
                SkeletonLine(font: .subheadline, width: nil)
                SkeletonLine(font: .subheadline, width: nil)
                SkeletonLine(font: .subheadline, width: 200)
            }
            Capsule()
                .fill(Color(.tertiarySystemBackground))
                .frame(height: 50)
                .frame(maxWidth: centered ? .infinity : 260)
            HStack(spacing: 0) {
                ForEach(0..<4, id: \.self) { _ in
                    VStack(spacing: Space.s1) {
                        SkeletonBlock(cornerRadius: Radius.artwork).frame(width: 26, height: 26)
                        SkeletonLine(font: .caption, width: 40)
                    }
                    .frame(maxWidth: .infinity, minHeight: 56)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: centered ? .center : .leading)
    }
}

/// Profile tiles with a name line, for "Who's watching?" while the profiles load.
struct ProfileSkeletonGrid: View {
    var count: Int = 4
    let avatarSize: CGFloat

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: avatarSize + Space.s2), spacing: Space.s4, alignment: .top)], spacing: Space.s5) {
            ForEach(0..<count, id: \.self) { _ in
                VStack(spacing: Space.s2) {
                    RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
                        .fill(Color(.tertiarySystemBackground))
                        .frame(width: avatarSize, height: avatarSize)
                    SkeletonLine(font: .subheadline, width: 72)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Strings.shared.loadingPage)
    }
}

/// A row of grey tiles at [tileWidth], for a row of posters or library tiles while it loads.
struct PosterSkeletonRow: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    var count: Int = 4
    var tileWidth: CGFloat = 112
    var aspectRatio: CGFloat = 2.0 / 3.0
    /// As many caption lines as the real tiles reserve, so the row keeps its height when they replace the grey ones.
    var captionLines = 2

    var body: some View {
        // In a scroll view that never scrolls: fixed-width tiles can be wider than a phone, and laid out on their
        // own they would stretch everything above them past the screen's edge.
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: Space.s3) {
                ForEach(0..<skeletonCount(count, regularWidth: sizeClass == .regular), id: \.self) { _ in
                    VStack(alignment: .leading, spacing: Space.s2) {
                        SkeletonBlock().aspectRatio(aspectRatio, contentMode: .fit)
                        ForEach(0..<captionLines, id: \.self) { line in
                            SkeletonLine(width: line == 0 ? 72 : 48)
                        }
                    }
                    .frame(width: tileWidth, alignment: .leading)
                }
            }
            .screenMargin()
        }
        .scrollDisabled(true)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Strings.shared.loadingPage)
    }
}
