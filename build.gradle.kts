import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.*

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "me.az"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

val koolVersion: String by project

kotlin {

//    android()
    js(IR) {
        browser {
            binaries.executable()
            @Suppress("EXPERIMENTAL_API_USAGE")
            distribution {
                directory = File("${rootDir}/dist/")
                name = "app"

            }
            commonWebpackConfig {
                // small js code
                //mode = KotlinWebpackConfig.Mode.PRODUCTION
                // readable js code but ~twice the file size
                mode = if(project.hasProperty("prod")) Mode.PRODUCTION else Mode.DEVELOPMENT
                cssSupport.enabled = true
                //outputPath = File(buildDir, "/processedResources/js/main/")
            }
        }
    }

    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = freeCompilerArgs + "-Xbackend-threads=0"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("de.fabmax.kool:kool-core:${koolVersion}")
//                implementation(":kool-core")
                implementation(kotlin("stdlib-common"))
                implementation(DepsCommon.kotlinCoroutines)
                implementation(DepsCommon.kotlinSerialization)
                implementation(DepsCommon.kotlinSerializationJson)
            }
        }
        val jsMain by getting {
            dependencies {
                //implementation(npm("pako", "2.0.4"))
                implementation("de.fabmax.kool:kool-core-js:${koolVersion}")

            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(DepsJvm.lwjgl())
                implementation(DepsJvm.lwjgl("stb"))
                runtimeOnly(DepsJvm.lwjglNatives("stb"))

                runtimeOnly(DepsJvm.lwjglNatives())
                runtimeOnly(DepsJvm.lwjglNatives("glfw"))
                runtimeOnly(DepsJvm.lwjglNatives("jemalloc"))
                runtimeOnly(DepsJvm.lwjglNatives("opengl"))
                runtimeOnly(DepsJvm.lwjglNatives("vma"))
                runtimeOnly(DepsJvm.lwjglNatives("shaderc"))
                runtimeOnly(DepsJvm.lwjglNatives("nfd"))

                implementation("de.fabmax.kool:kool-core-jvm:${koolVersion}")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
    }
}


tasks["clean"].doLast {
    delete("${rootDir}/dist/")
    delete(rootProject.buildDir)
}