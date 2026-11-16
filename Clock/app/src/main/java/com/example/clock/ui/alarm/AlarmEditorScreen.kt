package com.example.clock.ui.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.clock.ClockApp
import com.example.clock.data.Alarm
import com.example.clock.ui.ClockCopy

@Composable
fun AlarmEditorScreen(app: ClockApp, alarmId: Long, onClose: () -> Unit) {
    val existing = if (alarmId == 0L) null else app.alarms.find(alarmId)
    var hour by remember { mutableStateOf((existing?.hour ?: 7).toString()) }
    var minute by remember { mutableStateOf((existing?.minute ?: 0).toString().padStart(2, '0')) }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var repeatDays by remember { mutableStateOf(existing?.repeatDays ?: 0) }
    var vibrate by remember { mutableStateOf(existing?.vibrate ?: true) }
    var volumeRamp by remember { mutableStateOf(existing?.volumeRamp ?: true) }
    var slide by remember { mutableStateOf(existing?.slideToDismiss ?: true) }
    var shake by remember { mutableStateOf(existing?.shakeToDismiss ?: false) }
    var snooze by remember { mutableStateOf(existing?.snoozeMinutes ?: 10) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = if (existing == null) ClockCopy.EditorNewTitle else ClockCopy.EditorEditTitle, style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = hour, onValueChange = { hour = it }, label = { Text("時") }, modifier = Modifier.fillMaxWidth(0.45f))
            OutlinedTextField(value = minute, onValueChange = { minute = it }, label = { Text("分") }, modifier = Modifier.fillMaxWidth(0.45f))
        }
        OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(ClockCopy.LabelHint) }, modifier = Modifier.fillMaxWidth())
        Text(text = ClockCopy.RepeatLabel)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { repeatDays = 0 }) { Text(ClockCopy.RepeatOnce) }
            Button(onClick = { repeatDays = 0b1111111 }) { Text(ClockCopy.RepeatEveryDay) }
            Button(onClick = { repeatDays = 0b0011111 }) { Text(ClockCopy.RepeatWeekdays) }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = vibrate, onCheckedChange = { vibrate = it })
            Text(text = ClockCopy.Vibrate)
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = volumeRamp, onCheckedChange = { volumeRamp = it })
            Text(text = ClockCopy.VolumeRamp)
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = slide, onCheckedChange = { slide = it })
            Text(text = ClockCopy.DismissSlide)
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = shake, onCheckedChange = { shake = it })
            Text(text = ClockCopy.DismissShake)
        }
        Text(text = "${ClockCopy.SnoozeLabel}: $snooze")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 20, 30).forEach { m ->
                Button(onClick = { snooze = m }) { Text(m.toString()) }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val h = hour.toIntOrNull()?.coerceIn(0, 23) ?: 0
                val m = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                val alarm = Alarm(
                    id = existing?.id ?: 0L,
                    hour = h,
                    minute = m,
                    label = label,
                    enabled = existing?.enabled ?: true,
                    repeatDays = repeatDays,
                    vibrate = vibrate,
                    volumeRamp = volumeRamp,
                    slideToDismiss = slide,
                    shakeToDismiss = shake,
                    snoozeMinutes = snooze,
                )
                app.alarms.save(alarm)
                onClose()
            }) { Text(ClockCopy.Save) }
            TextButton(onClick = onClose) { Text(ClockCopy.Cancel) }
            if (existing != null) {
                TextButton(onClick = { app.alarms.delete(existing.id); onClose() }) { Text(ClockCopy.DeleteConfirm) }
            }
        }
    }
}
