package dev.sebastiano.headroom.buildlogic

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeComposeResourcesGeneratorTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `maps canonical strings plurals and drawable files to native resource IDs`() {
        val resources = temporaryFolder.newFolder("composeResources")
        resources.resolve("values").mkdirs()
        resources
            .resolve("values/strings.xml")
            .writeText(
                """
                <resources>
                    <string name="title">Title</string>
                    <plurals name="count"><item quantity="other">%d items</item></plurals>
                </resources>
                """
                    .trimIndent()
            )
        resources
            .resolve("values/strings_prototype.xml")
            .writeText("<resources><string name=\"when\">Keyword</string></resources>")
        resources.resolve("values-fr").mkdirs()
        resources
            .resolve("values-fr/strings.xml")
            .writeText("<resources><string name=\"title\">Titre</string></resources>")
        resources.resolve("drawable").mkdirs()
        resources.resolve("drawable/ic_add.xml").writeText("<vector />")

        val source = renderNativeComposeResources(resources, "example.android", "example.resources")

        assertTrue(source.contains("package example.resources"))
        assertTrue(source.contains("import example.android.R as AndroidR"))
        assertTrue(source.contains("import example.resources.`when`"))
        assertTrue(source.contains("Res.string.`title` -> AndroidR.string.`title`"))
        assertTrue(source.contains("Res.string.`when` -> AndroidR.string.`when`"))
        assertTrue(source.contains("Res.plurals.`count` -> AndroidR.plurals.`count`"))
        assertTrue(source.contains("Res.drawable.`ic_add` -> AndroidR.drawable.`ic_add`"))
        assertTrue(source.contains("fun stringId(resource: StringResource): Int?"))
        assertTrue(source.contains("fun pluralId(resource: PluralStringResource): Int?"))
        assertTrue(source.contains("fun drawableId(resource: DrawableResource): Int?"))
        assertEquals(1, source.lines().count { "Res.string.`title` ->" in it })
        assertEquals(3, source.lines().count { "else -> null" in it })
        assertEquals(
            source,
            renderNativeComposeResources(resources, "example.android", "example.resources"),
        )
    }

    @Test
    fun `large resource catalogs split lookups below JVM method size limits`() {
        val resources = temporaryFolder.newFolder("largeResources")
        resources.resolve("values").mkdirs()
        resources
            .resolve("values/strings.xml")
            .writeText(
                buildString {
                    append("<resources>")
                    repeat(5000) { append("<string name=\"label_$it\">Label</string>") }
                    append("</resources>")
                }
            )

        val source = renderNativeComposeResources(resources, "example.android", "example.resources")

        assertEquals(5000, source.lines().count { " -> AndroidR.string." in it })
        assertTrue(source.contains("private fun stringId0(resource: StringResource): Int?"))
        assertTrue(source.contains("private fun stringId1(resource: StringResource): Int?"))
        assertTrue(source.contains("stringId0(resource) ?:"))
    }

    @Test
    fun `rejects external XML entities`() {
        val resources = temporaryFolder.newFolder("untrustedResources")
        resources.resolve("values").mkdirs()
        val secret = temporaryFolder.newFile("outside.txt").apply { writeText("outside data") }
        resources
            .resolve("values/strings.xml")
            .writeText(
                """
            <!DOCTYPE resources [<!ENTITY external SYSTEM "${secret.toURI()}">]>
            <resources><string name="title">&external;</string></resources>
            """
                    .trimIndent()
            )

        val failure = runCatching {
            renderNativeComposeResources(resources, "example.android", "example.resources")
        }

        assertTrue("DOCTYPE must be rejected", failure.isFailure)
        assertEquals("outside data", Files.readString(secret.toPath()))
    }
}
