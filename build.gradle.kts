import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.Mode

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    distribution
    application
}

group = "me.az"
version = "0.6-SNAPSHOT"

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
        withJava()
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
                implementation(project("bits"))
                implementation("de.fabmax.kool:kool-core:${koolVersion}")
//                implementation(":kool-core")
                implementation(kotlin("stdlib-common"))
                implementation(DepsCommon.kotlinCoroutines)
                implementation(DepsCommon.kotlinSerialization)
                implementation(DepsCommon.kotlinSerializationJson)
                implementation("com.russhwolf:multiplatform-settings-no-arg:0.9")
                implementation("org.mifek.wfc:WFC-Kotlin:1.2.1")
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

                // bundle
                implementation(DepsJvm.lwjglNatives(platform = org.gradle.internal.os.OperatingSystem.WINDOWS))
                implementation(DepsJvm.lwjglNatives("glfw", org.gradle.internal.os.OperatingSystem.WINDOWS))
                implementation(DepsJvm.lwjglNatives("jemalloc", org.gradle.internal.os.OperatingSystem.WINDOWS))
                implementation(DepsJvm.lwjglNatives("opengl", org.gradle.internal.os.OperatingSystem.WINDOWS))
                implementation(DepsJvm.lwjglNatives("vma", org.gradle.internal.os.OperatingSystem.WINDOWS))
                implementation(DepsJvm.lwjglNatives("shaderc", org.gradle.internal.os.OperatingSystem.WINDOWS))
                implementation(DepsJvm.lwjglNatives("nfd", org.gradle.internal.os.OperatingSystem.WINDOWS))
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

application {
    mainClass.set("MainKt")
}

tasks["clean"].doLast {
    delete("${rootDir}/dist/")
    delete(rootProject.buildDir)
}

tasks.register<VersionNameUpdate>("updateVersion") {
    versionName = "$version"
    filesToUpdate = listOf(
        kotlin.sourceSets.findByName("commonMain")?.kotlin
            ?.sourceDirectories
            ?.map { File(it, "me/az/Version.kt") }
            ?.find { it.exists() }?.absolutePath ?: ""
    )
}
tasks["compileKotlinJs"].dependsOn("updateVersion")
tasks["compileKotlinJvm"].dependsOn("updateVersion")

distributions {
    main {
        distributionBaseName.set("ttld")
        contents {
            into("") {
                val jvmJar by tasks.getting
                from(jvmJar)
            }
            into("lib/") {
                val main by kotlin.jvm().compilations.getting
                from(main.runtimeDependencyFiles)
            }
            into("assets/") {
                from("src/commonMain/resources/")
            }
        }
    }
}

gradle.taskGraph.whenReady {
    allTasks
        .filter { it.hasProperty("duplicatesStrategy") } // Because it's some weird decorated wrapper that I can't cast.
        .forEach {
            println(it)
            it.setProperty("duplicatesStrategy", "EXCLUDE")
        }
}