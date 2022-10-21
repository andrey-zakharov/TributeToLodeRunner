package me.az.view

import AnimationFrames
import ImageAtlas
import Sprite
import de.fabmax.kool.math.Vec2i
import me.az.ilode.Actor
import me.az.utils.addDebugAxis

class ActorView(val actor: Actor,
                val atlas: ImageAtlas,
                val animations: AnimationFrames,
                val tileSize: Vec2i
) : Sprite(tileSize, atlas.tex, tileSize) {


    init {
//        addDebugAxis()
        onUpdate += {
            actor.level?.run {

                val sequence = animations.sequence[actor.action.id]?: throw NullPointerException(actor.action.id)
                if ( actor.sequenceSize == 0 ) actor.sequenceSize = sequence.size
                textureOffset.set(atlas.getTexOffset(sequence[actor.frameIndex % sequence.size]))

                setIdentity()
                translate(
                    actor.x - width / 2.0 + 0.5, height - actor.y - 0.5,
                    0.0
                )
//            scale(1f, 1f, 1f )
                translate(actor.ox.toDouble() / tileSize.x, -actor.oy.toDouble() / tileSize.y, 0.0)
            }
        }
    }
}