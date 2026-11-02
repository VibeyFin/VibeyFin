import SwiftUI
import Shared

/// The request sheet a Request opens (a show's season picker, or a movie's quality choice): its title, the choices that apply (4K,
/// quality, All missing seasons), one row per season with a tick, and one aqua button saying what will be asked for.
struct SeerrSeasonPickerView: View {
    let picker: SeerrSeasonPicker
    let sending: Bool
    @ObservedObject var model: SeerrTitleObservable

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: Space.s1) {
                    Text(picker.title.titleCased()).font(.headline).accessibilityAddTraits(.isHeader)
                    Text(picker.subtitle).font(.subheadline).foregroundStyle(.secondary)
                }
                .padding(.horizontal, Space.s5)
                .padding(.top, Space.s5)
                .padding(.bottom, Space.s3)
                choices
                if picker.showsSeasons {
                    ForEach(picker.seasons, id: \.number) { season in seasonRow(season) }
                }
            }
            .padding(.bottom, Space.s3)
        }
        .scrollBounceBehavior(.basedOnSize)
        .safeAreaInset(edge: .bottom) {
            // A plus and what it will ask for ("Request 2 seasons").
            PrimaryButton(action: { model.pickerConfirm() }) {
                if sending {
                    PrimaryButtonSpinner()
                } else {
                    Label(picker.confirmLabel.titleCased(), systemImage: "plus").lineLimit(1)
                }
            }
            .disabled(!picker.canConfirm && !sending)
            .padding(.horizontal, Space.s5)
            .padding(.vertical, Space.s3)
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    /// Standard or 4K, the server's quality profile (for whoever may choose), and All missing seasons, divided by hairlines.
    @ViewBuilder
    private var choices: some View {
        if picker.offer4k {
            Picker(Strings.shared.seerrQuality, selection: Binding(get: { picker.is4k }, set: { model.pickerQuality($0) })) {
                Text(Strings.shared.seerrStandard).tag(false)
                Text(Strings.shared.seerr4k).tag(true)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, Space.s5)
            .padding(.vertical, Space.s2)
        }
        if picker.qualitiesLoading {
            row { Text(Strings.shared.seerrQuality); Spacer(); ProgressView() }
        } else if !picker.qualities.isEmpty {
            row {
                Text(Strings.shared.seerrQuality)
                Spacer()
                Picker(
                    Strings.shared.seerrQuality,
                    selection: Binding(get: { picker.qualities.first(where: { $0.selected })?.id ?? -1 }, set: { model.pickerProfile($0) })
                ) {
                    ForEach(picker.qualities, id: \.id) { quality in Text(quality.name).tag(quality.id) }
                }
                .pickerStyle(.menu)
                .labelsHidden()
                .tint(.secondary)
            }
        }
        if picker.showsSeasons {
            row {
                Toggle(Strings.shared.seerrAllMissingSeasons, isOn: Binding(get: { picker.allMissing }, set: { model.pickerAllMissing($0) }))
                    .tint(Brand.accent)
            }
            Divider().padding(.horizontal, Space.s5)
        }
    }

    /// A season: its name and episode count, and at the end a tick once chosen; one that can't be asked for (available, requested)
    /// is muted and shows its state instead.
    @ViewBuilder
    private func seasonRow(_ season: SeerrPickerSeason) -> some View {
        let content = HStack(spacing: Space.s4) {
            VStack(alignment: .leading, spacing: 2) {
                Text(season.name).foregroundStyle(season.locked ? Color.secondary : Color.primary)
                Text(season.episodes).font(.caption).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            if season.locked, let label = season.stateLabel {
                Label { Text(label) } icon: { SeerrStateSymbol(state: season.state) }
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            } else {
                Image(systemName: "checkmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.primary)
                    .frame(width: 22)
                    .opacity(season.selected ? 1 : 0)
            }
        }
        .padding(.horizontal, Space.s5)
        .frame(minHeight: 52)
        .contentShape(Rectangle())
        if season.locked {
            content.accessibilityElement(children: .ignore).accessibilityLabel(season.description_)
        } else {
            Button { model.pickerSeason(season.number) } label: { content }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(season.description_)
                .accessibilityAddTraits(season.selected ? [.isButton, .isSelected] : .isButton)
        }
    }

    private func row<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        HStack(spacing: Space.s3) { content() }
            .padding(.horizontal, Space.s5)
            .frame(minHeight: 52)
    }
}

extension View {
    /// What a request came to, as the failure banner: with View or Try again when the notice has an action, otherwise just the words.
    func seerrNoticeBanner(_ notice: SeerrNotice?, showsAction: Bool = true, onAction: @escaping () -> Void, onDismiss: @escaping () -> Void) -> some View {
        failureBanner(notice?.text, actionLabel: showsAction ? notice?.actionLabel : nil, onRetry: onAction, onDismiss: onDismiss)
    }
}
