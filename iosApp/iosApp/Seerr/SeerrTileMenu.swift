import SwiftUI
import Shared

extension View {
    /// A Seerr poster's Button: press feedback and the native context menu, with the title read once the finger has rested. The menu
    /// goes on the Button, never inside its label, and no gesture of ours may sit beside it.
    func seerrTileButton(_ title: SeerrTitle) -> some View {
        modifier(SeerrTileMenuButton(title: title))
    }
}

private struct SeerrTileMenuButton: ViewModifier {
    let title: SeerrTitle
    @Environment(\.seerrMenu) private var menu

    @ViewBuilder
    func body(content: Content) -> some View {
        if let menu {
            content
                .buttonStyle(PressableButtonStyle(onHold: { menu.open(title) }))
                .modifier(LiveSeerrContextMenu(title: title, menu: menu))
        } else {
            content.buttonStyle(PressableButtonStyle())
        }
    }
}

private struct LiveSeerrContextMenu: ViewModifier {
    let title: SeerrTitle
    /// Not observed: a redraw of every poster during a press would cancel it.
    let menu: SeerrMenuOpener
    /// SwiftUI builds the rows when the poster is drawn, so they are rebuilt, for this poster only, when its title is read.
    @State private var shown: SeerrTitleUiState?

    func body(content: Content) -> some View {
        content
            .onReceive(menu.stateChanges.filter { $0.0.tmdbId == title.tmdbId && $0.0.type == title.type }) { shown = $0.1 }
            .contextMenu { rows }
    }

    /// Until the title is read the rows come from the poster itself, with a placeholder for the Trailer, so none appears late.
    @ViewBuilder
    private var rows: some View {
        if let state = shown, !state.loading {
            if let status = state.menuStatus {
                // Someone asked for it: where it stands is a row of its own, not a button.
                statusRow(status, detail: state.menuStatusDetail, state: state.state)
            }
            if state.canRequest {
                Button { menu.request() } label: {
                    Label((state.primary?.label ?? Strings.shared.seerrRequest).titleCased(), systemImage: "plus")
                }
                if state.offer4k {
                    Button { menu.request4k() } label: {
                        Label {
                            Text(Strings.shared.seerrRequest4k.titleCased())
                            Text(Strings.shared.seerrRequest4kQuota)
                        } icon: {
                            Image(systemName: "plus")
                        }
                    }
                }
            }
            detailsRow
            if let trailer = state.trailerUrl {
                Button { menu.handlers.trailer(trailer) } label: { Label(Strings.shared.trailer.titleCased(), systemImage: "film") }
            }
            if state.cancelRequestId != nil {
                Button(role: .destructive) { menu.cancelRequest() } label: {
                    Label(Strings.shared.seerrCancelRequest.titleCased(), systemImage: "xmark")
                }
            }
        } else {
            if title.state == .notrequested {
                Button { menu.request() } label: { Label(Strings.shared.seerrRequest.titleCased(), systemImage: "plus") }
            } else if let label = Strings.shared.seerrStateLabel(state: title.state) {
                statusRow(label, detail: nil, state: title.state)
            }
            detailsRow
            // Not `.disabled`: the system draws that too faint to read.
            Button {} label: { Label(Strings.shared.checkingTrailer, systemImage: "film") }
        }
    }

    private var detailsRow: some View {
        Button { menu.handlers.details(title) } label: { Label(Strings.shared.details.titleCased(), systemImage: "info.circle") }
    }

    /// The status of a title someone asked for: a disabled item with the whole sentence under it.
    private func statusRow(_ text: String, detail: String?, state: RequestState) -> some View {
        Button {} label: {
            Label {
                Text(text)
                if let detail { Text(detail) }
            } icon: {
                Image(systemName: SeerrMarker.symbol(state))
            }
        }
        .disabled(true)
    }
}

/// The season picker for the title whose menu is open, kept in step with its model.
struct SeerrMenuPickerSheet: View {
    @ObservedObject var model: SeerrTitleObservable

    var body: some View {
        if let picker = model.state?.picker {
            SeerrSeasonPickerView(picker: picker, sending: model.state?.sending == true, model: model)
        }
    }
}
