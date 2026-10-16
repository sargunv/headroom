package dev.sebastiano.headroom.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

@Composable expect fun uiStringResource(resource: StringResource, vararg arguments: Any): String

@Composable
expect fun uiPluralStringResource(
    resource: PluralStringResource,
    quantity: Int,
    vararg arguments: Any,
): String

@Composable expect fun uiPainterResource(resource: DrawableResource): Painter
