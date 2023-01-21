import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.provideDelegate
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

open class VersionNameUpdate : DefaultTask() {
    @Input
    var versionName = "0.0.0"

    @Input
    var filesToUpdate = listOf<String>()

    @TaskAction
    fun updateVersions() {
        filesToUpdate.forEach { file ->
            val releasing: String by project
            val versionStr = versionName.replace("SNAPSHOT", SimpleDateFormat("yyMMdd.HHmm").format(Date()))
            val text = mutableListOf<String>()
            var updated = false

            if (releasing == "true") {
                FileReader(file).use {
                    text += it.readLines()
                    for (i in text.indices) {
                        val startI = text[i].indexOf("const val Version =")
                        if (startI >= 0) {
                            text[i] = text[i].substring(0 until startI) + "const val Version = \"$versionStr\""
                            updated = true
                            break
                        }
                    }
                }
                if (updated) {
                    FileWriter(file).use {
                        text.forEach { line ->
                            it.append(line).append(System.lineSeparator())
                        }
                    }
                }
            }
        }
    }
}