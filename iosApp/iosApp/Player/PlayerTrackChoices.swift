import Foundation
import Shared

extension PlaybackUiState {
    /// What the player has to choose itself: the selected audio track of a direct play. The server
    /// chooses the audio of every other stream, and the player then leaves it alone.
    var audioChoice: AudioChoice? {
        guard stream?.audioInPlayer == true,
              let position = audioTracks.firstIndex(where: { $0.selected }) else { return nil }
        return AudioChoice(position: position, count: audioTracks.count)
    }

    /// The subtitle the player draws itself: an embedded image track, by its place among the file's subtitles.
    /// Nil when the stream has no such track to show, so the player leaves subtitles alone.
    var subtitleChoice: SubtitleChoice? {
        guard let stream, !stream.embeddedSubtitles.isEmpty else { return nil }
        let tracks = subtitleTracks.filter { $0.index >= 0 }
        let selected = tracks.first { $0.selected }?.index
        let embedded = stream.embeddedSubtitles.map(\.int32Value)
        guard selected == nil || embedded.contains(selected ?? -1) else { return nil }
        return SubtitleChoice(position: tracks.firstIndex { $0.selected }, count: tracks.count, index: selected.map(Int.init))
    }

    /// The subtitle the app paints itself: the selected track, when the server can send it as a file.
    var subtitleSource: SubtitleSource? {
        guard let stream,
              let selected = subtitleTracks.first(where: { $0.selected && $0.index >= 0 })?.index,
              let file = stream.subtitleFiles.first(where: { $0.index == selected }),
              let url = URL(string: file.url) else { return nil }
        return SubtitleSource(url: url, headers: stream.headers)
    }
}
