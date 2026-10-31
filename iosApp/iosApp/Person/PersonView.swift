import SwiftUI
import Shared

/// A cast member's page: a round portrait, the name, where and when they were born, the biography, then what they appear in.
struct PersonView: View {
    @StateObject private var model: PersonObservable
    @State private var expanded = false
    @Environment(\.horizontalSizeClass) private var sizeClass

    let onOpen: (MediaItem) -> Void
    let onExit: (HomeExit) -> Void

    init(personId: String, onOpen: @escaping (MediaItem) -> Void, onExit: @escaping (HomeExit) -> Void) {
        _model = StateObject(wrappedValue: PersonObservable(personId: personId))
        self.onOpen = onOpen
        self.onExit = onExit
    }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            ScrollView {
                VStack(spacing: Space.s4) {
                    if let state = model.state {
                        content(state)
                    } else {
                        skeleton
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, Space.s5)
            }
        }
        .onAppear { model.start() }
        .backMenuTitle(model.state?.person?.name ?? "")
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .toolbarRole(.editor)
        .onChange(of: model.state?.exit != nil) { _, hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
        }
    }

    @ViewBuilder
    private func content(_ state: PersonUiState) -> some View {
        if state.loading {
            skeleton
        } else if let message = state.message {
            LoadErrorView(message: message, onRetry: { model.retry() }).screenMargin()
        } else if let person = state.person {
            header(state, person)
            VStack(alignment: .leading, spacing: Space.s5) {
                if state.movies.isVisible {
                    MediaRowView(row: state.movies, onOpen: onOpen, onSeeAll: { _ in }, onRetry: { model.retry() }, tileWidth: sizeClass == .regular ? 168 : 112)
                }
                if state.shows.isVisible {
                    MediaRowView(row: state.shows, onOpen: onOpen, onSeeAll: { _ in }, onRetry: { model.retry() }, tileWidth: sizeClass == .regular ? 168 : 112)
                }
                if let message = state.appearancesMessage {
                    LoadErrorView(message: message, onRetry: { model.retry() }).screenMargin()
                }
                if state.noAppearances {
                    Text(Strings.shared.noAppearances)
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .screenMargin()
                }
            }
        }
    }

    private func header(_ state: PersonUiState, _ person: Person) -> some View {
        VStack(spacing: Space.s2) {
            portrait(person)
            Text(person.name)
                .font(.brandDisplay)
                .tracking(-0.8)
                .multilineTextAlignment(.center)
                .lineLimit(3)
                .padding(.top, Space.s2)
            if !state.facts.isEmpty {
                Text(Strings.shared.factsLine(facts: state.facts))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            Text(person.overview ?? Strings.shared.noBiography)
                .font(.subheadline)
                .foregroundStyle(person.overview == nil ? .secondary : .primary)
                .multilineTextAlignment(.center)
                .lineLimit(expanded ? nil : 4)
                .padding(.top, Space.s2)
            if let overview = person.overview, overview.count > 180 {
                Button((expanded ? Strings.shared.less : Strings.shared.more).titleCased()) { expanded.toggle() }
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .screenMargin()
    }

    private func portrait(_ person: Person) -> some View {
        PortraitView(name: person.name, imageUrl: person.imageUrl, size: 160, initialsFont: .brandDisplay)
            .accessibilityLabel(person.name)
    }

    private var skeleton: some View {
        VStack(spacing: Space.s3) {
            SkeletonBlock().frame(width: 160, height: 160).clipShape(Circle())
            SkeletonLine(font: .title, width: 200)
            SkeletonLine(font: .subheadline, width: 140)
            SkeletonLine(font: .subheadline, width: 280)
        }
    }
}
