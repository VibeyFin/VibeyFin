import SwiftUI
import Shared

/// One item's page: picture, title, the short facts, genres, Play and the overview.
struct ItemView: View {
    @StateObject private var model: ItemObservable
    @State private var showsPlayer = false
    @State private var startsOver = false

    private let itemId: String

    /// Called when the session ended and the screen must be left.
    let onExit: (HomeExit) -> Void

    init(itemId: String, onExit: @escaping (HomeExit) -> Void) {
        _model = StateObject(wrappedValue: ItemObservable(itemId: itemId))
        self.itemId = itemId
        self.onExit = onExit
    }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: Space.s3) {
                    if let state = model.state, !state.loading {
                        if let item = state.item {
                            details(item, state: state)
                        } else if let message = state.message {
                            LoadErrorView(message: message, onRetry: { model.retry() })
                        }
                    } else {
                        LoadingIndicator()
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .screenMargin()
                .padding(.vertical, Space.s5)
            }
        }
        .onAppear { model.start() }
        .fullScreenCover(isPresented: $showsPlayer) {
            PlayerView(itemId: itemId, startOver: startsOver, onExit: onExit)
        }
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        // Only the chevron, no "Back" label. The swipe from the edge still goes back.
        .toolbarRole(.editor)
        .onChange(of: model.state?.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
        }
    }

    private func play(startOver: Bool) {
        startsOver = startOver
        showsPlayer = true
    }

    @ViewBuilder
    private func details(_ item: MediaItem, state: ItemUiState) -> some View {
        let facts = state.facts
        // A wide backdrop when the server has one; otherwise the item's own picture at its own shape.
        if let backdrop = item.backdropUrl {
            Artwork(url: backdrop, title: item.title, aspectRatio: 16.0 / 9.0)
                .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
        } else if let poster = item.posterUrl {
            Artwork(url: poster, title: item.title, aspectRatio: CGFloat(item.posterAspectRatio))
                .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
        }
        if let seriesName = item.seriesName {
            Text(seriesName)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
        }
        Text(item.title)
            .font(.title2.bold())
            .foregroundStyle(.primary)
        if !facts.isEmpty {
            Text(facts.joined(separator: "  ·  "))
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        if !item.genres.isEmpty {
            Text(item.genres.joined(separator: "  ·  "))
                .font(.subheadline)
                .foregroundStyle(.primary)
        }
        // The one filled action on the page: the aqua capsule.
        // "Play" or "Resume from 41:12", with "Start over" beside it only when there is something to resume.
        if state.canPlay {
            HStack(spacing: Space.s3) {
                PrimaryButton(state.playLabel) { play(startOver: false) }
                if state.canStartOver {
                    SecondaryButton(title: Strings.shared.startOver) { play(startOver: true) }
                }
            }
            .padding(.vertical, Space.s1)
        }
        if let overview = item.overview {
            Text(overview)
                .font(.body)
                .foregroundStyle(.primary)
        }
    }
}
