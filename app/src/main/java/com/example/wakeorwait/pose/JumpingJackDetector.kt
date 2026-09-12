package com.example.wakeorwait.pose

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.hypot

enum class JumpingJackPhase {
    UNKNOWN,
    CLOSED,
    OPEN
}

data class PoseDetectionResult(
    val currentCount: Int,
    val targetCount: Int,
    val guidanceMessage: String,
    val isReady: Boolean,
    val isTargetReached: Boolean,
    val pose: Pose?
)

class JumpingJackDetector(
    private val targetReps: Int = 20,
    private val onTargetReached: () -> Unit
) {

    private var repCount = 0
    private var currentPhase = JumpingJackPhase.UNKNOWN
    private var hasBeenOpenInCurrentRep = false
    private var consecutiveReadyFrames = 0
    private var isReadyForCounting = false

    fun reset() {
        repCount = 0
        currentPhase = JumpingJackPhase.UNKNOWN
        hasBeenOpenInCurrentRep = false
        consecutiveReadyFrames = 0
        isReadyForCounting = false
    }

    fun processPose(pose: Pose?, imageWidth: Int, imageHeight: Int): PoseDetectionResult {
        if (pose == null) {
            return PoseDetectionResult(
                currentCount = repCount,
                targetCount = targetReps,
                guidanceMessage = "Move into frame.",
                isReady = false,
                isTargetReached = repCount >= targetReps,
                pose = null
            )
        }

        // Required landmarks
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        val minConfidence = 0.5f
        val essentialLandmarks = listOf(
            leftShoulder, rightShoulder,
            leftWrist, rightWrist,
            leftHip, rightHip,
            leftKnee, rightKnee,
            leftAnkle, rightAnkle
        )

        // 1. Check if all essential landmarks are visible
        val allVisible = essentialLandmarks.all { it != null && it.inFrameLikelihood >= minConfidence }
        if (!allVisible) {
            consecutiveReadyFrames = 0
            val anklesVisible = (leftAnkle?.inFrameLikelihood ?: 0f) >= minConfidence &&
                    (rightAnkle?.inFrameLikelihood ?: 0f) >= minConfidence

            val msg = if (!anklesVisible) {
                "I can't see your full body. Stand farther from the camera."
            } else {
                "Stand where I can see your full body."
            }

            return PoseDetectionResult(
                currentCount = repCount,
                targetCount = targetReps,
                guidanceMessage = msg,
                isReady = false,
                isTargetReached = repCount >= targetReps,
                pose = pose
            )
        }

        // Non-null assertions are safe here
        val ls = leftShoulder!!
        val rs = rightShoulder!!
        val lw = leftWrist!!
        val rw = rightWrist!!
        val la = leftAnkle!!
        val ra = rightAnkle!!

        // Distance / Scale check:
        // Distance from shoulders to ankles should take at least 35% of frame height
        val avgShoulderY = (ls.position.y + rs.position.y) / 2f
        val avgAnkleY = (la.position.y + ra.position.y) / 2f
        val bodyHeightInFrame = abs(avgAnkleY - avgShoulderY)

        if (imageHeight > 0 && bodyHeightInFrame > imageHeight * 0.92f) {
            consecutiveReadyFrames = 0
            return PoseDetectionResult(
                currentCount = repCount,
                targetCount = targetReps,
                guidanceMessage = "Stand farther from the camera.",
                isReady = false,
                isTargetReached = repCount >= targetReps,
                pose = pose
            )
        }

        // 2. Readiness Check
        if (!isReadyForCounting) {
            consecutiveReadyFrames++
            if (consecutiveReadyFrames < 5) {
                return PoseDetectionResult(
                    currentCount = repCount,
                    targetCount = targetReps,
                    guidanceMessage = "Hold still... checking readiness",
                    isReady = false,
                    isTargetReached = false,
                    pose = pose
                )
            } else {
                isReadyForCounting = true
            }
        }

        // 3. Biomechanical Metrics
        val shoulderWidth = hypot(ls.position.x - rs.position.x, ls.position.y - rs.position.y)
        val ankleDistance = hypot(la.position.x - ra.position.x, la.position.y - ra.position.y)

        // Arms:
        // In CLOSED position, wrists are lower than shoulders (Y is greater in screen coords)
        val armsDown = lw.position.y > ls.position.y && rw.position.y > rs.position.y
        // In OPEN position, wrists are higher than shoulders (Y is smaller in screen coords)
        val armsRaised = lw.position.y < ls.position.y && rw.position.y < rs.position.y

        // Legs:
        // In CLOSED position, ankles are close (distance <= 1.6 * shoulderWidth)
        val legsTogether = ankleDistance < (shoulderWidth * 1.6f)
        // In OPEN position, ankles are spread apart (distance >= 1.9 * shoulderWidth)
        val legsApart = ankleDistance > (shoulderWidth * 1.9f)

        var guidance = if (repCount == 0 && !hasBeenOpenInCurrentRep) {
            "READY... Start jumping jacks!"
        } else {
            "Good! Keep going."
        }

        // 4. State Machine Progression
        if (armsRaised && legsApart) {
            // Reached OPEN position
            currentPhase = JumpingJackPhase.OPEN
            hasBeenOpenInCurrentRep = true
            guidance = "Up! Now bring arms and legs together."
        } else if (armsDown && legsTogether) {
            // Reached CLOSED position
            if (hasBeenOpenInCurrentRep && currentPhase == JumpingJackPhase.OPEN) {
                // Completed a full cycle: CLOSED -> OPEN -> CLOSED
                repCount++
                hasBeenOpenInCurrentRep = false
                currentPhase = JumpingJackPhase.CLOSED
                guidance = "Rep $repCount done! Keep going!"

                if (repCount >= targetReps) {
                    onTargetReached()
                }
            } else {
                currentPhase = JumpingJackPhase.CLOSED
            }
        } else {
            // Intermediate state
            if (hasBeenOpenInCurrentRep) {
                guidance = "Close legs and lower arms to count rep."
            }
        }

        return PoseDetectionResult(
            currentCount = repCount,
            targetCount = targetReps,
            guidanceMessage = guidance,
            isReady = isReadyForCounting,
            isTargetReached = repCount >= targetReps,
            pose = pose
        )
    }
}
