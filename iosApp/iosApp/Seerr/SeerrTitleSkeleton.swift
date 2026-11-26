import SwiftUI
import Shared

extension SeerrTitleUiState {
    /// The Seerr title page's state with `Placeholders.seerrDetails` in it, for the hero to draw as a skeleton.
    static var placeholder: SeerrTitleUiState {
        let details: SeerrTitleDetails = Placeholders.shared.seerrDetails

        return SeerrTitleUiState(
            loading: false,
            message: nil,
            title: details.title,
            facts: details.genres,
            genres: details.genres,
            overview: details.title.overview,
            status: nil,
            tag: Strings.shared.seerrTag(state: details.title.state),
            statusDetail: nil,
            state: details.title.state,
            status4k: nil,
            primary: SeerrPrimary(label: Strings.shared.seerrRequest, enabled: true),
            trailerUrl: nil,
            offer4k: false,
            cancelRequestId: nil,
            seasons: [],
            episodes: nil,
            cast: [],
            moreLikeThis: [],
            picker: nil,
            sending: false,
            notice: nil,
            openItem: nil,
            menuSubtitle: "",
            menuStatus: nil,
            menuStatusDetail: nil,
            canRequest: false
        )
    }
}
