import SwiftUI
import Shared

/// Opens the Seerr poster menu from a long press anywhere in the tabs. The tab shell owns it and presents the menu; a request from
/// the menu leaves its message here for the shell's banner.
final class SeerrMenuOpener: ObservableObject {
    /// The title whose menu is open.
    @Published var title: SeerrTitle?
    /// What the last request from a menu came to, with the title it was for (for View).
    @Published var notice: SeerrNotice?
    @Published var noticeTitle: SeerrTitle?

    func open(_ title: SeerrTitle) { self.title = title }

    func close() { title = nil }
}

private struct SeerrMenuKey: EnvironmentKey {
    static let defaultValue: SeerrMenuOpener? = nil
}

extension EnvironmentValues {
    /// The Seerr poster menu the tab shell owns, so a Seerr poster anywhere can open it with a long press.
    var seerrMenu: SeerrMenuOpener? {
        get { self[SeerrMenuKey.self] }
        set { self[SeerrMenuKey.self] = newValue }
    }
}

/// The menu a long press on a Seerr poster opens: the status (once someone asked), Request, Request in 4K, Details, Trailer, Cancel
/// request. It has its own model for as long as it is open. A show's Request swaps it for the season picker. A request ends it: its
/// message goes to `onNotice`, which the shell shows.
struct SeerrMenuSheetView: View {
    let title: SeerrTitle
    let onDetails: () -> Void
    let onTrailer: (String) -> Void
    let onNotice: (SeerrNotice) -> Void
    let onDismiss: () -> Void
    @StateObject private var model: SeerrTitleObservable

    init(title: SeerrTitle, onDetails: @escaping () -> Void, onTrailer: @escaping (String) -> Void, onNotice: @escaping (SeerrNotice) -> Void, onDismiss: @escaping () -> Void) {
        self.title = title
        self.onDetails = onDetails
        self.onTrailer = onTrailer
        self.onNotice = onNotice
        self.onDismiss = onDismiss
        _model = StateObject(wrappedValue: SeerrTitleObservable(type: title.type, tmdbId: title.tmdbId))
    }

    var body: some View {
        Group {
            if let picker = model.state?.picker {
                SeerrSeasonPickerView(picker: picker, sending: model.state?.sending == true, model: model)
            } else {
                menu
            }
        }
        .onAppear { model.start() }
        .onChange(of: model.state?.notice) { notice in
            guard let notice else { return }
            model.noticeShown()
            onNotice(notice)
            onDismiss()
        }
    }

    private var menu: some View {
        let state = model.state
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: Space.s3) {
                Artwork(url: title.posterUrl, title: title.title, aspectRatio: 2.0 / 3.0, blurHash: nil)
                    .frame(width: 40)
                    .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title.title).font(.headline).lineLimit(2).accessibilityAddTraits(.isHeader)
                    Text(state?.menuSubtitle.isEmpty == false ? state!.menuSubtitle : Strings.shared.seerrMenuSubtitle(meta: Strings.shared.seerrTitleMeta(year: title.year, type: title.type), state: title.state))
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, Space.s5)
            .padding(.bottom, Space.s3)
            Divider().padding(.horizontal, Space.s5)
            if let state, !state.loading {
                if let status = state.menuStatus {
                    // Someone asked for it: where it stands is a row of its own, not a button.
                    row(status, detail: state.menuStatusDetail, systemImage: SeerrMarker.symbol(state.state), action: nil)
                }
                if state.canRequest {
                    row((state.primary?.label ?? Strings.shared.seerrRequest).titleCased(), systemImage: "plus") { model.request() }
                    if state.offer4k { row(Strings.shared.seerrRequest4k.titleCased(), systemImage: "plus") { model.request4k() } }
                }
                row(Strings.shared.details.titleCased(), systemImage: "info.circle", action: onDetails)
                if let trailer = state.trailerUrl {
                    row(Strings.shared.trailer.titleCased(), systemImage: "play.rectangle") { onTrailer(trailer) }
                }
                if state.cancelRequestId != nil {
                    row(Strings.shared.seerrCancelRequest.titleCased(), systemImage: "xmark") { model.cancelRequest() }
                }
            } else {
                // Every row comes at once, so none appears late under a finger.
                ForEach(0..<3, id: \.self) { _ in
                    SkeletonLine(font: .body, width: 140).frame(minHeight: 52).padding(.horizontal, Space.s5)
                }
            }
        }
        .padding(.top, Space.s5)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    @ViewBuilder
    private func row(_ title: String, detail: String? = nil, systemImage: String, action: (() -> Void)?) -> some View {
        let label = HStack(spacing: Space.s4) {
            Image(systemName: systemImage).frame(width: 24).foregroundStyle(.secondary)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).foregroundStyle(action == nil ? .secondary : .primary)
                if let detail { Text(detail).font(.caption).foregroundStyle(.secondary) }
            }
            Spacer(minLength: 0)
        }
        .frame(minHeight: 52)
        .padding(.horizontal, Space.s5)
        .contentShape(Rectangle())
        if let action {
            Button(action: action) { label }.buttonStyle(PressableButtonStyle())
        } else {
            label.accessibilityElement(children: .combine)
        }
    }
}

