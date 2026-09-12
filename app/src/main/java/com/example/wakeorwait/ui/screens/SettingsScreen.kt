package com.example.wakeorwait.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wakeorwait.data.model.Difficulty
import com.example.wakeorwait.data.repository.AlarmRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AlarmRepository.getInstance(context) }
    val settings by repository.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D1117))
            )
        },
        containerColor = Color(0xFF0D1117)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SectionHeader("GENERAL")
            }

            // 12/24 Hour Format
            item {
                SettingsToggleCard(
                    title = "24-Hour Time Format",
                    subtitle = if (settings.is24HourFormat) "e.g. 19:00" else "e.g. 7:00 PM",
                    isChecked = settings.is24HourFormat,
                    onCheckedChange = { isChecked ->
                        repository.updateSettings(settings.copy(is24HourFormat = isChecked))
                    }
                )
            }

            // Default Difficulty
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Default Difficulty",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Difficulty.entries.forEach { diff ->
                                val isSelected = settings.defaultDifficulty == diff
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        repository.updateSettings(settings.copy(defaultDifficulty = diff))
                                    },
                                    label = { Text("${diff.emoji} ${diff.displayName}") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(diff.colorHex).copy(alpha = 0.25f),
                                        selectedLabelColor = Color(diff.colorHex)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader("CHALLENGE SETTINGS")
            }

            // Jumping Jacks Rep Target
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
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
                                text = "Jumping Jacks Goal",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Reps required to dismiss first challenge",
                                fontSize = 12.sp,
                                color = Color(0xFF90A4AE)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (settings.jumpingJacksTarget > 5) {
                                        repository.updateSettings(
                                            settings.copy(jumpingJacksTarget = settings.jumpingJacksTarget - 5)
                                        )
                                    }
                                }
                            ) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease", tint = Color.White)
                            }

                            Text(
                                text = "${settings.jumpingJacksTarget}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            IconButton(
                                onClick = {
                                    if (settings.jumpingJacksTarget < 50) {
                                        repository.updateSettings(
                                            settings.copy(jumpingJacksTarget = settings.jumpingJacksTarget + 5)
                                        )
                                    }
                                }
                            ) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase", tint = Color.White)
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader("DEVICE PERMISSIONS & GUIDANCE")
            }

            // Exact Alarm Permissions
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                context.startActivity(intent)
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AlarmOn,
                            contentDescription = null,
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Exact Alarm Permission",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Required for ringing at the exact second even in Doze mode.",
                                fontSize = 12.sp,
                                color = Color(0xFF90A4AE)
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF78909C)
                        )
                    }
                }
            }

            item {
                SectionHeader("PRIVACY")
            }

            // Privacy Assurance Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131D24))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "On-Device Camera Privacy",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Camera processing happens on your device. WakeOrWait does not upload your camera feed, save pictures, or send data to external servers.",
                                fontSize = 12.sp,
                                color = Color(0xFFB0BEC5),
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = Color(0xFF78909C)
    )
}

@Composable
fun SettingsToggleCard(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
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
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF90A4AE)
                )
            }

            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFFF5722),
                    checkedTrackColor = Color(0xFFFF5722).copy(alpha = 0.3f)
                )
            )
        }
    }
}
