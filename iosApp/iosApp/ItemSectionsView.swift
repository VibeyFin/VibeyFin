import SwiftUI
import Shared

/// A show's seasons as a row of capsule buttons (the selected one filled `ink` with `bg` text), then the selected season's
/// episodes. The row opens scrolled to the season Play starts in; another season swaps the list in place and the old list stays
/// up, a little dimmed, until the new one arrives.
struct SeasonsSectionView: View {
    let seasons: SeasonsState
    /// A regular width scrolls the episodes sideways, as bigger cards.
    let wide: Bool
    let onSelect: (String) -> Void
    let onPlay: (String) -> Void
    let onToggleWatched: (String) -> Void
    let onViewEpisode: (MediaItem) -> Void
    let onRetrySeasons: () -> Void
    let onRetryEpisodes: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            if seasons.loading {
                HStack(spacing: Space.s2) {
                    ForEach(0..<3, id: \.self) { _ in
                        SkeletonBlock(cornerRadius: 18).frame(width: 96, height: 36)
                    }
                }
            } else if let message = seasons.message {
                LoadErrorView(message: message, onRetry: onRetrySeasons)
            } else if seasons.seasons.isEmpty {
                Text(Strings.shared.noSeasons)
                    .font(.body)
                    .foregroundStyle(.secondary)
            } else {
                seasonButtons
                episodes
            }
        }
        .animation(.easeOut(duration: 0.25), value: seasons.loading)
    }

    private var seasonButtons: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Space.s2) {
                    ForEach(seasons.seasons, id: \.id) { season in
                        let selected = season.id == seasons.selectedId
                        Button { onSelect(season.id) } label: {
                            Text(ItemFactsKt.getSeasonLabel(season))
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(selected ? Color(.systemBackground) : Color.primary)
                                .padding(.horizontal, Space.s4)
                                .frame(height: 36)
                                .background(Capsule().fill(selected ? Color.primary : Color(.tertiarySystemBackground)))
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(PressableButtonStyle())
                        .id(season.id)
                        .accessibilityLabel(Strings.shared.seasonDescription(name: ItemFactsKt.getSeasonLabel(season), selected: selected))
                        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
                    }
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityLabel(Strings.shared.seasonsDescription(count: Int32(seasons.seasons.count)))
            // Opens with the season Play starts in in view.
            .onAppear {
                if let id = seasons.selectedId { proxy.scrollTo(id, anchor: .center) }
            }
        }
    }

    /// Episode cards: 300 wide on a phone, 380 on a regular width.
    private var cardWidth: CGFloat { wide ? 380 : 300 }

    private func scrollToCurrent(_ proxy: ScrollViewProxy, _ list: EpisodeList) {
        let target = list.items.first(where: { $0.id == seasons.startEpisode?.id }) ?? list.items.first
        if let id = target?.id { proxy.scrollTo(id, anchor: .leading) }
    }

    @ViewBuilder
    private var episodes: some View {
        let list = seasons.episodes
        if let message = list.message {
            LoadErrorView(message: message, onRetry: onRetryEpisodes)
        } else if list.items.isEmpty && list.loading {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Space.s4) {
                    ForEach(0..<3, id: \.self) { _ in
                        SkeletonBlock().frame(width: cardWidth).aspectRatio(1.6, contentMode: .fit)
                    }
                }
            }
            .scrollDisabled(true)
        } else if list.items.isEmpty {
            Text(Strings.shared.noEpisodes)
                .font(.body)
                .foregroundStyle(.secondary)
        } else {
            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(alignment: .top, spacing: Space.s4) {
                        ForEach(list.items, id: \.id) { episode in
                            EpisodeCardView(
                                episode: episode,
                                width: cardWidth,
                                current: episode.id == seasons.startEpisode?.id,
                                onPlay: { onPlay(episode.id) },
                                onToggleWatched: { onToggleWatched(episode.id) },
                                onView: { onViewEpisode(episode) }
                            )
                            .id(episode.id)
                        }
                    }
                }
                // A season opens at the episode Play will start, so the one being watched is in view; another season at its first.
                .onAppear { scrollToCurrent(proxy, list) }
                .onChange(of: list.items.first?.id) { _ in scrollToCurrent(proxy, list) }
            }
            .opacity(list.loading ? 0.5 : 1)
        }
    }
}

/// An episode: a card in a row that scrolls sideways, all of it on the still. At the bottom, over a scrim: the "S1 E4" tag, the
/// title, two lines of synopsis, and a line with how long it is (or how much is left), its rating and the day it aired. A check
/// sits at the top once watched, a progress bar runs along the bottom edge when it is part watched, and the one Play starts has a
/// brighter outline. Tapping plays it; a long press opens a menu: View episode, and the watched mark.
struct EpisodeCardView: View {
    let episode: MediaItem
    let width: CGFloat
    let current: Bool
    let onPlay: () -> Void
    let onToggleWatched: () -> Void
    let onView: () -> Void

    private let aspect: CGFloat = 1.6

