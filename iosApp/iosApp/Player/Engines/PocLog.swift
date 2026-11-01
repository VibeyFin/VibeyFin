import Foundation

/// PoC: appends a line to `Documents/poc.log`, which `devicectl device copy from` can pull off the phone.
enum PocLog {
    private static let queue = DispatchQueue(label: "app.vibeyfin.poc-log")

    static func write(_ message: String) {
        queue.async {
            guard let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first?.appendingPathComponent("poc.log") else { return }
            let data = Data("\(Date().formatted(.iso8601)) \(message)\n".utf8)
            if let handle = try? FileHandle(forWritingTo: url) {
                handle.seekToEndOfFile()
                handle.write(data)
                try? handle.close()
            } else {
                try? data.write(to: url)
            }
        }
    }
}
