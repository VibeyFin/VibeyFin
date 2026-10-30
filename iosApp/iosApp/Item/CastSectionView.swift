import SwiftUI
import Shared

/// A row of the actors: a round picture, the name and the part they play. A person with no picture is a grey disc with initials.
struct CastSectionView: View {
    let cast: [CastMember]
    let tile: CGFloat
    /// The page's side margin, so the row can run to the screen's edges and start in line with the title.
    let margin: CGFloat
    let onOpenPerson: (String) -> Void

    /// One tile per person: an actor with several parts is listed once, as on Android.
    private var people: [CastMember] {
        var seen = Set<String>()
        return cast.filter { seen.insert($0.id).inserted }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            Text(Strings.shared.cast.titleCased())
                .sectionTitle()
                .accessibilityAddTraits(.isHeader)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: Space.s3) {
                    ForEach(people, id: \.id) { person in
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
                .padding(.horizontal, margin)
            }
            .padding(.horizontal, -margin)
        }
    }

    private func portrait(_ person: CastMember) -> some View {
        PortraitView(name: person.name, imageUrl: person.imageUrl, size: tile, initialsFont: .headline)
    }
}
