import de.fabmax.kool.createContext
import de.fabmax.kool.platform.Lwjgl3Context
import me.az.utils.canDebug
import me.az.utils.debugOnly
import me.az.utils.logd

suspend fun main(args: Array<String>) {
    println("debug=$canDebug")
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
            //isFullscreen = true
//        customFonts += "text" to "fonts/daugsmith/daugsmith.ttf"
            debugOnly {
                isFullscreen = false
                // apple has 280×192
                // ibm has 320×200 or 640×200
                val c = 4
                width = 280*c
                height = 192*c
            }
        }
    /*}*/
    val scene = if ( args.size > 0 ) AppState.valueOf(args[0].uppercase())
    else AppState.MAINMENU
    App(ctx, scene)
}
