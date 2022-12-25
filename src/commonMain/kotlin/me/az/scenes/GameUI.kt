package me.az.scenes

import AppContext
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.scene.Group
import me.az.ilode.Game
import me.az.view.SpriteSystem
import me.az.view.TextView

class GameUI(
    private val spriteSystem: SpriteSystem,
    val game: Game,
    private val gameSettings: AppContext
    ) : Group()
{
    private val fontAtlasId = spriteSystem.cfg.atlasIdByName["text"]!!
    private val fontAtlas = spriteSystem.cfg.atlases[fontAtlasId]
    private var dirty = true

    private val formatAsLevel = { lid: Int ->
        val level = ((lid + 1).toString().padStart(3, '0'))
        "level$level"
    }

    private val formatAsScore = { v: Int ->
        val scores = v.toString().padStart(7, '0')
        "score$scores"
    }

    private val formatAsLives = { v: Int ->
        val lives = v.toString().padStart(3, '0')
        "men$lives"
    }

    private val scoreText = mutableStateOf(formatAsScore( gameSettings.score.value )).also { t ->
        gameSettings.score.onChange {
            t.set( formatAsScore( it ) )
        }
    }

    private val livesText = mutableStateOf(formatAsLives(gameSettings.runnerLifes.value)).also { t->
        gameSettings.runnerLifes.onChange {
            t.set(formatAsLives(it))
        }
    }

    private val levelText = mutableStateOf(formatAsLevel(game.level?.levelId ?: -1)).also { t->
        gameSettings.currentLevel.onChange {
            t.set(formatAsLevel(it))
        }
    }

    private fun refresh() {
        levelText.set( formatAsLevel( game.level?.levelId ?: -1) )
        scoreText.set( formatAsScore( gameSettings.score.value ) )
        livesText.set( formatAsLives( gameSettings.runnerLifes.value ) )
    }

    private val livesTextView by lazy {
        TextView(spriteSystem, livesText, fontAtlas) {
            translate(-28f / 2f + 13f, 0f, 0f)
            scale(1f, -1f, 1f)
        }
    }
    private val scoreTextView by lazy {
        TextView(spriteSystem, scoreText, fontAtlas) {
            translate(-28f / 2f, 0f, 0f)
            scale(1f, -1f, 1f)
        }
    }
    private val levelTextView by lazy {
        TextView(spriteSystem, levelText, fontAtlas) {
            translate(-28f / 2f + 20f, 0f, 0f)
            scale(1f, -1f, 1f)
        }
    }

    init {
        game.onStateChanged += { dirty = true }
        gameSettings.score.onChange {
            dirty = true
            scoreTextView.setDirty()
        }
        gameSettings.currentLevel.onChange {
            dirty = true
        }
        gameSettings.runnerLifes.onChange {
            dirty = true
        }

        +scoreTextView
        +livesTextView
        +levelTextView

        onUpdate += {
            if ( dirty ) {
                refresh()
                spriteSystem.dirty = true
                dirty = false
            }
        }

    }
}
