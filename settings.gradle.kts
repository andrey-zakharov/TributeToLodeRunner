pluginManagement {
    plugins {
        val kotlinVersion: String by settings
        kotlin("plugin.serialization") version kotlinVersion
        kotlin("multiplatform") version kotlinVersion
    }
}

rootProject.name = "Infinner"

