import AppKit
import CoreGraphics
import Foundation

// Prints the on-screen windows, Dock policy and frontmost state of the given process ids as one JSON line.
let pids: Set<Int32> = Set(CommandLine.arguments.dropFirst().compactMap { Int32($0) })
let windows: [[String: Any]] = (CGWindowListCopyWindowInfo([.optionOnScreenOnly], kCGNullWindowID) as? [[String: Any]]) ?? []
var visible: [[String: Any]] = []
for window in windows {
    guard let pid = window[kCGWindowOwnerPID as String] as? Int32, pids.contains(pid) else { continue }
    let bounds: [String: Any] = (window[kCGWindowBounds as String] as? [String: Any]) ?? [:]
    visible.append(["pid": pid, "layer": window[kCGWindowLayer as String] ?? 0, "bounds": bounds])
}
var policies: [String: Int] = [:]
for pid in pids {
    if let application = NSRunningApplication(processIdentifier: pid) {
        policies[String(pid)] = application.activationPolicy.rawValue
    }
}
let front: Int32 = NSWorkspace.shared.frontmostApplication?.processIdentifier ?? -1
let report: [String: Any] = ["visible": visible, "frontmost": front, "frontmostOwned": pids.contains(front), "policies": policies]
let data: Data = try JSONSerialization.data(withJSONObject: report)
print(String(decoding: data, as: UTF8.self))
