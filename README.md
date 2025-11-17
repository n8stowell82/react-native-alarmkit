# rn-native-alarmkit

Cross-platform native alarm scheduling for React Native with automatic fallback handling.

## Features

- ✨ **Native System Alarms** - Uses AlarmKit (iOS 26+) and AlarmManager (Android 12+)
- 🔔 **Breaks Through Do Not Disturb** - Native alarms bypass Focus/Silent modes
- 🎯 **Exact Timing** - Guaranteed alarm delivery at precise times
- 🔄 **Smart Fallbacks** - Automatically uses notifications on older platforms
- 📱 **Full TypeScript Support** - Complete type definitions
- 🎨 **Customizable Actions** - Snooze, dismiss, and custom buttons
- ⏰ **Flexible Scheduling** - One-time, recurring (daily/weekly), and interval-based alarms
- 📊 **Comprehensive Management** - Query, update, and cancel alarms with ease

## Platform Support

| Platform | Capability | Version | Notes |
|----------|-----------|---------|-------|
| iOS 26+ | Native Alarms (AlarmKit) | iOS 26.0+ | Full system integration, Live Activities |
| iOS < 26 | Local Notifications | iOS 13.0+ | May be silenced by Do Not Disturb |
| Android 12+ (with permission) | Exact Alarms | API 31+ | Requires SCHEDULE_EXACT_ALARM permission |
| Android 12+ (no permission) | Inexact Alarms | API 31+ | Timing may be off by several minutes |
| Android < 12 | Exact Alarms | API 21+ | No permission required |

## Installation

```bash
npm install rn-native-alarmkit
# or
yarn add rn-native-alarmkit
```

### iOS Setup

```bash
cd ios && pod install
```

Add the following to your `Info.plist`:

```xml
<key>NSAlarmKitUsageDescription</key>
<string>We need alarm access to remind you at exact times</string>
```

### Android Setup

Add to `AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Exact alarm permission (Android 12+) -->
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />

    <!-- Notifications -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Wake lock for alarms -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <!-- Boot receiver to reschedule alarms -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application>
        <!-- Additional configuration will be added automatically -->
    </application>
</manifest>
```

## Quick Start

```typescript
import NativeAlarmManager from 'rn-native-alarmkit';

// Check capability
const capability = await NativeAlarmManager.checkCapability();
console.log('Using:', capability.capability);

// Request permission if needed
if (capability.requiresPermission && capability.canRequestPermission) {
  const granted = await NativeAlarmManager.requestPermission();
  if (!granted) {
    // Handle permission denied
  }
}

// Schedule a daily alarm
const alarm = await NativeAlarmManager.scheduleAlarm(
  {
    id: 'morning-alarm',
    type: 'recurring',
    time: { hour: 8, minute: 0 },
    daysOfWeek: [1, 2, 3, 4, 5], // Monday-Friday
  },
  {
    title: 'Good Morning!',
    body: 'Time to start your day',
    actions: [
      { id: 'dismiss', title: 'Dismiss', behavior: 'dismiss' },
      { id: 'snooze', title: 'Snooze 10m', behavior: 'snooze', snoozeDuration: 10 },
    ],
  }
);

// Listen for alarm events
NativeAlarmManager.onAlarmFired((event) => {
  console.log('Alarm fired:', event.alarm.config.title);
});
```

## Usage Examples

### Medication Reminder (Daily at specific times)

```typescript
await NativeAlarmManager.scheduleAlarm(
  {
    id: 'med-morning',
    type: 'recurring',
    time: { hour: 8, minute: 0 },
    daysOfWeek: [0, 1, 2, 3, 4, 5, 6], // Every day
  },
  {
    title: 'Take Morning Medication',
    body: 'Lisinopril 10mg',
    sound: 'medication_alert',
    category: 'medications',
    data: {
      medicationId: 'med-001',
      dosage: '10mg',
    },
    actions: [
      {
        id: 'taken',
        title: 'Taken',
        behavior: 'dismiss',
        icon: 'checkmark.circle.fill', // iOS SF Symbol
      },
      {
        id: 'snooze',
        title: 'Snooze 10m',
        behavior: 'snooze',
        snoozeDuration: 10,
        icon: 'clock.arrow.circlepath',
      },
    ],
    color: '#007AFF',
  }
);
```

### Interval-Based Reminder (Every 4 hours)

```typescript
await NativeAlarmManager.scheduleAlarm(
  {
    id: 'hydration-reminder',
    type: 'interval',
    intervalMinutes: 240, // 4 hours
  },
  {
    title: 'Drink Water',
    body: 'Stay hydrated!',
    category: 'health',
  }
);
```

### One-Time Alarm (Specific date/time)

