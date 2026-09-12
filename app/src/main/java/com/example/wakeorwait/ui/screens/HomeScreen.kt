package com.example.wakeorwait.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wakeorwait.alarm.AlarmScheduler
import com.example.wakeorwait.data.model.Alarm
import com.example.wakeorwait.data.model.Difficulty
import com.example.wakeorwait.data.repository.AlarmRepository
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(
    onNavigateToSetAlarm: (String?) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AlarmRepository.getInstance(context) }
    val scheduler = remember { AlarmScheduler(context) }

    val alarms by repository.alarms.collectAsState()
    val settings by repository.settings.collectAsState()

    val nextAlarm = remember(alarms) {
        repository.getNextActiveAlarm()
    }

    val humorousQuotes = listOf(
        "Sleep peacefully. Your future self won't.",
        "You can't snooze. You have to earn it.",
        "20 jumping jacks stand between you and breakfast.",
        "The alarm sounds loud. Your willpower better be louder.",
        "Tomorrow morning called. It wants 20 jumping jacks."
    )
    val randomQuote = remember { humorousQuotes.random() }

    Scaffold(
        containerColor = Color(0xFF0D1117),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToSetAlarm(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Alarm") },
                text = { Text("Set Alarm", fontWeight = FontWeight.Bold) },
                containerColor = Color(0xFFFF5722),
                contentColor = Color.White
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                // App Header
                Column {
                    Text(
                        text = "WakeOrWait",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "“You can't snooze. You have to earn it.”",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFFB74D)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = randomQuote,
                        fontSize = 12.sp,
                        color = Color(0xFF90A4AE)
                    )
                }
            }

            // Next Alarm Card
            item {
                NextAlarmCard(
                    nextAlarm = nextAlarm,
                    is24Hour = settings.is24HourFormat,
                    onToggle = { alarm, isEnabled ->
                        repository.toggleAlarm(alarm.id, isEnabled)
                        if (isEnabled) {
                            scheduler.scheduleAlarm(alarm.copy(isEnabled = true))
                        } else {
                            scheduler.cancelAlarm(alarm.id)
                        }
                    },
                    onTestTrigger = { alarm ->
                        // Schedule alarm 5 seconds from now for instant testing
                        val testAlarm = alarm.copy(
                            hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
                            minute = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)
                        )
                        // Trigger immediate test
                        val intent = Intent(context, AlarmActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra(AlarmScheduler.EXTRA_ALARM_ID, testAlarm.id)
                            putExtra(AlarmScheduler.EXTRA_DIFFICULTY, testAlarm.difficulty.name)
                            putExtra(AlarmScheduler.EXTRA_RINGTONE_NAME, testAlarm.ringtoneName)
                            putExtra(AlarmScheduler.EXTRA_RINGTONE_URI, testAlarm.ringtoneUri)
                            putExtra(AlarmScheduler.EXTRA_ALARM_TIME, testAlarm.formatTime(settings.is24HourFormat))
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // Action Buttons Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF81D4FA))
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Alarm History", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB0BEC5))
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Settings", fontSize = 13.sp)
                    }
                }
            }

            // Section: All Alarms
            item {
                Text(
                    text = "ALL ALARMS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = Color(0xFF78909C)
                )
            }

            if (alarms.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No alarms set yet. Tap '+ Set Alarm' below!",
                                color = Color(0xFF90A4AE),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmListItem(
                        alarm = alarm,
                        is24Hour = settings.is24HourFormat,
                        onClick = { onNavigateToSetAlarm(alarm.id) },
                        onToggle = { isEnabled ->
                            repository.toggleAlarm(alarm.id, isEnabled)
                            if (isEnabled) {
                                scheduler.scheduleAlarm(alarm.copy(isEnabled = true))
                            } else {
                                scheduler.cancelAlarm(alarm.id)
                            }
                        },
                        onDelete = {
                            scheduler.cancelAlarm(alarm.id)
                            repository.deleteAlarm(alarm.id)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(70.dp))
            }
        }
    }
}

@Composable
fun NextAlarmCard(
    nextAlarm: Alarm?,
    is24Hour: Boolean,
    onToggle: (Alarm, Boolean) -> Unit,
    onTestTrigger: (Alarm) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1F2430)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (nextAlarm != null) Color(0xFF00E676) else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NEXT ALARM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color(0xFF90A4AE)
                    )
                }

                if (nextAlarm != null) {
                    Switch(
                        checked = nextAlarm.isEnabled,
                        onCheckedChange = { onToggle(nextAlarm, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFF5722),
                            checkedTrackColor = Color(0xFFFF5722).copy(alpha = 0.3f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (nextAlarm != null) {
                Text(
                    text = nextAlarm.formatTime(is24Hour),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Countdown text
                val timeDiffMillis = nextAlarm.getNextTriggerTimeMillis() - System.currentTimeMillis()
                val hours = TimeUnit.MILLISECONDS.toHours(timeDiffMillis)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(timeDiffMillis) % 60
                val countdownText = if (hours > 0) {
                    "Alarm in $hours hr $minutes min"
                } else if (minutes > 0) {
                    "Alarm in $minutes min"
                } else {
                    "Alarm due soon"
                }

                Text(
                    text = countdownText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF81D4FA)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(nextAlarm.difficulty.colorHex).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${nextAlarm.difficulty.emoji} ${nextAlarm.difficulty.displayName} Mode",
                                color = Color(nextAlarm.difficulty.colorHex),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            color = Color(0xFF263238),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "🎵 ${nextAlarm.ringtoneName}",
                                color = Color(0xFFCFD8DC),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Quick test trigger button
                    IconButton(
                        onClick = { onTestTrigger(nextAlarm) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF37474F), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Test Alarm Now",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = "No Alarms Active",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Turn on an existing alarm or create a new one.",
                    fontSize = 13.sp,
                    color = Color(0xFF90A4AE)
                )
            }
        }
    }
}

@Composable
fun AlarmListItem(
    alarm: Alarm,
    is24Hour: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) Color(0xFF1A1F29) else Color(0xFF13171F)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.formatTime(is24Hour),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (alarm.isEnabled) Color.White else Color(0xFF78909C)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${alarm.difficulty.emoji} ${alarm.difficulty.displayName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(alarm.difficulty.colorHex)
                    )
                    Text(
                        text = "•",
                        color = Color(0xFF546E7A),
                        fontSize = 12.sp
                    )
                    Text(
                        text = alarm.repeatDaysText(),
                        fontSize = 12.sp,
                        color = Color(0xFFB0BEC5)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "🎵 ${alarm.ringtoneName}",
                    fontSize = 11.sp,
                    color = Color(0xFF78909C)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Alarm",
                        tint = Color(0xFF546E7A),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFF5722),
                        checkedTrackColor = Color(0xFFFF5722).copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}
