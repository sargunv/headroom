package dev.sebastiano.headroom.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.sebastiano.headroom.designsystem.platform.sharedPainterResource
import dev.sebastiano.headroom.designsystem.platform.sharedPluralStringResource
import dev.sebastiano.headroom.designsystem.platform.sharedStringResource
import dev.sebastiano.headroom.ui.resources.NativeComposeResources
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

@Composable
actual fun uiStringResource(resource: StringResource, vararg arguments: Any): String {
    val id = NativeComposeResources.stringId(resource)
    return when {
        id == null -> sharedStringResource(resource, *arguments)
        arguments.isEmpty() -> stringResource(id)
        else -> stringResource(id, *arguments)
    }
}

@Composable
actual fun uiPluralStringResource(
    resource: PluralStringResource,
    quantity: Int,
    vararg arguments: Any,
): String {
    val id = NativeComposeResources.pluralId(resource)
    return when {
        id == null -> sharedPluralStringResource(resource, quantity, *arguments)
        arguments.isEmpty() -> pluralStringResource(id, quantity)
        else -> pluralStringResource(id, quantity, *arguments)
    }
}

@Composable
actual fun uiPainterResource(resource: DrawableResource): Painter {
    val id = NativeComposeResources.drawableId(resource)
    return if (id != null) painterResource(id) else sharedPainterResource(resource)
}
