/**
 * Native Alarms TypeScript Definitions
 * Cross-platform alarm scheduling with AlarmKit (iOS) and AlarmManager (Android)
 */

/**
 * Alarm capability levels based on platform and permissions
 */
export enum AlarmCapability {
  /**
   * Native system alarms available (AlarmKit on iOS 26+, exact alarms on Android 12+)
   * - Breaks through Do Not Disturb / Focus
   * - Guaranteed exact timing
   * - System-level integration
   */
  NATIVE_ALARMS = 'native_alarms',

  /**
   * Notification-based alarms (local notifications)
   * - May be silenced by Do Not Disturb
   * - Generally reliable but not guaranteed
   * - Works on all platform versions
   */
  NOTIFICATION_BASED = 'notification',

  /**
   * Inexact alarms (Android only, when exact permission denied)
   * - Timing may be off by several minutes
   * - Subject to battery optimization
   * - Last resort for Android
   */
  INEXACT_ALARMS = 'inexact',

  /**
   * No reliable alarm mechanism available
   */
  NONE = 'none',
}

/**
 * Result of checking device alarm capabilities
 */
export interface AlarmCapabilityCheck {
  /** Current capability level */
  capability: AlarmCapability;

  /** Human-readable explanation of capability status */
  reason: string;

  /** Whether permission is required to use this capability */
  requiresPermission: boolean;

  /** Whether permission can be requested (or is it permanently denied) */
  canRequestPermission: boolean;

  /** Platform-specific details */
  platformDetails?: {
    platform: 'ios' | 'android';
    version: number | string;

    // iOS specific
    alarmKitAvailable?: boolean;
    alarmKitAuthStatus?: 'notDetermined' | 'authorized' | 'denied';

    // Android specific
    canScheduleExactAlarms?: boolean;
    hasUseExactAlarmPermission?: boolean;
  };
}

/**
 * Alarm schedule types
 */
export type AlarmScheduleType = 'fixed' | 'recurring' | 'interval';

/**
 * Day of week (Sunday = 0, Saturday = 6)
 */
export type DayOfWeek = 0 | 1 | 2 | 3 | 4 | 5 | 6;

/**
 * Time specification (24-hour format)
 */
export interface AlarmTime {
  /** Hour (0-23) */
  hour: number;

  /** Minute (0-59) */
  minute: number;
}

/**
 * Alarm schedule configuration
 */
export interface AlarmSchedule {
  /** Unique identifier for this alarm */
  id: string;

  /** Schedule type */
  type: AlarmScheduleType;

  /**
   * Time of day (for 'fixed' and 'recurring' types)
   * Required for 'fixed' and 'recurring'
   */
  time?: AlarmTime;

  /**
   * Specific date (for 'fixed' type only)
   * If not provided for 'fixed', uses next occurrence of time today/tomorrow
   */
  date?: Date;

  /**
   * Days of week for recurring alarms (0 = Sunday, 6 = Saturday)
   * Required for 'recurring' type
   */
  daysOfWeek?: DayOfWeek[];

  /**
   * Interval in minutes (for 'interval' type)
   * Required for 'interval' type
   */
  intervalMinutes?: number;

  /**
   * Start time for interval-based alarms
   * If not provided, starts immediately
   */
  startTime?: Date;
}

/**
 * Alarm action behaviors
 */
export type AlarmActionBehavior = 'dismiss' | 'snooze' | 'custom';

/**
 * Action button configuration
 */
export interface AlarmAction {
  /** Unique identifier for this action */
  id: string;

  /** Button text */
  title: string;

  /** Action behavior */
  behavior: AlarmActionBehavior;

  /**
   * Snooze duration in minutes (required if behavior is 'snooze')
   */
  snoozeDuration?: number;

  /**
   * Custom data passed to action handler (for 'custom' behavior)
   */
  data?: Record<string, any>;

  /**
   * iOS: SF Symbol name for button icon
   * Android: Material icon name
   */
  icon?: string;

  /** Button color (hex string) */
  color?: string;
}

/**
 * Alarm notification configuration
 */
export interface AlarmConfig {
  /** Alarm title (notification/alert title) */
  title: string;

  /** Alarm body text (notification body) */
  body?: string;

  /**
   * Sound name (platform-specific)
   * - iOS: Sound file name without extension
   * - Android: Sound file name in res/raw
   * - Use 'default' for system default
   * - Use 'none' for silent
   */
  sound?: string;

  /**
   * Custom metadata attached to alarm
   * Available in alarm fired callback
   */
  data?: Record<string, any>;

  /**
   * Action buttons
   * - iOS: Up to 2 actions (primary + secondary)
   * - Android: Up to 3 actions
   */
  actions?: AlarmAction[];

  /**
   * Theme color for alarm UI (hex string)
   * - iOS: Tint color for Live Activity
   * - Android: Notification accent color
   */
  color?: string;

  /**
   * Category identifier for grouping alarms
   * Useful for managing multiple alarm types (e.g., medications, workouts)
   */
  category?: string;
}

/**
 * Scheduled alarm (returned after scheduling)
 */
export interface ScheduledAlarm {
  /** Alarm unique identifier */
  id: string;

