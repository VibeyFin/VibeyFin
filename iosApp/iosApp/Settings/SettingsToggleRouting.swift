import Foundation

/// Where a flipped switch is reported: a developer flag by its id, any other switch by its key.
enum SettingsToggleRoute: Equatable {
    case devFlag(id: String)
    case setting

    static func of(devFlagId: String?) -> SettingsToggleRoute {
        guard let devFlagId else { return .setting }
        return .devFlag(id: devFlagId)
    }
}

/// A row's identity in a list: the setting's key name, plus the flag id for developer flags, which all share one key.
func settingRowID(keyName: String, devFlagId: String?) -> String {
    guard let devFlagId else { return keyName }
    return "\(keyName)/\(devFlagId)"
}
