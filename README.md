# uConnectivity

A connectivity sensor that lives at a site and reports up. Each install runs periodic network tests (reachability, latency/jitter, packet-loss, download/upload Mbps), keeps a local history, and POSTs each run to a central REST ingest. The user-facing surface is deliberately minimal — a menu bar item on macOS, a tray icon on Windows, and a home-screen widget on Android and iOS. The full app is just a Settings screen.

```
72↓ 85↑ Mbps        ← what you see in the macOS menu bar
```

Built as a Kotlin Multiplatform + Compose Multiplatform monorepo with a Ktor ingest server that shares wire types with the clients, so the contract can't silently drift.

## Surfaces

| Platform | Where it lives | What it shows |
|---|---|---|
| **macOS** | menu bar (NSStatusItem text via bundled Swift helper) | down/up Mbps + recent runs submenu |
| **Windows** | system tray icon (Compose Desktop Tray) | down/up Mbps in tooltip + menu, recent runs submenu |
| **Android** | home-screen widget (Jetpack Glance) | down/up Mbps + latency, status-tinted background |
| **iOS** | home-screen widget (WidgetKit) | down/up Mbps + latency, status-tinted background |

Tap the widget on mobile or "Settings…" from the desktop menu to enter the site ID, ingest URL, and bearer token. Everything else is automatic.

## Layout

```
uconnectivity/
├── shared-api/                  wire types shared by client and server (KMP, no Compose)
├── shared/                      measurement engine, SQLDelight history, upload queue,
│                                settings + secure stores, SurfaceBridge expect/actual
├── composeApp/                  KMP + Compose Multiplatform
│   ├── commonMain/              Settings screen + AppViewModel
│   ├── androidMain/             Application + WorkManager + Glance widget
│   ├── iosMain/                 Compose entry + AppContainer
│   └── jvmMain/                 desktop entry + Mac/Windows tray drivers
├── desktop-helper-macos/        Swift Package — the NSStatusItem helper
│                                spawned as a child process by the JVM
├── iosApp/                      Xcode wrapper around composeApp's iosMain framework
│                                + WidgetKit extension target
└── server/                      Ktor + Exposed + SQLite + admin CLI + Dockerfile
```

The shared modules own the measurement engine, the local history DB, the upload queue, and the settings/secure stores. Platform code only owns the periodic-background trigger, secure credential storage, and the surface (menu bar / tray / widget).

## Architecture in one diagram

```
                   ┌──────────────────────────┐
   periodic        │ MeasurementRun           │
   trigger ──────▶ │   reachability + latency │
   (WorkManager,   │   + jitter + loss        │
    BGTaskSched,   │   + speed (down/up)      │
    timer)         └────────────┬─────────────┘
                                ▼
                   ┌──────────────────────────┐
                   │ RunRepository (SQLDelight) │  ◀── observed by AppViewModel
                   └────────────┬─────────────┘
                                ├──▶ UploadQueue ─POST/v1/stats──▶ Ktor server
                                │
                                └──▶ SurfaceBridge
                                          │
                  ┌───────────────────────┼─────────────────────────┐
                  ▼                       ▼                         ▼
        Mac NSStatusItem       Win Compose Tray            Glance / WidgetKit
        (Swift helper child    (in-process)                (push reload after
         process, JSON IPC)                                 each run)
```

## Prereqs

- JDK 21+ (the build will auto-download a 21 toolchain via Foojay if missing)
- Android SDK with platform 35 + build-tools 35.0.x (for Android target) — set `ANDROID_HOME` or create `local.properties` with `sdk.dir=…`
- Xcode 16+ + an iPhone simulator (for iOS target)
- Swift toolchain 6.x (ships with Xcode — needed for the macOS menu-bar helper)
- Docker (optional — only for `server/` deploys)

## Quickstart

```sh
# 1. Bring up the ingest server
./gradlew :server:run

# 2. Provision a site and capture its bearer token
./gradlew :server:run --args="admin add-site office-1"
# → site_id=office-1-xxxxxx
# → token=ucon_…

# 3. Run a client (any of:)
./gradlew :composeApp:run                                    # desktop
./gradlew :composeApp:installDebug                           # Android (device/emulator)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \  # iOS
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=latest' \
  CODE_SIGN_IDENTITY="-" build

# 4. Open Settings on the client, paste the URL + token, hit Save.
#    First measurement appears within seconds; thereafter it runs hourly.
```

If you skip step 1, the client still works — speed tests fall back to `speed.cloudflare.com` (no auth), reachability/latency hits `1.1.1.1`, `8.8.8.8`, and `dns.google` over HTTPS HEAD. The upload queue just stays full of `PENDING` rows until you point it at a real ingest.

## Building

```sh
./gradlew :server:test                            # server tests (Ktor testApplication + round-trip)
./gradlew :shared:jvmTest                         # measurement-engine unit tests
./gradlew :composeApp:jvmJar                      # desktop JAR
./gradlew :composeApp:assembleDebug               # Android debug APK
./gradlew :composeApp:bundleDebug                 # Android debug AAB
./gradlew :composeApp:assembleRelease             # Android release APK (unsigned)
./gradlew :composeApp:compileKotlinIosX64         # iOS sim slice (compile only)
./gradlew :composeApp:packageDmg                  # macOS DMG (bundles the Swift helper)
./gradlew :composeApp:packageMsi                  # Windows MSI
./gradlew :composeApp:packageDeb                  # Linux DEB
```

