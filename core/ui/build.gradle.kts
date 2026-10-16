import dev.sebastiano.headroom.buildlogic.GenerateNativeComposeResources

plugins { alias(libs.plugins.headroom.kmp.compose.library) }

kotlin {
    android { namespace = "dev.sebastiano.headroom.ui" }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(projects.core.designsystem)
            api(projects.core.storage)
            api(libs.cmp.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.cmp.adaptive)
            implementation(libs.cmp.adaptive.layout)
            implementation(libs.cmp.adaptive.navigation)
            implementation(libs.cmp.material3.navigationSuite)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.androidx.lifecycle.viewmodel.savedstate)
            api(libs.androidx.lifecycle.viewmodel.compose)
            api(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.haze)
            implementation(libs.haze.blur)
            implementation(libs.reorderable)
            implementation(libs.aboutlibraries.compose.m3)
        }
        androidMain.dependencies {
            implementation(libs.compose.material3.adaptive.navigationSuite)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.browser)
            implementation(libs.androidx.tracing)
        }
        jvmTest.dependencies {
            implementation(libs.desktop.ui.test)
            implementation(compose.desktop.currentOs)
        }
        androidHostTest.dependencies {
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.junit.rule)
        }
    }
}

val resourcesPackage = providers.provider { "dev.sebastiano.headroom.ui.resources" }

compose.resources {
    publicResClass = true
    packageOfResClass = resourcesPackage.get()
    generateResClass = always
}

tasks.named<GenerateNativeComposeResources>("generateNativeComposeResources") {
    resourcePackage.set(resourcesPackage)
}

// Keep Android resource IDs available while existing Android callers migrate to Res accessors.
androidComponents {
    onVariants { variant ->
        variant.sources.res?.addStaticSourceDirectory("src/commonMain/composeResources")
    }
}
