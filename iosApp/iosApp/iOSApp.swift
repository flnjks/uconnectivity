import SwiftUI
import BackgroundTasks
import WidgetKit
import ComposeApp

let REFRESH_TASK_ID = "app.ucon.refresh"

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ComposeRoot()
                .ignoresSafeArea()
                .onAppear { scheduleNextRefresh() }
        }
    }
}

struct ComposeRoot: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // `MainViewController()` is generated from composeApp's iosMain.
        return MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Kotlin/Native has no WidgetKit cinterop — the SurfaceBridge writes JSON
        // into the App Group container and then calls back into Swift to ask the
        // WidgetCenter to refresh.
        AppContainer_iosKt.widgetReloader = {
            WidgetCenter.shared.reloadAllTimelines()
        }

        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: REFRESH_TASK_ID,
            using: nil
        ) { task in
            handleBackgroundRefresh(task: task as! BGAppRefreshTask)
        }
        return true
    }
}

func scheduleNextRefresh() {
    let req = BGAppRefreshTaskRequest(identifier: REFRESH_TASK_ID)
    // iOS coalesces — this is a hint, not a guarantee.
    req.earliestBeginDate = Date(timeIntervalSinceNow: 55 * 60) // ~hourly
    do {
        try BGTaskScheduler.shared.submit(req)
    } catch {
        NSLog("uConnectivity: failed to schedule refresh: \(error)")
    }
}

func handleBackgroundRefresh(task: BGAppRefreshTask) {
    scheduleNextRefresh()

    task.expirationHandler = {
        task.setTaskCompleted(success: false)
    }

    // Fire-and-forget trigger; the shared viewmodel persists locally and uploads
    // asynchronously via UploadQueue, so this is fine for BG windows.
    MainViewControllerKt.runBackgroundMeasurement()
    task.setTaskCompleted(success: true)
}
