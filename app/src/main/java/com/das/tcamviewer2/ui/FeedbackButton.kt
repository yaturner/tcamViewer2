package com.das.tcamviewer2.ui

import android.media.AudioManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext

@Composable
fun FeedbackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "buttonScale"
    )
    Button(
        onClick = {
            audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, 1.0f)
            onClick()
        },
        modifier = modifier.scale(scale),
        enabled = enabled,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun FeedbackTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "textButtonScale"
    )
    TextButton(
        onClick = {
            audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, 1.0f)
            onClick()
        },
        modifier = modifier.scale(scale),
        enabled = enabled,
        interactionSource = interactionSource,
        content = content
    )
}
