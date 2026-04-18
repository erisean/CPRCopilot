import SwiftUI

struct ContentView: View {
    @Environment(WearablesManager.self) private var manager

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                statusSection
                actionSection
                if manager.currentFrame != nil {
                    cameraPreview
                }
            }
            .padding()
            .navigationTitle("Meta Glasses")
        }
    }

    private var statusSection: some View {
        VStack(spacing: 8) {
            Image(systemName: statusIcon)
                .font(.system(size: 48))
                .foregroundStyle(statusColor)

            Text(statusText)
                .font(.headline)

            if !manager.devices.isEmpty {
                Text("\(manager.devices.count) device(s) found")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    private var actionSection: some View {
        VStack(spacing: 12) {
            switch manager.connectionState {
            case .disconnected:
                Button("Connect Glasses") {
                    manager.startRegistration()
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)

            case .registering:
                ProgressView("Registering...")

            case .registered:
                Button("Grant Camera Access") {
                    Task {
                        await manager.requestCameraPermission()
                    }
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)

            case .deviceFound:
                Button("Start Camera Stream") {
                    manager.startStreaming()
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)

            case .streaming:
                HStack(spacing: 16) {
                    Button("Capture Photo") {
                        manager.capturePhoto { data in
                            if let data, let image = UIImage(data: data) {
                                UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
                            }
                        }
                    }
                    .buttonStyle(.borderedProminent)

                    Button("Stop") {
                        manager.stopStreaming()
                    }
                    .buttonStyle(.bordered)
                    .tint(.red)
                }

            case .error(let message):
                Text(message)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)

                Button("Retry") {
                    manager.startRegistration()
                }
                .buttonStyle(.borderedProminent)
            }

            if case .disconnected = manager.connectionState {} else {
                Button("Disconnect") {
                    manager.unregister()
                }
                .font(.caption)
                .foregroundStyle(.red)
            }
        }
    }

    @ViewBuilder
    private var cameraPreview: some View {
        if let frame = manager.currentFrame {
            Image(uiImage: frame)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    private var statusIcon: String {
        switch manager.connectionState {
        case .disconnected: "eyeglasses"
        case .registering: "arrow.triangle.2.circlepath"
        case .registered: "checkmark.circle"
        case .deviceFound: "eye"
        case .streaming: "video.fill"
        case .error: "exclamationmark.triangle"
        }
    }

    private var statusColor: Color {
        switch manager.connectionState {
        case .disconnected: .secondary
        case .registering: .orange
        case .registered: .blue
        case .deviceFound: .green
        case .streaming: .green
        case .error: .red
        }
    }

    private var statusText: String {
        switch manager.connectionState {
        case .disconnected: "Not Connected"
        case .registering: "Registering..."
        case .registered: "Registered"
        case .deviceFound: "Device Ready"
        case .streaming: "Streaming"
        case .error: "Error"
        }
    }
}

#Preview {
    ContentView()
        .environment(WearablesManager())
}
