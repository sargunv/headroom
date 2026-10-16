package dev.sebastiano.headroom.shared

import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class OpenHeadroomTest {
    @Test
    fun `a headless opener observes and reopens cleanly without a Main dispatcher`() = runBlocking {
        val directory = Files.createTempDirectory("headroom-opener").toFile()
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            repeat(2) {
                val headroom = openHeadroom(directory.path, EmptySecrets, dispatcher)
                try {
                    val observed = CompletableDeferred<OverviewUi>()
                    val watch = headroom.watchOverview { overview -> observed.complete(overview) }
                    val overview = withTimeout(10_000) { observed.await() }
                    watch.cancel()
                    assertEquals(true, overview.isDemo)
                    headroom.refreshAll()
                } finally {
                    headroom.closeAndJoin()
                }
            }
        } finally {
            dispatcher.close()
            directory.deleteRecursively()
        }
    }
}

private object EmptySecrets : SecureValueStore {
    override fun read(key: String): String? = null

    override fun write(key: String, value: String): Boolean = true

    override fun delete(key: String): Boolean = true
}
