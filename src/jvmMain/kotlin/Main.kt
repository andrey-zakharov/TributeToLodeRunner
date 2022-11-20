import de.fabmax.kool.createContext
import de.fabmax.kool.platform.Lwjgl3Context
import me.az.utils.logd

@JvmField
val canDebug: Boolean = System.getProperty("debug").toBoolean()
inline fun debugOnly( block: () -> Unit ) { if ( canDebug ) block() }

suspend fun main() {
    logd { "Starting with Java = ${System.getProperty("java.version")}" }

    val assetsDir = "assets"
    val title = "Tribute to Lode Runner by Andrey Zakharov 2022"
    val ctx = /*try {
        createContext {
            assetsBaseDir = assetsDir
            renderBackend = Lwjgl3Context.Backend.VULKAN
            this.title = title
//        customFonts += "text" to "fonts/daugsmith/daugsmith.ttf"
        }
    } catch (e: java.lang.Exception) {*/
        createContext {
            localAssetPath = assetsDir
            renderBackend = Lwjgl3Context.Backend.OPEN_GL
            this.title = title
//        customFonts += "text" to "fonts/daugsmith/daugsmith.ttf"
        }
    /*}*/
    App(ctx)
}
