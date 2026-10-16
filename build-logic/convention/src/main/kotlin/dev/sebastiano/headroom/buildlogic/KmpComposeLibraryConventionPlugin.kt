package dev.sebastiano.headroom.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.dsl.Lint
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Shared Compose UI for Android and desktop, retaining Android's Robolectric test runner. */
class KmpComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("com.android.lint")

            val nativeResources =
                tasks.register<GenerateNativeComposeResources>("generateNativeComposeResources") {
                    resourceDirectory.set(
                        layout.projectDirectory.dir("src/commonMain/composeResources")
                    )
                    outputDirectory.set(
                        layout.buildDirectory.dir("generated/nativeComposeResources")
                    )
                }
            extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
                finalizeDsl { dsl ->
                    nativeResources.configure { androidNamespace.set(dsl.namespace) }
                }
                onVariants { variant ->
                    variant.sources.kotlin?.addGeneratedSourceDirectory(
                        nativeResources,
                        GenerateNativeComposeResources::outputDirectory,
                    )
                }
            }

            extensions.configure<KotlinMultiplatformExtension> {
                jvmToolchain(HeadroomSdk.JVM_TOOLCHAIN)
                jvm()
                (this as ExtensionAware)
                    .extensions
                    .getByType<KotlinMultiplatformAndroidLibraryTarget>()
                    .apply {
                        compileSdk = HeadroomSdk.COMPILE
                        minSdk = HeadroomSdk.MIN
                        androidResources.enable = true
                        lint {
                            configureZeroTolerance()
                            checkGeneratedSources = true
                        }
                        withHostTestBuilder { sourceSetTreeName = "test" }
                            .configure {
                                isIncludeAndroidResources = true
                                isReturnDefaultValues = false
                            }
                        packaging.resources.excludes +=
                            setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
                    }

                sourceSets.named("commonMain") {
                    dependencies {
                        implementation(libs.library("cmp-runtime"))
                        implementation(libs.library("cmp-foundation"))
                        implementation(libs.library("cmp-ui"))
                        implementation(libs.library("cmp-ui-graphics"))
                        implementation(libs.library("cmp-animation"))
                        implementation(libs.library("cmp-material3"))
                        implementation(libs.library("cmp-resources"))
                    }
                }
                sourceSets.named("androidMain") {
                    dependencies {
                        implementation(project.dependencies.platform(libs.library("compose-bom")))
                        implementation(libs.library("compose-material3"))
                    }
                }
                sourceSets.named("commonTest") {
                    dependencies {
                        implementation(libs.library("kotlin-test"))
                        implementation(libs.library("kotlinx-coroutines-test"))
                    }
                }
                sourceSets.named("jvmTest") {
                    dependencies {
                        implementation(project.dependencies.platform(libs.library("junit5-bom")))
                        implementation(libs.library("kotlin-test-junit5"))
                        runtimeOnly(libs.library("junit5-launcher"))
                    }
                }
                sourceSets.named("androidHostTest") {
                    dependencies {
                        implementation(libs.library("junit4"))
                        implementation(libs.library("kotlin-test-junit"))
                        implementation(libs.library("robolectric"))
                        implementation(libs.library("turbine"))
                        implementation(project.dependencies.platform(libs.library("compose-bom")))
                        implementation(libs.library("compose-ui-test-junit4"))
                        implementation(libs.library("compose-ui-test-manifest"))
                    }
                }
            }

            tasks.withType<Test>().configureEach {
                when (name) {
                    "jvmTest" -> useJUnitPlatform()
                    "testAndroidHostTest" -> {
                        useJUnit()
                        jvmArgs(
                            "--add-opens=java.base/java.io=ALL-UNNAMED",
                            "--add-opens=java.base/java.lang=ALL-UNNAMED",
                            "--add-opens=java.base/java.util=ALL-UNNAMED",
                            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                        )
                        maxHeapSize = "2g"
                        if (target.path == ":core:ui") {
                            providers.gradleProperty(APP_TEST_SHARD_PROPERTY).orNull?.let {
                                applyAppTestShard(this, it)
                            }
                        }
                    }
                }
            }
            extensions.configure<Lint> {
                configureZeroTolerance()
                // Native resource adapters use generated lookups; Lint must see their R references.
                checkGeneratedSources = true
            }
            configureQuality()
            dependencies { add("detektPlugins", libs.library("composeRules-detekt")) }
        }
}
