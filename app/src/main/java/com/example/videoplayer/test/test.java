package com.example.videoplayer.test;

// 1. Add dependencies in build.gradle.kts (Module: app)
/*
dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
}
*/

// 2. AndroidManifest.xml - Add permissions
/*
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
*/

// 3. BluetoothAutoOffWorker.kt
package com.example.yourapp.worker

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class BluetoothAutoOffWorker(
        context: Context,
        params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter

            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            applicationContext,
                            android.Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        bluetoothAdapter.disable()
                    }
                } else {
                    bluetoothAdapter.disable()
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}

// 4. BluetoothScheduler.kt
package com.example.yourapp.scheduler

import android.content.Context
import androidx.work.*
        import com.example.yourapp.worker.BluetoothAutoOffWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

object BluetoothScheduler {
private const val WORK_NAME = "bluetooth_auto_off_work"

fun scheduleBluetoothOffAt(context: Context, timeInMillis: Long) {
    val currentTime = System.currentTimeMillis()
    val delay = timeInMillis - currentTime

    if (delay > 0) {
        val workRequest = OneTimeWorkRequestBuilder<BluetoothAutoOffWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
        )
    }
}

fun scheduleBluetoothOff(context: Context, delayMinutes: Long) {
    val workRequest = OneTimeWorkRequestBuilder<BluetoothAutoOffWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
    )
}

fun cancelScheduledWork(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
}

fun isWorkScheduled(context: Context): Boolean {
    val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WORK_NAME).get()
    return workInfos.any { !it.state.isFinished }
}
}

// 5. DataStore for preferences
        package com.example.yourapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bluetooth_settings")

class BluetoothPreferences(private val context: Context) {
    companion object {
        val AUTO_OFF_ENABLED = booleanPreferencesKey("auto_off_enabled")
        val SCHEDULE_MODE = stringPreferencesKey("schedule_mode") // "timer" or "specific_time"
        val TIME_INTERVAL = longPreferencesKey("time_interval")
        val SCHEDULED_TIME = longPreferencesKey("scheduled_time")
    }

    val autoOffEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
            prefs[AUTO_OFF_ENABLED] ?: false
    }

    val scheduleMode: Flow<String> = context.dataStore.data.map { prefs ->
            prefs[SCHEDULE_MODE] ?: "timer"
    }

    val timeInterval: Flow<Long> = context.dataStore.data.map { prefs ->
            prefs[TIME_INTERVAL] ?: 30L
    }

    val scheduledTime: Flow<Long> = context.dataStore.data.map { prefs ->
            prefs[SCHEDULED_TIME] ?: 0L
    }

    suspend fun setAutoOffEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
                prefs[AUTO_OFF_ENABLED] = enabled
        }
    }

    suspend fun setScheduleMode(mode: String) {
        context.dataStore.edit { prefs ->
                prefs[SCHEDULE_MODE] = mode
        }
    }

    suspend fun setTimeInterval(minutes: Long) {
        context.dataStore.edit { prefs ->
                prefs[TIME_INTERVAL] = minutes
        }
    }

    suspend fun setScheduledTime(timeInMillis: Long) {
        context.dataStore.edit { prefs ->
                prefs[SCHEDULED_TIME] = timeInMillis
        }
    }
}

// 6. Jetpack Compose UI with Time/Date Picker
package com.example.yourapp.ui

import androidx.compose.foundation.layout.*
        import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
        import androidx.compose.runtime.*
        import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yourapp.data.BluetoothPreferences
