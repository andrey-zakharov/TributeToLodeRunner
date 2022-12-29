package me.az.view

import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.min
import de.fabmax.kool.math.spatial.BoundingBox
import de.fabmax.kool.pipeline.RenderPass
import de.fabmax.kool.scene.Group
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.OrthographicCamera
import de.fabmax.kool.scene.animation.*
import de.fabmax.kool.util.Viewport
import me.az.ilode.Game
import me.az.scenes.height
import me.az.scenes.width
import me.az.utils.StackedState

private operator fun Vec3f.minus(position: Vec3f) = Vec3f(
    this.x - position.x,
    this.y - position.y,
    this.z - position.z,
)

class InterpolatedVec3f(val from: MutableVec3f, val `to`: MutableVec3f) : InterpolatedValue<Vec3f>(from) {
    override fun updateValue(interpolationPos: Float) {
        val x = InterpolatedFloat(from.x, to.x)
        val y = InterpolatedFloat(from.y, to.y)
        val z = InterpolatedFloat(from.z, to.z)
        x.interpolate(interpolationPos)
        y.interpolate(interpolationPos)
        z.interpolate(interpolationPos)
        from.set(x.value, y.value, z.value)
    }
}

class CameraController(val cameraToControl: OrthographicCamera, name: String? = "camcontrol", val ctx: KoolContext, val viewGroup: Group) : Node(name) {

    private val cameraPos = InterpolatedVec3f(MutableVec3f(cameraToControl.position), MutableVec3f(cameraToControl.position))
    private val cameraAnimator = InverseSquareAnimator(cameraPos).apply {
        duration = 1f / 4f //runner.xMove
        repeating = Animator.ONCE
        progress = 0f
        speed = 1f
    }

    private var calculator: ((RenderPass.UpdateEvent) -> Unit)? = null

    fun startTrack(game: Game, boundNode: BoundingBox, followNode: Mat4f) {
        calculator = cameraCalculator(game, boundNode, followNode)
        calculator?.run { onUpdate += this }

        game.onPlayGame += cameraUpdaterGameTick
    }

    fun stopTrack(game: Game) {
        calculator?.run { onUpdate -= this }
        game.onPlayGame -= cameraUpdaterGameTick
    }

    var debug = ""
    private fun calculateCamera(borderZone: BoundingBox, followNode: Mat4f): (viewport: Viewport) -> MutableVec3f {

        return { viewport ->
            val resultPos = MutableVec3f(0.5f, 0.5f, 0f) // middle of the sprite
            followNode.transform(resultPos)
            viewGroup.toGlobalCoords(resultPos)
            val visibleWidth = min( cameraToControl.width, viewport.width * (cameraToControl.height / viewport.height))
            val deadZone = BoundingBox().apply {
//                add(Vec3f(-1f, -1f, 0f))
//                add(Vec3f(1f, 1f, 0f))
                add(Vec3f(-visibleWidth / 4f, -cameraToControl.height / 4f, 0f))
                add(Vec3f(visibleWidth / 4f, cameraToControl.height / 4f, 0f))
            }

            with(cameraToControl) {
                // camera shift origin in viewport center
                resultPos -= Vec3f(0f, height / 2f, 0f)
                // get distance from camera. if more than viewport / 4 - move
                val diff = resultPos - globalLookAt
                //debug = diff.toString()
                debug = "${viewport.width} ${cameraToControl.width} $visibleWidth"


                if ( diff.x > deadZone.max.x ) {
                    resultPos.x = globalPos.x + diff.x - deadZone.max.x
                } else if ( diff.x < deadZone.min.x ) {
                    resultPos.x = globalPos.x - (deadZone.min.x - diff.x)
                } else {
                    resultPos.x = globalPos.x
                }

                if ( diff.y > deadZone.max.y ) {
                    resultPos.y = this@with.globalPos.y + diff.y - deadZone.max.y
                } else if ( diff.y < deadZone.min.y ) {
                    resultPos.y = this@with.globalPos.y - (deadZone.min.y - diff.y)
                } else {
                    resultPos.y = this@with.globalPos.y
                }

                borderZone.clampToBounds(resultPos)

                resultPos
            }
        }

    }
    private val cameraUpdaterGameTick = { _: Game, _: Any? ->
        with(cameraToControl) {
            position.set(cameraAnimator.tick(ctx))
            lookAt.set(position.x, position.y, 0f)
        }
        Unit
    }

    private fun cameraCalculator(game: Game, boundNode: BoundingBox, followNode: Mat4f): (RenderPass.UpdateEvent) -> Unit {

        val calculator = calculateCamera(boundNode, followNode)

        return { ev: RenderPass.UpdateEvent ->
            //if (game.isPlaying) {
                val pos = calculator(ev.viewport)
                // anim?
                cameraPos.to.set(
                    pos.x /*+ (ctx.inputMgr.pointerState.primaryPointer.x.toFloat() - ev.viewport.width/2) / 50f*/,
                    pos.y /*+ (ctx.inputMgr.pointerState.primaryPointer.y.toFloat() - ev.viewport.height/2) / 50f*/,
                    10f
                )
                cameraPos.from.set(cameraPos.value)
                cameraAnimator.speed = 1f
                cameraAnimator.progress = 0f
            //}
        }
    }


}