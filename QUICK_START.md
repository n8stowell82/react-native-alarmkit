# Quick Start Guide

This guide will help you integrate `react-native-alarmkit` into your React Native app for reliable alarm scheduling.

## Installation

```bash
npm install react-native-alarmkit
cd ios && pod install && cd ..
```

## Basic Setup (5 minutes)

### 1. Check Device Capability

```typescript
import NativeAlarmManager from 'react-native-alarmkit';

// In your component or app initialization
const checkAlarmSupport = async () => {
  const capability = await NativeAlarmManager.checkCapability();

  console.log('Alarm capability:', capability.capability);
  // 'native_alarms' - Best, breaks through DND
  // 'notification' - Good, may be silenced
  // 'inexact' - Limited, timing not guaranteed
  // 'none' - Not supported

  if (capability.requiresPermission) {
    // Need to request permission
    const granted = await NativeAlarmManager.requestPermission();

    if (!granted) {
      Alert.alert(
        'Permission Needed',
        'For exact alarm timing, please enable alarms in Settings'
      );
    }
  }
};
```

### 2. Schedule Your First Alarm

```typescript
const scheduleAlarm = async () => {
  try {
    const alarm = await NativeAlarmManager.scheduleAlarm(
      // Schedule
      {
        id: 'my-first-alarm',
        type: 'recurring',
        time: { hour: 8, minute: 0 },
        daysOfWeek: [1, 2, 3, 4, 5], // Mon-Fri
      },
      // Config
      {
        title: 'Good Morning!',
        body: 'Time to start your day',
        actions: [
          { id: 'dismiss', title: 'Dismiss', behavior: 'dismiss' },
          { id: 'snooze', title: 'Snooze', behavior: 'snooze', snoozeDuration: 10 },
        ],
      }
    );

    console.log('Alarm scheduled:', alarm.nextFireDate);
  } catch (error) {
    console.error('Failed to schedule alarm:', error);
  }
};
```

### 3. Listen for Alarms

```typescript
import { useEffect } from 'react';

function AlarmListener() {
  useEffect(() => {
    // Listen for alarm events
    const unsubscribe = NativeAlarmManager.onAlarmFired((event) => {
      console.log('🔔 Alarm fired!', event.alarm.config.title);

      // Show in-app notification, update UI, etc.
      Alert.alert(
        event.alarm.config.title,
        event.alarm.config.body
      );
    });

    // Cleanup
    return () => unsubscribe();
  }, []);

  return null;
}
```

## Common Use Cases

### Medication Reminder

```typescript
const scheduleMedicationReminder = async (
  medicationId: string,
  medicationName: string,
  times: Array<{ hour: number; minute: number }>
) => {
  for (const time of times) {
    await NativeAlarmManager.scheduleAlarm(
      {
        id: `med-${medicationId}-${time.hour}${time.minute}`,
        type: 'recurring',
        time,
        daysOfWeek: [0, 1, 2, 3, 4, 5, 6], // Every day
      },
      {
        title: `Take ${medicationName}`,
        body: `Time for your ${medicationName} dose`,
        category: 'medications',
        data: { medicationId, time },
        actions: [
          { id: 'taken', title: 'Taken', behavior: 'dismiss' },
          { id: 'snooze', title: 'Later', behavior: 'snooze', snoozeDuration: 15 },
        ],
      }
    );
  }
};

// Usage
await scheduleMedicationReminder(
  'med-001',
  'Aspirin 100mg',
  [
    { hour: 8, minute: 0 },
    { hour: 20, minute: 0 },
  ]
);
```

### Hydration Reminder (Every 2 hours)

```typescript
const scheduleHydrationReminder = async () => {
  await NativeAlarmManager.scheduleAlarm(
    {
      id: 'hydration-reminder',
      type: 'interval',
      intervalMinutes: 120, // 2 hours
    },
    {
      title: '💧 Drink Water',
      body: 'Stay hydrated!',
      category: 'health',
    }
  );
};
```

### Workout Reminder (Specific days)

```typescript
const scheduleWorkoutReminder = async () => {
  await NativeAlarmManager.scheduleAlarm(
    {
      id: 'workout-reminder',
      type: 'recurring',
      time: { hour: 18, minute: 0 }, // 6 PM
      daysOfWeek: [1, 3, 5], // Mon, Wed, Fri
    },
    {
      title: '💪 Workout Time',
      body: "Let's get moving!",
      category: 'fitness',
    }
  );
};
```

## Managing Alarms

### View All Alarms

```typescript
const viewAllAlarms = async () => {
  const alarms = await NativeAlarmManager.getAllAlarms();

  console.log(`You have ${alarms.length} alarms scheduled`);

  alarms.forEach(alarm => {
    console.log(`- ${alarm.config.title} (${alarm.nextFireDate})`);
  });
};
```

### Cancel Specific Alarm

```typescript
const cancelAlarm = async (alarmId: string) => {
  await NativeAlarmManager.cancelAlarm(alarmId);
  console.log('Alarm cancelled');
};
```

### Cancel All Medication Alarms

```typescript
const cancelAllMedications = async () => {
  await NativeAlarmManager.cancelAlarmsByCategory('medications');
  console.log('All medication alarms cancelled');
};
```

### Update Alarm Time

```typescript
const updateAlarmTime = async (alarmId: string, newTime: { hour: number; minute: number }) => {
  const alarm = await NativeAlarmManager.getAlarm(alarmId);

  if (alarm) {
    await NativeAlarmManager.updateAlarm(
      alarmId,
      {
        ...alarm.schedule,
        time: newTime,
      },
      alarm.config
    );

    console.log('Alarm updated');
  }
};
```

