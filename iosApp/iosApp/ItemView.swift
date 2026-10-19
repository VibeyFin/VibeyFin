import SwiftUI
import Shared

/// One item's page: picture, title, the short facts, genres and overview.
struct ItemView: View {
    @StateObject private var model: ItemObservable

    /// Called when the session ended and the screen must be left.
    let onExit: (HomeExit) -> Void

    init(itemId: String, onExit: @escaping (HomeExit) -> Void) {
        _model = StateObject(wrappedValue: ItemObservable(itemId: itemId))
        self.onExit = onExit
    }

    var body: some View {
        ZStack {
            Palette.ink.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let state = model.state, !state.loading {
                        if let item = state.item {
                            details(item, facts: state.facts)
                        } else if let message = state.message {
                            LoadErrorView(message: message, onRetry: { model.retry() })
                        }
                    } else {
                        LoadingIndicator()
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(24)
            }
        }
        .onAppear { model.start() }
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        // Only the chevron, no "Back" label. The swipe from the edge still goes back.
        .toolbarRole(.editor)
        .onChange(of: model.state?.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
        }
    }

    @ViewBuilder
    private func details(_ item: MediaItem, facts: [String]) -> some View {
        // A wide backdrop when the server has one; otherwise the item's own picture at its own shape.
        if let backdrop = item.backdropUrl {
            Artwork(url: backdrop, title: item.title, aspectRatio: 16.0 / 9.0)
                .clipShape(RoundedRectangle(cornerRadius: 16))
        } else if let poster = item.posterUrl {
            Artwork(url: poster, title: item.title, aspectRatio: CGFloat(item.posterAspectRatio))
                .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        if let seriesName = item.seriesName {
            Text(seriesName)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Palette.textMuted)
        }
        Text(item.title).vibeTitle(size: 34)
        if !facts.isEmpty {
            Text(facts.joined(separator: "  ·  "))
                .font(.subheadline)
                .foregroundColor(Palette.textMuted)
        }
        if !item.genres.isEmpty {
            Text(item.genres.joined(separator: "  ·  "))
                .font(.subheadline)
                .foregroundColor(Palette.text)
        }
        if let overview = item.overview {
            Text(overview)
                .font(.body)
                .foregroundColor(Palette.text)
        }
    }
}
