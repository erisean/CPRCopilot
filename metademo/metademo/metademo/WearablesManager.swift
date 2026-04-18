import SwiftUI
import Combine
import MWDATCore
import MWDATCamera

@Observable
final class WearablesManager {
    enum ConnectionState {
        case disconnected
        case registering
        case registered
        case deviceFound
        case streaming
        case error(String)
    }

    private(set) var connectionState: ConnectionState = .disconnected
    private(set) var devices: [Device] = []
    private(set) var currentFrame: UIImage?
    private(set) var registrationState: RegistrationState?

    private var streamSession: StreamSession?
    private var cancellables = Set<AnyCancellable>()

    func configure() {
        do {
            try Wearables.configure()
        } catch {
            connectionState = .error("SDK configuration failed: \(error.localizedDescription)")
        }
    }

    func handleUrl(_ url: URL) {
        Wearables.shared.handleUrl(url)
    }

    func startRegistration() {
        connectionState = .registering
        Wearables.shared.startRegistration()
        observeRegistrationState()
    }

    func unregister() {
        stopStreaming()
        Wearables.shared.startUnregistration()
        connectionState = .disconnected
        devices = []
        registrationState = nil
    }

    func requestCameraPermission() async {
        do {
            let status = try await Wearables.shared.requestPermission(.camera)
            if status == .granted {
                observeDevices()
            }
        } catch {
            connectionState = .error("Camera permission failed: \(error.localizedDescription)")
        }
    }

    func startStreaming() {
        guard !devices.isEmpty else { return }

        let config = StreamSessionConfig(
            videoCodec: .raw,
            resolution: .low,
            frameRate: 24
        )
        let deviceSelector = AutoDeviceSelector(wearables: Wearables.shared)
        let session = StreamSession(
            streamSessionConfig: config,
            deviceSelector: deviceSelector
        )

        session.videoFramePublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] frame in
                self?.currentFrame = frame.toUIImage()
            }
            .store(in: &cancellables)

        session.statePublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] state in
                if case .streaming = state {
                    self?.connectionState = .streaming
                }
            }
            .store(in: &cancellables)

        streamSession = session
        connectionState = .streaming
    }

    func stopStreaming() {
        streamSession = nil
        cancellables.removeAll()
        currentFrame = nil
        if case .streaming = connectionState {
            connectionState = devices.isEmpty ? .registered : .deviceFound
        }
    }

    func capturePhoto(completion: @escaping (Data?) -> Void) {
        guard let session = streamSession else {
            completion(nil)
            return
        }

        session.photoDataPublisher
            .first()
            .receive(on: DispatchQueue.main)
            .sink { photoData in
                completion(photoData.data)
            }
            .store(in: &cancellables)

        session.capturePhoto(format: .jpeg)
    }

    private func observeRegistrationState() {
        Wearables.shared.registrationStateStream()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] state in
                self?.registrationState = state
                switch state {
                case .registered:
                    self?.connectionState = .registered
                    self?.observeDevices()
                case .unregistered:
                    self?.connectionState = .disconnected
                default:
                    break
                }
            }
            .store(in: &cancellables)
    }

    private func observeDevices() {
        Wearables.shared.devicesStream()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] devices in
                self?.devices = devices
                if !devices.isEmpty {
                    self?.connectionState = .deviceFound
                }
            }
            .store(in: &cancellables)
    }
}

private extension VideoFrame {
    func toUIImage() -> UIImage? {
        guard let cgImage = cgImage else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