  /** Schedule configuration */
  schedule: AlarmSchedule;

  /** Alarm configuration */
  config: AlarmConfig;

  /** Next scheduled fire date */
  nextFireDate: Date;

  /** Capability used for this alarm */
  capability: AlarmCapability;

  /** Whether alarm is currently active */
  isActive: boolean;

  /** Platform-specific alarm ID (for debugging) */
  platformAlarmId?: string;
}

/**
 * Alarm fired event
 */
export interface AlarmFiredEvent {
  /** Alarm that fired */
  alarm: ScheduledAlarm;

  /** Actual fire time (may differ slightly from scheduled time) */
  firedAt: Date;

  /** Action that was taken (if any) */
  action?: {
    actionId: string;
    actionTitle: string;
  };
}

/**
 * Permission state change event
 */
export interface PermissionChangedEvent {
  /** Whether permission is currently granted */
  granted: boolean;

  /** New capability level */
  capability: AlarmCapability;

  /** Platform */
  platform: 'ios' | 'android';
}

/**
 * Event listener cleanup function
 */
export type EventUnsubscribe = () => void;

/**
 * Main alarm manager interface
 */
export interface NativeAlarmManager {
  /**
   * Check current alarm capability for this device
   * Call this before scheduling alarms to determine what's available
   *
   * @returns Capability check result with permission status
   *
   * @example
   * ```typescript
   * const capability = await NativeAlarmManager.checkCapability();
   *
   * if (capability.requiresPermission) {
   *   // Show UI explaining why permission is needed
   *   const granted = await NativeAlarmManager.requestPermission();
   * }
   * ```
   */
  checkCapability(): Promise<AlarmCapabilityCheck>;

  /**
   * Request alarm permission from user
   *
   * - iOS 26+: Requests AlarmKit authorization
   * - Android 12+: Opens system settings for SCHEDULE_EXACT_ALARM permission
   * - Other platforms: Returns true (no permission needed)
   *
   * @returns Whether permission was granted
   *
   * @example
   * ```typescript
   * const granted = await NativeAlarmManager.requestPermission();
   *
   * if (!granted) {
   *   // Show message about limited functionality
   *   Alert.alert(
   *     'Permission Required',
   *     'Exact alarms are needed for reliable medication reminders.'
   *   );
   * }
   * ```
   */
  requestPermission(): Promise<boolean>;

  /**
   * Schedule a new alarm
   *
   * @param schedule - Alarm schedule configuration
   * @param config - Alarm notification configuration
   * @returns Scheduled alarm details
   * @throws Error if scheduling fails
   *
   * @example
   * ```typescript
   * // Daily medication reminder at 8 AM
   * const alarm = await NativeAlarmManager.scheduleAlarm(
   *   {
   *     id: 'med-morning-001',
   *     type: 'recurring',
   *     time: { hour: 8, minute: 0 },
   *     daysOfWeek: [1, 2, 3, 4, 5], // Mon-Fri
   *   },
   *   {
   *     title: 'Take Morning Medication',
   *     body: 'Lisinopril 10mg',
   *     sound: 'medication_alert',
   *     data: {
   *       medicationId: 'med-001',
   *       dosage: '10mg',
   *     },
   *     actions: [
   *       {
   *         id: 'taken',
   *         title: 'Taken',
   *         behavior: 'dismiss',
   *         icon: 'checkmark.circle.fill',
   *       },
   *       {
   *         id: 'snooze',
   *         title: 'Snooze 10m',
   *         behavior: 'snooze',
   *         snoozeDuration: 10,
   *         icon: 'clock.arrow.circlepath',
   *       },
   *     ],
   *   }
   * );
   * ```
   */
  scheduleAlarm(
    schedule: AlarmSchedule,
    config: AlarmConfig
  ): Promise<ScheduledAlarm>;

  /**
   * Update an existing alarm
   *
   * @param id - Alarm ID to update
   * @param schedule - New schedule configuration
   * @param config - New alarm configuration
   * @returns Updated alarm details
   * @throws Error if alarm not found or update fails
   */
  updateAlarm(
    id: string,
    schedule: AlarmSchedule,
    config: AlarmConfig
  ): Promise<ScheduledAlarm>;

  /**
   * Cancel a scheduled alarm
   *
   * @param id - Alarm ID to cancel
   * @throws Error if alarm not found
   *
   * @example
   * ```typescript
   * await NativeAlarmManager.cancelAlarm('med-morning-001');
   * ```
   */
  cancelAlarm(id: string): Promise<void>;

  /**
   * Cancel all scheduled alarms
   *
   * @example
   * ```typescript
   * await NativeAlarmManager.cancelAllAlarms();
   * ```
   */
  cancelAllAlarms(): Promise<void>;

  /**
   * Cancel all alarms in a specific category
   *
   * @param category - Category identifier
   *
   * @example
   * ```typescript
   * await NativeAlarmManager.cancelAlarmsByCategory('medications');
   * ```
   */
  cancelAlarmsByCategory(category: string): Promise<void>;

