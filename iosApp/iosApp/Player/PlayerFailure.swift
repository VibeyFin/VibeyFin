import AVFoundation
import Foundation
import Shared

extension PlaybackFailure {
    /// What AVFoundation says went wrong, as the shared playback model understands it. A lost
    /// connection, a timeout and a server error (5xx) are `.network`; a 401 is `.unauthorized`;
    /// everything else, including every other HTTP status and any error it can't place, is `.unsupported`.
    static func of(_ error: Error?, item: AVPlayerItem?) -> PlaybackFailure {
        // The status the server answered with, when the failure was an HTTP one.
        if let status = item?.errorLog()?.events.last?.errorStatusCode {
            switch status {
            case 401: return .unauthorized
            case 500...599: return .network
            default: break
            }
        }
        var current = error as NSError?
        while let nsError = current {
            if nsError.domain == NSURLErrorDomain, networkCodes.contains(nsError.code) { return .network }
            current = nsError.userInfo[NSUnderlyingErrorKey] as? NSError
        }
        return .unsupported
    }

    private static let networkCodes: Set<Int> = [
        NSURLErrorTimedOut,
        NSURLErrorCannotFindHost,
        NSURLErrorCannotConnectToHost,
        NSURLErrorNetworkConnectionLost,
        NSURLErrorDNSLookupFailed,
        NSURLErrorNotConnectedToInternet,
    ]
}
