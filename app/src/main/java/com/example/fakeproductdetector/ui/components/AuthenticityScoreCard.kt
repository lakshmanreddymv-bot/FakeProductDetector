package com.example.fakeproductdetector.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fakeproductdetector.domain.model.Verdict

// S: Single Responsibility — renders only the animated authenticity score arc card

/**
 * Maps an authenticity score to a semantic colour for the score arc and verdict label.
 *
 * @param score Authenticity score in the range 0–100.
 * @return Green for scores ≥ 75, orange for scores ≥ 50, red for scores below 50.
 */
private fun scoreColor(score: Float): Color = when {
    score >= 75f -> Color(0xFF4CAF50)
    score >= 50f -> Color(0xFFFF9800)
    else         -> Color(0xFFF44336)
}

/**
 * Composable card that displays the authenticity score as an animated arc gauge with
 * a colour-coded score number and verdict label.
 *
 * The arc animates from 0 to [score] over 1200 ms when first composed or when [score] changes.
 * Arc colour is determined by [scoreColor]: green (≥ 75), orange (≥ 50), red (< 50).
 *
 * @param score Authenticity score in the range 0–100; drives both the arc sweep and the displayed number.
 * @param verdict The categorical verdict label displayed below the arc.
 * @param modifier Optional [Modifier] applied to the outer [Card].
 */
@Composable
fun AuthenticityScoreCard(
    score: Float,
    verdict: Verdict,
    modifier: Modifier = Modifier
) {
    var animatedTarget by remember { mutableFloatStateOf(0f) }
    val animatedScore by animateFloatAsState(
        targetValue = animatedTarget,
        animationSpec = tween(durationMillis = 1200),
        label = "scoreAnimation"
    )

    LaunchedEffect(score) { animatedTarget = score }

    val arcColor = scoreColor(score)
    val trackColor = arcColor.copy(alpha = 0.15f)

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Authenticity Score",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(160.dp)) {
                    val strokeWidth = 16.dp.toPx()
                    val inset = strokeWidth / 2f
                    val arcRect = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(inset, inset)
                    val startAngle = 135f
                    val sweepTotal = 270f

                    // Track
                    drawArc(
                        color = trackColor,
                        startAngle = startAngle,
                        sweepAngle = sweepTotal,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcRect,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    // Progress
                    drawArc(
                        color = arcColor,
                        startAngle = startAngle,
                        sweepAngle = sweepTotal * (animatedScore / 100f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcRect,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${animatedScore.toInt()}",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = arcColor
                    )
                    Text(
                        text = "/ 100",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = verdict.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = arcColor
            )
        }
    }
}