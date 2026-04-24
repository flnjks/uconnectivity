package app.ucon.ios

import androidx.compose.ui.window.ComposeUIViewController
import app.ucon.ui.App
import platform.UIKit.UIViewController

/**
 * Factory invoked from Swift (iosApp) to mount the Compose UI inside a UIViewController.
 *
 * Usage in Swift:
 *   let vc = MainViewControllerKt.MainViewController()
 *   window?.rootViewController = vc
 */
@Suppress("unused")
fun MainViewController(): UIViewController = ComposeUIViewController {
    App(AppContainer.viewModel)
}

/**
 * Called from the Swift `AppDelegate` when a BGAppRefreshTask fires. Kicks off a
 * measurement; the task must call `setTaskCompleted(success:)` itself after awaiting.
 */
@Suppress("unused")
fun runBackgroundMeasurement() {
    AppContainer.viewModel.runNow()
}
