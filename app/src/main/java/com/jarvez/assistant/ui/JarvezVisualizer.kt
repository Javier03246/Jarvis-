package com.jarvez.assistant.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jarvez.assistant.command.AssistantState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JarvezVisualizer(
    state: AssistantState,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "jarvez_visualizer_infinite")

    val rotationSlow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationSlow"
    )

    val rotationFast by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == AssistantState.THINKING) 1500 else 4000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationFast"
    )

    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Box(
        modifier = modifier
            .size(240.dp)
            .testTag("jarvez_arc_reactor_visualizer"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f * 0.82f

            // Determine active colors based on state
            val (coreColor, ringColor, glowColor) = when (state) {
                AssistantState.READY -> Triple(Color(0xFF00E5FF), Color(0xFF00B4D8), Color(0x3300E5FF))
                AssistantState.LISTENING -> Triple(Color(0xFF00F5D4), Color(0xFF7000FF), Color(0x4400F5D4))
                AssistantState.THINKING -> Triple(Color(0xFFFFB703), Color(0xFF00E5FF), Color(0x44FFB703))
                AssistantState.EXECUTING -> Triple(Color(0xFF00F5D4), Color(0xFF38B000), Color(0x4400F5D4))
                AssistantState.SPEAKING -> Triple(Color(0xFF3A86FF), Color(0xFF00F0FF), Color(0x443A86FF))
                AssistantState.ERROR -> Triple(Color(0xFFFF0054), Color(0xFF9D0208), Color(0x44FF0054))
            }

            // 1. Ambient Background Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = center,
                    radius = baseRadius * 1.3f
                ),
                radius = baseRadius * 1.3f,
                center = center
            )

            // 2. Outer Static Dial with Tick Marks
            drawOuterDial(center, baseRadius, ringColor.copy(alpha = 0.5f))

            // 3. Middle Rotating Arc Rings
            drawRotatingArcs(center, baseRadius * 0.82f, ringColor, rotationSlow, rotationFast)

            // 4. Reactive State Visualizer
            when (state) {
                AssistantState.LISTENING -> {
                    // Reactive RMS ripples
                    val reactiveScale = 1f + (rmsLevel * 0.45f)
                    drawCircle(
                        color = coreColor.copy(alpha = 0.25f),
                        radius = baseRadius * 0.55f * reactiveScale,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = coreColor.copy(alpha = 0.5f),
                        radius = baseRadius * 0.38f * reactiveScale,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                AssistantState.SPEAKING -> {
                    // Sine waveform in center
                    drawSpeechWave(center, baseRadius * 0.4f, coreColor, wavePhase)
                }

                AssistantState.THINKING -> {
                    // High-speed orbital particles
                    drawOrbitalTracer(center, baseRadius * 0.62f, coreColor, rotationFast * 2f)
                }

                else -> {}
            }

            // 5. Central Arc Reactor Core
            val coreScale = if (state == AssistantState.LISTENING) {
                1f + (rmsLevel * 0.3f)
            } else {
                pulse
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, coreColor, coreColor.copy(alpha = 0.3f)),
                    center = center,
                    radius = baseRadius * 0.32f * coreScale
                ),
                radius = baseRadius * 0.32f * coreScale,
                center = center
            )

            // Inner core boundary ring
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = baseRadius * 0.18f * coreScale,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

private fun DrawScope.drawOuterDial(center: Offset, radius: Float, color: Color) {
    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Tick marks around perimeter
    val tickCount = 36
    val tickLength = 6.dp.toPx()
    for (i in 0 until tickCount) {
        val angle = (i * (360f / tickCount)) * (PI.toFloat() / 180f)
        val startX = center.x + (radius - tickLength) * cos(angle)
        val startY = center.y + (radius - tickLength) * sin(angle)
        val endX = center.x + radius * cos(angle)
        val endY = center.y + radius * sin(angle)
        drawLine(
            color = if (i % 3 == 0) color else color.copy(alpha = 0.3f),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = if (i % 3 == 0) 2.dp.toPx() else 1.dp.toPx()
        )
    }
}

private fun DrawScope.drawRotatingArcs(
    center: Offset,
    radius: Float,
    color: Color,
    rotationSlow: Float,
    rotationFast: Float
) {
    // Segmented outer arc (rotating clockwise)
    val arcRect = androidx.compose.ui.geometry.Rect(
        center.x - radius, center.y - radius,
        center.x + radius, center.y + radius
    )

    drawArc(
        color = color,
        startAngle = rotationSlow,
        sweepAngle = 70f,
        useCenter = false,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        topLeft = arcRect.topLeft,
        size = arcRect.size
    )

    drawArc(
        color = color,
        startAngle = rotationSlow + 180f,
        sweepAngle = 70f,
        useCenter = false,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        topLeft = arcRect.topLeft,
        size = arcRect.size
    )

    // Middle counter-rotating arc
    val innerRadius = radius * 0.8f
    val innerRect = androidx.compose.ui.geometry.Rect(
        center.x - innerRadius, center.y - innerRadius,
        center.x + innerRadius, center.y + innerRadius
    )

    drawArc(
        color = color.copy(alpha = 0.7f),
        startAngle = -rotationFast,
        sweepAngle = 45f,
        useCenter = false,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        topLeft = innerRect.topLeft,
        size = innerRect.size
    )

    drawArc(
        color = color.copy(alpha = 0.7f),
        startAngle = -rotationFast + 120f,
        sweepAngle = 45f,
        useCenter = false,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        topLeft = innerRect.topLeft,
        size = innerRect.size
    )

    drawArc(
        color = color.copy(alpha = 0.7f),
        startAngle = -rotationFast + 240f,
        sweepAngle = 45f,
        useCenter = false,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        topLeft = innerRect.topLeft,
        size = innerRect.size
    )
}

private fun DrawScope.drawSpeechWave(
    center: Offset,
    width: Float,
    color: Color,
    phase: Float
) {
    val path = Path()
    val steps = 30
    val startX = center.x - width
    val stepX = (width * 2) / steps

    for (i in 0..steps) {
        val x = startX + (i * stepX)
        val normalizedX = (i.toFloat() / steps) * 2f - 1f // -1 to 1
        val envelope = 1f - (normalizedX * normalizedX) // parabola envelope
        val y = center.y + (sin(phase + i * 0.4f) * 16.dp.toPx() * envelope)

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawOrbitalTracer(
    center: Offset,
    radius: Float,
    color: Color,
    angleDeg: Float
) {
    val rad = angleDeg * (PI.toFloat() / 180f)
    val tracerX = center.x + radius * cos(rad)
    val tracerY = center.y + radius * sin(rad)

    drawCircle(
        color = Color.White,
        radius = 4.dp.toPx(),
        center = Offset(tracerX, tracerY)
    )

    drawCircle(
        color = color.copy(alpha = 0.5f),
        radius = 8.dp.toPx(),
        center = Offset(tracerX, tracerY)
    )
}
