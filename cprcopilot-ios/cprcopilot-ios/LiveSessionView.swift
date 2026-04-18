import SwiftUI

struct LiveSessionView: View {
    @EnvironmentObject var receiver: PhoneSessionReceiver

    private var dataPoints: [CprDataPoint] {
        receiver.currentSession?.dataPoints ?? []
    }
    private var latest: CprDataPoint? { dataPoints.last }
    private var rate: Int { latest?.rate ?? 0 }
    private var feedback: String { latest?.feedback ?? "" }

    private var status: RateStatus {
        switch rate {
        case 0: .waiting
        case 100...120: .inZone
        case ..<100: .tooSlow
        default: .tooFast
        }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                header
                heroBpm
                RateChartView(dataPoints: dataPoints)
                    .frame(height: 160)
                    .padding(.horizontal)
                statsGrid
                guidanceBar
            }
            .padding(16)
        }
        .background(Color(.systemBackground))
    }

    private var header: some View {
        HStack {
            HStack(spacing: 8) {
                Circle()
                    .fill(.green)
                    .frame(width: 10, height: 10)
                Text("LIVE SESSION")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundStyle(.green)
            }
            Spacer()
            if receiver.isSimulating {
                Button("End Session") {
                    receiver.stopSimulation()
                }
                .font(.caption)
                .foregroundStyle(.red)
            }
        }
    }

    private var heroBpm: some View {
        VStack(spacing: 8) {
            Text(status.label)
                .font(.subheadline)
                .fontWeight(.bold)
                .foregroundStyle(status.color)
                .padding(.horizontal, 16)
                .padding(.vertical, 6)
                .background(status.color.opacity(0.15), in: Capsule())

            Text(rate > 0 ? "\(rate)" : "--")
                .font(.system(size: 80, weight: .bold, design: .rounded))

            Text("BPM")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    private var statsGrid: some View {
        let compressionCount = dataPoints.count
        let inZone = dataPoints.filter { $0.rate >= 100 && $0.rate <= 120 }.count
        let inZonePct = compressionCount > 0 ? inZone * 100 / compressionCount : 0
        let avgRate = compressionCount > 0
            ? dataPoints.map(\.rate).reduce(0, +) / compressionCount : 0
        let durationSec: Int64 = dataPoints.count >= 2
            ? (dataPoints.last!.timestampMs - dataPoints.first!.timestampMs) / 1000
            : 0

        return VStack(spacing: 8) {
            HStack(spacing: 8) {
                StatCard(label: "Compressions", value: "\(compressionCount)")
                StatCard(label: "Duration", value: formatDuration(durationSec))
            }
            HStack(spacing: 8) {
                StatCard(
                    label: "In Zone",
                    value: "\(inZonePct)%",
                    valueColor: inZonePct >= 80 ? .green : inZonePct >= 50 ? .orange : .red
                )
                StatCard(label: "Avg Rate", value: avgRate > 0 ? "\(avgRate)" : "--")
            }
        }
    }

    private var guidanceBar: some View {
        VStack(spacing: 4) {
            Text("CURRENT GUIDANCE")
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text(feedback.isEmpty ? "Waiting for compressions..." : feedback)
                .font(.headline)
                .foregroundStyle(status.color)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(status.color.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct StatCard: View {
    let label: String
    let value: String
    var valueColor: Color = .primary

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundStyle(valueColor)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }
}

enum RateStatus {
    case waiting, inZone, tooSlow, tooFast

    var label: String {
        switch self {
        case .waiting: "Waiting"
        case .inZone: "In Zone"
        case .tooSlow: "Too Slow"
        case .tooFast: "Too Fast"
        }
    }

    var color: Color {
        switch self {
        case .waiting: .gray
        case .inZone: .green
        case .tooSlow: .orange
        case .tooFast: .red
        }
    }
}
