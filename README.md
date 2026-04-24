# uConnectivity

A cross-platform connectivity logger. Each install runs periodic network tests (reachability, latency/jitter, packet-loss, download/upload Mbps), stores results locally, and POSTs them to a central REST ingest.

Targets: **Android**, **iOS**, **macOS desktop**, **Windows desktop**.

Built as a Kotlin Multiplatform + Compose Multiplatform monorepo with a Ktor ingest server that shares wire types with the clients.

## Layout

```
uconnectivity/
├── shared-api/       wire types shared by client and server (pure Kotlin MP)
├── server/           Ktor ingest (JVM) + SQLite + admin CLI
├── composeApp/       shared client: commonMain / androidMain / iosMain / desktopMain
└── iosApp/           Xcode wrapper around composeApp's iosMain framework
```

## Prereqs

- JDK 21+
- Android SDK (Android target) — set `ANDROID_HOME`
- Xcode (iOS target)
- Docker (server deploy)

## Build

```sh
# Server (JVM only — needs no mobile SDK)
./gradlew :server:run

# Desktop app
./gradlew :composeApp:run

# Android debug APK
./gradlew :composeApp:assembleDebug

# iOS — open iosApp/iosApp.xcodeproj in Xcode and Run
```

## Provision a site

```sh
./gradlew :server:run --args="admin add-site smoke-test"
# prints the site's bearer token — paste into the app's Settings screen
```

## Verifying

- Unit tests: `./gradlew :composeApp:jvmTest :server:test`
- Offline resilience: kill the server container, trigger manual runs on a client, bring it back; queue drains in order.
- iOS hourly is best-effort — OS coalesces background tasks based on battery/usage.

## Plan

See `/Users/jamie/.claude/plans/i-want-a-cross-shiny-bachman.md` for the full design doc.
