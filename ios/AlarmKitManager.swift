import Foundation
#if canImport(AlarmKit)
import AlarmKit
#endif

@available(iOS 26.0, *)
class AlarmKitManager {

    #if !canImport(AlarmKit)
    // Placeholder implementation when AlarmKit is not available
    weak var delegate: AlarmDelegate?

    init() {
        // Empty initializer for compatibility
    }

    func checkAuthorization() async -> String {
        return "notDetermined"
    }

    func requestPermission() async throws -> Bool {
        return false
    }

    func scheduleAlarm(schedule: NSDictionary, config: NSDictionary) async throws -> [String: Any] {
        throw NSError(domain: "AlarmKitManager", code: 500, userInfo: [NSLocalizedDescriptionKey: "AlarmKit not available"])
    }

    func cancelAlarm(id: String) async throws {
        throw NSError(domain: "AlarmKitManager", code: 500, userInfo: [NSLocalizedDescriptionKey: "AlarmKit not available"])
    }

    func cancelAllAlarms() async throws {
        throw NSError(domain: "AlarmKitManager", code: 500, userInfo: [NSLocalizedDescriptionKey: "AlarmKit not available"])
    }

    func cancelAlarmsByCategory(category: String) async throws {
        throw NSError(domain: "AlarmKitManager", code: 500, userInfo: [NSLocalizedDescriptionKey: "AlarmKit not available"])
    }

    func getAlarm(id: String) async throws -> [String: Any]? {
        return nil
    }

    func getAllAlarms() async throws -> [[String: Any]] {
        return []
    }

    func getAlarmsByCategory(category: String) async throws -> [[String: Any]] {
        return []
    }

    func snoozeAlarm(id: String, minutes: Int) async throws {
        throw NSError(domain: "AlarmKitManager", code: 500, userInfo: [NSLocalizedDescriptionKey: "AlarmKit not available"])
    }
    #else
    // Full AlarmKit implementation when available

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
        let state = await manager.authorizationState

        switch state {
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

        var alarmConfiguration: AlarmConfiguration<BasicAlarmMetadata>

        // Build alarm configuration based on type
        switch type {
        case "recurring":
            alarmConfiguration = try buildRecurringAlarm(schedule: schedule, config: config)
        case "interval":
            alarmConfiguration = try buildIntervalAlarm(schedule: schedule, config: config)
        case "fixed":
            alarmConfiguration = try buildFixedAlarm(schedule: schedule, config: config)
        default:
            throw NSError(
                domain: "AlarmKitManager",
                code: 400,
                userInfo: [NSLocalizedDescriptionKey: "Invalid schedule type: \(type)"]
            )
        }

        // Schedule alarm
        let uuid = UUID(uuidString: alarmId) ?? UUID()
        let alarm = try await manager.schedule(id: uuid, configuration: alarmConfiguration)

        // Calculate next fire date
        let nextFireDate = calculateNextFireDate(schedule: schedule)

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

        try manager.cancel(id: uuid)
        alarmMetadataStore.removeValue(forKey: id)
    }

    func cancelAllAlarms() async throws {
        // Get all alarms and cancel them
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

        let nextFireDate = calculateNextFireDate(schedule: schedule)

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
        guard let uuid = UUID(uuidString: id) else {
            throw NSError(
                domain: "AlarmKitManager",
                code: 400,
                userInfo: [NSLocalizedDescriptionKey: "Invalid alarm ID"]
            )
        }

        let duration = TimeInterval(minutes * 60)
        try manager.snooze(id: uuid, duration: duration)
    }

    // MARK: - Helper Methods

    private func buildFixedAlarm(schedule: NSDictionary, config: NSDictionary) throws -> AlarmConfiguration<BasicAlarmMetadata> {
        // Fixed alarms use countdown timer
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
            throw NSError(domain: "AlarmKitManager", code: 400, userInfo: [NSLocalizedDescriptionKey: "Invalid date"])
        }

        // If time has passed today, schedule for tomorrow
        if targetDate <= now {
            targetDate = calendar.date(byAdding: .day, value: 1, to: targetDate)!
        }

        let duration = targetDate.timeIntervalSince(now)

        let attributes = buildAlarmAttributes(config: config)

