package dev.sebastiano.headroom.tracing

import androidx.tracing.Tracer

object AppTracing {
    const val CATEGORY: String = "headroom"
    val tracer: Tracer
        get() = Tracer.global
}

@PublishedApi
internal actual fun beginUiTrace(name: String): (() -> Unit)? {
    val tracer = AppTracing.tracer
    if (!tracer.isCategoryEnabled(AppTracing.CATEGORY)) return null
    val section = tracer.beginSection(AppTracing.CATEGORY, name, token = null) {}
    return { section.close() }
}
