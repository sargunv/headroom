import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import dev.sebastiano.headroom.buildlogic.GenerateNativeComposeResources

plugins { alias(libs.plugins.headroom.kmp.compose.library) }

kotlin {
    android { namespace = "dev.sebastiano.headroom.designsystem" }
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(libs.graphics.shapes)
            api(libs.cmp.resources)
            implementation(libs.materialkolor.utilities)
            implementation(libs.kotlinx.atomicfu)
        }
        // Keep the original Android test paths, including their test-specific analysis rules.
        androidHostTest { kotlin.srcDir("src/test/kotlin") }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.desktop.ui.test)
        }
    }
}

val resourcesPackage = providers.provider { "dev.sebastiano.headroom.designsystem.resources" }

compose.resources {
    packageOfResClass = resourcesPackage.get()
    publicResClass = true
}

tasks.named<GenerateNativeComposeResources>("generateNativeComposeResources") {
    resourcePackage.set(resourcesPackage)
}

// Widgets and Android font loading retain R access to the same canonical common resources.
extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
    onVariants { variant ->
        variant.sources.res?.addStaticSourceDirectory("src/commonMain/composeResources")
        variant.sources.assets?.addStaticSourceDirectory("src/commonMain/resources")
    }
}
