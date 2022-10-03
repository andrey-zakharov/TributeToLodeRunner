import de.fabmax.kool.createContext
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.pipeline.OffscreenRenderPass2d
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.renderPassConfig
import de.fabmax.kool.platform.Lwjgl3Context
import de.fabmax.kool.scene.*
import de.fabmax.kool.util.Color

fun main() {

    createContext {
        renderBackend = Lwjgl3Context.Backend.OPEN_GL
        title = "Kool App"
    }.apply {



        run()
    }
}