## Complete Component Example

```typescript
import React, { useEffect, useState } from 'react';
import { View, Text, Button, Alert } from 'react-native';
import NativeAlarmManager, { AlarmCapability } from 'react-native-alarmkit';

export function AlarmManager() {
  const [capability, setCapability] = useState<AlarmCapability | null>(null);
  const [alarmCount, setAlarmCount] = useState(0);

  useEffect(() => {
    // Check capability on mount
    checkCapability();

    // Load alarm count
    loadAlarmCount();

    // Listen for alarms
    const unsubscribe = NativeAlarmManager.onAlarmFired((event) => {
      Alert.alert(
        event.alarm.config.title,
        event.alarm.config.body || '',
        [
          { text: 'OK', onPress: () => console.log('Alarm dismissed') },
        ]
      );
    });

    return () => unsubscribe();
  }, []);

  const checkCapability = async () => {
    const cap = await NativeAlarmManager.checkCapability();
    setCapability(cap.capability);

    if (cap.requiresPermission && cap.canRequestPermission) {
      // Auto-request permission
      await NativeAlarmManager.requestPermission();
    }
  };

  const loadAlarmCount = async () => {
    const alarms = await NativeAlarmManager.getAllAlarms();
    setAlarmCount(alarms.length);
  };

  const scheduleTestAlarm = async () => {
    try {
      // Schedule alarm for 1 minute from now
      const now = new Date();
      const testTime = new Date(now.getTime() + 60000);

      await NativeAlarmManager.scheduleAlarm(
        {
          id: `test-${Date.now()}`,
          type: 'fixed',
          time: {
            hour: testTime.getHours(),
            minute: testTime.getMinutes(),
          },
        },
        {
          title: 'Test Alarm',
          body: 'This is a test alarm',
        }
      );

      Alert.alert('Success', 'Test alarm scheduled for 1 minute from now');
      loadAlarmCount();
    } catch (error) {
      Alert.alert('Error', 'Failed to schedule alarm');
    }
  };

  const clearAllAlarms = async () => {
    await NativeAlarmManager.cancelAllAlarms();
    Alert.alert('Success', 'All alarms cleared');
    loadAlarmCount();
  };

  return (
    <View style={{ padding: 20 }}>
      <Text style={{ fontSize: 20, marginBottom: 20 }}>Alarm Manager</Text>

      <Text>Capability: {capability || 'Checking...'}</Text>
      <Text>Active alarms: {alarmCount}</Text>

      <Button title="Schedule Test Alarm" onPress={scheduleTestAlarm} />
      <Button title="Clear All Alarms" onPress={clearAllAlarms} />
    </View>
  );
}
```

## Integration with GentleTrack

For your GentleTrack medication reminder app:

```typescript
// In your medication service
import NativeAlarmManager from 'react-native-alarmkit';

export class MedicationAlarmService {
  // Schedule alarms when user adds medication
  async scheduleMedicationAlarms(medication: Medication) {
    const { id, name, timeSlots } = medication;

    for (const timeSlot of timeSlots) {
      const [hour, minute] = timeSlot.split(':').map(Number);

      await NativeAlarmManager.scheduleAlarm(
        {
          id: `med-${id}-${timeSlot}`,
          type: 'recurring',
          time: { hour, minute },
          daysOfWeek: [0, 1, 2, 3, 4, 5, 6],
        },
        {
          title: `Take ${name}`,
          body: `Time for your ${name} dose`,
          category: 'medications',
          data: { medicationId: id, timeSlot },
          sound: 'medication_alert',
          actions: [
            { id: 'taken', title: 'Taken', behavior: 'dismiss' },
            { id: 'skip', title: 'Skip', behavior: 'dismiss' },
            { id: 'snooze', title: 'Snooze 15m', behavior: 'snooze', snoozeDuration: 15 },
          ],
        }
      );
    }
  }

  // Cancel alarms when user deletes medication
  async cancelMedicationAlarms(medicationId: string) {
    const alarms = await NativeAlarmManager.getAllAlarms();

    for (const alarm of alarms) {
      if (alarm.config.data?.medicationId === medicationId) {
        await NativeAlarmManager.cancelAlarm(alarm.id);
      }
    }
  }

  // Handle alarm fired
  setupAlarmListener() {
    return NativeAlarmManager.onAlarmFired(async (event) => {
      const { medicationId, timeSlot } = event.alarm.config.data || {};

      if (medicationId) {
        // Log in local database
        await this.logMedicationTaken(medicationId, timeSlot);

        // Update UI
        this.notifyListeners({
          type: 'alarm_fired',
          medicationId,
          timeSlot,
        });
      }
    });
  }
}
```

## Troubleshooting

### Alarms Not Firing?

1. **Check capability:**
   ```typescript
   const cap = await NativeAlarmManager.checkCapability();
   console.log(cap);
   ```

2. **Request permission if needed:**
   ```typescript
   if (cap.requiresPermission) {
     await NativeAlarmManager.requestPermission();
   }
   ```

3. **Check battery optimization (Android):**
   - Settings > Apps > Your App > Battery > Unrestricted

4. **Test with a short interval:**
   ```typescript
   // Schedule alarm for 1 minute from now
   await NativeAlarmManager.scheduleAlarm(
     { id: 'test', type: 'interval', intervalMinutes: 1 },
     { title: 'Test' }
   );
   ```

## Next Steps

- Check out the [full API documentation](./README.md)
- Review [detailed implementation guide](./NATIVE_ALARM_APIS.md)
- See [implementation status](./IMPLEMENTATION_STATUS.md) for Android completion status

## Support

If you need help, check the documentation or open an issue on GitHub.
