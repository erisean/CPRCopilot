import Foundation
import CoreMotion
import Combine

final class CompressionDetector: ObservableObject {
    @Published var metrics = CompressionMetrics()
    @Published var rawMotion = RawMotionData()

    private let motionManager = CMMotionManager()
    private var compressionTimestamps: [TimeInterval] = []
    private var lastPeakTime: TimeInterval = 0
    private var isInCompression = false

    private let peakAccelThreshold: Double = 3.0
    private let minCompressionInterval: TimeInterval = 0.3
    private let maxCompressionInterval: TimeInterval = 1.2
    private let idleTimeout: TimeInterval = 3.0

    func start() {
        guard motionManager.isDeviceMotionAvailable else { return }

        motionManager.deviceMotionUpdateInterval = 1.0 / 100.0
        motionManager.startDeviceMotionUpdates(to: .main) { [weak self] motion, _ in
            guard let self, let motion else { return }
            self.processMotion(motion)
        }
    }

    func stop() {
        motionManager.stopDeviceMotionUpdates()
        reset()
    }

    private func reset() {
        compressionTimestamps.removeAll()
        lastPeakTime = 0
        isInCompression = false
        metrics = CompressionMetrics()
    }

    private func processMotion(_ motion: CMDeviceMotion) {
        let accel = motion.userAcceleration
        let gravity = motion.gravity
        let rotRate = motion.rotationRate
        let magnitude = sqrt(accel.x * accel.x + accel.y * accel.y + accel.z * accel.z)
        let now = motion.timestamp

        rawMotion = RawMotionData(
            timestamp: now,
            accelX: accel.x, accelY: accel.y, accelZ: accel.z,
            accelMagnitude: magnitude,
            gravityX: gravity.x, gravityY: gravity.y, gravityZ: gravity.z,
            rotX: rotRate.x, rotY: rotRate.y, rotZ: rotRate.z,
            isInCompression: isInCompression
        )

        if magnitude > peakAccelThreshold && !isInCompression {
            let timeSinceLastPeak = now - lastPeakTime

            if lastPeakTime == 0 || timeSinceLastPeak > minCompressionInterval {
                isInCompression = true

                if lastPeakTime > 0 && timeSinceLastPeak < maxCompressionInterval {
                    compressionTimestamps.append(now)
                    pruneOldTimestamps(now: now)
                    updateMetrics()
                } else {
                    compressionTimestamps = [now]
                }

                lastPeakTime = now
            }
        }

        if isInCompression && magnitude < peakAccelThreshold * 0.3 {
            isInCompression = false
        }

        if lastPeakTime > 0 && (now - lastPeakTime) > idleTimeout {
            reset()
        }
    }

    private func pruneOldTimestamps(now: TimeInterval) {
        compressionTimestamps.removeAll { now - $0 > 10 }
    }

    private func updateMetrics() {
        guard compressionTimestamps.count >= 2,
              let first = compressionTimestamps.first,
              let last = compressionTimestamps.last else { return }

        let windowSeconds = last - first
        guard windowSeconds > 0 else { return }

        let rate = Int(Double(compressionTimestamps.count - 1) / windowSeconds * 60)

        let feedback: CompressionFeedback = switch rate {
        case ..<100: .tooSlow
        case 121...: .tooFast
        default: .good
        }

        metrics = CompressionMetrics(
            rate: rate,
            depthCm: metrics.depthCm,
            isCompressing: true,
            feedback: feedback
        )
    }
}

struct CompressionMetrics {
    var rate: Int = 0
    var depthCm: Float = 0
    var isCompressing: Bool = false
    var feedback: CompressionFeedback = .idle
}

struct RawMotionData {
    var timestamp: TimeInterval = 0
    var accelX: Double = 0
    var accelY: Double = 0
    var accelZ: Double = 0
    var accelMagnitude: Double = 0
    var gravityX: Double = 0
    var gravityY: Double = 0
    var gravityZ: Double = 0
    var rotX: Double = 0
    var rotY: Double = 0
    var rotZ: Double = 0
    var isInCompression: Bool = false
}

enum CompressionFeedback {
    case idle, good, tooSlow, tooFast, tooShallow, tooDeep

    var message: String {
        switch self {
        case .idle: "Tap to start"
        case .good: "Good compressions!"
        case .tooSlow: "Push faster"
        case .tooFast: "Slow down"
        case .tooShallow: "Push harder"
        case .tooDeep: "Ease up"
        }
    }
}
