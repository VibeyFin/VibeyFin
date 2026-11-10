import SwiftUI
import Shared

/// A row's title with its help line under it.
private struct RowLabel: View {
    let title: String
    let help: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title.titleCased()).foregroundStyle(.primary)
            if let help {
                Text(help).font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }
}

/// One row of a settings page, for whatever kind of item the shared tree holds.
struct SettingsItemRow: View {
    let item: SettingItem
    let page: SettingsPage
    @ObservedObject var model: SettingsObservable
    /// Set in the iPad sidebar: a link is a selectable row, picked by its own key (two rows can open the same page), and not a push.
    var selection: Binding<SettingKey?>?
    let onAction: (ActionItem) -> Void
    /// Opens a page marked as a sheet (a language list) over this one, instead of pushing it.
    var onOpenSheet: (SettingsPageId) -> Void = { _ in }

    var body: some View {
        // A disabled item keeps its value on show, dimmed and inert.
        rowContent.disabled(!item.enabled)
    }

    @ViewBuilder
    private var rowContent: some View {
        switch item {
        case let toggle as ToggleItem: toggleRow(toggle)
        case let choice as ChoiceItem: choiceRow(choice)
        case let link as LinkItem: linkRow(link)
        case let action as ActionItem: actionRow(action)
        case let info as InfoItem: infoRow(info)
        case let custom as CustomItem: customRow(custom)
        default: EmptyView()
        }
    }

    // The whole row is the switch, so the label is a target too (a bare `Toggle` in a list only reacts to the switch itself).
    private func toggleRow(_ item: ToggleItem) -> some View {
        Toggle(isOn: Binding(get: { item.checked }, set: { model.toggle(item.key, $0) })) {
            RowLabel(title: item.title, help: item.help)
        }
        .tint(Brand.spark)
        .contentShape(Rectangle())
        .onTapGesture { model.toggle(item.key, !item.checked) }
    }