```typescript
await NativeAlarmManager.scheduleAlarm(
  {
    id: 'appointment',
    type: 'fixed',
    time: { hour: 14, minute: 30 },
    date: new Date('2025-12-25'),
  },
  {
    title: 'Doctor Appointment',
    body: 'Appointment at 2:30 PM',
  }
);
```

### Listening for Alarms

```typescript
// Listen for alarm fired events
const unsubscribe = NativeAlarmManager.onAlarmFired((event) => {
  console.log('Alarm fired:', event.alarm.id);

  // Access custom data
  if (event.alarm.config.data) {
    const medicationId = event.alarm.config.data.medicationId;
    // Update your app's state, log medication taken, etc.
  }

  // Check which action was taken
  if (event.action) {
    console.log('Action taken:', event.action.actionId);
  }
});

// Later: cleanup
unsubscribe();

// Listen for permission changes
NativeAlarmManager.onPermissionChanged((event) => {
  if (!event.granted) {
    Alert.alert(
      'Permission Required',
      'Alarms may not fire reliably. Please enable in Settings.'
    );
  }
});
```

### Managing Alarms

```typescript
// Get all alarms
const alarms = await NativeAlarmManager.getAllAlarms();
console.log(`${alarms.length} alarms scheduled`);

// Get alarms by category
const medAlarms = await NativeAlarmManager.getAlarmsByCategory('medications');

// Get specific alarm
const alarm = await NativeAlarmManager.getAlarm('morning-alarm');

// Update an alarm
await NativeAlarmManager.updateAlarm(
  'morning-alarm',
  {
    id: 'morning-alarm',
    type: 'recurring',
    time: { hour: 9, minute: 0 }, // Changed time
    daysOfWeek: [1, 2, 3, 4, 5],
  },
  {
    title: 'Updated Morning Alarm',
  }
);

// Cancel specific alarm
await NativeAlarmManager.cancelAlarm('morning-alarm');

// Cancel all alarms in category
await NativeAlarmManager.cancelAlarmsByCategory('medications');

// Cancel all alarms
await NativeAlarmManager.cancelAllAlarms();
```

## API Reference

### `checkCapability()`

Returns information about alarm capabilities on the current device.

```typescript
const capability = await NativeAlarmManager.checkCapability();
```

**Returns:** `AlarmCapabilityCheck`

```typescript
{
  capability: 'native_alarms' | 'notification' | 'inexact' | 'none',
  reason: string,
  requiresPermission: boolean,
  canRequestPermission: boolean,
  platformDetails: {
    platform: 'ios' | 'android',
    version: number | string,
    // iOS specific
    alarmKitAvailable?: boolean,
    alarmKitAuthStatus?: 'notDetermined' | 'authorized' | 'denied',
    // Android specific
    canScheduleExactAlarms?: boolean
  }
}
```

### `requestPermission()`

Requests alarm permission from the user.

```typescript
const granted = await NativeAlarmManager.requestPermission();
```

**Returns:** `Promise<boolean>` - Whether permission was granted

**Platform Notes:**
- **iOS 26+**: Shows AlarmKit authorization dialog
- **Android 12+**: Opens system settings for SCHEDULE_EXACT_ALARM
- **Other platforms**: Returns `true` (no permission needed)

### `scheduleAlarm(schedule, config)`

Schedules a new alarm.

```typescript
const alarm = await NativeAlarmManager.scheduleAlarm(schedule, config);
```

**Parameters:**

**`schedule: AlarmSchedule`**

```typescript
{
  id: string,                    // Unique identifier
  type: 'fixed' | 'recurring' | 'interval',

  // For 'fixed' and 'recurring':
  time?: {
    hour: number,                // 0-23
    minute: number               // 0-59
  },
  date?: Date,                   // For 'fixed' only

  // For 'recurring':
  daysOfWeek?: number[],         // 0=Sunday, 6=Saturday

  // For 'interval':
  intervalMinutes?: number,
  startTime?: Date
}
```

**`config: AlarmConfig`**

```typescript
{
  title: string,
  body?: string,
  sound?: string,                // 'default', 'none', or custom sound name
  category?: string,             // For grouping alarms
  color?: string,                // Hex color (e.g., '#007AFF')
  data?: Record<string, any>,    // Custom metadata
  actions?: AlarmAction[]
}
```

**`AlarmAction`**

```typescript
{
  id: string,
  title: string,
  behavior: 'dismiss' | 'snooze' | 'custom',
  snoozeDuration?: number,       // Minutes (for 'snooze')
  icon?: string,                 // Platform-specific icon name
  color?: string                 // Hex color
}
```

**Returns:** `Promise<ScheduledAlarm>`

### Event Listeners

**`onAlarmFired(callback)`**

Called when an alarm fires.

