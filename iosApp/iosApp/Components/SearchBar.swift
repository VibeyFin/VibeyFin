import SwiftUI
import UIKit

/// The system's search bar on its own, for a page that draws its own header above it: `.searchable` lives in the navigation bar,
/// which would put a second, larger title over the tab's header.
struct SearchBar: UIViewRepresentable {
    @Binding var text: String
    let prompt: String
    let onSubmit: () -> Void

    func makeUIView(context: Context) -> UISearchBar {
        let bar = UISearchBar()
        bar.searchBarStyle = .minimal
        bar.autocapitalizationType = .none
        bar.returnKeyType = .search
        bar.delegate = context.coordinator
        return bar
    }

    func updateUIView(_ bar: UISearchBar, context: Context) {
        context.coordinator.parent = self
        if bar.text != text { bar.text = text }
        bar.placeholder = prompt
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UISearchBarDelegate {
        var parent: SearchBar

        init(_ parent: SearchBar) { self.parent = parent }

        func searchBarTextDidBeginEditing(_ bar: UISearchBar) { bar.setShowsCancelButton(true, animated: true) }

        func searchBarTextDidEndEditing(_ bar: UISearchBar) { bar.setShowsCancelButton(false, animated: true) }

        func searchBar(_ bar: UISearchBar, textDidChange text: String) { parent.text = text }

        func searchBarSearchButtonClicked(_ bar: UISearchBar) {
            parent.onSubmit()
            bar.resignFirstResponder()
        }

        // As `.searchable` does: Cancel empties the field and puts the keyboard away.
        func searchBarCancelButtonClicked(_ bar: UISearchBar) {
            parent.text = ""
            bar.resignFirstResponder()
        }
    }
}
