package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

internal actual fun platformFont(
    weight: FontWeight,
    variationSettings: FontVariation.Settings,
): Font =
    Font(
        resource =
            "composeResources/dev.sebastiano.headroom.designsystem.resources/font/google_sans_flex.ttf",
        weight = weight,
        variationSettings = variationSettings,
    )
