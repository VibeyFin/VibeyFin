import SwiftUI
import Shared

/// One actor in the cast row: a library person, or one of Seerr's, who has no page.
struct CastPerson {
    let id: String
    let name: String
    let role: String?
    let imageUrl: String?

    init(_ member: CastMember) {
        self.init(id: member.id, name: member.name, role: member.role, imageUrl: member.imageUrl)
    }

    init(id: String, name: String, role: String?, imageUrl: String?) {
        self.id = id
        self.name = name
        self.role = role
        self.imageUrl = imageUrl
    }
}

/// A row of the actors: a round picture, the name and the part they play. A person with no picture is a grey disc with initials.
struct CastSectionView: View {
    let cast: [CastPerson]
    let tile: CGFloat
    /// The page's side margin, so the row can run to the screen's edges and start in line with the title.
    let margin: CGFloat
    /// Nil when the people have no page to open, such as Seerr's.
    var onOpenPerson: ((String) -> Void)?

    /// One tile per person: an actor with several parts is listed once, as on Android.
    private var people: [CastPerson] {
        var seen = Set<String>()
        return cast.filter { seen.insert($0.id).inserted }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            Text(Strings.shared.cast.titleCased())
                .sectionTitle()
                .accessibilityAddTraits(.isHeader)
            HorizontalRow(lifts: false) {
                LazyHStack(alignment: .top, spacing: Space.s3) {
                    ForEach(people, id: \.id) { person in
                        if let onOpenPerson {
                            Button { onOpenPerson(person.id) } label: { tileView(person) }
                                .buttonStyle(PressableButtonStyle())
                                .accessibilityElement(children: .combine)
                                .accessibilityHint(Strings.shared.personDescription(name: person.name, role: person.role))
                        } else {
                            tileView(person).accessibilityElement(children: .combine)
                        }
                    }
                }
            }
            .padding(.horizontal, -margin)
        }
    }

    private func tileView(_ person: CastPerson) -> some View {
        VStack(spacing: Space.s1) {
            PortraitView(name: person.name, imageUrl: person.imageUrl, size: tile, initialsFont: .headline)
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
}
