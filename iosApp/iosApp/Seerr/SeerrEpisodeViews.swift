import SwiftUI
import Shared

/// An episode of a Seerr show on a phone: laid out as the library's `EpisodeRowView`, with its still on the left and the "S1 E4" tag,
/// the title, its rating and air date, and two lines of synopsis. Seerr's episodes don't play: they show what you'd be asking for.
struct SeerrEpisodeRowView: View {
    let episode: SeerrEpisode

    private let aspect: CGFloat = 1.6

    var body: some View {
        HStack(alignment: .top, spacing: Space.s3) {
            Artwork(url: episode.stillUrl, title: episode.title, aspectRatio: aspect, blurHash: nil)
                .frame(width: 148)
                .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous).stroke(Color(.tertiarySystemBackground), lineWidth: 1))
            VStack(alignment: .leading, spacing: Space.s1) {
                if let tag = Strings.shared.episodeTag(season: KotlinInt(int: episode.season), episode: KotlinInt(int: episode.number)) {
                    Text(tag).font(.caption.weight(.medium)).foregroundStyle(.secondary)
                }
                Text(episode.title).font(.headline.weight(.bold)).foregroundStyle(.primary).lineLimit(1)
                if !facts.isEmpty {
                    Text(Strings.shared.factsLine(facts: facts)).font(.caption.weight(.semibold)).foregroundStyle(.secondary).lineLimit(1)
                }
                // Two lines, always, so every row is the same height.
                Text(episode.overview ?? "")
                    .font(.subheadline)
                    .foregroundStyle(.primary)
                    .lineLimit(2, reservesSpace: true)
                    .multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .combine)
    }

    /// Its rating and the day it aired.
    private var facts: [String] {
        var parts: [String] = []
        if let rating = episode.rating?.doubleValue, rating > 0 { parts.append(Strings.shared.ratingTag(rating: rating)) }
        if let date = Strings.shared.releaseDate(iso: episode.airDate) { parts.append(date) }
        return parts
    }
}

/// An episode of a Seerr show on a regular width: laid out as the library's `EpisodeCardView`, all of it on the still.
struct SeerrEpisodeCardView: View {
    let episode: SeerrEpisode
    let width: CGFloat

    private let aspect: CGFloat = 1.6

    var body: some View {
        Artwork(url: episode.stillUrl, title: episode.title, aspectRatio: aspect, blurHash: nil)
            .overlay {
                LinearGradient(
                    // The picture stays clear well down the card; only the lower part darkens, just enough for the white text to read.
                    stops: [.init(color: .clear, location: 0), .init(color: .clear, location: 0.4), .init(color: Brand.scrim.opacity(0.7), location: 0.75), .init(color: Brand.scrim, location: 1)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .allowsHitTesting(false)
            }
            .frame(width: width)
            .clipShape(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.artwork, style: .continuous).stroke(Color(.tertiarySystemBackground), lineWidth: 1))
            .overlay(alignment: .bottomLeading) { caption }
            .accessibilityElement(children: .combine)
    }

    private var caption: some View {
        VStack(alignment: .leading, spacing: Space.s1) {
            if let tag = Strings.shared.episodeTag(season: KotlinInt(int: episode.season), episode: KotlinInt(int: episode.number)) {
                Text(tag)
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(Brand.onMedia)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(RoundedRectangle(cornerRadius: 6, style: .continuous).fill(Brand.scrim))
            }
            Text(episode.title).font(.headline.weight(.bold)).foregroundStyle(Brand.onMedia).lineLimit(1)
            // Two lines, always, so every card in the row is the same.
            Text(episode.overview ?? "")
                .font(.subheadline)
                .foregroundStyle(Brand.onMedia)
                .lineLimit(2, reservesSpace: true)
                .multilineTextAlignment(.leading)
            HStack(spacing: Space.s3) {
                if let rating = episode.rating?.doubleValue, rating > 0 { Text(Strings.shared.ratingTag(rating: rating)) }
                Spacer(minLength: 0)
                if let date = Strings.shared.releaseDate(iso: episode.airDate) {
                    Text(date).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            .font(.caption.weight(.semibold))
            .foregroundStyle(Brand.onMedia)
        }
        .padding(Space.s3)
        .frame(width: width, alignment: .leading)
    }
}