    @ViewBuilder
    private func choiceRow(_ item: ChoiceItem) -> some View {
        if item.asPage {
            // The page is the checklist: its options are the rows.
            ChecklistRows(item: item, model: model)
        } else {
            // The whole row opens the menu, not only the value text.
            Menu {
                Picker(selection: Binding(get: { item.value }, set: { model.choose(item.key, $0) })) {
                    ForEach(item.options, id: \.value) { option in
                        Text(option.label).tag(option.value)
                    }
                } label: {
                    Text(item.title.titleCased())
                }
            } label: {
                HStack {
                    RowLabel(title: item.title, help: item.help)
                    Spacer(minLength: Space.s3)
                    Text(item.options.first { $0.value == item.value }?.label ?? "")
                        .foregroundStyle(.secondary)
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private func linkRow(_ item: LinkItem) -> some View {
        if let selection {
            linkLabel(item)
                .tag(Optional(item.key))
                .listRowBackground(selection.wrappedValue == item.key ? Brand.sparkSoft : nil)
        } else if model.page(item.target)?.sheet == true {
            // Its list opens as the picker sheet the filters use, over this page.
            Button { onOpenSheet(item.target) } label: {
                HStack {
                    linkLabel(item)
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        } else {
            NavigationLink(value: TabRoute.settingsPage(item.target)) { linkLabel(item) }
        }
    }

    private func linkLabel(_ item: LinkItem) -> some View {
        HStack {
            RowLabel(title: item.title, help: item.help)
            if let value = item.value {
                Spacer(minLength: Space.s3)
                Text(value).foregroundStyle(.secondary)
            }
        }
    }

    private func actionRow(_ item: ActionItem) -> some View {
        Button(role: item.destructive ? .destructive : nil) {
            onAction(item)
        } label: {
            RowLabel(title: item.title, help: item.help)
        }
    }

    private func infoRow(_ item: InfoItem) -> some View {
        HStack(alignment: .firstTextBaseline) {
            RowLabel(title: item.title, help: item.help)
            Spacer(minLength: Space.s3)
            Text(item.value).foregroundStyle(.secondary).multilineTextAlignment(.trailing)
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private func customRow(_ item: CustomItem) -> some View {
        switch item.kind {
        case .profileheader: ProfileHeaderRow(name: item.title, address: item.help)
        case .subtitlepreview: SubtitlePreviewRow(sample: item.title, style: subtitleStyle())
        case .pindots: PinDotsRow(title: item.title, description: item.help)
        default: EmptyView()
        }
    }

    /// The preview follows the size, colour and background rows, even while they are hidden behind "Follow system".
    private func subtitleStyle() -> SubtitleStyle {
        let all = model.state?.pages[page.id] ?? page
        func value(_ key: SettingKey, _ fallback: Int32) -> Int32 {
            for section in all.sections {
                for case let choice as ChoiceItem in section.items where choice.key == key { return choice.value }
            }
            return fallback
        }
        return SubtitleStyle(
            size: value(SettingKey.subtitlesize, 1),
            color: value(SettingKey.subtitlecolor, 0),
            background: value(SettingKey.subtitlebackground, 1)
        )
    }

}

/// The options of a choice as a checklist. The chosen row is a soft pill, with no tick.
struct ChecklistRows: View {
    let item: ChoiceItem
    @ObservedObject var model: SettingsObservable

    var body: some View {
        ForEach(item.options, id: \.value) { option in
            let selected = option.value == item.value
            Button {
                model.choose(item.key, option.value)
            } label: {
                Text(option.label)
                    .foregroundStyle(.primary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .listRowBackground(
                ZStack {
                    Color(.secondarySystemGroupedBackground)
                    if selected {
                        RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
                            .fill(Brand.sparkSoft)
                            .padding(.horizontal, Space.s2)
                            .padding(.vertical, 2)
                    }
                }
            )
            .accessibilityAddTraits(selected ? .isSelected : [])
        }
    }
}

/// The profile card at the top of the Profile page: the avatar tile, the name and the server address.
private struct ProfileHeaderRow: View {
    let name: String
    let address: String?

    var body: some View {
        HStack(spacing: Space.s4) {
            ZStack {
                Rectangle().fill(tint)
                Text(String(name.prefix(1)).uppercased())
                    .font(.title.weight(.heavy))
                    .foregroundStyle(.primary)
            }
            .frame(width: 64, height: 64)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(name).font(.title3.weight(.semibold)).foregroundStyle(.primary)
                if let address {
                    Text(address).font(.subheadline).foregroundStyle(.secondary)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }

    /// The same muted, stable colour per name as the "Who's watching?" tile.
    private var tint: Color { Brand.avatars[Int(AvatarTint.shared.of(name: name))] }
}

/// The indexes of the subtitle rows: size 0 to 3, colour 0 to 2, background 0 to 2.
private struct SubtitleStyle {
    let size: Int32
    let color: Int32
    let background: Int32
}

/// Subtitle sample colours: the viewer's own choices, not brand colours.
private enum SubtitleSample {
    static let white = Color.white
    static let yellow = Color.yellow
    static let cyan = Color.cyan
}

/// A 120 pt gradient card with a sample line near the bottom, drawn the way the chosen size, colour and background will draw the real
/// subtitles, as in the design mockup. It draws its own card, so the row has no background of its own; its insets keep the card on
/// the list's gutter and leave a gap before the next section.
private struct SubtitlePreviewRow: View {
    let sample: String
    let style: SubtitleStyle

    var body: some View {
        ZStack(alignment: .bottom) {
            LinearGradient(
                colors: [Brand.sparkSoft, Color(.systemBackground), Color(.secondarySystemGroupedBackground)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Text(sample)
                .font(font)
                .foregroundStyle(color)
                .shadow(color: style.background == 1 ? .black : .clear, radius: 2)
                .padding(.horizontal, Space.s2)
                .padding(.vertical, 2)
                .background(style.background == 2 ? Brand.scrim : Color.clear)
                .multilineTextAlignment(.center)
                .padding(.bottom, Space.s4)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 120)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: Space.s3, trailing: 0))
        .listRowBackground(Color.clear)
        .accessibilityElement(children: .combine)
    }

    private var font: Font {
        switch style.size {
        case 0: return .footnote
        case 2: return .title3
        case 3: return .title2
        default: return .body
        }
    }

    private var color: Color {
        switch style.color {
        case 1: return SubtitleSample.yellow
        case 2: return SubtitleSample.cyan
        default: return SubtitleSample.white
        }
    }
}

/// "PIN" with four filled dots: a PIN is set. The digits are never kept, so the dots only say that one exists.
private struct PinDotsRow: View {
    let title: String
    let description: String?

    var body: some View {
        HStack {
            Text(title)
            Spacer()
            PinDotsView(filled: 4)
        }
        .frame(minHeight: 44)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
        .accessibilityValue(description ?? "")
    }
}
