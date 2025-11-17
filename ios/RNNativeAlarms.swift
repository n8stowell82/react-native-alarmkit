import Foundation
import React

@objc(RNNativeAlarms)
class RNNativeAlarms: RCTEventEmitter {

    // Backing storage for AlarmKitManager (type-erased to avoid availability issues)
    private var _alarmKitManager: Any?

    // Computed property with availability annotation
    @available(iOS 26.0, *)
    private var alarmKitManager: AlarmKitManager? {
        get { _alarmKitManager as? AlarmKitManager }
        set { _alarmKitManager = newValue }
    }

    private var notificationFallback: NotificationFallback?
    private var hasListeners = false

    override init() {
        super.init()

        // Initialize managers based on iOS version
        if #available(iOS 26.0, *) {
            alarmKitManager = AlarmKitManager()
            alarmKitManager?.delegate = self
        }

        // Always initialize notification fallback as backup
        notificationFallback = NotificationFallback()
        notificationFallback?.delegate = self
    }

    // MARK: - Event Emitter Setup

    override func supportedEvents() -> [String]! {
        return [
            "RNNativeAlarms_AlarmFired",
            "RNNativeAlarms_PermissionChanged"
        ]
    }

    override func startObserving() {
        hasListeners = true
    }

    override func stopObserving() {
        hasListeners = false
    }

    override func constantsToExport() -> [AnyHashable : Any]! {
        return [
            "ALARM_FIRED_EVENT": "RNNativeAlarms_AlarmFired",
            "PERMISSION_CHANGED_EVENT": "RNNativeAlarms_PermissionChanged"
        ]
    }

    override static func requiresMainQueueSetup() -> Bool {
        return true
    }

    // MARK: - Capability & Permissions

    @objc(checkCapability:rejecter:)
    func checkCapability(_ resolve: @escaping RCTPromiseResolveBlock,
                         rejecter reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                let capability = await getCapabilityCheck()
                resolve(capability)
            } catch {
                reject("CHECK_CAPABILITY_ERROR", error.localizedDescription, error)
            }
        }
    }

    @objc(requestPermission:rejecter:)
    func requestPermission(_ resolve: @escaping RCTPromiseResolveBlock,
                           rejecter reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                var granted = false

                if #available(iOS 26.0, *), let manager = alarmKitManager {
                    granted = try await manager.requestPermission()
                } else {
                    // Fallback to notification permission
                    granted = try await notificationFallback?.requestPermission() ?? false
                }

                // Send permission changed event
                sendPermissionChangedEvent(granted: granted)

                resolve(granted)
            } catch {
                reject("REQUEST_PERMISSION_ERROR", error.localizedDescription, error)
            }
        }
    }

    // MARK: - Scheduling

    @objc(scheduleAlarm:config:resolver:rejecter:)
    func scheduleAlarm(_ schedule: NSDictionary,
                       config: NSDictionary,
                       resolver resolve: @escaping RCTPromiseResolveBlock,
                       rejecter reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                let capability = await getCapabilityCheck()
                let capabilityType = capability["capability"] as? String ?? "notification"

                var scheduledAlarm: [String: Any]

                if #available(iOS 26.0, *),
                   capabilityType == "native_alarms",
                   let manager = alarmKitManager {
                    // Use AlarmKit
                    scheduledAlarm = try await manager.scheduleAlarm(schedule: schedule, config: config)
                } else {
                    // Use notification fallback
                    scheduledAlarm = try await notificationFallback?.scheduleAlarm(schedule: schedule, config: config) ?? [:]
                }

                resolve(scheduledAlarm)
            } catch {
                reject("SCHEDULE_ALARM_ERROR", error.localizedDescription, error)
            }
        }
    }

    @objc(updateAlarm:schedule:config:resolver:rejecter:)
    func updateAlarm(_ alarmId: String,
                     schedule: NSDictionary,
                     config: NSDictionary,
                     resolver resolve: @escaping RCTPromiseResolveBlock,
                     rejecter reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                // Cancel existing alarm
                try await cancelAlarm(alarmId, resolver: { _ in }, rejecter: reject)

                // Schedule new alarm with same ID
                var mutableSchedule = schedule.mutableCopy() as! NSMutableDictionary
                mutableSchedule["id"] = alarmId

                try await scheduleAlarm(mutableSchedule, config: config, resolver: resolve, rejecter: reject)
            } catch {
                reject("UPDATE_ALARM_ERROR", error.localizedDescription, error)
            }
        }
    }

    // MARK: - Management

    @objc(cancelAlarm:resolver:rejecter:)
    func cancelAlarm(_ alarmId: String,
                     resolver resolve: @escaping RCTPromiseResolveBlock,
                     rejecter reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                var cancelled = false

                if #available(iOS 26.0, *), let manager = alarmKitManager {
                    try await manager.cancelAlarm(id: alarmId)
                    cancelled = true
                }

                // Also try to cancel from notification fallback
                try await notificationFallback?.cancelAlarm(id: alarmId)
                cancelled = true

                if !cancelled {
                    throw NSError(
                        domain: "RNNativeAlarms",
                        code: 404,
                        userInfo: [NSLocalizedDescriptionKey: "Alarm not found"]
                    )
                }

                resolve(nil)
            } catch {
                reject("CANCEL_ALARM_ERROR", error.localizedDescription, error)
            }
        }
    }

    @objc(cancelAllAlarms:rejecter:)
    func cancelAllAlarms(_ resolve: @escaping RCTPromiseResolveBlock,
                         rejecter reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                if #available(iOS 26.0, *), let manager = alarmKitManager {
                    try await manager.cancelAllAlarms()
                }

                try await notificationFallback?.cancelAllAlarms()

                resolve(nil)
            } catch {
                reject("CANCEL_ALL_ALARMS_ERROR", error.localizedDescription, error)
            }
        }
    }

    @objc(cancelAlarmsByCategory:resolver:rejecter:)
    func cancelAlarmsByCategory(_ category: String,
                                resolver resolve: @escaping RCTPromiseResolveBlock,
                                rejecter reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                if #available(iOS 26.0, *), let manager = alarmKitManager {
                    try await manager.cancelAlarmsByCategory(category: category)
                }

                try await notificationFallback?.cancelAlarmsByCategory(category: category)

                resolve(nil)
            } catch {
                reject("CANCEL_ALARMS_BY_CATEGORY_ERROR", error.localizedDescription, error)
            }
        }
    }

    // MARK: - Query

    @objc(getAlarm:resolver:rejecter:)
    func getAlarm(_ alarmId: String,
                  resolver resolve: @escaping RCTPromiseResolveBlock,
                  rejecter reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                var alarm: [String: Any]? = nil

                if #available(iOS 26.0, *), let manager = alarmKitManager {
                    alarm = try await manager.getAlarm(id: alarmId)
                }

                if alarm == nil {
                    alarm = try await notificationFallback?.getAlarm(id: alarmId)
                }

                resolve(alarm)
            } catch {
                reject("GET_ALARM_ERROR", error.localizedDescription, error)
            }
        }
    }

    @objc(getAllAlarms:rejecter:)
    func getAllAlarms(_ resolve: @escaping RCTPromiseResolveBlock,
                      rejecter reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                var alarms: [[String: Any]] = []

                if #available(iOS 26.0, *), let manager = alarmKitManager {
                    alarms.append(contentsOf: try await manager.getAllAlarms())
                }

                alarms.append(contentsOf: try await notificationFallback?.getAllAlarms() ?? [])

                resolve(alarms)
            } catch {
                reject("GET_ALL_ALARMS_ERROR", error.localizedDescription, error)
            }
        }
    }

    @objc(getAlarmsByCategory:resolver:rejecter:)
    func getAlarmsByCategory(_ category: String,
                             resolver resolve: @escaping RCTPromiseResolveBlock,
                             rejecter reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                var alarms: [[String: Any]] = []

                if #available(iOS 26.0, *), let manager = alarmKitManager {
                    alarms.append(contentsOf: try await manager.getAlarmsByCategory(category: category))
                }

                alarms.append(contentsOf: try await notificationFallback?.getAlarmsByCategory(category: category) ?? [])

                resolve(alarms)
            } catch {
                reject("GET_ALARMS_BY_CATEGORY_ERROR", error.localizedDescription, error)
            }
        }
    }

    // MARK: - Actions

    @objc(snoozeAlarm:minutes:resolver:rejecter:)
    func snoozeAlarm(_ alarmId: String,
                     minutes: NSNumber,
                     resolver resolve: @escaping RCTPromiseResolveBlock,
                     rejecter reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                let minutesInt = minutes.intValue

                if #available(iOS 26.0, *), let manager = alarmKitManager {
                    try await manager.snoozeAlarm(id: alarmId, minutes: minutesInt)
                } else {
                    try await notificationFallback?.snoozeAlarm(id: alarmId, minutes: minutesInt)
                }

                resolve(nil)
            } catch {
                reject("SNOOZE_ALARM_ERROR", error.localizedDescription, error)
            }
        }
    }

    // MARK: - Helper Methods

    private func getCapabilityCheck() async -> [String: Any] {
        var capability: [String: Any] = [:]

        if #available(iOS 26.0, *), let manager = alarmKitManager {
            let authStatus = await manager.checkAuthorization()
            let requiresPermission = authStatus == "notDetermined" || authStatus == "denied"
            let canRequest = authStatus == "notDetermined"

            capability = [
                "capability": authStatus == "authorized" ? "native_alarms" : "notification",
                "reason": authStatus == "authorized"
                    ? "AlarmKit available and authorized"
                    : (authStatus == "notDetermined"
                        ? "AlarmKit available, needs authorization"
                        : "AlarmKit denied, using notifications"),
                "requiresPermission": requiresPermission,
                "canRequestPermission": canRequest,
                "platformDetails": [
                    "platform": "ios",
                    "version": ProcessInfo.processInfo.operatingSystemVersion.majorVersion,
                    "alarmKitAvailable": true,
                    "alarmKitAuthStatus": authStatus
                ]
            ]
        } else {
            // iOS < 26, use notification fallback
            let version = ProcessInfo.processInfo.operatingSystemVersion.majorVersion
            capability = [
                "capability": "notification",
                "reason": "iOS \(version), using local notifications",
                "requiresPermission": false,
                "canRequestPermission": false,
                "platformDetails": [
                    "platform": "ios",
                    "version": version,
                    "alarmKitAvailable": false
                ]
            ]
        }

        return capability
    }

    private func sendPermissionChangedEvent(granted: Bool) {
        if hasListeners {
            Task { @MainActor in
                let capability = await getCapabilityCheck()
                sendEvent(
                    withName: "RNNativeAlarms_PermissionChanged",
                    body: [
                        "granted": granted,
                        "capability": capability["capability"] ?? "none",
                        "platform": "ios"
                    ]
                )
            }
        }
    }
}

// MARK: - Alarm Delegate

extension RNNativeAlarms: AlarmDelegate {
    func alarmDidFire(alarm: [String: Any]) {
        if hasListeners {
            sendEvent(
                withName: "RNNativeAlarms_AlarmFired",
                body: [
                    "alarm": alarm,
                    "firedAt": ISO8601DateFormatter().string(from: Date())
                ]
            )
        }
    }
}

// MARK: - Alarm Delegate Protocol

protocol AlarmDelegate: AnyObject {
    func alarmDidFire(alarm: [String: Any])
}
