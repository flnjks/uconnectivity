import AppKit
import Foundation

// MARK: — IPC payloads

struct UpdateMessage: Decodable {
    let title: String
    let menu: [MenuItem]
}

struct MenuItem: Decodable {
    let id: String          // identifier the parent uses to know which item fired
    let label: String
    let enabled: Bool
    let separatorBefore: Bool?
    let submenu: [MenuItem]?
}

private struct EventMessage: Encodable {
    let event: String
    let id: String
}

// MARK: — Status bar controller

@MainActor
final class StatusBarController {
    private let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
    private let menu = NSMenu()
    private let stdoutLock = NSLock()

    init() {
        item.button?.title = "uConnectivity"
        item.menu = menu
    }

    func apply(_ message: UpdateMessage) {
        item.button?.title = message.title
        menu.removeAllItems()
        for entry in message.menu {
            menu.addItem(buildItem(entry))
        }
    }

    private func buildItem(_ entry: MenuItem) -> NSMenuItem {
        if entry.separatorBefore == true && !menu.items.isEmpty {
            menu.addItem(.separator())
        }
        let nsItem = NSMenuItem(title: entry.label, action: nil, keyEquivalent: "")
        nsItem.isEnabled = entry.enabled
        nsItem.representedObject = entry.id
        if let sub = entry.submenu, !sub.isEmpty {
            let subMenu = NSMenu()
            for s in sub {
                let sItem = NSMenuItem(title: s.label, action: nil, keyEquivalent: "")
                sItem.isEnabled = s.enabled
                sItem.representedObject = s.id
                if s.enabled {
                    sItem.target = MenuTarget.shared
                    sItem.action = #selector(MenuTarget.menuItemFired(_:))
                }
                subMenu.addItem(sItem)
            }
            nsItem.submenu = subMenu
        } else if entry.enabled {
            nsItem.target = MenuTarget.shared
            nsItem.action = #selector(MenuTarget.menuItemFired(_:))
        }
        return nsItem
    }
}

// MARK: — Menu click forwarder

@MainActor
final class MenuTarget: NSObject {
    static let shared = MenuTarget()

    @objc func menuItemFired(_ sender: NSMenuItem) {
        let id = (sender.representedObject as? String) ?? sender.title
        emitEvent(.init(event: "click", id: id))
    }
}

// MARK: — IPC

private let stdoutLock = NSLock()
private let encoder = JSONEncoder()

private func emitEvent(_ event: EventMessage) {
    do {
        let data = try encoder.encode(event)
        stdoutLock.lock()
        defer { stdoutLock.unlock() }
        FileHandle.standardOutput.write(data)
        FileHandle.standardOutput.write("\n".data(using: .utf8)!)
        try? FileHandle.standardOutput.synchronize()
    } catch {
        // Ignore — best-effort.
    }
}

// MARK: — Main

@main
@MainActor
struct UconStatusBarApp {
    static func main() {
        let app = NSApplication.shared
        app.setActivationPolicy(.accessory)   // headless / status-bar only

        let controller = StatusBarController()

        // Read line-delimited JSON from stdin on a background queue; dispatch
        // updates to the main actor.
        DispatchQueue.global(qos: .utility).async {
            let stdin = FileHandle.standardInput
            var buffer = Data()
            while true {
                let chunk = stdin.availableData
                if chunk.isEmpty {
                    // EOF — parent died; exit so we don't linger as a zombie.
                    DispatchQueue.main.async { NSApplication.shared.terminate(nil) }
                    return
                }
                buffer.append(chunk)
                while let nl = buffer.firstIndex(of: 0x0a) {
                    let line = buffer.prefix(upTo: nl)
                    buffer = buffer.suffix(from: buffer.index(after: nl))
                    do {
                        let msg = try JSONDecoder().decode(UpdateMessage.self, from: line)
                        DispatchQueue.main.async {
                            MainActor.assumeIsolated { controller.apply(msg) }
                        }
                    } catch {
                        // Skip malformed lines.
                    }
                }
            }
        }

        app.run()
    }
}
