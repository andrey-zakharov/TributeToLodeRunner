import java.net.URI

pluginManagement {
    plugins {
        val kotlinVersion: String by settings
        kotlin("plugin.serialization") version kotlinVersion
        kotlin("multiplatform") version kotlinVersion
    }
}

sourceControl {
    gitRepository(
        //URI.create("https://github.com/andrey-zakharov/kit.git")
        uri("../kit")
    ) {
        producesModule("me.zakharov:kit")
    }
}

rootProject.name = "Infinner"
//include(":bits")
//project(":bits").projectDir = File("../kit")
//include("bits")

