import SwiftUI
import Shared

/// A cast member's page: a round portrait, the name, where and when they were born, the biography, then what they appear in.
struct PersonView: View {
    @StateObject private var model: PersonObservable
    @State private var expanded = false
    @Environment(\.horizontalSizeClass) private var sizeClass

    let onOpen: (MediaItem) -> Void
    let onExit: (HomeExit) -> Void

    private let portraitSize: CGFloat = 160
    private let wideTileWidth: CGFloat = 168
    /// The header stops here, so the biography stays readable on a wide iPad.
    private let contentMaxWidth: CGFloat = 720

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

    private var wide: Bool { sizeClass == .regular }

    @ViewBuilder
    private func content(_ state: PersonUiState) -> some View {
        if state.loading {
            skeleton
        } else if let message = state.message {
            LoadErrorView(message: message, onRetry: { model.retry() }).screenMargin()
        } else if let person = state.person {
            headerContent(facts: state.facts, person: person)
            VStack(alignment: .leading, spacing: Space.s5) {
                if state.movies.isVisible { filmography(state.movies) }
                if state.shows.isVisible { filmography(state.shows) }
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

    @ViewBuilder
    private func filmography(_ row: MediaRow) -> some View {
        if wide {
            VStack(alignment: .leading, spacing: Space.s3) {
                Text(row.title)
                    .sectionTitle()
                    .lineLimit(1)
                    .accessibilityAddTraits(.isHeader)
                if row.loading {
                    SkeletonGrid(minimumWidth: wideTileWidth) { PosterSkeletonTile() }
                } else if let message = row.message {
                    LoadErrorView(message: message, onRetry: { model.retry() })
                } else {
                    ArtworkGrid(items: row.items, minimumWidth: wideTileWidth, onOpen: onOpen)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .screenMargin()
        } else {
            MediaRowView(row: row, onOpen: onOpen, onSeeAll: { _ in }, onRetry: { model.retry() })
        }
    }

    @ViewBuilder
    private func headerContent(facts: [String], person: Person) -> some View {
        if wide { wideHeader(facts, person) } else { header(facts, person) }
    }

    private func header(_ facts: [String], _ person: Person) -> some View {
        VStack(spacing: Space.s2) {
            portrait(person)
            details(facts, person, alignment: .center)
                .padding(.top, Space.s2)
        }
        .screenMargin()
    }

    private func wideHeader(_ facts: [String], _ person: Person) -> some View {
        HStack(alignment: .top, spacing: Space.s5) {
            portrait(person)
            details(facts, person, alignment: .leading)
                .padding(.top, Space.s2)
        }
        .frame(maxWidth: contentMaxWidth, alignment: .leading)
        .frame(maxWidth: .infinity, alignment: .leading)
        .screenMargin()
    }

    private func details(_ facts: [String], _ person: Person, alignment: HorizontalAlignment) -> some View {
        let textAlignment: TextAlignment = alignment == .center ? .center : .leading

        return VStack(alignment: alignment, spacing: Space.s2) {
            Text(person.name)
                .font(.brandDisplay)
                .tracking(-0.4)
                .multilineTextAlignment(textAlignment)
                .lineLimit(3)
            if !facts.isEmpty {
                Text(Strings.shared.factsLine(facts: facts))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(textAlignment)
            }
            Text(person.overview ?? Strings.shared.noBiography)
                .font(.subheadline)
                .foregroundStyle(person.overview == nil ? .secondary : .primary)
                .multilineTextAlignment(textAlignment)
                .lineLimit(expanded ? nil : 4)
                .padding(.top, Space.s2)
            if let overview = person.overview, overview.count > 180 {
                Button((expanded ? Strings.shared.less : Strings.shared.more).titleCased()) { expanded.toggle() }
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func portrait(_ person: Person) -> some View {
        PortraitView(name: person.name, imageUrl: person.imageUrl, size: portraitSize, initialsFont: .brandDisplay)
            .accessibilityLabel(person.name)
    }

    private var skeleton: some View {
        let person: Person = Placeholders.shared.person

        return headerContent(facts: [person.birthplace ?? ""], person: person)
            .skeleton()
    }
}
