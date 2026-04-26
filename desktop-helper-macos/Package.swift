// swift-tools-version:6.0
import PackageDescription

let package = Package(
    name: "UconStatusBar",
    platforms: [.macOS(.v13)],
    products: [
        .executable(name: "uconnectivity-statusbar", targets: ["UconStatusBar"]),
    ],
    targets: [
        .executableTarget(
            name: "UconStatusBar",
            path: "Sources/UconStatusBar",
            swiftSettings: [
                .swiftLanguageMode(.v5),
            ]
        ),
    ]
)
