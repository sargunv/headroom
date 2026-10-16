package dev.sebastiano.headroom.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** Preserve Android Formatter semantics, including %% escapes, before substituting arguments. */
internal expect fun formatResourceTemplate(
    template: String,
    arguments: Array<out Any>,
    localeTag: String,
): String

internal expect fun resourceLocaleTag(): String

// Compose already decodes newlines, tabs, Unicode and backslashes, but leaves Android quote
// escapes.
internal fun decodeResourceTemplate(template: String): String =
    template.replace("\\'", "'").replace("\\\"", "\"")

@Composable expect fun sharedStringResource(resource: StringResource, vararg arguments: Any): String

@Composable
expect fun sharedPluralStringResource(
    resource: PluralStringResource,
    quantity: Int,
    vararg arguments: Any,
): String

@Composable expect fun sharedPainterResource(resource: DrawableResource): Painter

suspend fun sharedGetString(resource: StringResource, vararg arguments: Any): String =
    formatResourceTemplate(getString(resource), arguments, resourceLocaleTag())
