import org.gradle.internal.os.OperatingSystem

object Versions {
    val kotlinCoroutinesVersion = "1.6.0"
    val kotlinSerializationVersion = "1.3.2"
//    val koolVersion = "0.8.0"
    val koolVersion = "0.9.0-SNAPSHOT"

    val lwjglVersion = "3.3.1"
    val lwjglNatives = OperatingSystem.current().let {
        when {
            it.isLinux -> "natives-linux"
            it.isMacOsX -> "natives-macos"
            else -> "natives-windows"
        }
    }
}

object DepsCommon {
    val kotlinCoroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinCoroutinesVersion}"
    val kotlinSerialization = "org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.kotlinSerializationVersion}"
    val kotlinSerializationJson = "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinSerializationVersion}"
}

object DepsJvm {
    fun lwjgl(subLib: String? = null): String {
        return if (subLib != null) {
            "org.lwjgl:lwjgl-$subLib:${Versions.lwjglVersion}"
        } else {
            "org.lwjgl:lwjgl:${Versions.lwjglVersion}"
        }
    }

    fun lwjglNatives(subLib: String? = null): String {
        return if (subLib != null) {
            "org.lwjgl:lwjgl-$subLib:${Versions.lwjglVersion}:${Versions.lwjglNatives}"
        } else {
            "org.lwjgl:lwjgl:${Versions.lwjglVersion}:${Versions.lwjglNatives}"
        }
    }
}
