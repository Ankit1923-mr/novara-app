package com.example.novara.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.novara.ui.theme.OchreAccent
import com.example.novara.ui.theme.TerracottaPrimary
import com.example.novara.ui.theme.WarmOrange

@Composable
fun NovaraLogo(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    showSubtitle: Boolean = true
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(OchreAccent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size * 0.8f)) {
                val canvasWidth = this.size.width
                val canvasHeight = this.size.height
                val center = Offset(canvasWidth / 2, canvasHeight / 2)
                val radius = canvasWidth / 3

                // Sun Rays
                for (i in 0 until 12) {
                    val angle = (i * 30) * (Math.PI / 180)
                    val startX = center.x + (radius * 0.85f * Math.cos(angle)).toFloat()
                    val startY = center.y + (radius * 0.85f * Math.sin(angle)).toFloat()
                    val endX = center.x + (radius * 1.25f * Math.cos(angle)).toFloat()
                    val endY = center.y + (radius * 1.25f * Math.sin(angle)).toFloat()
                    drawLine(
                        color = WarmOrange,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 6f
                    )
                }

                // Sun Circle
                drawCircle(
                    color = TerracottaPrimary,
                    radius = radius,
                    center = center
                )

                // Open Book Emblem Overlay
                val bookPath = Path().apply {
                    val w = canvasWidth * 0.4f
                    val h = canvasHeight * 0.25f
                    val left = center.x - w / 2
                    val top = center.y - h / 3

                    moveTo(left, top)
                    quadraticTo(center.x - w / 4, top + 10f, center.x, top + 15f)
                    quadraticTo(center.x + w / 4, top + 10f, left + w, top)
                    lineTo(left + w, top + h)
                    quadraticTo(center.x + w / 4, top + h + 10f, center.x, top + h + 15f)
                    quadraticTo(center.x - w / 4, top + h + 10f, left, top + h)
                    close()
                }

                drawPath(
                    path = bookPath,
                    color = Color.White.copy(alpha = 0.95f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "NOVARA",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 4.sp,
            textAlign = TextAlign.Center
        )

        if (showSubtitle) {
            Text(
                text = "Adaptive Spanish Immersion",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
