package com.novara.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novara.app.ui.theme.*

/**
 * Custom Compose Vector Artwork components matching NOVARA editorial reference design.
 */

// 1. Botanical Leaf Emblem (NOVARA Logo Icon)
@Composable
fun BotanicalLeafEmblem(
    modifier: Modifier = Modifier,
    leafColor: Color = DeepTeal,
    accentColor: Color = Terracotta
) {
    Canvas(modifier = modifier.size(48.dp)) {
        val width = size.width
        val height = size.height

        // Left Leaf Path
        val leftLeaf = Path().apply {
            moveTo(width * 0.5f, height * 0.9f)
            cubicTo(
                width * 0.1f, height * 0.7f,
                width * 0.1f, height * 0.3f,
                width * 0.45f, height * 0.1f
            )
            cubicTo(
                width * 0.55f, height * 0.35f,
                width * 0.55f, height * 0.65f,
                width * 0.5f, height * 0.9f
            )
            close()
        }
        drawPath(path = leftLeaf, color = leafColor)

        // Right Leaf Path
        val rightLeaf = Path().apply {
            moveTo(width * 0.5f, height * 0.85f)
            cubicTo(
                width * 0.85f, height * 0.65f,
                width * 0.85f, height * 0.35f,
                width * 0.6f, height * 0.2f
            )
            cubicTo(
                width * 0.55f, height * 0.45f,
                width * 0.55f, height * 0.7f,
                width * 0.5f, height * 0.85f
            )
            close()
        }
        drawPath(path = rightLeaf, color = accentColor)
    }
}

// 2. Spanish Streetscape Architectural Illustration
@Composable
fun SpanishStreetscapeIllustration(
    modifier: Modifier = Modifier,
    height: Dp = 160.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceElevated)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Background Sky Tint
            drawRect(
                color = Color(0xFFF7EEDC),
                size = size
            )

            // Sun / Warm Arch
            drawCircle(
                color = Terracotta.copy(alpha = 0.2f),
                radius = h * 0.35f,
                center = Offset(w * 0.5f, h * 0.4f)
            )

            // Buildings Left
            drawRoundRect(
                color = Color(0xFFE8DAC4),
                topLeft = Offset(w * 0.05f, h * 0.3f),
                size = Size(w * 0.25f, h * 0.7f),
                cornerRadius = CornerRadius(8f)
            )
            drawRoundRect(
                color = Terracotta.copy(alpha = 0.8f),
                topLeft = Offset(w * 0.04f, h * 0.25f),
                size = Size(w * 0.27f, h * 0.08f),
                cornerRadius = CornerRadius(4f)
            )

            // Windows
            for (i in 0..2) {
                drawRoundRect(
                    color = DarkTeal,
                    topLeft = Offset(w * 0.12f, h * (0.38f + i * 0.18f)),
                    size = Size(w * 0.1f, h * 0.12f),
                    cornerRadius = CornerRadius(12f)
                )
            }

            // Archway Center
            val archPath = Path().apply {
                moveTo(w * 0.38f, h)
                lineTo(w * 0.38f, h * 0.45f)
                cubicTo(w * 0.38f, h * 0.25f, w * 0.62f, h * 0.25f, w * 0.62f, h * 0.45f)
                lineTo(w * 0.62f, h)
                close()
            }
            drawPath(path = archPath, color = DeepTeal)

            // Arch Opening Depth
            val innerArch = Path().apply {
                moveTo(w * 0.42f, h)
                lineTo(w * 0.42f, h * 0.52f)
                cubicTo(w * 0.42f, h * 0.35f, w * 0.58f, h * 0.35f, w * 0.58f, h * 0.52f)
                lineTo(w * 0.58f, h)
                close()
            }
            drawPath(path = innerArch, color = Color(0xFF0F3E39))

            // Right Building
            drawRoundRect(
                color = Color(0xFFDFCBB1),
                topLeft = Offset(w * 0.7f, h * 0.2f),
                size = Size(w * 0.25f, h * 0.8f),
                cornerRadius = CornerRadius(8f)
            )

            // Potted Plants
            drawCircle(
                color = Terracotta,
                radius = w * 0.04f,
                center = Offset(w * 0.32f, h * 0.88f)
            )
            drawCircle(
                color = SageDark,
                radius = w * 0.05f,
                center = Offset(w * 0.32f, h * 0.8f)
            )
        }
    }
}

