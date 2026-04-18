import Foundation
import WatchConnectivity

final class WatchDataSender: NSObject, WCSessionDelegate {
    static let shared = WatchDataSender()

    private override init() {
        super.init()
    }

    func activate() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    func sendDataPoint(_ dataPoint: CprDataPoint) {
        guard WCSession.default.isReachable else { return }
        guard let data = try? JSONEncoder().encode(dataPoint) else { return }
        WCSession.default.sendMessageData(data, replyHandler: nil)
    }

    func sendSessionStart() {
        guard WCSession.default.isReachable else { return }
        WCSession.default.sendMessage(
            ["type": WatchMessage.sessionStartKey],
            replyHandler: nil
        )
    }

    func sendSessionStop() {
        guard WCSession.default.isReachable else { return }
        WCSession.default.sendMessage(
            ["type": WatchMessage.sessionStopKey],
            replyHandler: nil
        )
    }

    func sendRawSnapshot(_ snapshot: RawSensorSnapshot) {
        guard WCSession.default.isReachable else { return }
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        WCSession.default.sendMessage(
            ["type": WatchMessage.rawSensorKey, "data": data],
            replyHandler: nil
        )
    }

    // MARK: - WCSessionDelegate

    func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: Error?
    ) {}
}
