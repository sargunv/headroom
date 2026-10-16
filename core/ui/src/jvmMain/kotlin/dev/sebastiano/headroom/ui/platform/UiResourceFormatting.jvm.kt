package dev.sebastiano.headroom.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import dev.sebastiano.headroom.designsystem.platform.sharedPainterResource
import dev.sebastiano.headroom.designsystem.platform.sharedPluralStringResource
import dev.sebastiano.headroom.designsystem.platform.sharedStringResource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

@Composable
actual fun uiStringResource(resource: StringResource, vararg arguments: Any): String =
    sharedStringResource(resource, *arguments)

@Composable
actual fun uiPluralStringResource(
    resource: PluralStringResource,
    quantity: Int,
    vararg arguments: Any,
): String = sharedPluralStringResource(resource, quantity, *arguments)

@Composable
actual fun uiPainterResource(resource: DrawableResource): Painter = sharedPainterResource(resource)