```typescript
const unsubscribe = NativeAlarmManager.onAlarmFired((event) => {
  // event.alarm - The alarm that fired
  // event.firedAt - Actual fire time
  // event.action - Action taken (if any)
});
```

**`onPermissionChanged(callback)`**

Called when permission status changes.

```typescript
const unsubscribe = NativeAlarmManager.onPermissionChanged((event) => {
  // event.granted - Whether permission is granted
  // event.capability - New capability level
  // event.platform - 'ios' or 'android'
});
```

## Error Handling

```typescript
import { AlarmError, AlarmErrorCode } from 'rn-native-alarmkit';

try {
  await NativeAlarmManager.scheduleAlarm(schedule, config);
} catch (error) {
  if (error instanceof AlarmError) {
    switch (error.code) {
      case AlarmErrorCode.PERMISSION_DENIED:
        // Handle permission denied
        break;
      case AlarmErrorCode.INVALID_SCHEDULE:
        // Handle invalid schedule
        break;
      case AlarmErrorCode.SYSTEM_ERROR:
        // Handle system error
        break;
    }
  }
}
```

## Best Practices

### 1. Always Check Capability First

```typescript
const capability = await NativeAlarmManager.checkCapability();

if (capability.capability === 'none') {
  // Show error to user
  Alert.alert('Alarms Not Supported', 'Your device does not support reliable alarms');
  return;
}

if (capability.capability === 'inexact') {
  // Warn user about timing accuracy
  Alert.alert(
    'Limited Accuracy',
    'Alarms may not fire at exact times. Grant permission for exact alarms in Settings.'
  );
}
```

### 2. Request Permission Before Scheduling

```typescript
if (capability.requiresPermission && capability.canRequestPermission) {
  const granted = await NativeAlarmManager.requestPermission();

  if (!granted) {
    // Explain why permission is needed
    Alert.alert(
      'Permission Required',
      'We need alarm permission to remind you at exact times for medication.'
    );
    return;
  }
}
```

### 3. Use Categories for Organization

```typescript
// Schedule all medication alarms with same category
await NativeAlarmManager.scheduleAlarm(
  { id: 'med-1', ... },
  { title: 'Med 1', category: 'medications' }
);

// Later: cancel all medication alarms at once
await NativeAlarmManager.cancelAlarmsByCategory('medications');
```

### 4. Store Alarm IDs

```typescript
// Store alarm ID in your local database
const alarm = await NativeAlarmManager.scheduleAlarm(schedule, config);

await database.execute(
  'INSERT INTO alarms (id, medication_id) VALUES (?, ?)',
  [alarm.id, medicationId]
);
```

### 5. Handle Platform Differences

```typescript
const actions: AlarmAction[] = [
  {
    id: 'taken',
    title: 'Taken',
    behavior: 'dismiss',
    icon: Platform.select({
      ios: 'checkmark.circle.fill',    // SF Symbol
      android: 'ic_check_circle'        // Material Icon
    })
  }
];
```

## Troubleshooting

### iOS: Alarms Not Firing

1. Check iOS version: `Settings > General > About > Software Version` (need iOS 26+)
2. Verify permission: Check capability status
3. Check notification settings: `Settings > Notifications > [Your App]`

### Android: Alarms Not Firing

1. Check battery optimization: `Settings > Apps > [Your App] > Battery > Unrestricted`
2. Verify permission:
   ```typescript
   const capability = await NativeAlarmManager.checkCapability();
   console.log(capability.platformDetails.canScheduleExactAlarms);
   ```
3. Grant exact alarm permission: `Settings > Apps > Special Access > Alarms & reminders`

### Alarms Not Persisting After Reboot

Android alarms are cleared on reboot. Implement a boot receiver to reschedule:

```kotlin
// In AndroidManifest.xml (already included if you followed setup)
<receiver android:name=".BootReceiver">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

```typescript
// In your app initialization
if (Platform.OS === 'android') {
  const alarms = await NativeAlarmManager.getAllAlarms();

  // Reschedule all alarms
  for (const alarm of alarms) {
    await NativeAlarmManager.updateAlarm(
      alarm.id,
      alarm.schedule,
      alarm.config
    );
  }
}
```

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## License

MIT © [Your Name]

## Related Documentation

- [iOS AlarmKit Documentation](https://developer.apple.com/documentation/AlarmKit)
- [Android AlarmManager Documentation](https://developer.android.com/develop/background-work/services/alarms/schedule)
- [Complete API Documentation](./NATIVE_ALARM_APIS.md)

## Support

If you encounter any issues or have questions:

1. Check the [troubleshooting section](#troubleshooting)
2. Review the [API documentation](./NATIVE_ALARM_APIS.md)
3. Open an issue on [GitHub](https://github.com/n8stowell82/rn-native-alarmkit/issues)