    var body: some View {
        Button(action: onPlay) {
            Artwork(url: episode.landscapeUrl ?? episode.posterUrl, title: episode.title, aspectRatio: aspect, blurHash: episode.landscapeBlurHash)
                .overlay {
                    LinearGradient(
                        stops: [.init(color: .clear, location: 0), .init(color: Brand.scrim, location: 0.3), .init(color: Brand.scrimStrong, location: 0.7), .init(color: Brand.scrimStrong, location: 1)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .allowsHitTesting(false)
                }
                .overlay(alignment: .topTrailing) {
                    if episode.played {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.title3)
                            .foregroundStyle(.white)
                            .padding(Space.s3)
                            .accessibilityLabel(Strings.shared.watched)
                    }
                }
                .overlay(alignment: .bottomLeading) { caption }
                .overlay(alignment: .bottom) {
                    // Aqua is playback progress: how far into the episode the user got.
                    if let progress = episode.progress {
                        GeometryReader { proxy in
                            ZStack(alignment: .leading) {
                                Rectangle().fill(Brand.progressTrack)
                                Rectangle().fill(Brand.accent).frame(width: proxy.size.width * CGFloat(progress.floatValue))
                            }
                        }
                        .frame(height: 4)
                    }
                }
                .frame(width: width)
                .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous).stroke(current ? Color.secondary : Color(.tertiarySystemBackground), lineWidth: 1))
                .contentShape(Rectangle())
        }
        .buttonStyle(PressableButtonStyle())
        .contextMenu {
            Button(action: onView) {
                Label(Strings.shared.viewEpisode.titleCased(), systemImage: "info.circle")
            }
            Button(action: onToggleWatched) {
                Label(
                    (episode.played ? Strings.shared.markUnwatched : Strings.shared.markWatched).titleCased(),
                    systemImage: episode.played ? "xmark.circle" : "checkmark.circle"
                )
            }
        }
    }

    private var caption: some View {
        VStack(alignment: .leading, spacing: Space.s1) {
            if let tag = Strings.shared.episodeTag(season: episode.seasonNumber, episode: episode.episodeNumber) {
                Text(tag)
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(RoundedRectangle(cornerRadius: 6, style: .continuous).fill(Brand.scrim))
            }
            Text(episode.title)
                .font(.headline.weight(.bold))
                .foregroundStyle(.white)
                .lineLimit(1)
            // Two lines, always, so every card in the row is the same.
            Text(episode.overview ?? "")
                .font(.subheadline)
                .foregroundStyle(.white)
                .lineLimit(2, reservesSpace: true)
                .multilineTextAlignment(.leading)
            HStack(spacing: Space.s3) {
                if let time = time { Text(time) }
                if let rating = episode.rating?.doubleValue, rating > 0 { Text(Strings.shared.ratingTag(rating: rating)) }
                Spacer(minLength: 0)
                if let date = Strings.shared.releaseDate(iso: episode.premiereDate) {
                    Text(date).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            .font(.caption.weight(.semibold))
            .foregroundStyle(.white)
        }
        .padding(Space.s3)
        .padding(.bottom, episode.progress != nil ? 4 : 0)
        .frame(width: width, alignment: .leading)
    }

    /// How long it is, or how much is left of it.
    private var time: String? {
        if episode.progress != nil, let runtime = episode.runtimeMs {
            return Strings.shared.timeLeftShort(remainingMs: runtime.int64Value - episode.resumePositionMs)
        }
        return episode.runtimeMinutes.map { Strings.shared.minutesShort(minutes: $0.int32Value) }
    }
}

/// A row of the actors: a round picture, the name and the part they play. A person with no picture is a grey disc with initials.
struct CastSectionView: View {
    let cast: [CastMember]
    let tile: CGFloat
    let onOpenPerson: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            Text(Strings.shared.cast.titleCased())
                .sectionTitle()
                .accessibilityAddTraits(.isHeader)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: Space.s3) {
                    ForEach(cast, id: \.id) { person in
                        Button { onOpenPerson(person.id) } label: {
                        VStack(spacing: Space.s1) {
                            portrait(person)
                            // Names take two lines and roles one, always, so the row keeps one height.
                            Text(person.name)
                                .font(.caption.weight(.medium))
                                .foregroundStyle(.primary)
                                .multilineTextAlignment(.center)
                                .lineLimit(2, reservesSpace: true)
                            Text(person.role ?? "")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                                .lineLimit(1, reservesSpace: true)
                        }
                        .frame(width: tile)
                        .contentShape(Rectangle())
                        }
                        .buttonStyle(PressableButtonStyle())
                        .accessibilityElement(children: .combine)
                        .accessibilityHint(Strings.shared.personDescription(name: person.name, role: person.role))
                    }
                }
            }
        }
    }

    private func portrait(_ person: CastMember) -> some View {
        ZStack {
            Circle().fill(Color(.tertiarySystemBackground))
            Text(initials(person.name))
                .font(.headline)
                .foregroundStyle(.secondary)
            if let url = person.imageUrl.flatMap({ URL(string: $0) }) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Color.clear
                }
                .clipShape(Circle())
            }
        }
        .frame(width: tile, height: tile)
    }

    private func initials(_ name: String) -> String {
        name.split(separator: " ").prefix(2).compactMap { $0.first.map { String($0).uppercased() } }.joined()
    }
}

/// The file's details, collapsed until asked for.
struct TechnicalInfoView: View {
    let lines: [TechnicalLine]
    @State private var open = false

    var body: some View {
        DisclosureGroup(isExpanded: $open) {
            VStack(alignment: .leading, spacing: Space.s2) {
                ForEach(lines, id: \.label) { line in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(line.label).font(.caption).foregroundStyle(.secondary)
                        Text(line.value).font(.subheadline).foregroundStyle(.primary)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, Space.s2)
        } label: {
            Text(Strings.shared.technicalInfo.titleCased())
                .sectionTitle()
        }
        .tint(.secondary)
    }
}
