package dev.sebastiano.headroom.widgets

/** The widget styles the app offers. */
enum class WidgetStyle {
    Rings,
    Bars,
    Shape,
    Countdown,
}

/**
 * Asks the launcher to pin a widget. Returns false when that cannot happen (no provider for the
 * style yet, or a launcher that does not support pinning), so the UI can explain.
 */
fun interface WidgetPinner {
    fun requestPin(style: WidgetStyle): Boolean
}