// 3. Cafe Coffee & Pastry Illustration
@Composable
fun CafeCoffeeIllustration(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(80.dp)) {
        val w = size.width
        val h = size.height

        // Saucer
        drawOval(
            color = SubtleBorder,
            topLeft = Offset(w * 0.1f, h * 0.65f),
            size = Size(w * 0.8f, h * 0.25f)
        )

        // Cup Body
        val cupPath = Path().apply {
            moveTo(w * 0.25f, h * 0.35f)
            lineTo(w * 0.75f, h * 0.35f)
            lineTo(w * 0.68f, h * 0.7f)
            cubicTo(w * 0.6f, h * 0.75f, w * 0.4f, h * 0.75f, w * 0.32f, h * 0.7f)
            close()
        }
        drawPath(path = cupPath, color = DeepTeal)

        // Cup Handle
        drawArc(
            color = DeepTeal,
            startAngle = 270f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.68f, h * 0.42f),
            size = Size(w * 0.18f, h * 0.22f),
            style = Stroke(width = 6f)
        )

        // Coffee surface
        drawOval(
            color = TerracottaDark,
            topLeft = Offset(w * 0.26f, h * 0.33f),
            size = Size(w * 0.48f, h * 0.1f)
        )

        // Steam Lines
        for (i in 0..1) {
            val startX = w * (0.4f + i * 0.2f)
            val steamPath = Path().apply {
                moveTo(startX, h * 0.28f)
                cubicTo(startX - 6f, h * 0.2f, startX + 6f, h * 0.15f, startX, h * 0.08f)
            }
            drawPath(
                path = steamPath,
                color = Terracotta.copy(alpha = 0.6f),
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
        }
    }
}

// 4. Circular Readiness Score Gauge
@Composable
fun ReadinessCircleGauge(
    scorePercent: Int,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 140.dp,
    strokeWidthDp: Dp = 12.dp,
    label: String = "Language Accuracy"
) {
    Box(
        modifier = modifier.size(sizeDp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidthDp.toPx()
            val radius = (size.minDimension - strokePx) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Background Track
            drawCircle(
                color = SubtleBorder,
                radius = radius,
                center = center,
                style = Stroke(width = strokePx)
            )

            // Progress Arc
            val sweepAngle = 360f * (scorePercent / 100f)
            drawArc(
                color = DeepTeal,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        val percentFontSize = when {
            sizeDp < 60.dp -> 11.sp
            sizeDp < 100.dp -> 18.sp
            sizeDp < 130.dp -> 22.sp
            else -> 30.sp
        }

        val labelFontSize = when {
            sizeDp < 60.dp -> 7.sp
            sizeDp < 100.dp -> 9.sp
            else -> 11.sp
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = (sizeDp.value * 0.12f).dp)
        ) {
            Text(
                text = "$scorePercent%",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = percentFontSize,
                color = DeepTeal
            )
            if (label.isNotBlank() && sizeDp >= 75.dp) {
                Text(
                    text = label,
                    fontSize = labelFontSize,
                    color = SecondaryText,
                    lineHeight = (labelFontSize.value * 1.15f).sp,
                    maxLines = 2
                )
            }
        }
    }
}

// 5. Learner Avatar Badge
@Composable
fun LearnerAvatarBadge(
    modifier: Modifier = Modifier,
    initials: String = "S",
    backgroundColor: Color = TerracottaContainer,
    textColor: Color = TerracottaDark,
    sizeDp: Dp = 44.dp
) {
    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = (sizeDp.value * 0.4f).sp,
            color = textColor
        )
    }
}
