package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

internal actual fun platformFont(
    weight: FontWeight,
    variationSettings: FontVariation.Settings,
): Font = Font(R.font.google_sans_flex, weight = weight, variationSettings = variationSettings)