## Settings

Each install needs:

- **Site ID** — a stable identifier per location. The server uses this to bucket runs.
- **Server base URL** — e.g. `https://ucon.example.com` or `http://localhost:8080`.
- **Bearer token** — printed once when you provision the site via `admin add-site`. Stored in:
  - macOS: file in `~/Library/Application Support/uConnectivity/` (TODO: Keychain)
  - Windows: file in `%APPDATA%/uConnectivity/` (TODO: DPAPI)
  - Android: `EncryptedSharedPreferences` (AES-256)
  - iOS: `NSUserDefaults` (TODO: Keychain)
- **Interval** — minutes between background runs. Floor of 5 minutes; iOS coalesces and won't honour anything tighter than ~15 min in practice anyway.
- **Speed test toggle** — defaults on. ~10 MB / hour at the default 1 MB payload.

## Status thresholds

The status pill (used as background tint on the mobile widgets and as the colored dot in the in-app card) follows fixed rules in `LastRunSummary.statusOf()`:

| State | Trigger |
|---|---|
| Good (green) | latency < 150 ms **and** loss < 2 % |
| Warn (amber) | latency 150–500 ms **or** loss 2–10 % |
| Bad (red) | latency ≥ 500 ms **or** loss ≥ 10 % |

The macOS menu bar / Windows tray header line shows just the numbers — `72↓ 85↑ Mbps` — without a status glyph; details are one click away.

## Server API

| Method | Path | Auth | Body |
|---|---|---|---|
| `GET` | `/healthz` | none | — |
| `POST` | `/v1/stats` | bearer | `RunReport` JSON |
| `GET` | `/v1/sites/{siteId}/stats?limit=` | bearer | — |
| `GET` | `/v1/speedtest/download?bytes=N` | bearer | streamed random bytes (≤ 25 MB) |
| `POST` | `/v1/speedtest/upload` | bearer | arbitrary body (≤ 25 MB) |

Tokens are stored hashed (Argon2id). Provisioning is a one-shot CLI: `./gradlew :server:run --args="admin add-site <label>"`.

## Known constraints

- **iOS hourly runs are best-effort.** `BGTaskScheduler` coalesces tasks based on battery, charging state, and app usage. The configured interval is a hint, not a guarantee. This is identical under any cross-platform framework.
- **Desktop background story is "tray + autostart", not a true service.** Works as long as the user is logged in. Upgrading to a launchd LaunchAgent / Windows Service is a follow-up.
- **macOS Swift helper isn't code-signed by default.** Ad-hoc signing works for development; a real DMG release needs the helper signed under the same Developer ID as the parent app or Gatekeeper will block it.
- **iOS App Group needs a paid Apple Developer account on real devices.** The simulator works without (verified).
- **Speed test against your own server costs bandwidth.** 5 MB × N clients × hourly adds up fast — budget accordingly, or leave the Cloudflare fallback enabled.

## Code-signing the macOS DMG

Local builds produce an unsigned DMG that works fine for development. For a release that passes Gatekeeper, set these before `./gradlew :composeApp:packageDmg`:

| Variable | Purpose |
|---|---|
| `MACOS_SIGN_IDENTITY` | e.g. `"Developer ID Application: Jane Doe (ABCD12EFGH)"` |
| `MACOS_SIGN_TEAM_ID` | the 10-char team id, e.g. `ABCD12EFGH` |
| `MACOS_NOTARIZATION_USER` | Apple ID email used for notarization |
| `MACOS_NOTARIZATION_PASS` | app-specific password from appleid.apple.com |

When `MACOS_SIGN_IDENTITY` is set, the build also signs the bundled `uconnectivity-statusbar` helper with the same identity using `composeApp/entitlements/macos-helper.entitlements` (a minimal hardened-runtime profile). The main app is signed with the JIT/library-validation entitlements that Skiko + the helper-spawn workflow require — see `composeApp/entitlements/macos.entitlements`.

## Roadmap

- [x] iOS bearer token in Keychain (Security.framework via Swift bridge).
- [x] Desktop bearer token in macOS Keychain (`security` CLI) / Windows DPAPI (JNA).
- [x] Code-sign + notarize config for the macOS DMG (env-var driven; bundled Swift helper signed too).
- [ ] Auto-start at login on desktop (launchd plist on macOS, Startup shortcut on Windows).
- [ ] Server-side ops dashboard (currently you'd query SQLite directly).
- [ ] Admin endpoint to revoke / rotate site tokens without restarting the server.

## Verification

- `./gradlew :server:test :shared:jvmTest` — should always pass.
- Round-trip smoke: `./gradlew :server:run`, provision a site, paste URL + token in Settings, tap "Run test now", `curl -H 'Authorization: Bearer …' http://localhost:8080/v1/sites/<id>/stats?limit=5` confirms the run arrived.
- Offline resilience: kill the server (or block its port), trigger three runs from a client, bring the server back; queue drains in order with exponential backoff (1s / 5s / 30s / 5m / 30m cap).

## License

MIT. See `LICENSE`.
