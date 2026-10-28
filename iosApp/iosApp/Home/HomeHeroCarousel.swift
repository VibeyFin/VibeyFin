import SwiftUI
import UIKit
import Shared

/// The featured items: one card, or from two a paged `TabView` of them with the system's page dots under it. It moves on by itself
/// every `HERO_ADVANCE_MILLIS`, once through, resting on the last; a swipe (or a tap on the dots) stops that until Home is left, a
/// finger on it pauses it, and it never moves under Reduce Motion, with VoiceOver on, or while Home is not on screen and in front.
/// A swipe runs round: past the last item comes the first, and before the first the last. Play and My List act on their own page.
struct HomeHeroCarousel: View {
    let items: [MediaItem]
    let onPlay: (String) -> Void
    let onOpen: (String) -> Void
    let onMyList: (String) -> Void

    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.scenePhase) private var scenePhase
    /// The item showing, from 0.
    @State private var page = 0
    /// The `TabView`'s page: the items sit at 1...count, with a copy of the last at 0 and of the first at count + 1, so a swipe past
    /// either end lands on a copy and is moved to the real item at once.
    @State private var slot = 1
    /// The item the timer moved to, so that change is not taken for a swipe.
    @State private var autoPage: Int?
    @State private var swiped = false
    @State private var onScreen = false
    @GestureState private var pressing = false

    private var wide: Bool { sizeClass == .regular }

    var body: some View {
        if items.count == 1, let item = items.first {
            HomeHeroView(item: item, onPlay: { onPlay(item.id) }, onOpen: onOpen, onMyList: { onMyList(item.id) })
        } else {
            VStack(spacing: 0) {
                TabView(selection: $slot) {
                    ForEach(0..<(items.count + 2), id: \.self) { slot in
                        let index = (slot + items.count - 1) % items.count
                        let item = items[index]
                        HomeHeroView(
                            item: item,
                            onPlay: { onPlay(item.id) },
                            onOpen: onOpen,
                            onMyList: { onMyList(item.id) },
                            label: Strings.shared.featuredPage(title: item.title, index: Int32(index + 1), count: Int32(items.count))
                        )
                        .tag(slot)
                        // The copies at the ends are only passed through; VoiceOver reads the real pages.
                        .accessibilityHidden(slot == 0 || slot == items.count + 1)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .aspectRatio(wide ? 2.6 : 4.0 / 3.0, contentMode: .fit)
                // Seen alongside the paging and the buttons: a finger resting on the card pauses it. Moving it cancels the
                // press, and the swipe that follows stops the carousel anyway.
                .simultaneousGesture(
                    LongPressGesture(minimumDuration: .infinity, maximumDistance: 10).updating($pressing) { value, state, _ in state = value }
                )
                HeroPageControl(count: items.count, current: $page)
                    .frame(height: HomeHeroCarousel.dotsHeight)
            }
            .onAppear { onScreen = true }
            .onDisappear { onScreen = false }
            .onChange(of: slot) { newSlot in
                let landed = (newSlot + items.count - 1) % items.count
                if newSlot == 0 || newSlot == items.count + 1 {
                    // On a copy at an end: once the move has finished, swap to the real item, which looks the same.
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: 350_000_000)
                        var plain = Transaction()
                        plain.disablesAnimations = true
                        withTransaction(plain) { slot = landed + 1 }
                    }
                }
                guard landed != page else { return }
                if landed != autoPage { swiped = true }
                autoPage = nil
                page = landed
            }
            .onChange(of: page) { newPage in
                // The dots moved the item (a swipe or the timer moves the pager first): the pager follows, and it stops like a swipe.
                if (slot + items.count - 1) % items.count != newPage {
                    swiped = true
                    slot = newPage + 1
                }
            }
            .onChange(of: items.map { $0.id }) { _ in
                // A new pick starts from its first item and may move on again.
                page = 0
                slot = 1
                swiped = false
            }
            .task(id: AdvanceKey(page: page, running: running)) {
                guard running, page < items.count - 1 else { return }
                try? await Task.sleep(nanoseconds: UInt64(HomeHeroKt.HERO_ADVANCE_MILLIS) * 1_000_000)
                guard !Task.isCancelled else { return }
                autoPage = page + 1
                withAnimation(.easeInOut(duration: 0.4)) { slot = page + 2 }
            }
        }
    }

    private var running: Bool {
        !swiped && !pressing && !reduceMotion && !voiceOver && scenePhase == .active && onScreen
    }

    /// The row the page dots sit in; the skeleton keeps it too when several items are coming, so the card does not move.
    static let dotsHeight: CGFloat = 24
}

private struct AdvanceKey: Equatable {
    let page: Int
    let running: Bool
}

/// The system's page dots, which VoiceOver reads and adjusts; tapping or adjusting them moves the carousel like a swipe.
private struct HeroPageControl: UIViewRepresentable {
    let count: Int
    @Binding var current: Int

    func makeCoordinator() -> Coordinator { Coordinator(current: $current) }

    func makeUIView(context: Context) -> UIPageControl {
        let control = UIPageControl()
        control.hidesForSinglePage = true
        control.addTarget(context.coordinator, action: #selector(Coordinator.changed(_:)), for: .valueChanged)
        return control
    }

    func updateUIView(_ control: UIPageControl, context: Context) {
        context.coordinator.current = $current
        control.numberOfPages = count
        control.currentPage = current
    }

    final class Coordinator: NSObject {
        var current: Binding<Int>

        init(current: Binding<Int>) { self.current = current }

        @objc func changed(_ control: UIPageControl) {
            withAnimation(.easeInOut(duration: 0.3)) { current.wrappedValue = control.currentPage }
        }
    }
}
