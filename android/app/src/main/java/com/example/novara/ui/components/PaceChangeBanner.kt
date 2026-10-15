package com.example.novara.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.novara.ui.theme.PaceBackground
import com.example.novara.ui.theme.PaceText

@Composable
fun PaceChangeBanner(
    reason: String,
    pacePreference: Float?,
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PaceBackground)
                .border(1.dp, PaceText.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            color = PaceBackground
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Pace Adjustment",
                    tint = PaceText
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Adaptive Pace: $reason${pacePreference?.let { " (Pace: ${(it * 100).toInt()}%)" } ?: ""}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = PaceText
                )
            }
        }
    }
}
