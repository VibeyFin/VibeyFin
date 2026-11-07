import Shared
import SwiftUI

extension View {
    /// "Search for subtitles" for a screen. The model lives with the screen, so a download that is still going reaches `onAdded`
    /// after the sheet has gone.
    func subtitleSearch(
        itemId: String?,
        isPresented: Binding<Bool>,
        onFailedWhileClosed: @escaping (String) -> Void,
        onAdded: @escaping (SubtitleAdded) -> Void
    ) -> some View {
        modifier(SubtitleSearchModifier(itemId: itemId, isPresented: isPresented, onFailedWhileClosed: onFailedWhileClosed, onAdded: onAdded))
    }
}

private struct SubtitleSearchModifier: ViewModifier {
    let itemId: String?
    @Binding var isPresented: Bool
    let onFailedWhileClosed: (String) -> Void
    let onAdded: (SubtitleAdded) -> Void
    @StateObject private var model = SubtitleSearchObservable()

    func body(content: Content) -> some View {
        content
            .onAppear { model.start() }
            .onChange(of: isPresented) { _, open in
                if open, let itemId { model.open(itemId) }
            }
            .onChange(of: model.state?.added) { _, added in
                guard let added else { return }
                model.addedHandled()
                onAdded(added)
                isPresented = false
            }
            // The next episode took over: the results were for the one before.
            .onChange(of: itemId) { _, _ in isPresented = false }
            .onChange(of: model.state?.message?.text) { _, text in
                guard let text, !isPresented else { return }
                model.dismissMessage()
                onFailedWhileClosed(text)
            }
            .sheet(isPresented: $isPresented) {
                SubtitleSearchView(model: model)
            }
    }
}

/// The sheet: its language, then the results, or what stands in for them.
private struct SubtitleSearchView: View {
    @ObservedObject var model: SubtitleSearchObservable
    @State private var choosingLanguage = false

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            Text(Strings.shared.searchForSubtitles.titleCased())
                .sheetTitle()
                .accessibilityAddTraits(.isHeader)
                .padding(.horizontal, Space.s4)
                .padding(.top, Space.s5)
            languageRow
            Divider()
            content
        }
        .failureBanner(
            model.state?.message?.text,
            actionLabel: model.state?.message?.canRetry == true ? Strings.shared.tryAgain : nil,
            onRetry: { model.retry() },
            onDismiss: { model.dismissMessage() }
        )
        .presentationDetents([.medium, .large])
        .presentationSizing(.form)
        .sheet(isPresented: $choosingLanguage, onDismiss: { model.searchLanguages("") }, content: {
            languagePicker
        })
    }

    private var languageRow: some View {
        Button {
            choosingLanguage = true
        } label: {
            HStack(spacing: Space.s2) {
                Text(Strings.shared.subtitleSearchLanguage.titleCased()).foregroundStyle(.primary)
                Spacer()
                Text(model.state?.languageName ?? "").foregroundStyle(.secondary)
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(.horizontal, Space.s4)
    }

    @ViewBuilder
    private var content: some View {
        if let state = model.state, !state.loading {
            if let problem = state.problem {
                ProblemView(problem: problem) { model.retry() }
                Spacer(minLength: 0)
            } else {
                List(state.results, id: \.id) { result in
                    ResultRow(result: result, downloading: state.downloadingId == result.id, enabled: state.downloadingId == nil && !result.added) {
                        model.choose(result.id)
                    }
                    .listRowBackground(Color.clear)
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        } else {
            VStack(alignment: .leading, spacing: Space.s4) {
                ForEach(0..<4, id: \.self) { _ in
                    VStack(alignment: .leading, spacing: Space.s1) {
                        SkeletonLine(font: .body, width: 240)
                        SkeletonLine(font: .footnote, width: 140)
                    }
                }
            }
            .padding(.horizontal, Space.s4)
            Spacer(minLength: 0)
        }
    }

    private var languagePicker: some View {
        let rows = (model.state?.languages ?? []).map { PickerRow(id: $0.code, label: $0.name, selected: $0.selected) }
        return PickerSheetView(
            title: Strings.shared.subtitleLanguageTitle.titleCased(),
            rows: rows,
            loading: false,
            message: nil,
            emptyText: rows.isEmpty ? Strings.shared.noLanguageMatches : nil,
            onPick: { code in
                model.selectLanguage(code)
                choosingLanguage = false
            },
            onRetry: {},
            search: Binding(get: { model.state?.languageQuery ?? "" }, set: { model.searchLanguages($0) }),
            searchHint: Strings.shared.searchLanguages
        )
    }
}

private struct ResultRow: View {
    let result: SubtitleResultRow
    let downloading: Bool
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Space.s3) {
                VStack(alignment: .leading, spacing: Space.s1) {
                    Text(result.name).font(.body).foregroundStyle(.primary).lineLimit(2)
                    HStack(spacing: Space.s2) {
                        if result.isMatch { MatchBadge() }
                        Text(result.details).font(.footnote).foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 0)
                if downloading {
                    ProgressView()
                } else if result.added {
                    Image(systemName: "checkmark").foregroundStyle(.secondary)
                } else {
                    Image(systemName: "arrow.down.circle").foregroundStyle(.secondary)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled || downloading || result.added ? 1 : 0.4)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(result.spoken)
        .accessibilityAddTraits(.isButton)
    }
}

private struct MatchBadge: View {
    var body: some View {
        Text(Strings.shared.subtitleMatch)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(.primary)
            .padding(.horizontal, 6)
            .padding(.vertical, 1)
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(.secondary, lineWidth: 1))
    }
}

private struct ProblemView: View {
    let problem: SubtitleSearchProblem
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: Space.s2) {
            Text(problem.title).font(.headline).foregroundStyle(.primary)
            Text(problem.text).font(.subheadline).foregroundStyle(.secondary)
            if problem.canRetry {
                SecondaryButton(title: Strings.shared.tryAgain, action: onRetry)
                    .frame(maxWidth: 200)
                    .padding(.top, Space.s2)
            }
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, Space.s5)
        .padding(.vertical, Space.s6)
    }
}

/// The way in on a title's page, under the technical info's subtitle tracks.
struct SubtitleSearchRow: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Space.s3) {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                Text(Strings.shared.searchForSubtitles.titleCased()).foregroundStyle(.primary)
                Spacer()
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableButtonStyle())
    }
}
