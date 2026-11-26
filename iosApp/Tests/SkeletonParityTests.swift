import Shared
import SwiftUI
import UIKit
import XCTest
@testable import VibeyFin

/// A skeleton is the real view drawn with placeholder data, so it must be exactly as tall as the real one at the same width.
@MainActor
final class SkeletonParityTests: XCTestCase {
    private let tolerance: CGFloat = 0.5

    override func setUp() {
        super.setUp()
        Texts.shared.select(tag: "en")
    }

    private func height<Content: View>(of view: Content, width: CGFloat) -> CGFloat {
        let host: UIHostingController<Content> = UIHostingController(rootView: view)
        let fitted: CGSize                     = host.sizeThatFits(in: CGSize(width: width, height: .greatestFiniteMagnitude))

        return fitted.height
    }

    private func assertSameHeight<Real: View, Placeholder: View>(_ real: Real, _ placeholder: Placeholder, width: CGFloat, file: StaticString = #filePath, line: UInt = #line) {
        let realHeight: CGFloat        = height(of: real, width: width)
        let placeholderHeight: CGFloat = height(of: placeholder, width: width)

        XCTAssertGreaterThan(realHeight, 0, file: file, line: line)
        XCTAssertEqual(realHeight, placeholderHeight, accuracy: tolerance, file: file, line: line)
    }

    func testAPosterTileSkeletonIsAsTallAsARealRowTile() {
        let realTile: ArtworkTile = ArtworkTile(item: Placeholders.shared.posterTile, captionLines: 2)

        assertSameHeight(realTile.frame(width: 112), PosterSkeletonTile().frame(width: 112).skeleton(), width: 112)
    }

    func testAContinueCardSkeletonIsAsTallAsARealCard() {
        let card: ContinueCard = ContinueCard(
            id: "real", title: "Dune", subtitle: nil, imageUrl: nil, imageBlurHash: nil, progress: nil, accessibilityLabel: "Dune", seriesId: nil, resumePositionMs: 0
        )
        let realCard: ContinueCardTile       = ContinueCardTile(card: card, onPlay: {}, onDetails: {}, onMarkWatched: {})
        let placeholderCard: ContinueCardTile = ContinueCardTile(card: Placeholders.shared.continueCard, onPlay: {}, onDetails: {}, onMarkWatched: {})

        assertSameHeight(realCard, placeholderCard.skeleton(), width: Size.thumbWidth)
    }

    func testASeerrPosterSkeletonIsAsTallAsARealTile() {
        let title: SeerrTitle = SeerrTitle(
            tmdbId: 1, type: .movie, title: "Dune", year: KotlinInt(value: 2021), posterUrl: nil, backdropUrl: nil, overview: nil, state: .notrequested, jellyfinItemId: nil, releaseDate: nil
        )

        assertSameHeight(SeerrPosterTile(title: title), SeerrPosterTile(title: Placeholders.shared.seerrTitle).skeleton(), width: 112)
    }

    func testAnEpisodeRowSkeletonIsAsTallAsARealRow() {
        let realRow: EpisodeRowView = EpisodeRowView(episode: Placeholders.shared.episode, current: false, onPlay: {}, onToggleWatched: {}, onView: {}, canDownload: true)

        assertSameHeight(realRow, realRow.skeleton(), width: 390)
    }

    func testAnEpisodeCardSkeletonIsAsTallAsARealCard() {
        let realCard: EpisodeCardView = EpisodeCardView(episode: Placeholders.shared.episode, width: 300, current: false, onPlay: {}, onToggleWatched: {}, onView: {}, canDownload: true)

        assertSameHeight(realCard, realCard.skeleton(), width: 300)
    }

    func testASeerrEpisodeRowSkeletonIsAsTallAsARealRow() {
        let realRow: SeerrEpisodeRowView = SeerrEpisodeRowView(episode: Placeholders.shared.seerrEpisode)

        assertSameHeight(realRow, realRow.skeleton(), width: 390)
    }

    func testTheItemHeroSkeletonIsAsTallAsTheRealDetails() {
        let details: ItemHeroPlaceholder = ItemHeroPlaceholder(centered: true)

        assertSameHeight(details, details.skeleton(), width: 370)
    }

    func testTheHomeHeroSkeletonIsAsTallAsTheRealHero() {
        let realHero: HomeHeroView = HomeHeroView(item: Placeholders.shared.item, onPlay: {}, onOpen: { _ in }, onMyList: {})

        assertSameHeight(realHero, HomeHeroSkeleton(), width: 358)
    }

    func testTheSkeletonCountFillsTheWidthAndOneMore() {
        XCTAssertEqual(skeletonTileCount(screenWidth: 400, tileWidth: 100, spacing: 10, known: nil), 4)
        XCTAssertEqual(skeletonTileCount(screenWidth: 400, tileWidth: 100, spacing: 10, known: 7), 7)
    }

    func testTheSkeletonGridFillsTheScreenHeight() {
        let count: Int = skeletonGridCount(screenWidth: 400, screenHeight: 800, minimumWidth: 110, spacing: 12, rowSpacing: 16, captionHeight: 48)

        XCTAssertEqual(count % 3, 0)
        XCTAssertGreaterThanOrEqual(count, 12)
    }
}