  /**
   * Get a specific scheduled alarm by ID
   *
   * @param id - Alarm ID
   * @returns Alarm details or null if not found
   */
  getAlarm(id: string): Promise<ScheduledAlarm | null>;

  /**
   * Get all scheduled alarms
   *
   * @returns Array of all scheduled alarms
   *
   * @example
   * ```typescript
   * const alarms = await NativeAlarmManager.getAllAlarms();
   * console.log(`${alarms.length} alarms scheduled`);
   * ```
   */
  getAllAlarms(): Promise<ScheduledAlarm[]>;

  /**
   * Get alarms by category
   *
   * @param category - Category identifier
   * @returns Array of alarms in this category
   */
  getAlarmsByCategory(category: string): Promise<ScheduledAlarm[]>;

  /**
   * Listen for alarm fired events
   *
   * @param callback - Called when an alarm fires
   * @returns Cleanup function to remove listener
   *
   * @example
   * ```typescript
   * const unsubscribe = NativeAlarmManager.onAlarmFired((event) => {
   *   console.log(`Alarm ${event.alarm.id} fired at ${event.firedAt}`);
   *
   *   // Update app state, log medication taken, etc.
   *   if (event.action?.actionId === 'taken') {
   *     logMedicationTaken(event.alarm.config.data.medicationId);
   *   }
   * });
   *
   * // Later: cleanup
   * unsubscribe();
   * ```
   */
  onAlarmFired(callback: (event: AlarmFiredEvent) => void): EventUnsubscribe;

  /**
   * Listen for permission state changes
   *
   * @param callback - Called when permission status changes
   * @returns Cleanup function to remove listener
   *
   * @example
   * ```typescript
   * NativeAlarmManager.onPermissionChanged((event) => {
   *   if (!event.granted) {
   *     Alert.alert(
   *       'Permission Revoked',
   *       'Alarms may not fire reliably. Please re-enable in Settings.'
   *     );
   *   }
   * });
   * ```
   */
  onPermissionChanged(
    callback: (event: PermissionChangedEvent) => void
  ): EventUnsubscribe;

  /**
   * Snooze a currently firing alarm
   *
   * @param id - Alarm ID
   * @param minutes - Snooze duration in minutes
   *
   * @example
   * ```typescript
   * // In alarm fired handler
   * await NativeAlarmManager.snoozeAlarm('med-morning-001', 10);
   * ```
   */
  snoozeAlarm(id: string, minutes: number): Promise<void>;
}

/**
 * Error codes for alarm operations
 */
export enum AlarmErrorCode {
  /** Permission denied by user */
  PERMISSION_DENIED = 'PERMISSION_DENIED',

  /** Alarm not found */
  ALARM_NOT_FOUND = 'ALARM_NOT_FOUND',

  /** Invalid schedule configuration */
  INVALID_SCHEDULE = 'INVALID_SCHEDULE',

  /** Invalid alarm configuration */
  INVALID_CONFIG = 'INVALID_CONFIG',

  /** Platform not supported */
  PLATFORM_NOT_SUPPORTED = 'PLATFORM_NOT_SUPPORTED',

  /** Native module not linked */
  MODULE_NOT_LINKED = 'MODULE_NOT_LINKED',

  /** System error (e.g., AlarmManager failure) */
  SYSTEM_ERROR = 'SYSTEM_ERROR',

  /** Too many alarms scheduled */
  TOO_MANY_ALARMS = 'TOO_MANY_ALARMS',

  /** Unknown error */
  UNKNOWN = 'UNKNOWN',
}

/**
 * Alarm operation error
 */
export class AlarmError extends Error {
  constructor(
    public code: AlarmErrorCode,
    message: string,
    public details?: any
  ) {
    super(message);
    this.name = 'AlarmError';
  }
}

/**
 * Validation helpers
 */
export const AlarmValidation = {
  /**
   * Validate alarm schedule configuration
   */
  isValidSchedule(schedule: AlarmSchedule): boolean {
    if (!schedule.id || schedule.id.trim() === '') {
      return false;
    }

    switch (schedule.type) {
      case 'fixed':
      case 'recurring':
        if (!schedule.time) return false;
        if (schedule.time.hour < 0 || schedule.time.hour > 23) return false;
        if (schedule.time.minute < 0 || schedule.time.minute > 59) return false;

        if (schedule.type === 'recurring') {
          if (!schedule.daysOfWeek || schedule.daysOfWeek.length === 0) {
            return false;
          }
          if (schedule.daysOfWeek.some(day => day < 0 || day > 6)) {
            return false;
          }
        }
        break;

      case 'interval':
        if (!schedule.intervalMinutes || schedule.intervalMinutes < 1) {
          return false;
        }
        break;

      default:
        return false;
    }

    return true;
  },

  /**
   * Validate alarm config
   */
  isValidConfig(config: AlarmConfig): boolean {
    if (!config.title || config.title.trim() === '') {
      return false;
    }

    if (config.actions && config.actions.length > 0) {
      for (const action of config.actions) {
        if (!action.id || !action.title) return false;
        if (action.behavior === 'snooze' && !action.snoozeDuration) {
          return false;
        }
      }
    }

    return true;
  },
};
