import Shared
import SwiftUI

/// A person's picture in a circle, over their initials while it loads or when there is none.
struct PortraitView: View {
    let name: String
    let imageUrl: String?
    let size: CGFloat
    let initialsFont: Font

    var body: some View {
        ZStack {
            Circle().fill(Color(.tertiarySystemBackground))
            Text(Strings.shared.initials(name: name))
                .font(initialsFont)
                .foregroundStyle(.secondary)
            if let url = imageUrl.flatMap({ URL(string: $0) }) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Color.clear
                }
                .clipShape(Circle())
            }
        }
        .frame(width: size, height: size)
    }
}
