import SwiftUI

struct ScorecardView: View {
    @EnvironmentObject var receiver: PhoneSessionReceiver

    private var dataPoints: [CprDataPoint] {
        receiver.completedSession?.dataPoints ?? []
    }

    var body: some View {
        let analysis = SessionAnalysis(dataPoints: dataPoints)

        ScrollView {
            VStack(spacing: 20) {
                Text("Session Complete")
                    .font(.title)
                    .fontWeight(.bold)

                gradeRing(analysis: analysis)

                Text(analysis.summary)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal)

                metricsGrid(analysis: analysis)

                VStack(alignment: .leading) {
                    Text("Rate Over Time")
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .padding(.leading, 4)
                    RateChartView(dataPoints: dataPoints, annotate: true)
                        .frame(height: 180)
                }
                .padding(.horizontal)

                timeBreakdown(analysis: analysis)
                coachingTip(analysis: analysis)

                Button("Done") {
                    receiver.dismissScorecard()
                }
                .buttonStyle(.borderedProminent)
                .padding(.top, 8)
            }
            .padding(20)
        }
    }

    @ViewBuilder
    private func gradeRing(analysis: SessionAnalysis) -> some View {
        ZStack {
            Circle()
                .stroke(analysis.grade.color.opacity(0.15), lineWidth: 12)
                .frame(width: 150, height: 150)

            Circle()
                .trim(from: 0, to: CGFloat(analysis.inZonePct) / 100)
                .stroke(analysis.grade.color, style: StrokeStyle(lineWidth: 12, lineCap: .round))
                .frame(width: 150, height: 150)
                .rotationEffect(.degrees(-90))

            VStack(spacing: 2) {
                Text(analysis.grade.letter)
                    .font(.system(size: 48, weight: .bold))
                    .foregroundStyle(analysis.grade.color)
                Text("\(analysis.inZonePct)% in zone")
                    .font(.caption)
                    .foregroundStyle(analysis.grade.color.opacity(0.8))
                Text(analysis.grade.label)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }

    @ViewBuilder
    private func metricsGrid(analysis: SessionAnalysis) -> some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                metricCard(label: "Duration", value: formatDuration(analysis.durationSec))
                metricCard(label: "Compressions", value: "\(analysis.totalCompressions)")
            }
            HStack(spacing: 8) {
                metricCard(
                    label: "Avg Rate",
                    value: "\(analysis.avgRate) BPM",
                    progress: Float(analysis.avgRate - 60) / 100.0,
                    progressColor: analysis.avgRate >= 100 && analysis.avgRate <= 120 ? .green : .orange
                )
                metricCard(
                    label: "Best Streak",
                    value: formatDuration(analysis.bestStreakSec),
                    progress: analysis.durationSec > 0
                        ? Float(analysis.bestStreakSec) / Float(analysis.durationSec)
                        : 0,
                    progressColor: .blue
                )
            }
        }
    }

    @ViewBuilder
    private func metricCard(
        label: String,
        value: String,
        progress: Float? = nil,
        progressColor: Color = .green
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.title3)
                .fontWeight(.bold)
            if let progress {
                ProgressView(value: min(max(progress, 0), 1))
                    .tint(progressColor)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private func timeBreakdown(analysis: SessionAnalysis) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Time Breakdown")
                .font(.subheadline)
                .fontWeight(.bold)

            breakdownRow(
                color: .green,
                label: "In zone (100–120 BPM)",
                time: formatDuration(analysis.durationSec * Int64(analysis.inZonePct) / 100),
                pct: "\(analysis.inZonePct)%"
            )
            breakdownRow(
                color: .red,
                label: "Too fast (>120 BPM)",
                time: formatDuration(analysis.durationSec * Int64(analysis.tooFastPct) / 100),
                pct: "\(analysis.tooFastPct)%"
            )
            breakdownRow(
                color: .orange,
                label: "Too slow (<100 BPM)",
                time: formatDuration(analysis.durationSec * Int64(analysis.tooSlowPct) / 100),
                pct: "\(analysis.tooSlowPct)%"
            )
        }
        .padding()
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private func breakdownRow(color: Color, label: String, time: String, pct: String) -> some View {
        HStack {
            Circle().fill(color).frame(width: 10, height: 10)
            Text(label).font(.subheadline)
            Spacer()
            Text(time).fontWeight(.bold).font(.subheadline)
            Text(pct)
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(width: 36, alignment: .trailing)
        }
    }

    @ViewBuilder
    private func coachingTip(analysis: SessionAnalysis) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Coaching Tip")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundStyle(analysis.grade.color)
            Text(analysis.tip)
                .font(.subheadline)
                .foregroundStyle(.primary.opacity(0.85))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(analysis.grade.color.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - Analysis

struct GradeInfo {
    let letter: String
    let color: Color
    let label: String
}

struct SessionAnalysis {
    let totalCompressions: Int
    let avgRate: Int
    let inZonePct: Int
    let tooFastPct: Int
    let tooSlowPct: Int
    let durationSec: Int64
    let bestStreakSec: Int64
    let grade: GradeInfo
    let summary: String
    let tip: String

    init(dataPoints: [CprDataPoint]) {
        totalCompressions = dataPoints.count
        avgRate = dataPoints.isEmpty ? 0 : dataPoints.map(\.rate).reduce(0, +) / dataPoints.count
        let inZone = dataPoints.filter { $0.rate >= 100 && $0.rate <= 120 }.count
        inZonePct = dataPoints.isEmpty ? 0 : inZone * 100 / dataPoints.count
        let tooFast = dataPoints.filter { $0.rate > 120 }.count
        let tooSlow = dataPoints.filter { $0.rate < 100 }.count
        tooFastPct = dataPoints.isEmpty ? 0 : tooFast * 100 / dataPoints.count
        tooSlowPct = dataPoints.isEmpty ? 0 : tooSlow * 100 / dataPoints.count
        durationSec = dataPoints.count >= 2
            ? (dataPoints.last!.timestampMs - dataPoints.first!.timestampMs) / 1000 : 0
        bestStreakSec = Self.computeBestStreak(dataPoints)

        grade = switch inZonePct {
        case 80...100: GradeInfo(letter: "A", color: Color(red: 0.43, green: 0.91, blue: 0.63), label: "Excellent")
        case 65..<80: GradeInfo(letter: "B", color: Color(red: 0.52, green: 0.72, blue: 0.92), label: "Good")
        case 45..<65: GradeInfo(letter: "C", color: Color(red: 0.96, green: 0.65, blue: 0.14), label: "Needs work")
        default: GradeInfo(letter: "D", color: Color(red: 0.89, green: 0.29, blue: 0.29), label: "Keep practicing")
        }

        let drift = Self.computeDrift(dataPoints)
        let longestPause = Self.longestPause(dataPoints)

        summary = Self.generateSummary(
            grade: grade, inZonePct: inZonePct, avgRate: avgRate,
            tooFast: tooFast, tooSlow: tooSlow, total: dataPoints.count,
            drift: drift, longestPause: longestPause
        )
        tip = Self.generateTip(
            grade: grade, tooFast: tooFast, tooSlow: tooSlow,
            total: dataPoints.count, longestPause: longestPause
        )
    }

    private static func computeBestStreak(_ points: [CprDataPoint]) -> Int64 {
        guard points.count >= 2 else { return 0 }
        var bestMs: Int64 = 0
        var streakStart = points.first!.timestampMs
        var inStreak = points.first!.rate >= 100 && points.first!.rate <= 120
        for i in 1..<points.count {
            let pointInZone = points[i].rate >= 100 && points[i].rate <= 120
            if pointInZone && !inStreak {
                streakStart = points[i].timestampMs
                inStreak = true
            } else if !pointInZone && inStreak {
                bestMs = max(bestMs, points[i].timestampMs - streakStart)
                inStreak = false
            }
        }
        if inStreak { bestMs = max(bestMs, points.last!.timestampMs - streakStart) }
        return bestMs / 1000
    }

    private static func computeDrift(_ points: [CprDataPoint]) -> Int {
        guard points.count > 9 else { return 0 }
        let thirdSize = points.count / 3
        let firstAvg = points.prefix(thirdSize).map(\.rate).reduce(0, +) / thirdSize
        let lastAvg = points.suffix(thirdSize).map(\.rate).reduce(0, +) / thirdSize
        return lastAvg - firstAvg
    }

    private static func longestPause(_ points: [CprDataPoint]) -> Int64 {
        guard points.count >= 2 else { return 0 }
        var maxGap: Int64 = 0
        for i in 1..<points.count {
            maxGap = max(maxGap, points[i].timestampMs - points[i - 1].timestampMs)
        }
        return maxGap / 1000
    }

    private static func generateSummary(
        grade: GradeInfo, inZonePct: Int, avgRate: Int,
        tooFast: Int, tooSlow: Int, total: Int,
        drift: Int, longestPause: Int64
    ) -> String {
        switch grade.letter {
        case "A":
            if drift > 8 { return "Strong session — consistent rate with minor drift upward in the final minute." }
            if drift < -8 { return "Strong session — solid start with a slight slowdown toward the end." }
            return "Excellent session — consistent rate and depth throughout."
        case "B":
            if tooFast > tooSlow { return "Good session — you tend to speed up, especially under fatigue. Focus on matching the metronome." }
            if drift > 10 { return "Good session — rate drifted faster in the second half. Try to maintain a steady pace." }
            return "Good session — mostly on target with some variation. Tighten up consistency for an A."
        case "C":
            if tooFast > total / 2 { return "Needs work — compressions were mostly too fast. Slow down to match the 110 BPM target." }
            if tooSlow > total / 2 { return "Needs work — compressions were mostly too slow. Pick up the pace to reach 100 BPM." }
            if longestPause > 5 { return "Needs work — several pauses longer than 5 seconds interrupted your rhythm." }
            return "Needs work — rate was inconsistent. Focus on finding a steady rhythm and maintaining it."
        default:
            if tooFast > tooSlow && longestPause > 5 { return "Keep practicing — compressions were mostly too fast with several pauses longer than 5 seconds." }
            if tooFast > tooSlow { return "Keep practicing — compressions were too fast. Slow down significantly to reach the 100–120 target." }
            if tooSlow > tooFast { return "Keep practicing — compressions were too slow. Push faster and try to sustain the rhythm." }
            return "Keep practicing — rate was highly inconsistent. Focus on a steady, metronome-like rhythm."
        }
    }

    private static func generateTip(
        grade: GradeInfo, tooFast: Int, tooSlow: Int,
        total: Int, longestPause: Int64
    ) -> String {
        let hasPauses = longestPause > 3
        switch grade.letter {
        case "A":
            return "Excellent work. To maintain this level, practice with the metronome off occasionally to build internal timing."
        case "B":
            if tooFast > tooSlow { return "You tend to speed up under fatigue. Focus on matching the haptic metronome, especially after the first minute." }
            if hasPauses { return "Minimize pauses between compressions. If you need to rest, coordinate with a partner for seamless handoffs." }
            return "You're close to an A. Focus on tightening your rate consistency — aim for less than 5 BPM variation."
        case "C":
            if tooFast > total / 2 { return "Consciously slow down. Count \"one-and-two-and\" in your head to approximate 110 BPM. The metronome vibration is your target pace." }
            if tooSlow > total / 2 { return "Push harder and faster. Imagine pushing to the beat of \"Stayin' Alive\" by the Bee Gees — it's at 104 BPM." }
            return "Two areas to focus on: find a consistent rhythm first, then work on maintaining it. Use the metronome as your anchor."
        default:
            if tooFast > tooSlow && hasPauses { return "Two things to focus on: slow down to match the metronome, and avoid pausing between compressions." }
            if tooFast > tooSlow { return "You're compressing too aggressively. Slow down and let the metronome guide your pace — quality beats speed." }
            if hasPauses { return "Minimize interruptions. Continuous compressions are critical — every pause drops blood flow to the brain." }
            return "Start by just matching the metronome rhythm. Don't worry about depth yet — get the timing right first, then add force."
        }
    }
}
