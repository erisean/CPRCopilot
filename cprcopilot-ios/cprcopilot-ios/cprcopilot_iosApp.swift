import SwiftUI

@main
struct cprcopilot_iosApp: App {
    @StateObject private var receiver = PhoneSessionReceiver()

    var body: some Scene {
        WindowGroup {
            PhoneRootView()
                .environmentObject(receiver)
        }
    }
}
