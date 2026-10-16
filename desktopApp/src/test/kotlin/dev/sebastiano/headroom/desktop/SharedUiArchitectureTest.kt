package dev.sebastiano.headroom.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedUiArchitectureTest {
    private val root =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { it.resolve("settings.gradle.kts").exists() }

    @Test
    fun `original screens and view models are compiled from common code`() {
        val sources = root.resolve("core/ui/src/commonMain/kotlin/dev/sebastiano/headroom/ui")
        listOf(
                "HeadroomApp.kt",
                "HomeScaffold.kt",
                "overview/OverviewScreen.kt",
                "detail/DetailScreen.kt",
                "accounts/AccountsScreen.kt",
                "resets/ResetsScreen.kt",
                "stats/StatsScreen.kt",
                "settings/SettingsScreen.kt",
                "home/HomeViewModel.kt",
                "accounts/AccountsViewModel.kt",
                "settings/SettingsViewModel.kt",
                "stats/StatsViewModel.kt",
            )
            .forEach { relative ->
                assertTrue(
                    sources.resolve(relative).exists(),
                    "$relative must have one shared implementation",
                )
                assertFalse(
                    root
                        .resolve("app/src/main/kotlin/dev/sebastiano/headroom/ui/$relative")
                        .exists(),
                    "$relative must not have an Android presentation fork",
                )
            }
    }

    @Test
    fun `Android and Nucleus shells both render the original shared application`() {
        val android =
            root.resolve("app/src/main/kotlin/dev/sebastiano/headroom/MainActivity.kt").readText()
        val desktop =
            Files.walk(
                    root.resolve("desktopApp/src/main/kotlin/dev/sebastiano/headroom/desktop/shell")
                )
                .use { paths ->
                    paths
                        .filter { it.toString().endsWith(".kt") }
                        .map { it.readText() }
                        .toList()
                        .joinToString("\n")
                }
        assertTrue(android.contains("HeadroomApp("))
        assertTrue(desktop.contains("HeadroomApp("))
        assertFalse(desktop.contains("desktop.ui.DesktopApp"))
    }

    @Test
    fun `common presentation contains no platform imports and desktop has no screen fork`() {
        val forbidden =
            Regex(
                "(?m)^import (android\\.|java\\.|javax\\.|dev\\.sebastiano\\.headroom\\.desktop\\.)"
            )
        listOf("core/ui", "core/designsystem").forEach { module ->
            val sources = root.resolve("$module/src/commonMain/kotlin")
            if (sources.exists())
                Files.walk(sources).use { files ->
                    files
                        .filter { it.toString().endsWith(".kt") }
                        .forEach { source ->
                            assertFalse(
                                forbidden.containsMatchIn(source.readText()),
                                "Platform import in $source",
                            )
                        }
                }
        }
        val fork = root.resolve("desktopApp/src/main/kotlin/dev/sebastiano/headroom/desktop/ui")
        if (fork.exists())
            Files.walk(fork).use { files ->
                assertFalse(
                    files.anyMatch { it.toString().endsWith(".kt") },
                    "Remove the duplicate desktop presentation",
                )
            }
    }
}
