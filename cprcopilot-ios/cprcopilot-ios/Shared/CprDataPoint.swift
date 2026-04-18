import Foundation

struct CprDataPoint: Codable, Identifiable {
    let id: UUID
    let timestampMs: Int64
    let rate: Int
    let depthCm: Float
    let feedback: String

    init(timestampMs: Int64, rate: Int, depthCm: Float, feedback: String) {
        self.id = UUID()
        self.timestampMs = timestampMs
        self.rate = rate
        self.depthCm = depthCm
        self.feedback = feedback
    }
}

struct CprSession: Codable, Identifiable {
    let id: UUID
    let startTimeMs: Int64
    var dataPoints: [CprDataPoint]

    init(startTimeMs: Int64, dataPoints: [CprDataPoint] = []) {
        self.id = UUID()
        self.startTimeMs = startTimeMs
        self.dataPoints = dataPoints
    }
}

struct RawSensorSnapshot: Codable {
    let timestampMs: Int64
    let accelX: Double
    let accelY: Double
    let accelZ: Double
    let accelMagnitude: Double
    let gravityX: Double
    let gravityY: Double
    let gravityZ: Double
    let rotX: Double
    let rotY: Double
    let rotZ: Double
    let isInCompression: Bool
    let computedRate: Int
    let feedback: String
}

enum WatchMessage {
    static let sessionStartKey = "session-start"
    static let sessionStopKey = "session-stop"
    static let dataPointKey = "data-point"
    static let rawSensorKey = "raw-sensor"
}
