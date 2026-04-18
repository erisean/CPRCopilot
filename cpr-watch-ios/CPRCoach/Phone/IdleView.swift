import SwiftUI

struct IdleView: View {
    @EnvironmentObject var receiver: PhoneSessionReceiver

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                Spacer().frame(height: 32)

                Text("CPR Coach")
                    .font(.largeTitle)
                    .fontWeight(.bold)

                Text("Waiting for watch session")
                    .foregroundStyle(.secondary)

                Text("Start a CPR session on your watch,\nor use debug mode to preview.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                    .font(.subheadline)

                Button("Start Debug Session") {
                    receiver.startSimulation()
                }
                .buttonStyle(.bordered)

                if !receiver.pastSessions.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Past Sessions")
                            .font(.title3)
                            .fontWeight(.bold)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        ForEach(Array(receiver.pastSessions.reversed().enumerated()), id: \.element.id) { index, session in
                            PastSessionRow(
                                index: receiver.pastSessions.count - index,
                                session: session
                            )
                        }
                    }
                }
            }
            .padding(20)
        }
    }
}

private struct PastSessionRow: View {
    let index: Int
    let session: CprSession

    var body: some View {
        let avgRate = session.dataPoints.isEmpty ? 0 :
            session.dataPoints.map(\.rate).reduce(0, +) / session.dataPoints.count
        let inZone = session.dataPoints.filter { $0.rate >= 100 && $0.rate <= 120 }.count
        let inZonePct = session.dataPoints.isEmpty ? 0 : inZone * 100 / session.dataPoints.count
        let durationSec = session.dataPoints.count >= 2
            ? (session.dataPoints.last!.timestampMs - session.dataPoints.first!.timestampMs) / 1000
            : 0

        HStack {
            VStack(alignment: .leading) {
                Text("Session #\(index)")
                    .fontWeight(.bold)
                Text("\(session.dataPoints.count) compressions · \(formatDuration(durationSec))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing) {
                Text("\(avgRate) BPM")
                    .fontWeight(.bold)
                Text("\(inZonePct)% in zone")
                    .font(.caption)
                    .foregroundStyle(inZonePct >= 80 ? .green : .orange)
            }
        }
        .padding()
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }
}

func formatDuration(_ seconds: Int64) -> String {
    let min = seconds / 60
    let sec = seconds % 60
    return min > 0 ? "\(min)m \(sec)s" : "\(sec)s"
}
