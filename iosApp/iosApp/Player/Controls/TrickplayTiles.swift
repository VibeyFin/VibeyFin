import Shared
import SwiftUI

/// The scrub-preview thumbnails of one playback. The tile images are fetched ahead of use, one at a time at low priority,
/// from `aim` (the whole film, or only the tiles near the position when the data is limited); what arrives is kept as the
/// compressed bytes, bounded, and only the tile being scrubbed and the next one in its direction are decoded, off the main
/// actor. One thumbnail is then cropped out by the shared maths. Make a new one for a new track (the next episode) and
/// `close` the old: that stops the fetching and drops the caches. A tile that can't be had counts as missing for a short
/// while (scrubbing carries on with the timestamp alone), then it is tried again.
@MainActor
final class TrickplayTiles {
    private let track: TrickplayTrack
    private let headers: [String: String]
    private var bytes = TileByteCache(limit: Int(TrickplayKt.TRICKPLAY_COMPRESSED_CACHE_BYTES))
    private var decoded: [Int: CGImage] = [:]
    /// When each tile failed, in milliseconds of system uptime.
    private var failed: [Int: Int64] = [:]
    private var fetching: [Int: Task<Data?, Never>] = [:]
    private var decoding: [Int: Task<CGImage?, Never>] = [:]
    private var prefetch: Task<Void, Never>?
    private var aimState = TrickplayAim()
    private var limited: Bool
    private var aimedMs: Int64 = 0
    private var scrubTile = -1
    private var cameFromTile: Int?
    private var keptDecoded: [Int] = []

    init(track: TrickplayTrack, headers: [String: String], limited: Bool) {
        self.track = track
        self.headers = headers
        self.limited = limited
    }

    /// Whether this is the set of `track`, so a new one is only made when the item changes.
    func isFor(_ other: TrickplayTrack) -> Bool { track.tilesUrl == other.tilesUrl }

    /// How big the preview is drawn over a bar `barWidth` points wide on a screen of `screenScale` pixels to the point.
    func previewSize(barWidth: CGFloat, screenScale: CGFloat) -> CGSize {
        let size = track.previewSize(barWidth: Float(barWidth), screenScale: Float(screenScale))
        return CGSize(width: CGFloat(size.width), height: CGFloat(size.height))
    }

    /// Which tile image holds the thumbnail for `positionMs`.
    func tileIndex(atMs positionMs: Int64) -> Int32 { track.locate(positionMs: positionMs).tileIndex }

    /// Points the prefetching at `positionMs`: nothing changes while the position stays in the same tile on the same
    /// kind of connection, otherwise the tiles still to fetch are ordered again from here.
    func aim(atMs positionMs: Int64) {
        aimedMs = positionMs
        guard aimState.needsAim(tile: Int(tileIndex(atMs: positionMs)), limited: limited) else { return }
        prefetch?.cancel()
        let order = track.prefetchTiles(positionMs: positionMs, dataLimited: limited).map { $0.intValue }
        prefetch = Task(priority: .utility) { [weak self] in
            for index in order {
                guard !Task.isCancelled, let self else { return }
                _ = await self.fetch(index, priority: .utility)
            }
        }
    }

    /// The connection became limited (or stopped being so): what is fetched ahead follows, from where it was aimed.
    func setLimited(_ limited: Bool) {
        guard limited != self.limited else { return }
        self.limited = limited
        aim(atMs: aimedMs)
    }

    /// Stops the fetching and drops everything held.
    func close() {
        prefetch?.cancel()
        prefetch = nil
        fetching.values.forEach { $0.cancel() }
        fetching = [:]
        decoding = [:]
        bytes.removeAll()
        decoded = [:]
    }

    /// The thumbnail for `positionMs`, or nil when its tile can't be had.
    func thumbnail(atMs positionMs: Int64) async -> UIImage? {
        let spot = track.locate(positionMs: positionMs)
        keepDecoded(around: Int(spot.tileIndex))
        guard let tile = await tile(Int(spot.tileIndex)) else { return nil }
        let width = Int(track.thumbWidth)
        let height = Int(track.thumbHeight)
        let rect = CGRect(x: Int(spot.column) * width, y: Int(spot.row) * height, width: width, height: height)
        guard CGRect(x: 0, y: 0, width: tile.width, height: tile.height).contains(rect),
              let cropped = tile.cropping(to: rect) else { return nil }
        return UIImage(cgImage: cropped)
    }

