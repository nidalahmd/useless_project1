package com.example.wakeorwait.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.wakeorwait.alarm.AlarmScheduler
import com.example.wakeorwait.alarm.AlarmService
import com.example.wakeorwait.challenge.MathGenerator
import com.example.wakeorwait.challenge.MathProblem
import com.example.wakeorwait.challenge.ReverseWordGenerator
import com.example.wakeorwait.challenge.ReverseWordProblem
import com.example.wakeorwait.data.model.Difficulty
import com.example.wakeorwait.pose.JumpingJackDetector
import com.example.wakeorwait.pose.PoseDetectionResult
import com.example.wakeorwait.pose.PoseOverlayView
import com.example.wakeorwait.theme.WakeOrWaitTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executors

class AlarmActivity : ComponentActivity() {

    private var hasCameraPermission by mutableStateOf(false)

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure activity to turn screen on and display over keyguard/lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Check camera permission
        hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        val diffStr = intent.getStringExtra(AlarmScheduler.EXTRA_DIFFICULTY) ?: "NORMAL"
        val difficulty = try { Difficulty.valueOf(diffStr) } catch (e: Exception) { Difficulty.NORMAL }
        val alarmTime = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TIME) ?: "7:00 AM"

        setContent {
            WakeOrWaitTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF101216)
                ) {
                    AlarmScreenContent(
                        difficulty = difficulty,
                        alarmTime = alarmTime,
                        hasCameraPermission = hasCameraPermission,
                        onRequestCameraPermission = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onFinish = { finish() }
                    )
                }
            }
        }
    }
}

enum class ChallengeStage {
    JUMPING_JACKS,
    MATH,
    REVERSE_WORD,
    COMPLETED,
    TIMED_OUT
}

