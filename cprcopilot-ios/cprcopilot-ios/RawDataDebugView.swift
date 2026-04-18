import SwiftUI

struct RawDataDebugView: View {
    @EnvironmentObject var receiver: PhoneSessionReceiver

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                if let raw = receiver.latestRaw {
                    liveValues(raw)
                    accelChart
                    magnitudeChart
                    gravitySection(raw)
                    gyroSection(raw)
                    stateSection(raw)
                } else {
                    Text("No raw sensor data yet.\nStart a session on the watch.")
                        .foregroundStyle(.secondary)
                        .padding(.top, 40)
                        .frame(maxWidth: .infinity)
                        .multilineTextAlignment(.center)
                }

                if !receiver.rawSnapshots.isEmpty {
                    rawLog
                }
            }
            .padding(16)
        }
        .background(Color(.systemBackground))
        .navigationTitle("Raw Sensor Data")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var header: some View {
        HStack {
            Text("Raw Sensor Debug")
                .font(.title2)
                .fontWeight(.bold)
            Spacer()
            if receiver.latestRaw != nil {
                HStack(spacing: 4) {
                    Circle().fill(.green).frame(width: 8, height: 8)
                    Text("LIVE")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundStyle(.green)
                }
            }
        }
    }

    @ViewBuilder
    private func liveValues(_ raw: RawSensorSnapshot) -> some View {
        VStack(spacing: 8) {
            sensorCard(title: "User Acceleration (m/s²)") {
                HStack(spacing: 12) {
                    axisValue("X", raw.accelX, color: .red)
                    axisValue("Y", raw.accelY, color: .green)
                    axisValue("Z", raw.accelZ, color: .blue)
                    axisValue("‖‖", raw.accelMagnitude, color: .white)
                }
            }
        }
    }

    private var accelChart: some View {
        sensorCard(title: "Acceleration Axes") {
            Canvas { context, size in
                let snapshots = receiver.rawSnapshots
                guard snapshots.count >= 2 else { return }

                drawAxisLine(in: &context, size: size, snapshots: snapshots, selector: \.accelX, color: .red)
                drawAxisLine(in: &context, size: size, snapshots: snapshots, selector: \.accelY, color: .green)
                drawAxisLine(in: &context, size: size, snapshots: snapshots, selector: \.accelZ, color: .blue)

                // Zero line
                let zeroY = size.height / 2
                context.stroke(
                    Path { p in p.move(to: CGPoint(x: 0, y: zeroY)); p.addLine(to: CGPoint(x: size.width, y: zeroY)) },
                    with: .color(.gray.opacity(0.3)),
                    lineWidth: 1
                )
            }
            .frame(height: 120)

            HStack(spacing: 16) {
                legendDot(color: .red, label: "X")
                legendDot(color: .green, label: "Y")
                legendDot(color: .blue, label: "Z")
            }
            .font(.caption2)
        }
    }

    private var magnitudeChart: some View {
        sensorCard(title: "Acceleration Magnitude") {
            Canvas { context, size in
                let snapshots = receiver.rawSnapshots
                guard snapshots.count >= 2 else { return }

                let maxMag = max(snapshots.map(\.accelMagnitude).max() ?? 10, 5)
                let xStep = size.width / CGFloat(max(snapshots.count - 1, 1))

                // Threshold line
                let threshY = size.height * (1 - 3.0 / maxMag)
                var dashPath = Path()
                var dx: CGFloat = 0
                while dx < size.width {
                    dashPath.move(to: CGPoint(x: dx, y: threshY))
                    dashPath.addLine(to: CGPoint(x: min(dx + 6, size.width), y: threshY))
                    dx += 10
                }
                context.stroke(dashPath, with: .color(.orange.opacity(0.6)), lineWidth: 1)

                // Magnitude line
                var path = Path()
                for (i, snap) in snapshots.enumerated() {
                    let x = CGFloat(i) * xStep
                    let y = size.height * (1 - snap.accelMagnitude / maxMag)
                    if i == 0 { path.move(to: CGPoint(x: x, y: y)) }
                    else { path.addLine(to: CGPoint(x: x, y: y)) }
                }
                context.stroke(path, with: .color(.cyan), lineWidth: 2)

                // Compression markers
                for (i, snap) in snapshots.enumerated() where snap.isInCompression {
                    let x = CGFloat(i) * xStep
                    let y = size.height * (1 - snap.accelMagnitude / maxMag)
                    context.fill(Circle().path(in: CGRect(x: x - 3, y: y - 3, width: 6, height: 6)),
                                 with: .color(.yellow))
                }
            }
            .frame(height: 120)

            HStack(spacing: 16) {
                legendDot(color: .cyan, label: "Magnitude")
                legendDot(color: .yellow, label: "Compression")
                legendDot(color: .orange, label: "Threshold (3.0)")
            }
            .font(.caption2)
        }
    }

    @ViewBuilder
    private func gravitySection(_ raw: RawSensorSnapshot) -> some View {
        sensorCard(title: "Gravity Vector (m/s²)") {
            HStack(spacing: 12) {
                axisValue("X", raw.gravityX, color: .red)
                axisValue("Y", raw.gravityY, color: .green)
                axisValue("Z", raw.gravityZ, color: .blue)
            }
        }
    }

    @ViewBuilder
    private func gyroSection(_ raw: RawSensorSnapshot) -> some View {
        sensorCard(title: "Rotation Rate (rad/s)") {
            HStack(spacing: 12) {
                axisValue("X", raw.rotX, color: .red)
                axisValue("Y", raw.rotY, color: .green)
                axisValue("Z", raw.rotZ, color: .blue)
            }
        }
    }

    @ViewBuilder
    private func stateSection(_ raw: RawSensorSnapshot) -> some View {
        sensorCard(title: "Detector State") {
            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("In Compression")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(raw.isInCompression ? "YES" : "NO")
                        .fontWeight(.bold)
                        .foregroundStyle(raw.isInCompression ? .yellow : .gray)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Computed Rate")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text("\(raw.computedRate) BPM")
                        .fontWeight(.bold)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Feedback")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(raw.feedback)
                        .fontWeight(.bold)
                        .font(.caption)
                }
            }
        }
    }

    private var rawLog: some View {
        sensorCard(title: "Recent Samples (\(receiver.rawSnapshots.count))") {
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text("Time").frame(width: 70, alignment: .leading)
                    Text("Accel").frame(width: 60, alignment: .trailing)
                    Text("Rate").frame(width: 40, alignment: .trailing)
                    Text("State").frame(width: 50, alignment: .trailing)
                }
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundStyle(.secondary)

                ForEach(Array(receiver.rawSnapshots.suffix(20).reversed().enumerated()), id: \.offset) { _, snap in
                    HStack {
                        Text(String(format: "%.2f", Double(snap.timestampMs % 100000) / 1000.0))
                            .frame(width: 70, alignment: .leading)
                        Text(String(format: "%.2f", snap.accelMagnitude))
                            .frame(width: 60, alignment: .trailing)
                        Text("\(snap.computedRate)")
                            .frame(width: 40, alignment: .trailing)
                        Text(snap.isInCompression ? "COMP" : "—")
                            .frame(width: 50, alignment: .trailing)
                            .foregroundStyle(snap.isInCompression ? .yellow : .gray)
                    }
                    .font(.system(.caption2, design: .monospaced))
                }
            }
        }
    }

    // MARK: - Helpers

    @ViewBuilder
    private func sensorCard<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.caption)
                .fontWeight(.bold)
                .foregroundStyle(.secondary)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private func axisValue(_ label: String, _ value: Double, color: Color) -> some View {
        VStack(spacing: 2) {
            Text(label)
                .font(.caption2)
                .foregroundStyle(color)
            Text(String(format: "%+.3f", value))
                .font(.system(.caption, design: .monospaced))
                .fontWeight(.bold)
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private func legendDot(color: Color, label: String) -> some View {
        HStack(spacing: 4) {
            Circle().fill(color).frame(width: 6, height: 6)
            Text(label).foregroundStyle(.secondary)
        }
    }

    private func drawAxisLine(
        in context: inout GraphicsContext,
        size: CGSize,
        snapshots: [RawSensorSnapshot],
        selector: KeyPath<RawSensorSnapshot, Double>,
        color: Color
    ) {
        let maxVal: Double = 10
        let xStep = size.width / CGFloat(max(snapshots.count - 1, 1))
        var path = Path()
        for (i, snap) in snapshots.enumerated() {
            let val = min(max(snap[keyPath: selector], -maxVal), maxVal)
            let x = CGFloat(i) * xStep
            let y = size.height * (1 - (val + maxVal) / (2 * maxVal))
            if i == 0 { path.move(to: CGPoint(x: x, y: y)) }
            else { path.addLine(to: CGPoint(x: x, y: y)) }
        }
        context.stroke(path, with: .color(color), lineWidth: 1.5)
    }
}
