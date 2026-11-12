import SwiftUI
import Shared

extension View {
    /// The sheet that lists a title's external subtitle files, and the question before one is deleted on the server.
    func subtitleDelete(
        tracks: [DeletableSubtitle],
        isPresented: Binding<Bool>,
        prompt: DeletableSubtitle?,
        onChoose: @escaping (Int32) -> Void,
        onConfirm: @escaping () -> Void,
        onCancel: @escaping () -> Void
    ) -> some View {
        sheet(isPresented: isPresented) {
            SubtitleDeleteView(tracks: tracks) { index in
                isPresented.wrappedValue = false
                onChoose(index)
            }
        }
        // An alert, not a confirmationDialog: on iPad that is a popover pointing at the screen's top edge.
        .alert(
            prompt.map { Strings.shared.deleteSubtitleTitle(name: $0.name) } ?? "",
            isPresented: Binding(get: { prompt != nil }, set: { if !$0 { onCancel() } })
        ) {
            Button(Strings.shared.deleteSubtitleConfirm.titleCased(), role: .destructive, action: onConfirm)
            Button(Strings.shared.cancel.titleCased(), role: .cancel, action: onCancel)
        } message: {
            Text(Strings.shared.deleteSubtitleText)
        }
    }
}

private struct SubtitleDeleteView: View {
    let tracks: [DeletableSubtitle]
    let onChoose: (Int32) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            VStack(alignment: .leading, spacing: Space.s1) {
                Text(Strings.shared.deleteSubtitles.titleCased())
                    .sheetTitle()
                    .accessibilityAddTraits(.isHeader)
                Text(Strings.shared.deleteSubtitlesHelp)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, Space.s4)
            .padding(.top, Space.s5)
            Divider()
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(tracks, id: \.streamIndex) { track in
                        Button { onChoose(track.streamIndex) } label: {
                            HStack(spacing: Space.s3) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(track.name).foregroundStyle(.primary)
                                    if let fileName = track.fileName {
                                        Text(fileName).font(.footnote).foregroundStyle(.secondary)
                                    }
                                }
                                .multilineTextAlignment(.leading)
                                Spacer()
                                Image(systemName: "trash").foregroundStyle(.red)
                            }
                            .frame(minHeight: 44)
                            .padding(.horizontal, Space.s4)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(PressableButtonStyle())
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(track.description_)
                        .accessibilityAddTraits(.isButton)
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationSizing(.form)
    }
}
