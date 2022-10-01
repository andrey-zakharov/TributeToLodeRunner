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

        // create offscreen content
        val backgroundGroup = group {
            +colorMesh {
                generate {
                    cube {
                        colored()
                        centered()
                    }
                }
                shader = KslUnlitShader { color { vertexColor() } }
            }
            onUpdate += { rotate(it.deltaT * 30f, Vec3f.Y_AXIS) }
        }

        // setup offscreen pass
        val off = OffscreenRenderPass2d(backgroundGroup, renderPassConfig {
            width = 512
            height = 512
            addColorTexture {
                colorFormat = TexFormat.RGBA
            }
        }).apply {
            clearColor = Color.BLACK
            camera.position.set(0f, 1f, 2f)
        }

        // create main scene and draw offscreen result on a quad
        scenes += scene {
            defaultCamTransform()
            addOffscreenPass(off)

            +textureMesh {
                generate {
                    rect {
                        size.set(2f, 2f)
                        origin.set(-1f, -1f, 0f)
                    }
                }
                shader = KslUnlitShader { color { textureColor(off.colorTexture) } }
            }
        }

        run()
    }
}