import SwiftUI

/// Loads one subtitle file and keeps its cues. A new source replaces the cues; nil clears them.
@MainActor
final class SubtitleTrackModel: ObservableObject {
    @Published private(set) var cues: [SubtitleCue] = []
    private var task: Task<Void, Never>?
    private var loaded: SubtitleSource?

    func load(_ source: SubtitleSource?) {
        guard source != loaded else { return }
        loaded = source
        task?.cancel()
        cues = []
        guard let source else { return }
        task = Task { [weak self] in
            var request = URLRequest(url: source.url)
            for (name, value) in source.headers { request.setValue(value, forHTTPHeaderField: name) }
            guard let (data, response) = try? await URLSession.shared.data(for: request), !Task.isCancelled,
                  (response as? HTTPURLResponse).map({ (200..<300).contains($0.statusCode) }) ?? true else {
                PlayerLog.write("subtitle fetch failed \(source.url.lastPathComponent)")
                return
            }
            let parsed = SubtitleParser.parse(String(bytes: data, encoding: .utf8) ?? "")
            PlayerLog.write("subtitle loaded bytes=\(data.count) cues=\(parsed.count) first=\(parsed.first.map { "\($0.start)" } ?? "-")")
            self?.cues = parsed
        }
    }
}

/// The subtitle text over the video, at the player's own position, drawn the same for every engine.
struct SubtitleOverlay: View {
    let source: SubtitleSource?
    let position: () -> TimeInterval?
    @StateObject private var track = SubtitleTrackModel()

    var body: some View {
        // No ticking while there is nothing to show: most titles play without a text subtitle.
        TimelineView(.animation(minimumInterval: 0.1, paused: track.cues.isEmpty)) { _ in
            let shown = position().map { SubtitleParser.active(in: track.cues, at: $0) } ?? []
            VStack {
                Spacer()
                if !shown.isEmpty {
                    Text(shown.map(\.text).joined(separator: "\n"))
                        .font(.title3.weight(.semibold))
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.white)
                        .shadow(color: .black, radius: 3)
                        .padding(.horizontal, 48)
                        .padding(.bottom, 32)
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .onAppear { track.load(source) }
        .onChange(of: source) { _, source in track.load(source) }
    }
}
