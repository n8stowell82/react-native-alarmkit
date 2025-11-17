import Foundation
import AlarmKit
import SwiftUI

// Metadata for alarms
nonisolated struct BasicAlarmMetadata: AlarmMetadata {
    // Empty metadata for basic alarms
}

@available(iOS 26.0, *)
class AlarmKitManager {

    weak var delegate: AlarmDelegate?
    private let manager = AlarmManager.shared
    private var alarmMetadataStore: [String: [String: Any]] = [:]

    init() {
        // Start monitoring alarms
        Task {
            await monitorAlarms()
        }
    }

    // MARK: - Authorization

    func checkAuthorization() async -> String {
        switch manager.authorizationState {
        case .notDetermined:
            return "notDetermined"
        case .authorized:
            return "authorized"
        case .denied:
            return "denied"
        @unknown default:
            return "notDetermined"
        }
    }

    func requestPermission() async throws -> Bool {
        let state = try await manager.requestAuthorization()
        return state == .authorized
    }

    // MARK: - Scheduling

    func scheduleAlarm(schedule: NSDictionary, config: NSDictionary) async throws -> [String: Any] {
        let alarmId = schedule["id"] as? String ?? UUID().uuidString
        let type = schedule["type"] as? String ?? "fixed"

        // Store metadata
        alarmMetadataStore[alarmId] = [
            "schedule": schedule,
            "config": config
        ]

        let attributes = buildAlarmAttributes(config: config)
        let duration = calculateDuration(schedule: schedule)

        // Schedule alarm using timer configuration
        let uuid = UUID(uuidString: alarmId) ?? UUID()
        let alarm = try await manager.schedule(
            id: uuid,
            configuration: .timer(
                duration: duration,
                attributes: attributes
            )
        )

        let nextFireDate = Date.now.addingTimeInterval(duration)

        return [
            "id": alarmId,
            "schedule": schedule,
            "config": config,
            "nextFireDate": ISO8601DateFormatter().string(from: nextFireDate),
            "capability": "native_alarms",
            "isActive": true,
            "platformAlarmId": uuid.uuidString
        ]
    }

    func cancelAlarm(id: String) async throws {
        guard let uuid = UUID(uuidString: id) else {
            throw NSError(
                domain: "AlarmKitManager",
                code: 400,
                userInfo: [NSLocalizedDescriptionKey: "Invalid alarm ID"]
            )
        }

        try await manager.cancel(id: uuid)
        alarmMetadataStore.removeValue(forKey: id)
    }

    func cancelAllAlarms() async throws {
        let alarms = try await getAllAlarms()

        for alarm in alarms {
            if let id = alarm["id"] as? String {
                try await cancelAlarm(id: id)
            }
        }
    }

    func cancelAlarmsByCategory(category: String) async throws {
        let alarms = try await getAlarmsByCategory(category: category)

        for alarm in alarms {
            if let id = alarm["id"] as? String {
                try await cancelAlarm(id: id)
            }
        }
    }

    // MARK: - Query

    func getAlarm(id: String) async throws -> [String: Any]? {
        guard let metadata = alarmMetadataStore[id] else {
            return nil
        }

        guard let schedule = metadata["schedule"] as? NSDictionary,
              let config = metadata["config"] as? NSDictionary else {
            return nil
        }

        let duration = calculateDuration(schedule: schedule)
        let nextFireDate = Date.now.addingTimeInterval(duration)

        return [
            "id": id,
            "schedule": schedule,
            "config": config,
            "nextFireDate": ISO8601DateFormatter().string(from: nextFireDate),
            "capability": "native_alarms",
            "isActive": true,
            "platformAlarmId": id
        ]
    }

    func getAllAlarms() async throws -> [[String: Any]] {
        var alarms: [[String: Any]] = []

        for (id, _) in alarmMetadataStore {
            if let alarm = try await getAlarm(id: id) {
                alarms.append(alarm)
            }
        }

        return alarms
    }

    func getAlarmsByCategory(category: String) async throws -> [[String: Any]] {
        var alarms: [[String: Any]] = []

        for (id, metadata) in alarmMetadataStore {
            guard let config = metadata["config"] as? NSDictionary,
                  let alarmCategory = config["category"] as? String,
                  alarmCategory == category else {
                continue
            }

            if let alarm = try await getAlarm(id: id) {
                alarms.append(alarm)
            }
        }

        return alarms
    }

