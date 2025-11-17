import { AlarmValidation, AlarmErrorCode, AlarmError } from '../src/types';

describe('AlarmValidation', () => {
  describe('isValidSchedule', () => {
    it('should validate fixed schedule', () => {
      const validSchedule = {
        id: 'test-1',
        type: 'fixed' as const,
        time: { hour: 8, minute: 30 },
      };

      expect(AlarmValidation.isValidSchedule(validSchedule)).toBe(true);
    });

    it('should reject schedule without id', () => {
      const invalidSchedule = {
        id: '',
        type: 'fixed' as const,
        time: { hour: 8, minute: 30 },
      };

      expect(AlarmValidation.isValidSchedule(invalidSchedule)).toBe(false);
    });

    it('should reject schedule with invalid hour', () => {
      const invalidSchedule = {
        id: 'test-1',
        type: 'fixed' as const,
        time: { hour: 25, minute: 30 },
      };

      expect(AlarmValidation.isValidSchedule(invalidSchedule)).toBe(false);
    });

    it('should reject schedule with invalid minute', () => {
      const invalidSchedule = {
        id: 'test-1',
        type: 'fixed' as const,
        time: { hour: 8, minute: 60 },
      };

      expect(AlarmValidation.isValidSchedule(invalidSchedule)).toBe(false);
    });

    it('should validate recurring schedule', () => {
      const validSchedule = {
        id: 'test-1',
        type: 'recurring' as const,
        time: { hour: 8, minute: 0 },
        daysOfWeek: [1, 2, 3, 4, 5] as any,
      };

      expect(AlarmValidation.isValidSchedule(validSchedule)).toBe(true);
    });

    it('should reject recurring schedule without daysOfWeek', () => {
      const invalidSchedule = {
        id: 'test-1',
        type: 'recurring' as const,
        time: { hour: 8, minute: 0 },
      };

      expect(AlarmValidation.isValidSchedule(invalidSchedule)).toBe(false);
    });

    it('should reject recurring schedule with invalid day', () => {
      const invalidSchedule = {
        id: 'test-1',
        type: 'recurring' as const,
        time: { hour: 8, minute: 0 },
        daysOfWeek: [1, 2, 7] as any, // 7 is invalid
      };

      expect(AlarmValidation.isValidSchedule(invalidSchedule)).toBe(false);
    });

    it('should validate interval schedule', () => {
      const validSchedule = {
        id: 'test-1',
        type: 'interval' as const,
        intervalMinutes: 60,
      };

      expect(AlarmValidation.isValidSchedule(validSchedule)).toBe(true);
    });

    it('should reject interval schedule with invalid interval', () => {
      const invalidSchedule = {
        id: 'test-1',
        type: 'interval' as const,
        intervalMinutes: 0,
      };

      expect(AlarmValidation.isValidSchedule(invalidSchedule)).toBe(false);
    });
  });

  describe('isValidConfig', () => {
    it('should validate config with title', () => {
      const validConfig = {
        title: 'Test Alarm',
      };

      expect(AlarmValidation.isValidConfig(validConfig)).toBe(true);
    });

    it('should reject config without title', () => {
      const invalidConfig = {
        title: '',
      };

      expect(AlarmValidation.isValidConfig(invalidConfig)).toBe(false);
    });

    it('should validate config with actions', () => {
      const validConfig = {
        title: 'Test Alarm',
        actions: [
          { id: 'dismiss', title: 'Dismiss', behavior: 'dismiss' as const },
          {
            id: 'snooze',
            title: 'Snooze',
            behavior: 'snooze' as const,
            snoozeDuration: 10,
          },
        ],
      };

      expect(AlarmValidation.isValidConfig(validConfig)).toBe(true);
    });

    it('should reject config with invalid action (missing snooze duration)', () => {
      const invalidConfig = {
        title: 'Test Alarm',
        actions: [
          {
            id: 'snooze',
            title: 'Snooze',
            behavior: 'snooze' as const,
            // Missing snoozeDuration
          },
        ],
      };

      expect(AlarmValidation.isValidConfig(invalidConfig)).toBe(false);
    });

    it('should reject config with action missing id', () => {
      const invalidConfig = {
        title: 'Test Alarm',
        actions: [
          { id: '', title: 'Dismiss', behavior: 'dismiss' as const },
        ],
      };

      expect(AlarmValidation.isValidConfig(invalidConfig)).toBe(false);
    });
  });
});

describe('AlarmError', () => {
  it('should create error with code and message', () => {
    const error = new AlarmError(
      AlarmErrorCode.PERMISSION_DENIED,
      'Permission denied'
    );

    expect(error.code).toBe(AlarmErrorCode.PERMISSION_DENIED);
    expect(error.message).toBe('Permission denied');
    expect(error.name).toBe('AlarmError');
  });

  it('should include details', () => {
    const error = new AlarmError(
      AlarmErrorCode.INVALID_SCHEDULE,
      'Invalid schedule',
      { hour: 25 }
    );

    expect(error.details).toEqual({ hour: 25 });
  });
});
