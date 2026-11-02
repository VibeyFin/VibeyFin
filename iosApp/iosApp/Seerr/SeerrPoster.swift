import SwiftUI
import Shared

/// A Seerr title as a poster tile: the artwork with its marker in the badge slot (top-left), the title on up to two lines and
/// "2025 · Movie" under it. Read as one element, with what Seerr knows of it.
struct SeerrPosterTile: View {
    let title: SeerrTitle
    /// Under the title instead of "2025 · Movie", such as Coming soon's "Dec 12".
    var meta: String?
    /// A title the library has is drawn as the library's own tile, with no Seerr marker (the discover rows).
    var plainInLibrary = false

    var body: some View {
        let meta = self.meta ?? Strings.shared.seerrTitleMeta(year: title.year, type: title.type)
        let marked = !(plainInLibrary && title.jellyfinItemId != nil)
        VStack(alignment: .leading, spacing: Space.s2) {
            Artwork(url: title.posterUrl, title: title.title, aspectRatio: 2.0 / 3.0, blurHash: nil)
                .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
                .overlay(alignment: .topLeading) { if marked { SeerrMarker(state: title.state).padding(6) } }
            VStack(alignment: .leading, spacing: 2) {
                // Always two lines' room, so tiles are one height and rows never change size.
                Text(title.title).font(.subheadline.weight(.semibold)).foregroundStyle(.primary).lineLimit(2, reservesSpace: true)
                Text(meta).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            .multilineTextAlignment(.leading)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Strings.shared.seerrTitleDescription(title: title.title, meta: meta, state: title.state))
    }
}

/// What Seerr knows about a title, in the poster's one badge slot: a + disc for a title nobody asked for, otherwise a pill with a
/// symbol and a word. Primary text on real glass (iOS 26) or the scrim, never aqua; a failure is in red.
struct SeerrMarker: View {
    let state: RequestState

    var body: some View {
        if let label = Strings.shared.seerrStateLabel(state: state) {
            HStack(spacing: 4) {
                Image(systemName: Self.symbol(state)).font(.system(size: 10, weight: .semibold))
                Text(label).font(.caption2.weight(.semibold)).lineLimit(1)
            }
            .foregroundStyle(state == .failed ? Color(.systemRed) : Color.primary)
            .padding(.leading, 6)
            .padding(.trailing, 8)
            .frame(height: 22)
            .modifier(MarkerBackground())
        } else {
            Image(systemName: "plus")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.primary)
                .frame(width: 26, height: 26)
                .modifier(MarkerBackground())
        }
    }

    static func symbol(_ state: RequestState) -> String {
        switch state {
        case .requested: return "clock"
        case .approved: return "hourglass"
        case .partlyavailable: return "circle.lefthalf.filled"
        case .available: return "play.circle"
        case .declined: return "nosign"
        default: return "exclamationmark.circle"
        }
    }
}

/// Real Liquid Glass on iOS 26, the scrim before that.
private struct MarkerBackground: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.glassEffect(.regular, in: Capsule())
        } else {
            content.background(Capsule().fill(Brand.scrim))
        }
    }
}

/// A grey stand-in for `SeerrPosterTile`, laid out like it, so the row keeps its height when the real tiles arrive.
struct SeerrPosterPlaceholder: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Space.s2) {
            SkeletonBlock().aspectRatio(2.0 / 3.0, contentMode: .fit)
            VStack(alignment: .leading, spacing: 2) {
                Text(" ").font(.subheadline.weight(.semibold)).lineLimit(2, reservesSpace: true)
                Text(" ").font(.caption).lineLimit(1)
            }
            .hidden()
        }
        .accessibilityHidden(true)
    }
}
