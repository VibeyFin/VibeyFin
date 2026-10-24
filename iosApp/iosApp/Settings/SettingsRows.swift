import SwiftUI
import Shared

/// Up to this many options a choice is a menu on its row; above it, a checklist page.
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
    let onAction: (ActionItem) -> Void

    var body: some View {
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
    // The switch is aqua, the app tint.
    private func toggleRow(_ item: ToggleItem) -> some View {
        Toggle(isOn: Binding(get: { item.checked }, set: { model.toggle(item.key, $0) })) {
            RowLabel(title: item.title, help: item.help)
        }
        .tint(Brand.accent)
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

    private func linkRow(_ item: LinkItem) -> some View {
        NavigationLink(value: TabRoute.settingsPage(item.target)) {
            HStack {
                RowLabel(title: item.title, help: item.help)
                if let value = item.value {
                    Spacer(minLength: Space.s3)
                    Text(value).foregroundStyle(.secondary)
                }
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
        // The PIN dots belong to the PIN page, which iOS doesn't have yet.
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
                            .fill(Brand.accentSoft)
                            .padding(.horizontal, Space.s2)
                            .padding(.vertical, 2)
                    }
                }
            )
            .accessibilityAddTraits(selected ? .isSelected : [])
        }
    }
}

/// The checklist page of a choice with more options than a menu holds. It reads the item from the live state, so the pill follows the tap.
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
    private var tint: Color {
        let sum = name.lowercased().unicodeScalars.reduce(0) { ($0 &* 31) &+ Int($1.value) }
        return Brand.avatars[abs(sum) % Brand.avatars.count]
    }
}

/// The indexes of the subtitle rows: size 0 to 3, colour 0 to 2, background 0 to 2.
private struct SubtitleStyle {
    let size: Int32
    let color: Int32
    let background: Int32
}

/// A sample line drawn the way the chosen size, colour and background will draw the real subtitles.
/// Subtitle sample colours: the viewer's own choices, not brand colours.
private enum SubtitleSample {
    static let white = Color.white
    static let yellow = Color.yellow
    static let cyan = Color.cyan
}

/// A 120 pt gradient card with the sample line near the bottom, as in the design mockup. It draws its own card, so the row has no
/// background or insets of its own.
private struct SubtitlePreviewRow: View {
    let sample: String
    let style: SubtitleStyle

    var body: some View {
        ZStack(alignment: .bottom) {
            LinearGradient(
                colors: [Brand.accentSoft, Color(.systemBackground), Color(.secondarySystemGroupedBackground)],
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
        .listRowInsets(EdgeInsets())
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