        return AlarmConfiguration.timer(
            duration: duration,
            attributes: attributes
        )
    }

    private func buildRecurringAlarm(schedule: NSDictionary, config: NSDictionary) throws -> AlarmConfiguration<BasicAlarmMetadata> {
        guard let time = schedule["time"] as? NSDictionary,
              let hour = time["hour"] as? Int,
              let minute = time["minute"] as? Int else {
            throw NSError(domain: "AlarmKitManager", code: 400, userInfo: [NSLocalizedDescriptionKey: "Invalid time"])
        }

        let daysOfWeek = schedule["daysOfWeek"] as? [Int] ?? []

        let alarmTime = Alarm.Schedule.Relative.Time(hour: hour, minute: minute)

        let recurrence: Alarm.Schedule.Relative.Recurrence
        if daysOfWeek.isEmpty {
            recurrence = .daily
        } else {
            let days = daysOfWeek.compactMap { dayInt -> DayOfWeek? in
                switch dayInt {
                case 0: return .sunday
                case 1: return .monday
                case 2: return .tuesday
                case 3: return .wednesday
                case 4: return .thursday
                case 5: return .friday
                case 6: return .saturday
                default: return nil
                }
            }
            recurrence = .weekly(days)
        }

        let alarmSchedule = Alarm.Schedule.Relative(time: alarmTime, repeats: recurrence)
        let attributes = buildAlarmAttributes(config: config)

        return AlarmConfiguration(
            schedule: alarmSchedule,
            attributes: attributes
        )
    }

    private func buildIntervalAlarm(schedule: NSDictionary, config: NSDictionary) throws -> AlarmConfiguration<BasicAlarmMetadata> {
        guard let intervalMinutes = schedule["intervalMinutes"] as? Int else {
            throw NSError(domain: "AlarmKitManager", code: 400, userInfo: [NSLocalizedDescriptionKey: "Invalid interval"])
        }

        let duration = TimeInterval(intervalMinutes * 60)
        let attributes = buildAlarmAttributes(config: config)

        return AlarmConfiguration.timer(
            duration: duration,
            attributes: attributes
        )
    }

    private func buildAlarmAttributes(config: NSDictionary) -> AlarmAttributes<BasicAlarmMetadata> {
        let title = config["title"] as? String ?? "Alarm"
        let sound = config["sound"] as? String
        let colorHex = config["color"] as? String ?? "#007AFF"

        // Build stop button
        let stopButton = AlarmButton(
            text: "Done",
            textColor: .white,
            systemImageName: "checkmark.circle.fill"
        )

        // Build secondary button if actions exist
        var secondaryButton: AlarmButton?
        var secondaryBehavior: AlarmPresentation.Alert.SecondaryButtonBehavior?

        if let actions = config["actions"] as? [[String: Any]],
           actions.count > 1 {
            let secondaryAction = actions[1]
            let actionTitle = secondaryAction["title"] as? String ?? "Snooze"
            let actionIcon = secondaryAction["icon"] as? String ?? "clock.arrow.circlepath"
            let behavior = secondaryAction["behavior"] as? String ?? "snooze"

            secondaryButton = AlarmButton(
                text: actionTitle,
                textColor: .gray,
                systemImageName: actionIcon
            )

            if behavior == "snooze" {
                let snoozeDuration = secondaryAction["snoozeDuration"] as? Int ?? 10
                secondaryBehavior = .snooze(duration: TimeInterval(snoozeDuration * 60))
            } else {
                secondaryBehavior = .custom
            }
        }

        // Build alert presentation
        let alert: AlarmPresentation.Alert
        if let secondary = secondaryButton, let behavior = secondaryBehavior {
            alert = AlarmPresentation.Alert(
                title: title,
                stopButton: stopButton,
                secondaryButton: secondary,
                secondaryButtonBehavior: behavior
            )
        } else {
            alert = AlarmPresentation.Alert(
                title: title,
                stopButton: stopButton
            )
        }

        // Build sound
        var alertSound: AlertConfiguration.AlertSound = .default
        if let soundName = sound {
            if soundName == "none" {
                alertSound = .none
            } else if soundName != "default" {
                alertSound = .named(soundName)
            }
        }

        // Build attributes
        let metadata = BasicAlarmMetadata()
        let tintColor = UIColor(hex: colorHex)

        let attributes = AlarmAttributes<BasicAlarmMetadata>(
            presentation: AlarmPresentation(alert: alert),
            tintColor: tintColor
        )

        return attributes
    }

    private func calculateNextFireDate(schedule: NSDictionary) -> Date {
        let type = schedule["type"] as? String ?? "fixed"
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
            return now
        }

        // If time has passed today, schedule for tomorrow (or next occurrence for recurring)
        if targetDate <= now {
            if type == "recurring" {
                let daysOfWeek = schedule["daysOfWeek"] as? [Int] ?? []
                if !daysOfWeek.isEmpty {
                    // Find next day of week
                    let currentWeekday = calendar.component(.weekday, from: now) - 1
                    var daysToAdd = 1

                    for i in 1...7 {
                        let checkDay = (currentWeekday + i) % 7
                        if daysOfWeek.contains(checkDay) {
                            daysToAdd = i
                            break
                        }
                    }

                    targetDate = calendar.date(byAdding: .day, value: daysToAdd, to: targetDate)!
                } else {
                    targetDate = calendar.date(byAdding: .day, value: 1, to: targetDate)!
                }
            } else {
                targetDate = calendar.date(byAdding: .day, value: 1, to: targetDate)!
            }
        }

        return targetDate
    }

    private func monitorAlarms() async {
        for await alarms in manager.alarmUpdates {
            for alarm in alarms {
                if alarm.state == .alerting {
                    // Alarm fired - notify delegate
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
    #endif
}

#if canImport(AlarmKit)
// MARK: - Basic Alarm Metadata

struct BasicAlarmMetadata: AlarmMetadata {
    // Empty metadata for basic alarms
}

// MARK: - UIColor Extension

extension UIColor {
    convenience init(hex: String) {
        var hexSanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        hexSanitized = hexSanitized.replacingOccurrences(of: "#", with: "")

        var rgb: UInt64 = 0
        Scanner(string: hexSanitized).scanHexInt64(&rgb)

        let red = CGFloat((rgb & 0xFF0000) >> 16) / 255.0
        let green = CGFloat((rgb & 0x00FF00) >> 8) / 255.0
        let blue = CGFloat(rgb & 0x0000FF) / 255.0

        self.init(red: red, green: green, blue: blue, alpha: 1.0)
    }
}
#endif