    /// Keeps decoded only the scrubbed tile and the next one in the direction of travel, and starts decoding that next one.
    private func keepDecoded(around tileIndex: Int) {
        if tileIndex != scrubTile {
            cameFromTile = scrubTile >= 0 ? scrubTile : nil
            scrubTile = tileIndex
        }
        let previous = cameFromTile.map { KotlinInt(int: Int32($0)) }
        keptDecoded = track.decodedTiles(tileIndex: Int32(scrubTile), previousTileIndex: previous).map { $0.intValue }
        decoded = decoded.filter { keptDecoded.contains($0.key) }
        for next in keptDecoded where next != scrubTile {
            Task(priority: .utility) { [weak self] in _ = await self?.tile(next) }
        }
    }

    private func tile(_ index: Int) async -> CGImage? {
        if let image = decoded[index] { return image }
        if let running = decoding[index] { return await running.value }
        guard let data = await fetch(index, priority: .userInitiated) else { return nil }
        if let image = decoded[index] { return image }
        if let running = decoding[index] { return await running.value }
        // The JPEG decode (a tile is a few megapixels) happens off the main actor.
        let task = Task.detached(priority: .userInitiated) { UIImage(data: data)?.preparingForDisplay()?.cgImage }
        decoding[index] = task
        let image = await task.value
        decoding[index] = nil
        if let image {
            if keptDecoded.contains(index) { decoded[index] = image }
        } else {
            failed[index] = Self.nowMs()
            bytes.remove(index)
        }
        return image
    }

    private func fetch(_ index: Int, priority: TaskPriority) async -> Data? {
        if let cached = bytes.value(for: index) { return cached }
        if let failedAt = failed[index] {
            guard track.mayRetryTile(failedAtMs: failedAt, nowMs: Self.nowMs()) else { return nil }
            failed[index] = nil
        }
        if let running = fetching[index] { return await running.value }
        let task = Task.detached(priority: priority) { [track, headers] () -> Data? in
            await Self.load(track.tileUrl(tileIndex: Int32(index)), headers: headers)
        }
        fetching[index] = task
        let data = await task.value
        fetching[index] = nil
        if let data {
            bytes.insert(data, for: index)
        } else {
            failed[index] = Self.nowMs()
        }
        return data
    }

    /// A tile from the server with the stream's headers, or from the device when the title plays from a download.
    private nonisolated static func load(_ address: String, headers: [String: String]) async -> Data? {
        guard let url = URL(string: address) else { return nil }
        if url.isFileURL { return try? Data(contentsOf: url) }
        var request = URLRequest(url: url)
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return data
    }

    /// A monotonic clock in milliseconds, for timing failed tiles.
    private static func nowMs() -> Int64 { Int64(ProcessInfo.processInfo.systemUptime * 1000) }
}

/// Whether the prefetching has to be ordered again: only when the position reaches another tile or the connection changes.
struct TrickplayAim {
    private var tile: Int?
    private var limited = false

    mutating func needsAim(tile: Int, limited: Bool) -> Bool {
        guard tile != self.tile || limited != self.limited else { return false }
        self.tile = tile
        self.limited = limited
        return true
    }
}

/// The fetched tile images as the bytes they came in, at most `limit` of them: the one used longest ago goes first.
struct TileByteCache {
    let limit: Int
    private(set) var totalBytes = 0
    private var entries: [Int: Data] = [:]
    /// Least recently used first.
    private(set) var order: [Int] = []

    init(limit: Int) {
        self.limit = limit
    }

    mutating func insert(_ data: Data, for key: Int) {
        remove(key)
        guard data.count <= limit else { return }
        entries[key] = data
        order.append(key)
        totalBytes += data.count
        while totalBytes > limit, let oldest = order.first { remove(oldest) }
    }

    mutating func value(for key: Int) -> Data? {
        guard let data = entries[key] else { return nil }
        order.removeAll { $0 == key }
        order.append(key)
        return data
    }

    mutating func remove(_ key: Int) {
        guard let data = entries.removeValue(forKey: key) else { return }
        totalBytes -= data.count
        order.removeAll { $0 == key }
    }

    mutating func removeAll() {
        entries = [:]
        order = []
        totalBytes = 0
    }
}
