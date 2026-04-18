import SwiftUI
import MWDATCore

@main
struct metademoApp: App {
    @State private var wearablesManager = WearablesManager()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(wearablesManager)
                .onOpenURL { url in
                    wearablesManager.handleUrl(url)
                }
                .onAppear {
                    wearablesManager.configure()
                }
        }
    }
}
