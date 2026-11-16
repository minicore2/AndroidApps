package com.example.clock.ui.alarm

import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import com.example.clock.data.Alarm
import com.example.clock.data.dayBit
import com.example.clock.data.hasDay
import com.example.clock.ui.ClockCopy
import com.example.clock.ui.nextOccurrenceLabel
import com.example.clock.ui.theme.ClockMonoFont
import java.time.DayOfWeek

@Composable
fun AlarmListItem(
    alarm: Alarm,
    onEnabledChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onSkipNext: () -> Unit,
    onDelete: () -> Unit,
    onOverflowClick: () -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val timeColor: Color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant
    val secondaryText: String = when {
        !alarm.enabled -> ClockCopy.DisabledText
        alarm.skippedNext -> ClockCopy.SkippedBadge
        alarm.nextTriggerAt > 0L -> nextOccurrenceLabel(context, alarm.nextTriggerAt)
        else -> "未排定"
    }
    val secondaryColor: Color = when {
        alarm.skippedNext -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        leadingContent = {
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onEnabledChange,
            )
        },
        headlineContent = {
            Column {
                Text(
                    text = alarm.timeText,
                    style = TextStyle(
                        fontFamily = ClockMonoFont,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.W700,
                    ),
                    color = timeColor,
                )
                if (alarm.label.isNotBlank()) {
                    Text(
                        text = alarm.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor,
                    maxLines = 1,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = { onOverflowClick(); expanded = true }) {
                Text(text = "⋮")
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (alarm.enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
        ),
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DropdownMenuItem(
            text = { Text(text = ClockCopy.Edit) },
            onClick = { onClick(); expanded = false },
        )
        DropdownMenuItem(
            text = { Text(text = ClockCopy.SkipNext) },
            onClick = { onSkipNext(); expanded = false },
            enabled = alarm.enabled,
        )
        DropdownMenuItem(
            text = { Text(text = ClockCopy.DeleteConfirm) },
            onClick = { onDelete(); expanded = false },
        )
    }
}

fun dayShort(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> ClockCopy.DayMon
    DayOfWeek.TUESDAY -> ClockCopy.DayTue
    DayOfWeek.WEDNESDAY -> ClockCopy.DayWed
    DayOfWeek.THURSDAY -> ClockCopy.DayThu
    DayOfWeek.FRIDAY -> ClockCopy.DayFri
    DayOfWeek.SATURDAY -> ClockCopy.DaySat
    DayOfWeek.SUNDAY -> ClockCopy.DaySun
}

fun repeatSummary(repeatDays: Int): String {
    return when (repeatDays) {
        0 -> ClockCopy.RepeatOnce
        0b1111111 -> ClockCopy.RepeatEveryDay
        0b0011111 -> ClockCopy.RepeatWeekdays
        else -> {
            val days = (1..7).filter { hasDay(repeatDays, DayOfWeek.of(it)) }
            ClockCopy.RepeatWeeklyPrefix + days.joinToString("、") { dayShort(DayOfWeek.of(it)) }
        }
    }
}
