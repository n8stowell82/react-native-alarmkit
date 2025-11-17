/**
 * High-level alarm manager with automatic fallback handling
 * Main entry point for the library
 */

import { Platform } from 'react-native';
import NativeAlarmModule from './NativeAlarmModule';
import type {
  NativeAlarmManager as INativeAlarmManager,
  AlarmSchedule,
  AlarmConfig,
  ScheduledAlarm,
  AlarmCapabilityCheck,
  AlarmFiredEvent,
  PermissionChangedEvent,
  EventUnsubscribe,
} from './types';
import { AlarmValidation, AlarmError, AlarmErrorCode, AlarmCapability } from './types';

/**
 * Main alarm manager implementation
 */
class AlarmManager implements INativeAlarmManager {
  private _cachedCapability: AlarmCapabilityCheck | null = null;
  private _capabilityCacheTime: number = 0;
  private readonly CAPABILITY_CACHE_TTL = 30000; // 30 seconds

  /**
   * Check alarm capability with caching
   */
  async checkCapability(): Promise<AlarmCapabilityCheck> {
    const now = Date.now();

    // Return cached result if fresh
    if (
      this._cachedCapability &&
      now - this._capabilityCacheTime < this.CAPABILITY_CACHE_TTL
    ) {
      return this._cachedCapability;
    }

    try {
      const capability = await NativeAlarmModule.checkCapability();
      this._cachedCapability = capability;
      this._capabilityCacheTime = now;
      return capability;
    } catch (error) {
      console.error('[AlarmManager] Failed to check capability:', error);

      // Return fallback capability
      return {
        capability: AlarmCapability.NOTIFICATION_BASED,
        reason: 'Unable to determine capability, using notifications',
        requiresPermission: false,
        canRequestPermission: false,
        platformDetails: {
          platform: Platform.OS as 'ios' | 'android',
          version: Platform.Version,
        },
      };
    }
  }

  /**
   * Request permission with error handling
   */
  async requestPermission(): Promise<boolean> {
    try {
      const granted = await NativeAlarmModule.requestPermission();

      // Invalidate capability cache after permission change
      this._cachedCapability = null;

      return granted;
    } catch (error) {
      console.error('[AlarmManager] Failed to request permission:', error);
      return false;
    }
  }

