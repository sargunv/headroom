package dev.sebastiano.headroom.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.sebastiano.headroom.designsystem.resources.NativeComposeResources
import java.util.Locale
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

@Composable
actual fun sharedStringResource(resource: StringResource, vararg arguments: Any): String {
    val id =
        requireNotNull(NativeComposeResources.stringId(resource)) { "Unknown string: $resource" }
    return if (arguments.isEmpty()) stringResource(id) else stringResource(id, *arguments)
}

@Composable
actual fun sharedPluralStringResource(
    resource: PluralStringResource,
    quantity: Int,
    vararg arguments: Any,
): String {
    val id =
        requireNotNull(NativeComposeResources.pluralId(resource)) { "Unknown plural: $resource" }
    return if (arguments.isEmpty()) pluralStringResource(id, quantity)
    else pluralStringResource(id, quantity, *arguments)
}

@Composable
actual fun sharedPainterResource(resource: DrawableResource): Painter =
    painterResource(
        requireNotNull(NativeComposeResources.drawableId(resource)) {
            "Unknown drawable: $resource"
        }
    )

@Suppress(
    "SpreadOperator"
) // Java Formatter requires varargs to preserve Android template semantics.
internal actual fun formatResourceTemplate(
    template: String,
    arguments: Array<out Any>,
    localeTag: String,
): String =
    if (arguments.isEmpty()) decodeResourceTemplate(template)
    else
        String.format(
            Locale.forLanguageTag(localeTag),
            decodeResourceTemplate(template),
            *arguments,
        )

internal actual fun resourceLocaleTag(): String = Locale.getDefault().toLanguageTag()
