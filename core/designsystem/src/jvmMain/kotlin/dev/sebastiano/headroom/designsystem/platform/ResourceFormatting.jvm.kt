package dev.sebastiano.headroom.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalLocale
import java.util.Locale
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun sharedStringResource(resource: StringResource, vararg arguments: Any): String =
    formatResourceTemplate(stringResource(resource), arguments, LocalLocale.current.toLanguageTag())

@Composable
actual fun sharedPluralStringResource(
    resource: PluralStringResource,
    quantity: Int,
    vararg arguments: Any,
): String =
    formatResourceTemplate(
        pluralStringResource(resource, quantity),
        arguments,
        LocalLocale.current.toLanguageTag(),
    )

@Composable
actual fun sharedPainterResource(resource: DrawableResource): Painter = painterResource(resource)

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
