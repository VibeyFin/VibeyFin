import SwiftUI

/// Loads one subtitle file and keeps its cues. A new source replaces the cues; nil clears them.
@MainActor
final class SubtitleTrackModel: ObservableObject {
    @Published private(set) var cues: [SubtitleCue] = []
    private var task: Task<Void, Never>?
    private var loaded: SubtitleSource?

    /// `onUnauthorized` hears a 401: the token is no longer good, which the shared model handles like any rejected call.
    func load(_ source: SubtitleSource?, onUnauthorized: @escaping () -> Void) {
        guard source != loaded else { return }
        loaded = source
        task?.cancel()
        cues = []
        guard let source else { return }
        task = Task { [weak self] in
            var request = URLRequest(url: source.url)
            for (name, value) in source.headers { request.setValue(value, forHTTPHeaderField: name) }
            guard let (data, response) = try? await URLSession.shared.data(for: request), !Task.isCancelled else {
                PlayerLog.write("subtitle fetch failed \(source.url.lastPathComponent)")
                return
            }
            let status = (response as? HTTPURLResponse)?.statusCode ?? 200
            guard (200..<300).contains(status) else {
                PlayerLog.write("subtitle fetch status=\(status) \(source.url.lastPathComponent)")
                if status == 401 { onUnauthorized() }
                return
            }
            let parsed = SubtitleParser.parse(SubtitleParser.decode(data))
            PlayerLog.write("subtitle loaded bytes=\(data.count) cues=\(parsed.count) first=\(parsed.first.map { "\($0.start)" } ?? "-")")
            self?.cues = parsed
        }
    }
}

/// The subtitle text over the video, at the player's own position, drawn the same for every engine.
struct SubtitleOverlay: View {
    let source: SubtitleSource?
    let onUnauthorized: () -> Void
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
                        .foregroundStyle(Brand.onMedia)
                        .shadow(color: Brand.scrimStrong, radius: 3)
                        .padding(.horizontal, 48)
                        .padding(.bottom, 32)
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .onAppear { track.load(source, onUnauthorized: onUnauthorized) }
        .onChange(of: source) { _, source in track.load(source, onUnauthorized: onUnauthorized) }
    }
}
