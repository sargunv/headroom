import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    alias(libs.plugins.headroom.jvm.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.nucleus)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
}

dependencies {
    implementation(projects.shared)
    implementation(projects.core.ui)
    implementation(projects.core.model)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jna)
    implementation(libs.materialkolor.utilities)
    implementation(compose.desktop.currentOs)
    implementation(libs.desktop.material3)
    implementation(libs.nucleus.application)
    implementation(libs.nucleus.tray)
    implementation(libs.nucleus.notifications.common)
    implementation(libs.nucleus.notifications.macos)
    implementation(libs.nucleus.autolaunch)
    testImplementation(libs.desktop.ui.test)
    add("detektPlugins", libs.composeRules.detekt)
}

tasks.named<ProcessResources>("processResources") {
    from(tasks.named("exportLibraryDefinitions")) { into("licenses") }
    from(rootProject.file("LICENSE")) { into("licenses/headroom") }
}

nucleus.application {
    mainClass = "dev.sebastiano.headroom.desktop.MainKt"
    nucleusOptimization = false
    jvmArgs("-XstartOnFirstThread")

    nativeDistributions {
        packageName = "Headroom"
        packageVersion = "1.2.0"
        targetFormats(TargetFormat.Dmg)
        protocol("Headroom", "headroom")
        enableAotCache = false
        macOS {
            bundleID = "dev.sebastiano.headroom.desktop"
            minimumSystemVersion = "13.0"
            iconFile.set(project.file("src/main/resources/Headroom.icns"))
        }
    }
}
