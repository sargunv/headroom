package dev.sebastiano.headroom.tracing

@PublishedApi internal actual fun beginUiTrace(name: String): (() -> Unit)? = null
