import SwiftUI
import Shared

/// The Search tab: the system's search field, and under it what matches, grouped into movies, shows, episodes, people and collections.
struct SearchView: View {
    @StateObject private var model = SearchObservable()
    @State private var text = ""

    /// A Seerr title was tapped: its Seerr page, or the library's when the library has it.
    var onOpenSeerr: (SeerrTitle) -> Void = { _ in }
    let onOpenResult: (SearchResult) -> Void
    let onExit: (HomeExit) -> Void
    @Environment(\.seerrMenu) private var seerrMenu

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            VStack(spacing: 0) {
                // The same header as the other tabs, then the field.
                TabHeader(title: Strings.shared.tabSearch)
                SearchBar(
                    text: $text,
                    prompt: model.state?.offline == true ? Strings.shared.offlineSearchHint : Strings.shared.searchHint,
                    onSubmit: { model.submit() }
                )
                .screenMargin()
                // The bar's own inset, taken back so the field lines up with the title above it.
                .padding(.horizontal, -Space.s2)
                content.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
        }
        .onAppear { model.start() }
        .onChange(of: text) { model.setQuery($0) }
        .backMenuTitle(Strings.shared.tabSearch.titleCased())
        // The tab draws its own header; the system bar above it would only be empty space.
        .tabRootBar()
        .onChange(of: model.state?.exit != nil) { hasExit in
            if hasExit, let exit = model.state?.exit { onExit(exit) }
        }
    }

    /// One button per kind, once something is typed: All lists every group, the others only theirs.
    private func chips(_ state: SearchUiState) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Space.s2) {
                ForEach(state.chips, id: \.filter) { chip in
                    // The same chip as Home and a category page; the chosen one fills with ink.
                    FilterChipButton(label: chip.label.titleCased(), active: chip.selected) { model.setFilter(chip.filter) }
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
                if state.offline {
                    prompt(Strings.shared.offlineSearchPrompt, Strings.shared.offlineBannerHelp)
                } else {
                    prompt(Strings.shared.searchPrompt, Strings.shared.searchPromptHelp)
                }
            } else if state.message == nil && state.noResults == false && state.searched.isEmpty {
                // The first answer for this text is on its way.
                VStack(spacing: Space.s3) { chips(state); skeleton }
            } else if let message = state.message {
                LoadErrorView(message: message, onRetry: { model.retry() }).screenMargin()
            } else if state.noResults {
                VStack(spacing: 0) { chips(state); prompt(Strings.shared.noSearchResults(query: state.searched), state.offline ? Strings.shared.offlineSearchEmpty : Strings.shared.noSearchResultsHelp) }
            } else if state.searched.isEmpty {
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
                // The library had nothing, but Seerr's group follows: say so above it (issue #14).
                if state.libraryNoMatch {
                    Text(Strings.shared.libraryNoMatch(query: state.searched))
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .padding(.top, Space.s3)
                }
                ForEach(state.visibleGroups, id: \.kind) { group in
                    Text(group.title.titleCased())
                        .sectionTitle()
                        .padding(.top, Space.s3)
                        .accessibilityAddTraits(.isHeader)
                    // Posters and portraits sit in a narrow grid; episodes are rows, one per line on a phone and more on an iPad.
                    LazyVGrid(
                        columns: [GridItem(.adaptive(minimum: group.kind == .episode ? 300 : 110), spacing: Space.s3, alignment: .top)],
                        alignment: .leading,
                        spacing: Space.s4
                    ) {
                        ForEach(group.results, id: \.id) { result in
                            Button { model.resultOpened(); onOpenResult(result) } label: { tile(result) }
                                .buttonStyle(PressableButtonStyle())
                                .accessibilityLabel(Strings.shared.searchResultDescription(title: result.title, subtitle: result.subtitle))
                        }
                    }
                }
                if let seerr = state.seerrGroup {
                    seerrGroup(seerr, titled: state.filter != .notinlibrary)
                }
            }
            .screenMargin()
            .padding(.vertical, Space.s2)
            // The older answer stays while the next one is on its way, dimmed.
            .opacity(state.loading ? 0.5 : 1)
        }
        }
    }

    /// Seerr's titles that aren't in the library, always last: the heading (left out when the filter shows nothing else), a note,
    /// then the tiles, three grey tiles while Seerr answers, or why it didn't.
    @ViewBuilder
    private func seerrGroup(_ seerr: SeerrSearch, titled: Bool) -> some View {
        VStack(alignment: .leading, spacing: Space.s1) {
            if titled {
                Text(Strings.shared.seerrNotInLibrary.titleCased())
                    .sectionTitle()
                    .accessibilityAddTraits(.isHeader)
            }
            Text(Strings.shared.seerrSearchNote).font(.footnote).foregroundStyle(.secondary)
        }
        .padding(.top, Space.s3)
        if let failed = seerr as? SeerrSearchFailed {
            LoadErrorView(message: failed.message, onRetry: { model.retrySeerr() })
        } else {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 110), spacing: Space.s3, alignment: .top)], alignment: .leading, spacing: Space.s4) {
                if let found = seerr as? SeerrSearchFound {
                    ForEach(found.titles, id: \.self) { title in
                        // A tap opens its Seerr page; a long press its Seerr menu (Request, Details, Trailer).
                        Button {
                            model.resultOpened()
                            onOpenSeerr(title)
                        } label: { SeerrPosterTile(title: title) }
                            .buttonStyle(PressableButtonStyle())
                            .simultaneousGesture(LongPressGesture(minimumDuration: 0.45).onEnded { _ in seerrMenu?.open(title) })
                            .accessibilityAction(named: Strings.shared.showOptions) { seerrMenu?.open(title) }
                    }
                } else {
                    ForEach(0..<3, id: \.self) { _ in
                        VStack(alignment: .leading, spacing: Space.s2) {
                            SkeletonBlock().aspectRatio(2.0 / 3.0, contentMode: .fit)
                            SkeletonLine(font: .subheadline, width: 80)
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func tile(_ result: SearchResult) -> some View {
        if result.kind == .episode {
            episodeRow(result)
        } else {
            posterTile(result)
        }
    }

    /// The show and episode number need the room a tile doesn't have, so an episode reads as a row with the still beside it.
    private func episodeRow(_ result: SearchResult) -> some View {
        HStack(spacing: Space.s3) {
            picture(result).frame(width: 128)
            VStack(alignment: .leading, spacing: 2) {
                Text(result.title).font(.subheadline.weight(.semibold)).foregroundStyle(.primary).lineLimit(2)
                if let subtitle = result.subtitle {
                    Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                }
            }
            .multilineTextAlignment(.leading)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .contentShape(Rectangle())
    }

    private func posterTile(_ result: SearchResult) -> some View {
        let isPerson = result.kind == .person
        return VStack(alignment: isPerson ? .center : .leading, spacing: Space.s2) {
            picture(result)
            VStack(alignment: isPerson ? .center : .leading, spacing: 2) {
                Text(result.title).font(.subheadline.weight(.semibold)).foregroundStyle(.primary).lineLimit(2)
                if let subtitle = result.subtitle {
                    Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            .multilineTextAlignment(isPerson ? .center : .leading)
            .frame(maxWidth: .infinity, alignment: isPerson ? .center : .leading)
        }
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private func picture(_ result: SearchResult) -> some View {
        switch result.kind {
        case .person:
            ZStack {
                Circle().fill(Color(.tertiarySystemBackground))
                Text(Strings.shared.initials(name: result.title))
                    .font(.headline).foregroundStyle(.secondary)
                if let url = result.imageUrl.flatMap({ URL(string: $0) }) {
                    AsyncImage(url: url) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
                }
            }
            .aspectRatio(1, contentMode: .fit)
            .clipShape(Circle())
        case .episode:
            Artwork(url: result.imageUrl, title: result.title, aspectRatio: 16.0 / 9.0, blurHash: nil)
                .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
        default:
            Artwork(url: result.imageUrl, title: result.title, aspectRatio: 2.0 / 3.0, blurHash: nil)
                .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
        }
    }

    private var skeleton: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 110), spacing: Space.s3, alignment: .top)], alignment: .leading, spacing: Space.s4) {
            ForEach(0..<6, id: \.self) { _ in
                VStack(alignment: .leading, spacing: Space.s2) {
                    SkeletonBlock().aspectRatio(2.0 / 3.0, contentMode: .fit)
                    SkeletonLine(font: .subheadline, width: 80)
                }
            }
        }
        .screenMargin()
        .padding(.top, Space.s2)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }
}
