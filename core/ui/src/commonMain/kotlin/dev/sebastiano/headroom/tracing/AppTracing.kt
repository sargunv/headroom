package dev.sebastiano.headroom.tracing

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable

@PublishedApi internal expect fun beginUiTrace(name: String): (() -> Unit)?

/**
 * Runs [block] inside a trace section called [name]. Unlike `Tracer.trace`, the block is not
 * `crossinline`, so a composable can call other composables inside it.
 */
inline fun <T> traced(name: String, block: () -> T): T {
    val close = beginUiTrace(name) ?: return block()
    try {
        return block()
    } finally {
        close()
    }
}

/**
 * A lazy list item whose composition is recorded as a trace section called [name], so a slow frame
 * while scrolling shows which item was being composed.
 */
fun LazyListScope.tracedItem(
    name: String,
    key: Any? = null,
    content: @Composable LazyItemScope.() -> Unit,
) {
    item(key = key) { traced(name) { content() } }
}
