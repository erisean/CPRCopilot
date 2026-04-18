import Foundation
import Combine

@MainActor
final class WatchCprViewModel: ObservableObject {
    @Published var isActive = false
    @Published var rate: Int = 0
    @Published var depthCm: Float = 0
    @Published var feedbackMessage: String = "Tap to start"
    @Published var feedback: CompressionFeedback = .idle

    private let detector = CompressionDetector()
    private let haptic = HapticCoach()
    private let sender = WatchDataSender.shared
    private var cancellables = Set<AnyCancellable>()

    init() {
        sender.activate()
    }

    func startSession() {
        guard !isActive else { return }
        isActive = true

        detector.start()
        haptic.startMetronome()
        sender.sendSessionStart()

        detector.$metrics
            .receive(on: DispatchQueue.main)
            .sink { [weak self] metrics in
                guard let self else { return }
                self.rate = metrics.rate
                self.depthCm = metrics.depthCm
                self.feedback = metrics.feedback
                self.feedbackMessage = metrics.feedback.message

                if metrics.isCompressing && metrics.rate > 0 {
                    self.sender.sendDataPoint(CprDataPoint(
                        timestampMs: Int64(Date().timeIntervalSince1970 * 1000),
                        rate: metrics.rate,
                        depthCm: metrics.depthCm,
                        feedback: metrics.feedback.message
                    ))
                }

                if metrics.feedback != .good &&
                    metrics.feedback != .idle &&
                    metrics.isCompressing {
                    self.haptic.pulseWarning()
                }
            }
            .store(in: &cancellables)

        detector.$rawMotion
            .receive(on: DispatchQueue.main)
            .throttle(for: .milliseconds(100), scheduler: DispatchQueue.main, latest: true)
            .sink { [weak self] raw in
                guard let self else { return }
                self.sender.sendRawSnapshot(RawSensorSnapshot(
                    timestampMs: Int64(Date().timeIntervalSince1970 * 1000),
                    accelX: raw.accelX, accelY: raw.accelY, accelZ: raw.accelZ,
                    accelMagnitude: raw.accelMagnitude,
                    gravityX: raw.gravityX, gravityY: raw.gravityY, gravityZ: raw.gravityZ,
                    rotX: raw.rotX, rotY: raw.rotY, rotZ: raw.rotZ,
                    isInCompression: raw.isInCompression,
                    computedRate: self.rate,
                    feedback: self.feedbackMessage
                ))
            }
            .store(in: &cancellables)
    }

    func stopSession() {
        isActive = false
        detector.stop()
        haptic.stopMetronome()
        sender.sendSessionStop()
        cancellables.removeAll()
        rate = 0
        depthCm = 0
        feedbackMessage = "Tap to start"
        feedback = .idle
    }
}
