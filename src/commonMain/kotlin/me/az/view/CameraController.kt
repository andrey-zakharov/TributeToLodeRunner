package me.az.view

import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
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

class CameraController(private val cameraToControl: OrthographicCamera, name: String? = "camcontrol", val ctx: KoolContext) : Node(name) {

    private val cameraPos = InterpolatedVec3f(MutableVec3f(cameraToControl.position), MutableVec3f(cameraToControl.position))
    private val cameraAnimator = InverseSquareAnimator(cameraPos).apply {
        duration = 1f / 4f //runner.xMove
        repeating = Animator.ONCE
        progress = 0f
        speed = 1f
    }

    private var calculator: ((RenderPass.UpdateEvent) -> Unit)? = null
//    var boundNode: Group? = null
//    var followNode: Node? = null

//    fun calculateCamera(levelView!!, levelView!!.runnerView)

    fun startTrack(game: Game, boundNode: Group, followNode: Node) {
        calculator = cameraCalculator(game, boundNode, followNode)
        calculator?.run { onUpdate += this }

        game.onPlayGame += cameraUpdater
    }

    fun stopTrack(game: Game) {
        calculator?.run { onUpdate -= this }
        game.onPlayGame -= cameraUpdater
    }

    private fun calculateCamera(boundNode: Group, followNode: Node): (viewport: Viewport) -> MutableVec3f {

        val borderZone = with(cameraToControl) {
            val scaledMin = MutableVec3f(boundNode.bounds.min)
            val scaledMax = MutableVec3f(boundNode.bounds.max)
            boundNode.transform.transform(scaledMin)
            boundNode.transform.transform(scaledMax)

            BoundingBox().apply {
                add(Vec3f( scaledMin.x + this@with.width / 2f, 0f, 0f))
                add(Vec3f( scaledMax.x - this@with.width / 2f, scaledMax.y - this@with.height, 0f))
            }
        }

        return { viewport ->
            val resultPos = MutableVec3f(followNode.globalCenter)
            val deadZone = BoundingBox().apply {
                add(Vec3f(-viewport.width / 4f, -viewport.height / 4f, 0f))
                add(Vec3f(viewport.width / 4f, viewport.height / 4f, 0f))
            }

            with(cameraToControl) {
                //camera shift
                resultPos -= Vec3f(0f, this@with.height / 2f, 0f)
                // get distance from camera. if more than viewport / 4 - move
                val diff = resultPos - this@with.globalLookAt

                if ( diff.x > deadZone.max.x ) {
                    resultPos.x = this@with.globalPos.x + diff.x - deadZone.max.x
                } else if ( diff.x < deadZone.min.x ) {
                    resultPos.x = this@with.globalPos.x - (deadZone.min.x - diff.x)
                } else {
                    resultPos.x = this@with.globalPos.x
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
    private val cameraUpdater = { _: Game, _: Any? ->
        with(cameraToControl) {
            position.set(cameraAnimator.tick(ctx))
            lookAt.set(position.x, position.y, 0f)
        }
        Unit
    }

    private fun cameraCalculator(game: Game, boundNode: Group, followNode: Node): (RenderPass.UpdateEvent) -> Unit {

        val calculator = calculateCamera(boundNode, followNode)

        return { ev: RenderPass.UpdateEvent ->
            if (game.isPlaying) {
                val pos = calculator(ev.viewport)
                // anim?
                cameraPos.to.set(
                    pos.x /*+ (ctx.inputMgr.pointerState.primaryPointer.x.toFloat() - it.viewport.me.az.view.getWidth/2) / 50f*/,
                    pos.y /*+ (ctx.inputMgr.pointerState.primaryPointer.y.toFloat() - it.viewport.me.az.view.getHeight/2) / 50f*/,
                    10f
                )
                cameraPos.from.set(cameraPos.value)
                cameraAnimator.speed = 1f
                cameraAnimator.progress = 0f
            }
        }
    }


}