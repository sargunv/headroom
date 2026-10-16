package dev.sebastiano.headroom.buildlogic

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element

/** Maps shared resource handles to Android IDs used by the native resource adapters. */
@CacheableTask
abstract class GenerateNativeComposeResources : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceDirectory: DirectoryProperty

    @get:Input abstract val androidNamespace: Property<String>

    @get:Input abstract val resourcePackage: Property<String>

    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val source =
            renderNativeComposeResources(
                resourceDirectory.get().asFile,
                androidNamespace.get(),
                resourcePackage.get(),
            )
        outputDirectory
            .get()
            .asFile
            .apply { mkdirs() }
            .resolve("NativeComposeResources.kt")
            .writeText(source)
    }
}

internal fun renderNativeComposeResources(
    resourceDirectory: File,
    androidNamespace: String,
    resourcePackage: String,
): String {
    val strings = sortedSetOf<String>()
    val plurals = sortedSetOf<String>()
    val drawables = sortedSetOf<String>()
    val parser =
        DocumentBuilderFactory.newInstance()
            .apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            .newDocumentBuilder()

    resourceDirectory
        .listFiles()
        .orEmpty()
        .filter(File::isDirectory)
        .sortedBy(File::getName)
        .forEach { directory ->
            when (directory.name.substringBefore('-')) {
                "values" ->
                    directory
                        .listFiles()
                        .orEmpty()
                        .filter { it.extension == "xml" }
                        .sortedBy(File::getName)
                        .forEach { file ->
                            val children = parser.parse(file).documentElement.childNodes
                            for (index in 0 until children.length) {
                                val element = children.item(index) as? Element ?: continue
                                when (element.tagName) {
                                    "string" ->
                                        strings += resourceIdentifier(element.getAttribute("name"))
                                    "plurals" ->
                                        plurals += resourceIdentifier(element.getAttribute("name"))
                                }
                            }
                        }
                "drawable" ->
                    directory.listFiles().orEmpty().filter(File::isFile).forEach {
                        drawables += resourceIdentifier(it.nameWithoutExtension.removeSuffix(".9"))
                    }
            }
        }

    return buildString {
        appendLine("// Generated from canonical Compose resources. Do not edit.")
        appendLine("package $resourcePackage")
        appendLine()
        appendLine("import $androidNamespace.R as AndroidR")
        (strings + plurals + drawables).sorted().forEach {
            appendLine("import $resourcePackage.`$it`")
        }
        appendLine("import org.jetbrains.compose.resources.DrawableResource")
        appendLine("import org.jetbrains.compose.resources.PluralStringResource")
        appendLine("import org.jetbrains.compose.resources.StringResource")
        appendLine()
        appendLine("internal object NativeComposeResources {")
        appendLookup("stringId", "StringResource", "string", strings)
        appendLine()
        appendLookup("pluralId", "PluralStringResource", "plurals", plurals)
        appendLine()
        appendLookup("drawableId", "DrawableResource", "drawable", drawables)
        appendLine("}")
    }
}

private fun resourceIdentifier(name: String): String {
    val identifier = name.replace('.', '_').replace('-', '_')
    require(identifier.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
        "Unsupported resource name: $name"
    }
    return identifier
}

private fun StringBuilder.appendLookup(
    function: String,
    type: String,
    category: String,
    names: Set<String>,
) {
    val chunks = names.chunked(LOOKUP_CHUNK_SIZE)
    if (chunks.size > 1) {
        appendLine("    fun $function(resource: $type): Int? =")
        chunks.indices.forEach { index ->
            val continuation = if (index == chunks.lastIndex) "" else " ?:"
            appendLine("        $function$index(resource)$continuation")
        }
        chunks.forEachIndexed { index, chunk ->
            appendLine()
            appendWhenLookup("private fun $function$index", type, category, chunk)
        }
    } else {
        appendWhenLookup("fun $function", type, category, names)
    }
}

private fun StringBuilder.appendWhenLookup(
    declaration: String,
    type: String,
    category: String,
    names: Collection<String>,
) {
    appendLine("    $declaration(resource: $type): Int? = when (resource) {")
    names.forEach { appendLine("        Res.$category.`$it` -> AndroidR.$category.`$it`") }
    appendLine("        else -> null")
    appendLine("    }")
}

// Each comparison calls generated accessors; bound method sizes for large resource catalogs.
private const val LOOKUP_CHUNK_SIZE = 128
