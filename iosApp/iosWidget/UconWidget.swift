import WidgetKit
import SwiftUI

private let APP_GROUP_ID = "group.app.ucon.shared"
private let LAST_RUN_FILE = "last_run.json"

// Mirrors shared-api LastRunSummary's JSON shape.
struct UconStatusSummary: Codable {
    let ts: String
    let status: String           // "Good" | "Warn" | "Bad"
    let downMbps: Double?
    let upMbps: Double?
    let avgLatencyMs: Double?
    let lossPct: Double?
    let siteLabel: String?
}

private struct SurfacePayload: Codable {
    let latest: UconStatusSummary?
    let recent: [UconStatusSummary]
}

struct UconEntry: TimelineEntry {
    let date: Date
    let summary: UconStatusSummary?
}

struct UconProvider: TimelineProvider {
    func placeholder(in context: Context) -> UconEntry {
        UconEntry(date: Date(), summary: nil)
    }
    func getSnapshot(in context: Context, completion: @escaping (UconEntry) -> Void) {
        completion(UconEntry(date: Date(), summary: readLatest()))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<UconEntry>) -> Void) {
        let entry = UconEntry(date: Date(), summary: readLatest())
        // Refresh hint: every 15 min. Real refreshes are pushed by the main app via
        // WidgetCenter.reloadAllTimelines() after every measurement run.
        let next = Date().addingTimeInterval(15 * 60)
        completion(Timeline(entries: [entry], policy: .after(next)))
    }
}

private func readLatest() -> UconStatusSummary? {
    guard let containerUrl = FileManager.default
        .containerURL(forSecurityApplicationGroupIdentifier: APP_GROUP_ID) else { return nil }
    let url = containerUrl.appendingPathComponent(LAST_RUN_FILE)
    guard let data = try? Data(contentsOf: url),
          let payload = try? JSONDecoder().decode(SurfacePayload.self, from: data) else { return nil }
    return payload.latest
}

struct UconWidgetEntryView: View {
    var entry: UconEntry

    var body: some View {
        let s = entry.summary
        ZStack(alignment: .leading) {
            background(for: s?.status)
            VStack(alignment: .leading, spacing: 4) {
                Text(headerLine(s))
                    .font(.headline)
                    .foregroundColor(.white)
                Text(subLine(s))
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.85))
            }
            .padding(12)
        }
    }

    private func headerLine(_ s: UconStatusSummary?) -> String {
        guard let s = s else { return "uConnectivity" }
        let pill = pillFor(s.status)
        let down = s.downMbps.map { String(format: "%.0f", $0) } ?? "—"
        let up = s.upMbps.map { String(format: "%.0f", $0) } ?? "—"
        return "\(pill) \(down)↓ / \(up)↑ Mbps"
    }
    private func subLine(_ s: UconStatusSummary?) -> String {
        guard let s = s else { return "tap to set up" }
        let lat = s.avgLatencyMs.map { String(format: "%.0f ms", $0) } ?? "—"
        let loss = s.lossPct.map { String(format: "%.1f%%", $0) } ?? "—"
        return "lat \(lat) · loss \(loss)"
    }
    private func pillFor(_ status: String) -> String {
        switch status {
        case "Good": return "✓"
        case "Warn": return "!"
        case "Bad": return "✕"
        default: return "·"
        }
    }
    private func background(for status: String?) -> some View {
        let color: Color = {
            switch status {
            case "Good": return Color(red: 0.11, green: 0.37, blue: 0.13)
            case "Warn": return Color(red: 0.96, green: 0.50, blue: 0.09)
            case "Bad":  return Color(red: 0.72, green: 0.11, blue: 0.11)
            default: return Color(white: 0.26)
            }
        }()
        return color
    }
}

struct UconWidget: Widget {
    let kind = "app.ucon.widget"
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: UconProvider()) { entry in
            UconWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("uConnectivity")
        .description("Latest connectivity test result.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@main
struct UconWidgetBundle: WidgetBundle {
    var body: some Widget {
        UconWidget()
    }
}
