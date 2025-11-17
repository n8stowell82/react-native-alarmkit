// Mock React Native modules
jest.mock('react-native', () => ({
  NativeModules: {
    RNNativeAlarms: {
      checkCapability: jest.fn(),
      requestPermission: jest.fn(),
      scheduleAlarm: jest.fn(),
      updateAlarm: jest.fn(),
      cancelAlarm: jest.fn(),
      cancelAllAlarms: jest.fn(),
      cancelAlarmsByCategory: jest.fn(),
      getAlarm: jest.fn(),
      getAllAlarms: jest.fn(),
      getAlarmsByCategory: jest.fn(),
      snoozeAlarm: jest.fn(),
      getConstants: jest.fn(() => ({
        ALARM_FIRED_EVENT: 'RNNativeAlarms_AlarmFired',
        PERMISSION_CHANGED_EVENT: 'RNNativeAlarms_PermissionChanged',
      })),
    },
  },
  NativeEventEmitter: jest.fn(() => ({
    addListener: jest.fn(() => ({
      remove: jest.fn(),
    })),
  })),
  Platform: {
    OS: 'ios',
    Version: '26.0',
    select: jest.fn((obj) => obj.ios || obj.default),
  },
}));
