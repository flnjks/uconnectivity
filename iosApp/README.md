# iosApp

Xcode project wrapping the `composeApp` iOS framework.

## Setup (first time on a dev machine)

1. Ensure Xcode 16+ is installed and selected: `sudo xcode-select --switch /Applications/Xcode.app`.
2. From the repo root, build the shared framework so it is produced for linking:
   ```sh
   ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
   ```
3. Open this directory in Xcode (`xed .` from `iosApp/`), create an `iosApp.xcodeproj` using the
   "App" template, iOS target, SwiftUI lifecycle, and add:
   - The `iOSApp.swift` and `Info.plist` already present in `iosApp/iosApp/`.
   - A "Framework Search Paths" build setting pointing at the produced
     `composeApp/build/bin/iosSimulatorArm64/debugFramework` (or the arch the simulator needs).
   - The `ComposeApp.framework` as an embedded framework in Build Phases.
4. Under Signing & Capabilities add **Background Modes** → check `Background fetch`
   and `Background processing`. The Info.plist already lists the BGTask identifier
   `app.ucon.refresh`.

## Running background tasks on the simulator

While paused in the debugger:

```
e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"app.ucon.refresh"]
```

Resume; the app's registered handler fires and runs a measurement.
