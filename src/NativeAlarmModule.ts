/**
 * Native module bridge for alarm operations
 * Low-level interface to native iOS and Android implementations
 */

import { NativeModules, NativeEventEmitter, Platform } from 'react-native';
import type {
  AlarmSchedule,
  AlarmConfig,
  ScheduledAlarm,
  AlarmCapabilityCheck,
  AlarmFiredEvent,
  PermissionChangedEvent,
} from './types';
import { AlarmError, AlarmErrorCode } from './types';

const LINKING_ERROR =
  `The package 'react-native-alarmkit' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- Run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

/**
 * Native module specification (TurboModule compatible)
 */
interface NativeAlarmsSpec {
  // Capability & Permissions
  checkCapability(): Promise<AlarmCapabilityCheck>;
  requestPermission(): Promise<boolean>;

  // Scheduling
  scheduleAlarm(
    schedule: AlarmSchedule,
    config: AlarmConfig
  ): Promise<ScheduledAlarm>;
  updateAlarm(
    id: string,
    schedule: AlarmSchedule,
    config: AlarmConfig
  ): Promise<ScheduledAlarm>;

  // Management
  cancelAlarm(id: string): Promise<void>;
  cancelAllAlarms(): Promise<void>;
  cancelAlarmsByCategory(category: string): Promise<void>;

  // Query
  getAlarm(id: string): Promise<ScheduledAlarm | null>;
  getAllAlarms(): Promise<ScheduledAlarm[]>;
  getAlarmsByCategory(category: string): Promise<ScheduledAlarm[]>;

  // Actions
  snoozeAlarm(id: string, minutes: number): Promise<void>;

  // Constants
  getConstants(): {
    ALARM_FIRED_EVENT: string;
    PERMISSION_CHANGED_EVENT: string;
  };
}

/**
 * Get native module with error handling
 */
const NativeAlarmsModule = NativeModules.RNNativeAlarms
  ? (NativeModules.RNNativeAlarms as NativeAlarmsSpec)
  : new Proxy(
      {},
      {
        get() {
          throw new AlarmError(
            AlarmErrorCode.MODULE_NOT_LINKED,
            LINKING_ERROR
          );
        },
      }
    ) as NativeAlarmsSpec;

/**
 * Event emitter for native events
 */
const eventEmitter = new NativeEventEmitter(
  NativeModules.RNNativeAlarms || undefined
);

/**
 * Event names from native module
 */
const EVENTS = NativeAlarmsModule.getConstants?.() || {
  ALARM_FIRED_EVENT: 'RNNativeAlarms_AlarmFired',
  PERMISSION_CHANGED_EVENT: 'RNNativeAlarms_PermissionChanged',
};

/**
 * Wrapped native module with error handling and type safety
 */
export const NativeAlarmModule = {
  /**
   * Check device alarm capability
   */
  async checkCapability(): Promise<AlarmCapabilityCheck> {
    try {
      return await NativeAlarmsModule.checkCapability();
    } catch (error) {
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to check capability',
        error
      );
    }
  },

  /**
   * Request alarm permission
   */
  async requestPermission(): Promise<boolean> {
    try {
      return await NativeAlarmsModule.requestPermission();
    } catch (error) {
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to request permission',
        error
      );
    }
  },

  /**
   * Schedule a new alarm
   */
  async scheduleAlarm(
    schedule: AlarmSchedule,
    config: AlarmConfig
  ): Promise<ScheduledAlarm> {
    try {
      const alarm = await NativeAlarmsModule.scheduleAlarm(schedule, config);

      // Convert date strings to Date objects
      return {
        ...alarm,
        nextFireDate: new Date(alarm.nextFireDate),
      };
    } catch (error: any) {
      if (error.code) {
        throw new AlarmError(
          error.code as AlarmErrorCode,
          error.message,
          error
        );
      }
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to schedule alarm',
        error
      );
    }
  },

  /**
   * Update an existing alarm
   */
  async updateAlarm(
    id: string,
    schedule: AlarmSchedule,
    config: AlarmConfig
  ): Promise<ScheduledAlarm> {
    try {
      const alarm = await NativeAlarmsModule.updateAlarm(id, schedule, config);

      return {
        ...alarm,
        nextFireDate: new Date(alarm.nextFireDate),
      };
    } catch (error: any) {
      if (error.code) {
        throw new AlarmError(
          error.code as AlarmErrorCode,
          error.message,
          error
        );
      }
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to update alarm',
        error
      );
    }
  },

  /**
   * Cancel an alarm by ID
   */
  async cancelAlarm(id: string): Promise<void> {
    try {
      await NativeAlarmsModule.cancelAlarm(id);
    } catch (error: any) {
      if (error.code === 'ALARM_NOT_FOUND') {
        throw new AlarmError(
          AlarmErrorCode.ALARM_NOT_FOUND,
          `Alarm with ID '${id}' not found`
        );
      }
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to cancel alarm',
        error
      );
    }
  },

  /**
   * Cancel all alarms
   */
  async cancelAllAlarms(): Promise<void> {
    try {
      await NativeAlarmsModule.cancelAllAlarms();
    } catch (error) {
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to cancel all alarms',
        error
      );
    }
  },

  /**
   * Cancel alarms by category
   */
  async cancelAlarmsByCategory(category: string): Promise<void> {
    try {
      await NativeAlarmsModule.cancelAlarmsByCategory(category);
    } catch (error) {
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to cancel alarms by category',
        error
      );
    }
  },

  /**
   * Get alarm by ID
   */
  async getAlarm(id: string): Promise<ScheduledAlarm | null> {
    try {
      const alarm = await NativeAlarmsModule.getAlarm(id);

      if (!alarm) return null;

      return {
        ...alarm,
        nextFireDate: new Date(alarm.nextFireDate),
      };
    } catch (error) {
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to get alarm',
        error
      );
    }
  },

  /**
   * Get all scheduled alarms
   */
  async getAllAlarms(): Promise<ScheduledAlarm[]> {
    try {
      const alarms = await NativeAlarmsModule.getAllAlarms();

      return alarms.map(alarm => ({
        ...alarm,
        nextFireDate: new Date(alarm.nextFireDate),
      }));
    } catch (error) {
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to get alarms',
        error
      );
    }
  },

  /**
   * Get alarms by category
   */
  async getAlarmsByCategory(category: string): Promise<ScheduledAlarm[]> {
    try {
      const alarms = await NativeAlarmsModule.getAlarmsByCategory(category);

      return alarms.map(alarm => ({
        ...alarm,
        nextFireDate: new Date(alarm.nextFireDate),
      }));
    } catch (error) {
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to get alarms by category',
        error
      );
    }
  },

  /**
   * Snooze an alarm
   */
  async snoozeAlarm(id: string, minutes: number): Promise<void> {
    try {
      await NativeAlarmsModule.snoozeAlarm(id, minutes);
    } catch (error: any) {
      if (error.code === 'ALARM_NOT_FOUND') {
        throw new AlarmError(
          AlarmErrorCode.ALARM_NOT_FOUND,
          `Alarm with ID '${id}' not found`
        );
      }
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to snooze alarm',
        error
      );
    }
  },

  /**
   * Subscribe to alarm fired events
   */
  onAlarmFired(callback: (event: AlarmFiredEvent) => void): () => void {
    const subscription = eventEmitter.addListener(
      EVENTS.ALARM_FIRED_EVENT,
      (event: any) => {
        // Convert date strings to Date objects
        callback({
          ...event,
          firedAt: new Date(event.firedAt),
          alarm: {
            ...event.alarm,
            nextFireDate: new Date(event.alarm.nextFireDate),
          },
        });
      }
    );

    return () => subscription.remove();
  },

  /**
   * Subscribe to permission changed events
   */
  onPermissionChanged(
    callback: (event: PermissionChangedEvent) => void
  ): () => void {
    const subscription = eventEmitter.addListener(
      EVENTS.PERMISSION_CHANGED_EVENT,
      callback
    );

    return () => subscription.remove();
  },
};

export default NativeAlarmModule;