/// The season picker a show's Request opens: 4K when allowed, All missing seasons, each season with a checkmark, and one aqua button.
struct SeerrSeasonPickerView: View {
    let picker: SeerrSeasonPicker
    let sending: Bool
    @ObservedObject var model: SeerrTitleObservable

    var body: some View {
        NavigationStack {
            List {
                if picker.offer4k {
                    Section {
                        Picker(Strings.shared.seerrQuality, selection: Binding(get: { picker.is4k }, set: { model.pickerQuality($0) })) {
                            Text(Strings.shared.seerrStandard).tag(false)
                            Text(Strings.shared.seerr4k).tag(true)
                        }
                        .pickerStyle(.segmented)
                    }
                }
                // The quality to ask for, for whoever may choose it: the server's quality profiles, its default picked.
                if picker.qualitiesLoading || !picker.qualities.isEmpty {
                    Section {
                        if picker.qualitiesLoading {
                            LabeledContent(Strings.shared.seerrQuality) { ProgressView() }
                        } else {
                            // A plain dropdown of the server's quality profiles, no search; the default is chosen to begin with.
                            Picker(
                                Strings.shared.seerrQuality,
                                selection: Binding(
                                    get: { picker.qualities.first(where: { $0.selected })?.id ?? -1 },
                                    set: { model.pickerProfile($0) }
                                )
                            ) {
                                ForEach(picker.qualities, id: \.id) { quality in Text(quality.name).tag(quality.id) }
                            }
                            .pickerStyle(.menu)
                        }
                    }
                }
                if picker.showsSeasons {
                Section {
                    Toggle(Strings.shared.seerrAllMissingSeasons, isOn: Binding(get: { picker.allMissing }, set: { model.pickerAllMissing($0) }))
                        .tint(Color(.systemGray))
                }
                Section {
                    ForEach(picker.seasons, id: \.number) { season in
                        Button { model.pickerSeason(season.number) } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(season.name).foregroundStyle(season.locked ? .secondary : .primary)
                                    Text(season.episodes).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                if let label = season.stateLabel {
                                    SeerrStatusLine(text: label, state: season.state).font(.caption)
                                } else if season.selected {
                                    Image(systemName: "checkmark").foregroundStyle(Brand.accent)
                                }
                            }
                        }
                        .disabled(season.locked)
                        .accessibilityAddTraits(season.selected ? .isSelected : [])
                    }
                }
                }
            }
            .listStyle(.insetGrouped)
            .safeAreaInset(edge: .bottom) {
                // A plus, not words: what it will ask for ("Request 2 seasons") is what VoiceOver says.
                PrimaryButton(action: { model.pickerConfirm() }) {
                    if sending { PrimaryButtonSpinner() } else { Image(systemName: "plus").font(.title3.weight(.semibold)) }
                }
                .disabled(!picker.canConfirm && !sending)
                .accessibilityLabel(picker.confirmLabel)
                .padding(Space.s4)
                .background(.bar)
            }
            .navigationTitle(picker.title.titleCased())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    VStack(spacing: 0) {
                        Text(picker.title.titleCased()).font(.headline)
                        Text(picker.subtitle).font(.caption).foregroundStyle(.secondary)
                    }
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button(Strings.shared.cancel.titleCased()) { model.pickerDismiss() }
                }
            }
        }
        .presentationDetents([.large])
    }
}

/// What a request came to, at the bottom of the screen: the failure banner's capsule on the system material, with View or Try again
/// when [showsAction]. Never a dialog; it goes by itself after a few seconds.
private struct SeerrNoticeBanner: ViewModifier {
    let notice: SeerrNotice?
    let showsAction: Bool
    let onAction: () -> Void
    let onDismiss: () -> Void

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let notice {
                    HStack(spacing: Space.s3) {
                        Text(notice.text)
                            .font(.subheadline)
                            .foregroundStyle(.primary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        if showsAction, let label = notice.actionLabel {
                            Button(label.titleCased()) {
                                onDismiss()
                                onAction()
                            }
                            .buttonStyle(PressableButtonStyle())
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.primary)
                        }
                    }
                    .padding(Space.s4)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                    .padding(Space.s4)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .task(id: notice.text) {
                        try? await Task.sleep(nanoseconds: 5_000_000_000)
                        onDismiss()
                    }
                }
            }
            .animation(.default, value: notice?.text)
    }
}

extension View {
    func seerrNoticeBanner(_ notice: SeerrNotice?, showsAction: Bool, onAction: @escaping () -> Void, onDismiss: @escaping () -> Void) -> some View {
        modifier(SeerrNoticeBanner(notice: notice, showsAction: showsAction, onAction: onAction, onDismiss: onDismiss))
    }
}