import com.example.yourapp.scheduler.BluetoothScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothAutoOffScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { BluetoothPreferences(context) }

    val autoOffEnabled by preferences.autoOffEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scheduleMode by preferences.scheduleMode.collectAsStateWithLifecycle(initialValue = "timer")
    val timeInterval by preferences.timeInterval.collectAsStateWithLifecycle(initialValue = 30L)
    val scheduledTime by preferences.scheduledTime.collectAsStateWithLifecycle(initialValue = 0L)

    var selectedMinutes by remember { mutableStateOf(timeInterval) }
    var selectedDateTime by remember { mutableStateOf(Calendar.getInstance()) }

    val timePickerState = rememberTimePickerState(
            initialHour = selectedDateTime.get(Calendar.HOUR_OF_DAY),
            initialMinute = selectedDateTime.get(Calendar.MINUTE)
    )
    val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateTime.timeInMillis
    )

    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(timeInterval) {
        selectedMinutes = timeInterval
    }

    Column(
            modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
                text = "Bluetooth Auto-Off",
                style = MaterialTheme.typography.headlineMedium
        )

        Card(
                modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Auto-Off")
                    Switch(
                            checked = autoOffEnabled,
                            onCheckedChange = { enabled ->
                                    scope.launch {
                                    preferences.setAutoOffEnabled(enabled)
                    if (!enabled) {
                        BluetoothScheduler.cancelScheduledWork(context)
                    }
                            }
                        }
                    )
                }

                if (autoOffEnabled) {
                    Divider()

                    Text(
                            text = "Schedule Mode",
                            style = MaterialTheme.typography.titleMedium
                    )

                    Column(
                            modifier = Modifier.selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                    selected = scheduleMode == "timer",
                                    onClick = {
                                            scope.launch {
                                            preferences.setScheduleMode("timer")
                                    }
                                    }
                            )
                            Text("After specific duration")
                        }

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                    selected = scheduleMode == "specific_time",
                                    onClick = {
                                            scope.launch {
                                            preferences.setScheduleMode("specific_time")
                                    }
                                    }
                            )
                            Text("At specific date & time")
                        }
                    }

                    Divider()

                    if (scheduleMode == "timer") {
                        // Timer Mode UI
                        Text(
                                text = "Turn off after: $selectedMinutes minutes",
                                style = MaterialTheme.typography.bodyLarge
                        )

                        Slider(
                                value = selectedMinutes.toFloat(),
                                onValueChange = { selectedMinutes = it.toLong() },
                                valueRange = 5f..120f,
                                steps = 22,
                                modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("5 min", style = MaterialTheme.typography.bodySmall)
                            Text("120 min", style = MaterialTheme.typography.bodySmall)
                        }

                        Button(
                                onClick = {
                                        scope.launch {
                                        preferences.setTimeInterval(selectedMinutes)
                                        BluetoothScheduler.scheduleBluetoothOff(context, selectedMinutes)
                                }
                                },
                                modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Timer")
                        }
                    } else {
                        // Specific Time Mode UI
                        OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { showDatePicker = true }
                        ) {
                            Row(
                                    modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                            text = "Date",
                                            style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                            text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                                    .format(selectedDateTime.time),
                                            style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                Icon(Icons.Default.Schedule, contentDescription = "Pick date")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { showTimePicker = true }
                        ) {
                            Row(
                                    modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                            text = "Time",
                                            style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                            text = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                                    .format(selectedDateTime.time),
                                            style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                Icon(Icons.Default.Schedule, contentDescription = "Pick time")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                                onClick = {
                                        scope.launch {
                                        val timeInMillis = selectedDateTime.timeInMillis
                                        preferences.setScheduledTime(timeInMillis)
                                        BluetoothScheduler.scheduleBluetoothOffAt(context, timeInMillis)
                                }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedDateTime.timeInMillis > System.currentTimeMillis()
                        ) {
                            Text("Schedule")
                        }

                        if (selectedDateTime.timeInMillis <= System.currentTimeMillis()) {
                            Text(
                                    text = "Please select a future date and time",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        if (autoOffEnabled) {
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
            ) {
                val infoText = if (scheduleMode == "timer") {
                    "Bluetooth will turn off after $timeInterval minutes from now"
                } else {
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                    "Bluetooth will turn off on ${dateFormat.format(selectedDateTime.time)}"
                }

                Text(
                        text = infoText,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // Time Picker Dialog
    if (showTimePicker) {
        TimePickerDialog(
                onDismiss = { showTimePicker = false },
                onConfirm = {
                        selectedDateTime.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        selectedDateTime.set(Calendar.MINUTE, timePickerState.minute)
                        selectedDateTime.set(Calendar.SECOND, 0)
                        showTimePicker = false
                }
        ) {
            TimePicker(state = timePickerState)
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                        TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                        val calendar = Calendar.getInstance()
                        calendar.timeInMillis = millis
                        selectedDateTime.set(Calendar.YEAR, calendar.get(Calendar.YEAR))
                        selectedDateTime.set(Calendar.MONTH, calendar.get(Calendar.MONTH))
                        selectedDateTime.set(Calendar.DAY_OF_MONTH, calendar.get(Calendar.DAY_OF_MONTH))
                }
                        showDatePicker = false
                }) {
            Text("OK")
        }
            },
        dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                Text("Cancel")
        }
        }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun TimePickerDialog(
        onDismiss: () -> Unit,
onConfirm: () -> Unit,
content: @Composable () -> Unit
) {
AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = {
    TextButton(onClick = onDismiss) {
        Text("Cancel")
    }
},
confirmButton = {
TextButton(onClick = onConfirm) {
    Text("OK")
}
        },
text = { content() }
        )
        }

// 7. Request permissions in your Activity/MainActivity
/*
private val bluetoothPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->
    // Handle permission results
}

// In onCreate or appropriate place
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    bluetoothPermissionLauncher.launch(
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS
        )
    )
}
*/