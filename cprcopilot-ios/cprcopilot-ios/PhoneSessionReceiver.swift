import Foundation
import WatchConnectivity

@MainActor
final class PhoneSessionReceiver: NSObject, ObservableObject, WCSessionDelegate {
    @Published var currentSession: CprSession?
    @Published var pastSessions: [CprSession] = []
    @Published var completedSession: CprSession?
    @Published var isSimulating = false
    @Published var rawSnapshots: [RawSensorSnapshot] = []
    @Published var latestRaw: RawSensorSnapshot?

    private var simulationTask: Task<Void, Never>?
    private let maxRawSnapshots = 500

    override init() {
        super.init()
        if WCSession.isSupported() {
            WCSession.default.delegate = self
            WCSession.default.activate()
        }
    }

    var latestDataPoint: CprDataPoint? {
        currentSession?.dataPoints.last
    }

    // MARK: - Debug simulation

    func startSimulation() {
        guard simulationTask == nil else { return }
        isSimulating = true
        completedSession = nil
        currentSession = CprSession(startTimeMs: Int64(Date().timeIntervalSince1970 * 1000))

        simulationTask = Task { @MainActor in
            var count = 0
            while !Task.isCancelled {
                count += 1
                let phase = Double(count) / 30.0
                let rateDrift = Int(sin(phase) * 15)
                let depthDrift = Float(sin(phase * 0.7) * 1.2)

                let rate = max(80, min(140, 110 + rateDrift + Int.random(in: -3...3)))
                let depth = max(2, min(8, 5.5 + depthDrift + Float.random(in: -0.2...0.2)))

                let feedback: String = switch true {
                case rate < 100: "Push faster"
                case rate > 120: "Slow down"
                case depth < 5.0: "Push harder"
                case depth > 6.0: "Ease up"
                default: "Good compressions!"
                }

                let dp = CprDataPoint(
                    timestampMs: Int64(Date().timeIntervalSince1970 * 1000),
                    rate: rate,
                    depthCm: depth,
                    feedback: feedback
                )
                currentSession?.dataPoints.append(dp)

                try? await Task.sleep(for: .milliseconds(545))
            }
        }
    }

    func stopSimulation() {
        simulationTask?.cancel()
        simulationTask = nil
        completedSession = currentSession
        if let session = currentSession, !session.dataPoints.isEmpty {
            pastSessions.append(session)
        }
        currentSession = nil
        isSimulating = false
    }

    func dismissScorecard() {
        completedSession = nil
    }

    // MARK: - WCSessionDelegate

    nonisolated func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: Error?
    ) {}

    nonisolated func sessionDidBecomeInactive(_ session: WCSession) {}
    nonisolated func sessionDidDeactivate(_ session: WCSession) {
        session.activate()
    }

    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        Task { @MainActor in
            guard let type = message["type"] as? String else { return }
            switch type {
            case WatchMessage.sessionStartKey:
                completedSession = nil
                rawSnapshots.removeAll()
                currentSession = CprSession(
                    startTimeMs: Int64(Date().timeIntervalSince1970 * 1000)
                )
            case WatchMessage.sessionStopKey:
                completedSession = currentSession
                if let session = currentSession, !session.dataPoints.isEmpty {
                    pastSessions.append(session)
                }
                currentSession = nil
            case WatchMessage.rawSensorKey:
                if let data = message["data"] as? Data,
                   let snapshot = try? JSONDecoder().decode(RawSensorSnapshot.self, from: data) {
                    latestRaw = snapshot
                    rawSnapshots.append(snapshot)
                    if rawSnapshots.count > maxRawSnapshots {
                        rawSnapshots.removeFirst(rawSnapshots.count - maxRawSnapshots)
                    }
                }
            default:
                break
            }
        }
    }

    nonisolated func session(_ session: WCSession, didReceiveMessageData messageData: Data) {
        guard let dataPoint = try? JSONDecoder().decode(CprDataPoint.self, from: messageData) else { return }
        Task { @MainActor in
            if currentSession == nil {
                currentSession = CprSession(
                    startTimeMs: Int64(Date().timeIntervalSince1970 * 1000)
                )
            }
            currentSession?.dataPoints.append(dataPoint)
        }
    }
}