  /**
   * Schedule alarm with validation and fallback handling
   */
  async scheduleAlarm(
    schedule: AlarmSchedule,
    config: AlarmConfig
  ): Promise<ScheduledAlarm> {
    // Validate inputs
    if (!AlarmValidation.isValidSchedule(schedule)) {
      throw new AlarmError(
        AlarmErrorCode.INVALID_SCHEDULE,
        'Invalid alarm schedule configuration',
        { schedule }
      );
    }

    if (!AlarmValidation.isValidConfig(config)) {
      throw new AlarmError(
        AlarmErrorCode.INVALID_CONFIG,
        'Invalid alarm configuration',
        { config }
      );
    }

    // Check capability before scheduling
    const capability = await this.checkCapability();

    // Warn if using fallback mechanism
    if (capability.capability === AlarmCapability.INEXACT_ALARMS) {
      console.warn(
        '[AlarmManager] Using inexact alarms - timing may not be precise. ' +
          'Consider requesting SCHEDULE_EXACT_ALARM permission.'
      );
    } else if (capability.capability === AlarmCapability.NOTIFICATION_BASED) {
      console.warn(
        '[AlarmManager] Using notification-based alarms - may be affected by ' +
          'Do Not Disturb or Focus modes.'
      );
    }

    // If permission required but not granted, throw error
    if (capability.requiresPermission && capability.canRequestPermission) {
      throw new AlarmError(
        AlarmErrorCode.PERMISSION_DENIED,
        'Alarm permission required. Call requestPermission() first.'
      );
    }

    try {
      return await NativeAlarmModule.scheduleAlarm(schedule, config);
    } catch (error) {
      if (error instanceof AlarmError) {
        throw error;
      }

      console.error('[AlarmManager] Failed to schedule alarm:', error);
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to schedule alarm',
        error
      );
    }
  }

  /**
   * Update existing alarm
   */
  async updateAlarm(
    id: string,
    schedule: AlarmSchedule,
    config: AlarmConfig
  ): Promise<ScheduledAlarm> {
    // Validate inputs
    if (!AlarmValidation.isValidSchedule(schedule)) {
      throw new AlarmError(
        AlarmErrorCode.INVALID_SCHEDULE,
        'Invalid alarm schedule configuration',
        { schedule }
      );
    }

    if (!AlarmValidation.isValidConfig(config)) {
      throw new AlarmError(
        AlarmErrorCode.INVALID_CONFIG,
        'Invalid alarm configuration',
        { config }
      );
    }

    try {
      return await NativeAlarmModule.updateAlarm(id, schedule, config);
    } catch (error) {
      if (error instanceof AlarmError) {
        throw error;
      }

      console.error('[AlarmManager] Failed to update alarm:', error);
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to update alarm',
        error
      );
    }
  }

  /**
   * Cancel alarm by ID
   */
  async cancelAlarm(id: string): Promise<void> {
    try {
      await NativeAlarmModule.cancelAlarm(id);
    } catch (error) {
      if (error instanceof AlarmError) {
        throw error;
      }

      console.error('[AlarmManager] Failed to cancel alarm:', error);
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to cancel alarm',
        error
      );
    }
  }

  /**
   * Cancel all alarms
   */
  async cancelAllAlarms(): Promise<void> {
    try {
      await NativeAlarmModule.cancelAllAlarms();
    } catch (error) {
      console.error('[AlarmManager] Failed to cancel all alarms:', error);
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to cancel all alarms',
        error
      );
    }
  }

  /**
   * Cancel alarms by category
   */
  async cancelAlarmsByCategory(category: string): Promise<void> {
    try {
      await NativeAlarmModule.cancelAlarmsByCategory(category);
    } catch (error) {
      console.error(
        '[AlarmManager] Failed to cancel alarms by category:',
        error
      );
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to cancel alarms by category',
        error
      );
    }
  }

  /**
   * Get alarm by ID
   */
  async getAlarm(id: string): Promise<ScheduledAlarm | null> {
    try {
      return await NativeAlarmModule.getAlarm(id);
    } catch (error) {
      console.error('[AlarmManager] Failed to get alarm:', error);
      return null;
    }
  }

  /**
   * Get all scheduled alarms
   */
  async getAllAlarms(): Promise<ScheduledAlarm[]> {
    try {
      return await NativeAlarmModule.getAllAlarms();
    } catch (error) {
      console.error('[AlarmManager] Failed to get all alarms:', error);
      return [];
    }
  }

  /**
   * Get alarms by category
   */
  async getAlarmsByCategory(category: string): Promise<ScheduledAlarm[]> {
    try {
      return await NativeAlarmModule.getAlarmsByCategory(category);
    } catch (error) {
      console.error('[AlarmManager] Failed to get alarms by category:', error);
      return [];
    }
  }

  /**
   * Snooze an alarm
   */
  async snoozeAlarm(id: string, minutes: number): Promise<void> {
    if (minutes < 1) {
      throw new AlarmError(
        AlarmErrorCode.INVALID_CONFIG,
        'Snooze duration must be at least 1 minute'
      );
    }

    try {
      await NativeAlarmModule.snoozeAlarm(id, minutes);
    } catch (error) {
      if (error instanceof AlarmError) {
        throw error;
      }

      console.error('[AlarmManager] Failed to snooze alarm:', error);
      throw new AlarmError(
        AlarmErrorCode.SYSTEM_ERROR,
        'Failed to snooze alarm',
        error
      );
    }
  }

  /**
   * Subscribe to alarm fired events
   */
  onAlarmFired(callback: (event: AlarmFiredEvent) => void): EventUnsubscribe {
    return NativeAlarmModule.onAlarmFired(callback);
  }

  /**
   * Subscribe to permission changed events
   */
  onPermissionChanged(
    callback: (event: PermissionChangedEvent) => void
  ): EventUnsubscribe {
    const unsubscribe = NativeAlarmModule.onPermissionChanged(event => {
      // Invalidate capability cache on permission change
      this._cachedCapability = null;
      callback(event);
    });

    return unsubscribe;
  }
}

/**
 * Singleton instance
 */
const alarmManagerInstance = new AlarmManager();

export default alarmManagerInstance;
export { AlarmManager };
