/**
 * react-native-alarmkit
 *
 * Cross-platform native alarm scheduling for React Native
 * - iOS 26+: AlarmKit for system-level alarms
 * - Android 12+: AlarmManager exact alarms
 * - Automatic fallback to notifications on older platforms
 *
 * @packageDocumentation
 */

export { default as NativeAlarmManager } from './AlarmManager';
export { default } from './AlarmManager';

// Export types
export type {
  // Core types
  AlarmSchedule,
  AlarmConfig,
  ScheduledAlarm,
  AlarmTime,
  AlarmAction,

  // Events
  AlarmFiredEvent,
  PermissionChangedEvent,

  // Capability
  AlarmCapabilityCheck,

  // Enums
  AlarmScheduleType,
  DayOfWeek,
  AlarmActionBehavior,

  // Interface
  NativeAlarmManager as INativeAlarmManager,
  EventUnsubscribe,
} from './types';

// Export enums and utilities
export {
  AlarmCapability,
  AlarmErrorCode,
  AlarmError,
  AlarmValidation,
} from './types';

/**
 * Quick usage example:
 *
 * ```typescript
 * import NativeAlarmManager from 'react-native-alarmkit';
 *
 * // Check capability
 * const capability = await NativeAlarmManager.checkCapability();
 * console.log('Using:', capability.capability);
 *
 * // Request permission if needed
 * if (capability.requiresPermission) {
 *   await NativeAlarmManager.requestPermission();
 * }
 *
 * // Schedule daily alarm
 * const alarm = await NativeAlarmManager.scheduleAlarm(
 *   {
 *     id: 'morning-med',
 *     type: 'recurring',
 *     time: { hour: 8, minute: 0 },
 *     daysOfWeek: [1, 2, 3, 4, 5], // Mon-Fri
 *   },
 *   {
 *     title: 'Take Morning Medication',
 *     body: 'Lisinopril 10mg',
 *     actions: [
 *       { id: 'taken', title: 'Taken', behavior: 'dismiss' },
 *       { id: 'snooze', title: 'Snooze', behavior: 'snooze', snoozeDuration: 10 },
 *     ],
 *   }
 * );
 *
 * // Listen for alarms
 * NativeAlarmManager.onAlarmFired((event) => {
 *   console.log('Alarm fired:', event.alarm.id);
 * });
 * ```
 */
