package com.example.wakeorwait.pose

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentPose: Pose? = null
    private var sourceImageWidth: Int = 0
    private var sourceImageHeight: Int = 0
    private var isFrontFacing: Boolean = true

    private val dotPaint = Paint().apply {
        color = Color.parseColor("#00E676") // Bright neon green
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.parseColor("#00E5FF") // Cyan skeleton lines
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    fun setPose(pose: Pose?, imageWidth: Int, imageHeight: Int, frontFacing: Boolean = true) {
        currentPose = pose
        sourceImageWidth = imageWidth
        sourceImageHeight = imageHeight
        isFrontFacing = frontFacing
        postInvalidate()
    }

    fun clear() {
        currentPose = null
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pose = currentPose ?: return
        if (sourceImageWidth == 0 || sourceImageHeight == 0 || width == 0 || height == 0) return

        // Scale factors to map camera coordinates to screen view coordinates
        val scaleX = width.toFloat() / sourceImageWidth.toFloat()
        val scaleY = height.toFloat() / sourceImageHeight.toFloat()
        val scale = maxOf(scaleX, scaleY)

        val offsetX = (width - sourceImageWidth * scale) / 2f
        val offsetY = (height - sourceImageHeight * scale) / 2f

        fun transformX(x: Float): Float {
            val scaled = x * scale + offsetX
            return if (isFrontFacing) {
                // Mirror for front-facing camera preview
                width - scaled
            } else {
                scaled
            }
        }

        fun transformY(y: Float): Float {
            return y * scale + offsetY
        }

        fun drawLineBetween(startType: Int, endType: Int) {
            val start = pose.getPoseLandmark(startType) ?: return
            val end = pose.getPoseLandmark(endType) ?: return
            if (start.inFrameLikelihood > 0.4f && end.inFrameLikelihood > 0.4f) {
                canvas.drawLine(
                    transformX(start.position.x),
                    transformY(start.position.y),
                    transformX(end.position.x),
                    transformY(end.position.y),
                    linePaint
                )
            }
        }

        // Torso lines
        drawLineBetween(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
        drawLineBetween(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP)
        drawLineBetween(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP)
        drawLineBetween(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)

        // Arms
        drawLineBetween(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW)
        drawLineBetween(PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)
        drawLineBetween(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW)
        drawLineBetween(PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)

        // Legs
        drawLineBetween(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE)
        drawLineBetween(PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)
        drawLineBetween(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE)
        drawLineBetween(PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)

        // Draw joint points
        for (landmark in pose.allPoseLandmarks) {
            if (landmark.inFrameLikelihood > 0.4f) {
                canvas.drawCircle(
                    transformX(landmark.position.x),
                    transformY(landmark.position.y),
                    12f,
                    dotPaint
                )
            }
        }
    }
}
