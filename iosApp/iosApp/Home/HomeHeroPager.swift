import SwiftUI
import UIKit

/// The hero's pages side by side with `gap` between the cards, a full page per swipe. SwiftUI's page-style `TabView` has no gap and
/// jumps instead of sliding when the page is set from code, so this is a paging `UICollectionView`: a change of `slot` made inside
/// `withAnimation` slides to it, one made without animation (or with `disablesAnimations`) jumps. The view is `gap` wider than the
/// card, half of it on each side, so the neighbouring card is never seen at rest. A page is `page(slot)`, from 0 to `pages - 1`.
struct HeroPager<Page: View>: UIViewRepresentable {
    let pages: Int
    @Binding var slot: Int
    let gap: CGFloat
    @ViewBuilder let page: (Int) -> Page

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> PagerView {
        let view = PagerView()
        view.backgroundColor = .clear
        view.isPagingEnabled = true
        view.bounces = false
        view.showsHorizontalScrollIndicator = false
        view.contentInsetAdjustmentBehavior = .never
        view.delaysContentTouches = false
        view.isDirectionalLockEnabled = true
        view.register(UICollectionViewCell.self, forCellWithReuseIdentifier: "page")
        view.dataSource = context.coordinator
        view.delegate = context.coordinator
        return view
    }

    func updateUIView(_ view: PagerView, context: Context) {
        let coordinator = context.coordinator
        coordinator.parent = self
        view.slot = slot
        for cell in view.visibleCells {
            if let indexPath = view.indexPath(for: cell) { coordinator.configure(cell, at: indexPath.item) }
        }
        guard view.bounds.width > 0, !view.isDragging, !view.isDecelerating, coordinator.animatingTo != slot else { return }
        let target = CGPoint(x: CGFloat(slot) * view.bounds.width, y: 0)
        guard abs(view.contentOffset.x - target.x) > 1 else { return }
        let animated = context.transaction.animation != nil && !context.transaction.disablesAnimations
        coordinator.animatingTo = animated ? slot : nil
        view.setContentOffset(target, animated: animated)
    }

    final class Coordinator: NSObject, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout {
        let cellId = "page"
        var parent: HeroPager?
        /// The slot a slide from code is on its way to, so a redraw in the middle of it does not start it again.
        var animatingTo: Int?

        func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int { parent?.pages ?? 0 }

        func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
            let cell = collectionView.dequeueReusableCell(withReuseIdentifier: cellId, for: indexPath)
            configure(cell, at: indexPath.item)
            return cell
        }

        func configure(_ cell: UICollectionViewCell, at index: Int) {
            guard let parent else { return }
            cell.backgroundColor = .clear
            cell.contentConfiguration = UIHostingConfiguration { parent.page(index) }
                .margins(.all, 0)
                .margins(.horizontal, parent.gap / 2)
                // The pager is `Lift.room` taller than the card, so a long press can grow it.
                .margins(.vertical, Lift.room)
        }

        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
            CGSize(width: max(collectionView.bounds.width, 1), height: max(collectionView.bounds.height, 1))
        }

        func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) { settle(scrollView) }

        func scrollViewDidEndScrollingAnimation(_ scrollView: UIScrollView) {
            animatingTo = nil
            settle(scrollView)
        }

        /// A swipe (or a slide) came to rest on a page: the binding follows.
        private func settle(_ scrollView: UIScrollView) {
            guard let parent, scrollView.bounds.width > 0 else { return }
            let landed = Int((scrollView.contentOffset.x / scrollView.bounds.width).rounded())
            if landed != parent.slot { parent.$slot.wrappedValue = landed }
        }
    }
}

/// The collection view, keeping its page when the width changes (a turn, a split view).
final class PagerView: UICollectionView {
    var slot = 0
    private var lastWidth: CGFloat = 0

    init() {
        let layout = UICollectionViewFlowLayout()
        layout.scrollDirection = .horizontal
        layout.minimumLineSpacing = 0
        layout.minimumInteritemSpacing = 0
        super.init(frame: .zero, collectionViewLayout: layout)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

    override func layoutSubviews() {
        if bounds.width != lastWidth {
            lastWidth = bounds.width
            collectionViewLayout.invalidateLayout()
            super.layoutSubviews()
            contentOffset = CGPoint(x: CGFloat(slot) * bounds.width, y: 0)
        } else {
            super.layoutSubviews()
        }
    }
}
