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

/// A grey bar as tall as one line of text in [font]: a redacted word, so it is exactly as tall as the real thing.
struct SkeletonLine: View {
    var font: Font = .subheadline
    var width: CGFloat = 96

    var body: some View {
        Text(Strings.shared.loadingPage)
            .font(font)
            .redacted(reason: .placeholder)
            .frame(width: width, alignment: .leading)
            .clipped()
    }
}

/// Poster tiles while a grid loads: [count] grey posters with a title line under each.
struct PosterSkeletonGrid: View {
    var count: Int = 6
    let minimumWidth: CGFloat
    var aspectRatio: CGFloat = 2.0 / 3.0

    var body: some View {
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: minimumWidth), spacing: Space.s3, alignment: .top)],
            alignment: .leading,
            spacing: Space.s4
        ) {
            ForEach(0..<count, id: \.self) { _ in
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

/// The item page while it loads: the picture, the title, the facts line, the action and the overview.
struct ItemSkeleton: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    private var wide: Bool { sizeClass == .regular }

    var body: some View {
        // The shapes of the page: the tall picture with the logo, the facts, Play and the four actions at its foot.
        GeometryReader { proxy in
            ZStack(alignment: .bottom) {
                Rectangle().fill(Color(.tertiarySystemBackground))
                VStack(alignment: wide ? .leading : .center, spacing: Space.s3) {
                    RoundedRectangle(cornerRadius: 6).fill(Color(.quaternarySystemFill)).frame(width: proxy.size.width * 0.6, height: 56)
                    RoundedRectangle(cornerRadius: 6).fill(Color(.quaternarySystemFill)).frame(width: 150, height: 16)
                    RoundedRectangle(cornerRadius: 6).fill(Color(.quaternarySystemFill)).frame(height: 40)
                    Capsule().fill(Color(.quaternarySystemFill)).frame(height: 50)
                    HStack {
                        ForEach(0..<4, id: \.self) { _ in
                            VStack(spacing: Space.s1) {
                                Circle().fill(Color(.quaternarySystemFill)).frame(width: 30, height: 30)
                                RoundedRectangle(cornerRadius: 6).fill(Color(.quaternarySystemFill)).frame(width: 56, height: 14)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                }
                .padding(.horizontal, Space.s4)
                .padding(.bottom, Space.s5)
            }
            .frame(height: min(proxy.size.width * 1.5, proxy.size.height))
        }
        .ignoresSafeArea(edges: .top)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Strings.shared.loadingPage)
    }
}

/// Round avatars with a name line, for "Who's watching?" while the profiles load.
struct ProfileSkeletonGrid: View {
    var count: Int = 4
    let avatarSize: CGFloat

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 110), spacing: Space.s4, alignment: .top)], spacing: Space.s5) {
            ForEach(0..<count, id: \.self) { _ in
                VStack(spacing: Space.s2) {
                    Circle().fill(Color(.tertiarySystemBackground)).frame(width: avatarSize, height: avatarSize)
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
                ForEach(0..<count, id: \.self) { _ in
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