    // MARK: - Actions

    func snoozeAlarm(id: String, minutes: Int) async throws {
        // For snooze, cancel existing and reschedule
        guard let metadata = alarmMetadataStore[id],
              let schedule = metadata["schedule"] as? NSDictionary,
              let config = metadata["config"] as? NSDictionary else {
            throw NSError(
                domain: "AlarmKitManager",
                code: 404,
                userInfo: [NSLocalizedDescriptionKey: "Alarm not found"]
            )
        }

        try await cancelAlarm(id: id)

        // Reschedule with snooze delay
        let snoozeDuration = TimeInterval(minutes * 60)
        let attributes = buildAlarmAttributes(config: config)

        let uuid = UUID(uuidString: id) ?? UUID()
        _ = try await manager.schedule(
            id: uuid,
            configuration: .timer(
                duration: snoozeDuration,
                attributes: attributes
            )
        )

        // Restore metadata
        alarmMetadataStore[id] = metadata
    }

    // MARK: - Helper Methods

    private func calculateDuration(schedule: NSDictionary) -> TimeInterval {
        let type = schedule["type"] as? String ?? "fixed"

        if type == "interval", let intervalMinutes = schedule["intervalMinutes"] as? Int {
            return TimeInterval(intervalMinutes * 60)
        }

        // For fixed and recurring, calculate time until next alarm
        let time = schedule["time"] as? NSDictionary
        let hour = time?["hour"] as? Int ?? 8
        let minute = time?["minute"] as? Int ?? 0

        let now = Date()
        var calendar = Calendar.current
        calendar.timeZone = TimeZone.current

        var components = calendar.dateComponents([.year, .month, .day], from: now)
        components.hour = hour
        components.minute = minute
        components.second = 0

        guard var targetDate = calendar.date(from: components) else {
            return 3600 // Default 1 hour
        }

        // If time has passed today, schedule for tomorrow
        if targetDate <= now {
            targetDate = calendar.date(byAdding: .day, value: 1, to: targetDate)!
        }

        return targetDate.timeIntervalSince(now)
    }

    private func buildAlarmAttributes(config: NSDictionary) -> AlarmAttributes<BasicAlarmMetadata> {
        let title = config["title"] as? String ?? "Alarm"
        let colorHex = config["color"] as? String ?? "#007AFF"

        // Build stop button
        let stopButton = AlarmButton(
            text: "Done",
            textColor: hexToColor(colorHex),
            systemImageName: "checkmark.circle.fill"
        )

        // Build alert presentation
        let alert = AlarmPresentation.Alert(
            title: LocalizedStringResource(stringLiteral: title),
            stopButton: stopButton
        )

        let presentation = AlarmPresentation(alert: alert)
        let tintColor = hexToColor(colorHex)

        let attributes = AlarmAttributes<BasicAlarmMetadata>(
            presentation: presentation,
            tintColor: tintColor
        )

        return attributes
    }

    private func hexToColor(_ hex: String) -> Color {
        var hexSanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        hexSanitized = hexSanitized.replacingOccurrences(of: "#", with: "")

        var rgb: UInt64 = 0
        Scanner(string: hexSanitized).scanHexInt64(&rgb)

        let red = Double((rgb & 0xFF0000) >> 16) / 255.0
        let green = Double((rgb & 0x00FF00) >> 8) / 255.0
        let blue = Double(rgb & 0x0000FF) / 255.0

        return Color(red: red, green: green, blue: blue)
    }

    private func monitorAlarms() async {
        for await alarms in manager.alarmUpdates {
            for alarm in alarms {
                if alarm.state == .alerting {
                    let alarmId = alarm.id.uuidString

                    if let metadata = alarmMetadataStore[alarmId],
                       let schedule = metadata["schedule"] as? NSDictionary,
                       let config = metadata["config"] as? NSDictionary {

                        let alarmData: [String: Any] = [
                            "id": alarmId,
                            "schedule": schedule,
                            "config": config,
                            "nextFireDate": ISO8601DateFormatter().string(from: Date()),
                            "capability": "native_alarms",
                            "isActive": true
                        ]

                        delegate?.alarmDidFire(alarm: alarmData)
                    }
                }
            }
        }
    }
}
