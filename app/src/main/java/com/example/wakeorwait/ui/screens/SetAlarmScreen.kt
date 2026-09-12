package com.example.wakeorwait.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wakeorwait.alarm.AlarmScheduler
import com.example.wakeorwait.audio.RingtoneManager
import com.example.wakeorwait.data.model.Alarm
import com.example.wakeorwait.data.model.CustomRingtone
import com.example.wakeorwait.data.model.Difficulty
import com.example.wakeorwait.data.repository.AlarmRepository
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetAlarmScreen(
    alarmIdToEdit: String?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AlarmRepository.getInstance(context) }
    val ringtoneManager = remember { RingtoneManager.getInstance(context) }
    val scheduler = remember { AlarmScheduler(context) }

    val settings by repository.settings.collectAsState()
    val customRingtones by repository.customRingtones.collectAsState()

    // Existing alarm or new alarm defaults
    val existingAlarm = remember(alarmIdToEdit) {
        if (alarmIdToEdit != null) repository.getAlarmById(alarmIdToEdit) else null
    }

    var selectedHour by remember {
        val cal = Calendar.getInstance()
        mutableIntStateOf(existingAlarm?.hour ?: cal.get(Calendar.HOUR_OF_DAY))
    }
    var selectedMinute by remember {
        val cal = Calendar.getInstance()
        mutableIntStateOf(existingAlarm?.minute ?: ((cal.get(Calendar.MINUTE) + 5) % 60))
    }

    var selectedRepeatDays by remember {
        mutableStateOf(existingAlarm?.repeatDays ?: emptySet())
    }
    var selectedDifficulty by remember {
        mutableStateOf(existingAlarm?.difficulty ?: settings.defaultDifficulty)
    }
    var selectedRingtoneName by remember {
        mutableStateOf(existingAlarm?.ringtoneName ?: settings.defaultRingtoneName)
    }
    var selectedRingtoneUri by remember {
        mutableStateOf(existingAlarm?.ringtoneUri ?: settings.defaultRingtoneUri)
    }

    var currentlyPreviewing by remember { mutableStateOf<String?>(null) }

    // File picker for custom audio
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val imported = ringtoneManager.importAudioFile(uri)
            if (imported != null) {
                selectedRingtoneName = imported.name
                selectedRingtoneUri = imported.filePath
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ringtoneManager.stopPreview()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (existingAlarm == null) "Set Alarm" else "Edit Alarm",
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. TIME PICKER SECTION
            item {
                TimePickerCard(
                    hour = selectedHour,
                    minute = selectedMinute,
                    is24Hour = settings.is24HourFormat,
                    onTimeChanged = { h, m ->
                        selectedHour = h
                        selectedMinute = m
                    }
                )
            }

            // 2. REPEAT DAYS SECTION
            item {
                RepeatDaysCard(
                    selectedDays = selectedRepeatDays,
                    onDaysChanged = { selectedRepeatDays = it }
                )
            }

            // 3. DIFFICULTY SELECTOR
            item {
                DifficultySelector(
                    selectedDifficulty = selectedDifficulty,
                    onSelect = { selectedDifficulty = it }
                )
            }

            // 4. RINGTONE SELECTOR & CUSTOM AUDIO
            item {
                RingtoneSelectorCard(
                    builtInList = ringtoneManager.builtInRingtones,
                    customList = customRingtones,
                    selectedName = selectedRingtoneName,
                    currentlyPreviewing = currentlyPreviewing,
                    onSelect = { name, uri ->
                        selectedRingtoneName = name
                        selectedRingtoneUri = uri
                    },
                    onPreview = { name, uri ->
                        if (currentlyPreviewing == name) {
                            ringtoneManager.stopPreview()
                            currentlyPreviewing = null
                        } else {
                            currentlyPreviewing = name
                            ringtoneManager.previewRingtone(uri, name) {
                                currentlyPreviewing = null
                            }
                        }
                    },
                    onDeleteCustom = { id ->
                        repository.deleteCustomRingtone(id)
                        if (selectedRingtoneUri?.contains(id) == true) {
                            selectedRingtoneName = "Siren Blitz"
                            selectedRingtoneUri = null
                        }
                    },
                    onAddCustomAudio = {
                        audioPickerLauncher.launch("audio/*")
                    }
                )
            }

            // 5. SAVE BUTTON
            item {
                Button(
                    onClick = {
                        val alarmToSave = Alarm(
                            id = existingAlarm?.id ?: java.util.UUID.randomUUID().toString(),
                            hour = selectedHour,
                            minute = selectedMinute,
                            repeatDays = selectedRepeatDays,
                            difficulty = selectedDifficulty,
                            ringtoneName = selectedRingtoneName,
                            ringtoneUri = selectedRingtoneUri,
                            isEnabled = true,
                            label = "Challenge Alarm"
                        )
                        repository.saveAlarm(alarmToSave)
                        scheduler.scheduleAlarm(alarmToSave)
                        onNavigateBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (existingAlarm == null) "Save Alarm" else "Update Alarm",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun TimePickerCard(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    onTimeChanged: (Int, Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ALARM TIME",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFF90A4AE)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Time increment/decrement selectors
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Hours control
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = {
                        val newH = (hour + 1) % 24
                        onTimeChanged(newH, minute)
                    }) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Hour +", tint = Color.White)
                    }

                    val displayH = if (is24Hour) hour else when (val h = hour % 12) { 0 -> 12 else -> h }
                    Text(
                        text = String.format("%02d", displayH),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    IconButton(onClick = {
                        val newH = if (hour == 0) 23 else hour - 1
                        onTimeChanged(newH, minute)
                    }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Hour -", tint = Color.White)
                    }
                }

                Text(
                    text = ":",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Minutes control
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = {
                        val newM = (minute + 1) % 60
                        onTimeChanged(hour, newM)
                    }) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Minute +", tint = Color.White)
                    }

                    Text(
                        text = String.format("%02d", minute),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    IconButton(onClick = {
                        val newM = if (minute == 0) 59 else minute - 1
                        onTimeChanged(hour, newM)
                    }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minute -", tint = Color.White)
                    }
                }

                if (!is24Hour) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        val isAm = hour < 12
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (!isAm) onTimeChanged(hour - 12, minute)
                                },
                            color = if (isAm) Color(0xFFFF5722) else Color(0xFF263238)
                        ) {
                            Text(
                                text = "AM",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (isAm) onTimeChanged(hour + 12, minute)
                                },
                            color = if (!isAm) Color(0xFFFF5722) else Color(0xFF263238)
                        ) {
                            Text(
                                text = "PM",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RepeatDaysCard(
    selectedDays: Set<Int>,
    onDaysChanged: (Set<Int>) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "REPEAT",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFF90A4AE)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetChip(
                    text = "Once",
                    isSelected = selectedDays.isEmpty(),
                    onClick = { onDaysChanged(emptySet()) },
                    modifier = Modifier.weight(1f)
                )
                PresetChip(
                    text = "Every day",
                    isSelected = selectedDays.size == 7,
                    onClick = { onDaysChanged(setOf(1, 2, 3, 4, 5, 6, 7)) },
                    modifier = Modifier.weight(1f)
                )
                PresetChip(
                    text = "Weekdays",
                    isSelected = selectedDays == setOf(1, 2, 3, 4, 5),
                    onClick = { onDaysChanged(setOf(1, 2, 3, 4, 5)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Day circles (Mon=1 ... Sun=7)
            val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dayNames.forEachIndexed { index, name ->
                    val dayNum = index + 1
                    val isSelected = selectedDays.contains(dayNum)

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFFFF5722) else Color(0xFF21262D))
                            .clickable {
                                val updated = if (isSelected) {
                                    selectedDays - dayNum
                                } else {
                                    selectedDays + dayNum
                                }
                                onDaysChanged(updated)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            color = if (isSelected) Color.White else Color(0xFF8B949E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PresetChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = if (isSelected) Color(0xFFFF5722).copy(alpha = 0.25f) else Color(0xFF21262D),
        border = if (isSelected) BorderStroke(1.dp, Color(0xFFFF5722)) else null
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) Color(0xFFFF5722) else Color(0xFF8B949E),
            modifier = Modifier.padding(vertical = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DifficultySelector(
    selectedDifficulty: Difficulty,
    onSelect: (Difficulty) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "DIFFICULTY",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color(0xFF90A4AE)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Difficulty.entries.forEach { diff ->
                val isSelected = diff == selectedDifficulty
                val color = Color(diff.colorHex)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(diff) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFF1E2633) else Color(0xFF161B22)
                    ),
                    border = if (isSelected) BorderStroke(2.dp, color) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = diff.emoji,
                            fontSize = 28.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = diff.displayName.uppercase(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = color
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = diff.description,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }

                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = color,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RingtoneSelectorCard(
    builtInList: List<String>,
    customList: List<CustomRingtone>,
    selectedName: String,
    currentlyPreviewing: String?,
    onSelect: (String, String?) -> Unit,
    onPreview: (String, String?) -> Unit,
    onDeleteCustom: (String) -> Unit,
    onAddCustomAudio: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RINGTONE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFF90A4AE)
                )

                TextButton(
                    onClick = onAddCustomAudio,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00E676))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Add Custom Audio", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Built-in list
            builtInList.forEach { soundName ->
                val isSelected = selectedName == soundName
                val isPlaying = currentlyPreviewing == soundName

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(soundName, null) }
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFFFF5722) else Color(0xFF78909C),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = soundName,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = { onPreview(soundName, null) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Preview",
                            tint = if (isPlaying) Color(0xFFFF5252) else Color(0xFF81D4FA),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Custom Audio List
            if (customList.isNotEmpty()) {
                Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF21262D))
                Text(
                    text = "CUSTOM AUDIO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF78909C)
                )

                customList.forEach { custom ->
                    val isSelected = selectedName == custom.name
                    val isPlaying = currentlyPreviewing == custom.name

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(custom.name, custom.filePath) }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFFFF5722) else Color(0xFF78909C),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "🎵 ${custom.name}",
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = Color.White,
                                maxLines = 1
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onPreview(custom.name, custom.filePath) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Preview",
                                    tint = if (isPlaying) Color(0xFFFF5252) else Color(0xFF81D4FA),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { onDeleteCustom(custom.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFF78909C),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
