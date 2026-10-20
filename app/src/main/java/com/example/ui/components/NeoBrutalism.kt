package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeoCoral
import com.example.ui.theme.NeoYellow

/**
 * Paper Neobrutalism Card container.
 * Features a solid offset paper shadow with 0-blur and a crisp ink border.
 */
@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    shadowColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = 2.dp,
    shadowOffset: Dp = 3.dp,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentOffset by animateDpAsState(
        targetValue = if (onClick != null && isPressed) 1.dp else shadowOffset,
        animationSpec = tween(durationMillis = 60),
        label = "NeoCardShadow"
    )

    Box(
        modifier = modifier.padding(end = shadowOffset, bottom = shadowOffset)
    ) {
        // Hard paper drop shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = currentOffset, y = currentOffset)
                .clip(shape)
                .background(shadowColor)
        )

        // Foreground paper card
        val cardModifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .border(borderWidth, borderColor, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .then(
                if (testTag != null) Modifier.testTag(testTag) else Modifier
            )
            .padding(contentPadding)

        Box(
            modifier = cardModifier
        ) {
            content()
        }
    }
}

/**
 * Tactile Paper Neobrutalist Button.
 * Rebounds with a crisp physical stamp press effect.
 */
@Composable
fun NeoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = NeoCoral,
    contentColor: Color = Color.White,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    shadowColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = 2.dp,
    shadowOffset: Dp = 3.dp,
    height: Dp = 40.dp,
    fontSize: Int = 12,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    testTag: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentOffset by animateDpAsState(
        targetValue = if (isPressed) 1.dp else shadowOffset,
        animationSpec = tween(durationMillis = 50),
        label = "NeoButtonShadow"
    )

    Box(
        modifier = modifier
            .padding(end = shadowOffset, bottom = shadowOffset)
    ) {
        // Solid Paper Shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = currentOffset, y = currentOffset)
                .clip(shape)
                .background(shadowColor)
        )

        // Button Surface
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(containerColor)
                .border(borderWidth, borderColor, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .then(
                    if (testTag != null) Modifier.testTag(testTag) else Modifier
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Neo-Sticker Badge for tags, statuses, and counts.
 */
@Composable
fun NeoBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = NeoYellow,
    textColor: Color = Color(0xFF141416),
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = 1.5.dp,
    shadowOffset: Dp = 1.5.dp,
    shape: RoundedCornerShape = RoundedCornerShape(4.dp),
    fontSize: Int = 9
) {
    Box(
        modifier = modifier.padding(end = shadowOffset, bottom = shadowOffset)
    ) {
        // Shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .clip(shape)
                .background(borderColor)
        )

        // Tag foreground
        Box(
            modifier = Modifier
                .clip(shape)
                .background(backgroundColor)
                .border(borderWidth, borderColor, shape)
                .padding(horizontal = 5.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.4.sp
            )
        }
    }
}

/**
 * Neobrutalism Interactive Pill Toggle (e.g. HD / SD)
 */
@Composable
fun NeoPillToggle(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    activeColor: Color = NeoCoral,
    inactiveColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    testTag: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val shadowOffset = if (selected) 2.5.dp else 1.5.dp
    val currentOffset by animateDpAsState(
        targetValue = if (isPressed) 0.5.dp else shadowOffset,
        animationSpec = tween(durationMillis = 50),
        label = "NeoPillShadow"
    )

    Box(
        modifier = modifier.padding(end = shadowOffset, bottom = shadowOffset)
    ) {
        // Shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = currentOffset, y = currentOffset)
                .clip(RoundedCornerShape(8.dp))
                .background(borderColor)
        )

        // Foreground Pill
        Row(
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) activeColor else inactiveColor)
                .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .then(
                    if (testTag != null) Modifier.testTag(testTag) else Modifier
                )
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
