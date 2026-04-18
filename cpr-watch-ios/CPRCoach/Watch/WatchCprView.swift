import SwiftUI

struct WatchCprView: View {
    @StateObject private var viewModel = WatchCprViewModel()

    var body: some View {
        if viewModel.isActive {
            activeView
        } else {
            idleView
        }
    }

    private var idleView: some View {
        VStack(spacing: 16) {
            Text("CPR")
                .font(.title2)
                .fontWeight(.bold)
            Text("Coach")
                .font(.caption)
                .foregroundStyle(.secondary)

            Button(action: { viewModel.startSession() }) {
                Text("GO")
                    .font(.headline)
                    .fontWeight(.bold)
            }
            .buttonStyle(.borderedProminent)
            .tint(.red)
            .clipShape(Circle())
            .frame(width: 64, height: 64)
        }
    }

    private var activeView: some View {
        VStack(spacing: 4) {
            Text("\(viewModel.rate)")
                .font(.system(size: 48, weight: .bold, design: .rounded))
                .foregroundStyle(feedbackColor)

            Text("BPM")
                .font(.caption2)
                .foregroundStyle(.secondary)

            if viewModel.depthCm > 0 {
                Text(String(format: "%.1f cm", viewModel.depthCm))
                    .font(.caption)
            }

            Text(viewModel.feedbackMessage)
                .font(.caption)
                .fontWeight(.bold)
                .foregroundStyle(feedbackColor)
                .multilineTextAlignment(.center)

            Button(action: { viewModel.stopSession() }) {
                Image(systemName: "stop.fill")
                    .font(.caption)
            }
            .buttonStyle(.bordered)
            .tint(.gray)
        }
    }

    private var feedbackColor: Color {
        switch viewModel.feedback {
        case .good: .green
        case .idle: .gray
        case .tooSlow, .tooShallow: .orange
        case .tooFast, .tooDeep: .red
        }
    }
}
