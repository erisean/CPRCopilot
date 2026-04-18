import SwiftUI

struct PhoneRootView: View {
    @EnvironmentObject var receiver: PhoneSessionReceiver
    @State private var showDebug = false

    var body: some View {
        NavigationStack {
            Group {
                if showDebug {
                    RawDataDebugView()
                } else if receiver.currentSession != nil {
                    LiveSessionView()
                } else if receiver.completedSession != nil {
                    ScorecardView()
                } else {
                    IdleView()
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(showDebug ? "Dashboard" : "Raw Data") {
                        showDebug.toggle()
                    }
                    .font(.caption)
                }
            }
        }
    }
}
