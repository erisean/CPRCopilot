import SwiftUI

struct RateChartView: View {
    let dataPoints: [CprDataPoint]
    var annotate: Bool = false

    private let minValue: Float = 60
    private let maxValue: Float = 160
    private let bandLow: Float = 100
    private let bandHigh: Float = 120

    var body: some View {
        Canvas { context, size in
            guard !dataPoints.isEmpty else { return }

            let range = maxValue - minValue
            let bandTopY = size.height * CGFloat(1 - (bandHigh - minValue) / range)
            let bandBottomY = size.height * CGFloat(1 - (bandLow - minValue) / range)

            // Target zone
            context.fill(
                Path(CGRect(
                    x: 0, y: bandTopY,
                    width: size.width, height: bandBottomY - bandTopY
                )),
                with: .color(.green.opacity(0.1))
            )

            // Band lines
            drawDashedLine(in: &context, y: bandTopY, width: size.width)
            drawDashedLine(in: &context, y: bandBottomY, width: size.width)

            guard dataPoints.count >= 2 else { return }

            let xStep = size.width / CGFloat(max(dataPoints.count - 1, 1))

            // Line
            var path = Path()
            for (i, point) in dataPoints.enumerated() {
                let value = CGFloat(min(max(Float(point.rate), minValue), maxValue))
                let x = CGFloat(i) * xStep
                let y = size.height * (1 - CGFloat((value - CGFloat(minValue)) / CGFloat(range)))
                if i == 0 { path.move(to: CGPoint(x: x, y: y)) }
                else { path.addLine(to: CGPoint(x: x, y: y)) }
            }
            context.stroke(path, with: .color(Color(red: 0.26, green: 0.65, blue: 0.96)), lineWidth: 2.5)

            // Peak annotations
            if annotate {
                let peaks = findPeaks(dataPoints)
                for peakIdx in peaks {
                    let point = dataPoints[peakIdx]
                    let value = CGFloat(min(max(Float(point.rate), minValue), maxValue))
                    let x = CGFloat(peakIdx) * xStep
                    let y = size.height * (1 - CGFloat((value - CGFloat(minValue)) / CGFloat(range)))

                    context.fill(Circle().path(in: CGRect(x: x - 4, y: y - 4, width: 8, height: 8)),
                                 with: .color(.red))

                    context.draw(
                        Text("\(point.rate)").font(.system(size: 9, weight: .bold)).foregroundStyle(.red),
                        at: CGPoint(x: x, y: y - 12)
                    )
                }
            }
        }
    }

    private func drawDashedLine(in context: inout GraphicsContext, y: CGFloat, width: CGFloat) {
        var path = Path()
        var x: CGFloat = 0
        while x < width {
            path.move(to: CGPoint(x: x, y: y))
            path.addLine(to: CGPoint(x: min(x + 6, width), y: y))
            x += 10
        }
        context.stroke(path, with: .color(.green.opacity(0.3)), lineWidth: 1)
    }

    private func findPeaks(_ points: [CprDataPoint]) -> [Int] {
        guard points.count > 2 else { return [] }
        var peaks: [Int] = []
        for i in 1..<(points.count - 1) {
            let prev = points[i - 1].rate
            let curr = points[i].rate
            let next = points[i + 1].rate
            if curr > prev && curr > next && (curr > 120 || curr < 100) {
                if peaks.isEmpty || i - peaks.last! > points.count / 10 {
                    peaks.append(i)
                }
            }
        }
        return Array(peaks.prefix(5))
    }
}
