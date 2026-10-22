import SwiftUI
import Shared

/// The Search tab: the system's search field, and under it what matches, grouped into movies, shows, episodes, people and collections.
struct SearchView: View {
    @StateObject private var model = SearchObservable()
    @State private var text = ""

    let onOpenResult: (SearchResult) -> Void
    let onExit: (HomeExit) -> Void

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            content
        }
        .onAppear { model.start() }
        .searchable(text: $text, placement: .navigationBarDrawer(displayMode: .always), prompt: Strings.shared.searchHint)
        .onChange(of: text) { model.setQuery($0) }
        .onSubmit(of: .search) { model.submit() }
        .navigationTitle(Strings.shared.tabSearch.titleCased())
        .onChange(of: model.state?.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
        }
    }

    /// One button per kind, once something is typed: All lists every group, the others only theirs.
    private func chips(_ state: SearchUiState) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Space.s2) {
                ForEach(state.chips, id: \.filter) { chip in
                    Button { model.setFilter(chip.filter) } label: {
                        Text(chip.label.titleCased())
                            .font(.subheadline.weight(.medium))
                            .padding(.horizontal, Space.s3)
                            .frame(minHeight: 36)
                            .background(Capsule().fill(chip.selected ? Color(.secondarySystemFill) : Color(.tertiarySystemBackground)))
                            .foregroundStyle(chip.selected ? Color.primary : Color.secondary)
                    }
                    .buttonStyle(PressableButtonStyle())
                    .accessibilityAddTraits(chip.selected ? .isSelected : [])
                }
            }
            .screenMargin()
        }
    }

    @ViewBuilder
    private var content: some View {
        if let state = model.state {
            if state.showsPrompt && !state.history.isEmpty {
                history(state)
            } else if state.showsPrompt {
                prompt(Strings.shared.searchPrompt, Strings.shared.searchPromptHelp)
            } else if state.message == nil && state.noResults == false && state.groups.isEmpty {
                VStack(spacing: Space.s3) { chips(state); skeleton }
            } else if let message = state.message {
                LoadErrorView(message: message, onRetry: { model.retry() }).screenMargin()
            } else if state.noResults {
                VStack(spacing: 0) { chips(state); prompt(Strings.shared.noSearchResults(query: state.searched), Strings.shared.noSearchResultsHelp) }
            } else if state.groups.isEmpty {
                skeleton
            } else {
                results(state)
            }
        } else {
            prompt(Strings.shared.searchPrompt, Strings.shared.searchPromptHelp)
        }
    }

    /// What was searched for before, newest first: a tap runs it again, the cross takes it off, "Clear all" empties the list.
    private func history(_ state: SearchUiState) -> some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(Strings.shared.recentSearches.titleCased()).sectionTitle().accessibilityAddTraits(.isHeader)
                    Spacer()
                    Button(Strings.shared.clearHistory.titleCased()) { model.clearHistory() }
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                ForEach(state.history, id: \.self) { term in
                    HStack(spacing: Space.s3) {
                        Button { text = term } label: {
                            HStack(spacing: Space.s3) {
                                Image(systemName: "clock.arrow.circlepath").foregroundStyle(.secondary)
                                Text(term).font(.body).foregroundStyle(.primary).lineLimit(1)
                                Spacer(minLength: 0)
                            }
                            .frame(minHeight: 44)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(PressableButtonStyle())
                        Button { model.removeFromHistory(term) } label: {
                            Image(systemName: "xmark").foregroundStyle(.secondary).frame(width: 44, height: 44)
                        }
                        .accessibilityLabel(Strings.shared.removeFromHistory(term: term))
                    }
                }
            }
            .screenMargin()
            .padding(.vertical, Space.s2)
        }
    }

    private func prompt(_ title: String, _ help: String) -> some View {
        VStack(spacing: Space.s2) {
            Image(systemName: "magnifyingglass").font(.largeTitle).foregroundStyle(.secondary)
            Text(title).font(.headline).multilineTextAlignment(.center)
            Text(help).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .screenMargin()
        // Near the top, under the field: centred it would ride up with the keyboard.
        .padding(.top, 72)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    private func results(_ state: SearchUiState) -> some View {
        VStack(spacing: Space.s2) {
        chips(state)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Space.s2) {
                ForEach(state.visibleGroups, id: \.kind) { group in
                    Text(group.title.titleCased())
                        .sectionTitle()
                        .padding(.top, Space.s3)
                        .accessibilityAddTraits(.isHeader)
                    ForEach(group.results, id: \.id) { result in
                        Button { model.resultOpened(); onOpenResult(result) } label: { row(result) }
                            .buttonStyle(PressableButtonStyle())
                            .accessibilityLabel(Strings.shared.searchResultDescription(title: result.title, subtitle: result.subtitle))
                    }
                }
            }
            .screenMargin()
            .padding(.vertical, Space.s2)
            // The older answer stays while the next one is on its way, dimmed.
            .opacity(state.loading ? 0.5 : 1)
        }
        }
    }

    private func row(_ result: SearchResult) -> some View {
        HStack(spacing: Space.s3) {
            picture(result)
            VStack(alignment: .leading, spacing: 2) {
                Text(result.title).font(.headline).foregroundStyle(.primary).lineLimit(2)
                if let subtitle = result.subtitle {
                    Text(subtitle).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private func picture(_ result: SearchResult) -> some View {
        switch result.kind {
        case .person:
            ZStack {
                Circle().fill(Color(.tertiarySystemBackground))
                Text(result.title.split(separator: " ").prefix(2).compactMap { $0.first.map(String.init) }.joined().uppercased())
                    .font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                if let url = result.imageUrl.flatMap({ URL(string: $0) }) {
                    AsyncImage(url: url) { $0.resizable().scaledToFill() } placeholder: { Color.clear }.clipShape(Circle())
                }
            }
            .frame(width: 56, height: 56)
        case .episode:
            Artwork(url: result.imageUrl, title: result.title, aspectRatio: 16.0 / 9.0, blurHash: nil)
                .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
                .frame(width: 112)
        default:
            Artwork(url: result.imageUrl, title: result.title, aspectRatio: 2.0 / 3.0, blurHash: nil)
                .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
                .frame(width: 56)
        }
    }

    private var skeleton: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            ForEach(0..<5, id: \.self) { _ in
                HStack(spacing: Space.s3) {
                    SkeletonBlock().frame(width: 56, height: 84)
                    VStack(alignment: .leading, spacing: Space.s1) {
                        SkeletonLine(font: .headline, width: 180)
                        SkeletonLine(font: .subheadline, width: 80)
                    }
                }
            }
            Spacer()
        }
        .screenMargin()
        .padding(.top, Space.s2)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