@Composable
fun AlarmScreenContent(
    difficulty: Difficulty,
    alarmTime: String,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val serviceState by AlarmService.serviceState.collectAsState()

    var currentStage by remember { mutableStateOf(ChallengeStage.JUMPING_JACKS) }
    var mathProblem by remember { mutableStateOf<MathProblem?>(null) }
    var reverseProblem by remember { mutableStateOf<ReverseWordProblem?>(null) }
    val startTimeMillis = remember { System.currentTimeMillis() }

    // Synchronize timeout/completed from service
    LaunchedEffect(serviceState.isTimedOut, serviceState.isCompleted) {
        if (serviceState.isTimedOut) {
            currentStage = ChallengeStage.TIMED_OUT
        } else if (serviceState.isCompleted) {
            currentStage = ChallengeStage.COMPLETED
        }
    }

    // Initialize math problem when reaching math stage
    LaunchedEffect(currentStage) {
        if (currentStage == ChallengeStage.MATH && mathProblem == null) {
            mathProblem = MathGenerator.generateProblem(difficulty)
        } else if (currentStage == ChallengeStage.REVERSE_WORD && reverseProblem == null) {
            reverseProblem = ReverseWordGenerator.generateProblem()
        }
    }

    // Format remaining time (e.g. 09:42)
    val remainingSecs = serviceState.remainingSeconds
    val formattedTimeRemaining = String.format(
        "%02d:%02d",
        remainingSecs / 60,
        remainingSecs % 60
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "WAKEORWAIT",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = Color(0xFFFF5722)
                )
                Text(
                    text = "⏰ $alarmTime",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Difficulty & Countdown badge
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = Color(difficulty.colorHex).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${difficulty.emoji} ${difficulty.displayName.uppercase()}",
                        color = Color(difficulty.colorHex),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Remaining: $formattedTimeRemaining",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (remainingSecs < 120) Color(0xFFFF5252) else Color(0xFFB0BEC5)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // PROGRESS STEPPER
        ChallengeStepper(
            difficulty = difficulty,
            currentStage = currentStage
        )

        Spacer(modifier = Modifier.height(12.dp))

        // STAGE CONTENT AREA
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentStage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ChallengeStageTransition"
            ) { stage ->
                when (stage) {
                    ChallengeStage.JUMPING_JACKS -> {
                        JumpingJackChallengeView(
                            hasCameraPermission = hasCameraPermission,
                            onRequestPermission = onRequestCameraPermission,
                            onCompleted = {
                                if (difficulty == Difficulty.EASY) {
                                    val timeTaken = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()
                                    AlarmService.completeChallenge(context, timeTaken)
                                    currentStage = ChallengeStage.COMPLETED
                                } else {
                                    currentStage = ChallengeStage.MATH
                                }
                            }
                        )
                    }
                    ChallengeStage.MATH -> {
                        mathProblem?.let { problem ->
                            MathChallengeView(
                                problem = problem,
                                onCorrect = {
                                    if (difficulty == Difficulty.NORMAL) {
                                        val timeTaken = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()
                                        AlarmService.completeChallenge(context, timeTaken)
                                        currentStage = ChallengeStage.COMPLETED
                                    } else {
                                        currentStage = ChallengeStage.REVERSE_WORD
                                    }
                                }
                            )
                        }
                    }
                    ChallengeStage.REVERSE_WORD -> {
                        reverseProblem?.let { problem ->
                            ReverseWordChallengeView(
                                problem = problem,
                                onCorrect = {
                                    val timeTaken = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()
                                    AlarmService.completeChallenge(context, timeTaken)
                                    currentStage = ChallengeStage.COMPLETED
                                }
                            )
                        }
                    }
                    ChallengeStage.COMPLETED -> {
                        ChallengeSuccessView(
                            timeTakenSeconds = serviceState.elapsedSeconds,
                            onClose = onFinish
                        )
                    }
                    ChallengeStage.TIMED_OUT -> {
                        ChallengeTimeoutView(
                            onClose = onFinish
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChallengeStepper(
    difficulty: Difficulty,
    currentStage: ChallengeStage
) {
    val total = difficulty.totalChallenges
    val currentStepNumber = when (currentStage) {
        ChallengeStage.JUMPING_JACKS -> 1
        ChallengeStage.MATH -> 2
        ChallengeStage.REVERSE_WORD -> 3
        ChallengeStage.COMPLETED, ChallengeStage.TIMED_OUT -> total
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Challenge $currentStepNumber of $total",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFFFB74D)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StepItem(
                label = "Jumping Jacks",
                icon = Icons.Default.DirectionsRun,
                isDone = currentStepNumber > 1 || currentStage == ChallengeStage.COMPLETED,
                isCurrent = currentStage == ChallengeStage.JUMPING_JACKS,
                modifier = Modifier.weight(1f)
            )
            if (difficulty == Difficulty.NORMAL || difficulty == Difficulty.EVIL) {
                StepItem(
                    label = "Math",
                    icon = Icons.Default.Functions,
                    isDone = currentStepNumber > 2 || currentStage == ChallengeStage.COMPLETED,
                    isCurrent = currentStage == ChallengeStage.MATH,
                    modifier = Modifier.weight(1f)
                )
            }
            if (difficulty == Difficulty.EVIL) {
                StepItem(
                    label = "Reverse",
                    icon = Icons.Default.TextFields,
                    isDone = currentStage == ChallengeStage.COMPLETED,
                    isCurrent = currentStage == ChallengeStage.REVERSE_WORD,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StepItem(
    label: String,
    icon: ImageVector,
    isDone: Boolean,
    isCurrent: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isDone -> Color(0xFF2E7D32)
        isCurrent -> Color(0xFFE65100)
        else -> Color(0xFF263238)
    }

    Surface(
        modifier = modifier,
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isDone) Icons.Default.CheckCircle else icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
fun JumpingJackChallengeView(
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    var detectionResult by remember {
        mutableStateOf(
            PoseDetectionResult(
                currentCount = 0,
                targetCount = 20,
                guidanceMessage = "Initializing camera...",
                isReady = false,
                isTargetReached = false,
                pose = null
            )
        )
    }

    if (!hasCameraPermission) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222B))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📷 Camera Access Required",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Camera permission is required for jumping-jack challenges. All image processing happens 100% locally on your device!",
                    textAlign = TextAlign.Center,
                    color = Color(0xFFB0BEC5),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                ) {
                    Text("Grant Camera Permission")
                }
            }
        }
        return
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val poseDetector = remember {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        PoseDetection.getClient(options)
    }

    val detector = remember {
        JumpingJackDetector(
            targetReps = 20,
            onTargetReached = {
                onCompleted()
            }
        )
    }

    var overlayViewRef by remember { mutableStateOf<PoseOverlayView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview + Pose Overlay
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp)),
            factory = { ctx ->
                val frameLayout = android.widget.FrameLayout(ctx)
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val overlayView = PoseOverlayView(ctx)
                overlayViewRef = overlayView

                frameLayout.addView(previewView)
                frameLayout.addView(overlayView)

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            val isRotated = imageProxy.imageInfo.rotationDegrees == 90 ||
                                    imageProxy.imageInfo.rotationDegrees == 270
                            val width = if (isRotated) imageProxy.height else imageProxy.width
                            val height = if (isRotated) imageProxy.width else imageProxy.height

                            poseDetector.process(image)
                                .addOnSuccessListener { pose ->
                                    val result = detector.processPose(pose, width, height)
                                    detectionResult = result
                                    overlayView.setPose(pose, width, height, frontFacing = true)
                                }
                                .addOnFailureListener {
                                    // ignore frame failure
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    // Default to front camera for easy jumping jack posture
                    val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            context as ComponentActivity,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                frameLayout
            }
        )

        // Overlay Dashboard
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Status & Guidance Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.75f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "JUMPING JACKS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64FFDA),
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "${detectionResult.currentCount} / ${detectionResult.targetCount}",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { (detectionResult.currentCount.toFloat() / detectionResult.targetCount.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = Color(0xFF00E676),
                        trackColor = Color(0xFF37474F)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = detectionResult.guidanceMessage,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (detectionResult.isReady) Color(0xFFFFD54F) else Color(0xFFFF8A80),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun MathChallengeView(
    problem: MathProblem,
    onCorrect: () -> Unit
) {
    var userAnswer by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf("Solve this to continue.") }
    var isError by remember { mutableStateOf(false) }

    fun submitAnswer() {
        if (MathGenerator.verifyAnswer(userAnswer, problem)) {
            isError = false
            feedbackMessage = "Correct! ✅"
            onCorrect()
        } else {
            isError = true
            feedbackMessage = "Wrong 😭 Try again."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222B)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "MATH CHALLENGE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFFFFB74D)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "${problem.expression} = ?",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = feedbackMessage,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isError) Color(0xFFFF5252) else Color(0xFF81C784)
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = userAnswer,
                    onValueChange = { input ->
                        userAnswer = input.filter { it.isDigit() || it == '-' }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Enter answer") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitAnswer() }),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFFB74D),
                        unfocusedBorderColor = Color(0xFF455A64)
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { submitAnswer() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Submit Answer",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun ReverseWordChallengeView(
    problem: ReverseWordProblem,
    onCorrect: () -> Unit
) {
    var userInput by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf("Reverse this word to escape Evil Mode.") }
    var isError by remember { mutableStateOf(false) }

    fun submitAnswer() {
        if (ReverseWordGenerator.verifyAnswer(userInput, problem)) {
            isError = false
            feedbackMessage = "You escaped Evil Mode. ☠️"
            onCorrect()
        } else {
            isError = true
            feedbackMessage = "Nope 😂 Try again."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222B)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "REVERSE WORD CHALLENGE ☠️",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFFEF5350)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = problem.originalWord,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    color = Color(0xFFFF5252)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = feedbackMessage,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isError) Color(0xFFFF5252) else Color(0xFF81C784)
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = userInput,
                    onValueChange = { userInput = it.uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Type reversed word") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitAnswer() }),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFEF5350),
                        unfocusedBorderColor = Color(0xFF455A64)
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { submitAnswer() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Verify Word",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ChallengeSuccessView(
    timeTakenSeconds: Int,
    onClose: () -> Unit
) {
    val mins = timeTakenSeconds / 60
    val secs = timeTakenSeconds % 60
    val durationText = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3320)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🎉 YOU'RE AWAKE",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF69F0AE)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Congratulations. You're technically awake.",
                fontSize = 16.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Completed in $durationText",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFB9F6CA)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Return to Home",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun ChallengeTimeoutView(
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF332020)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⏱ 10:00 TIMEOUT",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFF5252)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "You survived 10 minutes. 🫡",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "You waited 10 minutes just to avoid 20 jumping jacks. Respect.",
                fontSize = 14.sp,
                color = Color(0xFFFFCDD2),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Return to Home",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
