import SwiftUI

@main
struct CPRCoachApp: App {
    @StateObject private var receiver = PhoneSessionReceiver()

    var body: some Scene {
        WindowGroup {
            PhoneRootView()
                .environmentObject(receiver)
        }
    }
}
