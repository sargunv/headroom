package dev.sebastiano.headroom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The frame the sheet steps are drawn in: the sheet's shape, colour and drag handle over a scrim,
 * at the sheet's width. The real sheet is a separate window, which Robolectric does not capture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SheetFrame(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .background(Color.Black.copy(alpha = SCRIM_ALPHA)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = BottomSheetDefaults.ExpandedShape,
            color = BottomSheetDefaults.ContainerColor,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().testTag(SHEET_FRAME_TAG),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BottomSheetDefaults.DragHandle()
                content()
            }
        }
    }
}

private const val SCRIM_ALPHA = 0.32f

/** The sheet itself, which the step screenshots are cropped to. */
internal const val SHEET_FRAME_TAG = "sheet-frame"

internal const val PROTOTYPE_PHONE = "w411dp-h891dp-xxhdpi"
internal const val PROTOTYPE_EXPANDED = "w1280dp-h800dp-xhdpi"
internal const val PROTOTYPE_SETTLE_STEPS = 40
internal const val PROTOTYPE_STEP_MILLIS = 50L

internal fun prototypeScreenshot(name: String): String =
    "build/outputs/roborazzi/prototypes/$name.png"
