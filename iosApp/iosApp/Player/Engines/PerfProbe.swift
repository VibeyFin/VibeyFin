import Foundation
import UIKit

/// PoC: logs how hard the app works while a title plays, every 15 s, to compare engines on CPU, heat and battery.
/// CPU is the app's share of one core summed over its threads (100 = one full core). Battery is the device's level in percent.
final class PerfProbe {
    private var timer: Timer?
    private var started = Date()
    private var startLevel: Float = -1

    func start(engine: String, title: String) {
        stop()
        UIDevice.current.isBatteryMonitoringEnabled = true
        started = Date()
        startLevel = UIDevice.current.batteryLevel
        PocLog.write("perf start engine=\(engine) title=\(title) battery=\(Self.percent(startLevel))")
        timer = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { [weak self] _ in
            self?.sample(engine: engine)
        }
    }

    func stop() {
        guard timer != nil else { return }
        timer?.invalidate()
        timer = nil
        PocLog.write("perf stop after \(Int(Date().timeIntervalSince(started))) s battery=\(Self.percent(UIDevice.current.batteryLevel)) from \(Self.percent(startLevel))")
    }

    private func sample(engine: String) {
        let thermal = ProcessInfo.processInfo.thermalState.rawValue
        let low = ProcessInfo.processInfo.isLowPowerModeEnabled ? 1 : 0
        PocLog.write("perf engine=\(engine) cpu=\(Int(Self.cpu())) thermal=\(thermal) lowpower=\(low) battery=\(Self.percent(UIDevice.current.batteryLevel))")
    }

    private static func percent(_ level: Float) -> String { level < 0 ? "?" : "\(Int(level * 100))" }

    private static func cpu() -> Double {
        var threads: thread_act_array_t?
        var count = mach_msg_type_number_t(0)
        guard task_threads(mach_task_self_, &threads, &count) == KERN_SUCCESS, let threads else { return 0 }
        defer { vm_deallocate(mach_task_self_, vm_address_t(UInt(bitPattern: threads)), vm_size_t(Int(count) * MemoryLayout<thread_t>.size)) }
        var total = 0.0
        for index in 0..<Int(count) {
            var info = thread_basic_info()
            var size = mach_msg_type_number_t(MemoryLayout<thread_basic_info>.size / MemoryLayout<integer_t>.size)
            let result = withUnsafeMutablePointer(to: &info) {
                $0.withMemoryRebound(to: integer_t.self, capacity: Int(size)) {
                    thread_info(threads[index], thread_flavor_t(THREAD_BASIC_INFO), $0, &size)
                }
            }
            if result == KERN_SUCCESS, info.flags & TH_FLAGS_IDLE == 0 {
                total += Double(info.cpu_usage) / Double(TH_USAGE_SCALE) * 100
            }
        }
        return total
    }
}
