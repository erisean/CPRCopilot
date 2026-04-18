import Foundation
import WatchKit

final class HapticCoach {
    private var timer: Timer?

    // 110 bpm = 0.545s interval
    private let targetInterval: TimeInterval = 60.0 / 110.0

    func startMetronome() {
        stopMetronome()
        timer = Timer.scheduledTimer(withTimeInterval: targetInterval, repeats: true) { _ in
            WKInterfaceDevice.current().play(.click)
        }
    }

    func stopMetronome() {
        timer?.invalidate()
        timer = nil
    }

    func pulseWarning() {
        WKInterfaceDevice.current().play(.notification)
    }

    var isRunning: Bool { timer != nil }
}
