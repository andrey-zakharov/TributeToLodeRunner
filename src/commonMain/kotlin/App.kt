import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.*

val simpleTextureProps = TextureProps(TexFormat.RGBA,
    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
    FilterMethod.NEAREST, FilterMethod.NEAREST, mipMapping = false, maxAnisotropy = 1
)

class App(ctx: KoolContext) {
    init {
        ctx.assetMgr.assetsBaseDir = "." // = resources
        ctx.scenes += GameLevelScene(ctx.assetMgr, "level")
        ctx.run()
    }
}

class GameLevelScene(val assets: AssetManager, name: String?) : Scene(name) {
    var levelState = State.NEW
    var tileSet: TileSet = TileSet.SPRITES_APPLE2

    private var tilesAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "tiles"))
    private var runnerAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "runner"))
    private var guardAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "guard"))
    private var holeAtlas: ImageAtlas = ImageAtlas(ImageAtlasSpec(tileSet, "hole"))

    private val levels = GameLevels(assets, tilesAtlas)
    val gameWidth = 560f
    val gameHeight = 384f

    init {

        camera = OrthographicCamera("plain").apply {
            projCorrectionMode = Camera.ProjCorrectionMode.ONSCREEN
            isClipToViewport = true
            isKeepAspectRatio = true
            setCentered(gameHeight, 0.1f, 10f)
        }

        onRenderScene += {
            checkState(it)
        }
    }

    fun checkState(ctx: KoolContext) {
        if (levelState == State.NEW) {
            // load resources (async from AssetManager CoroutineScope)
            levelState = State.LOADING
            ctx.assetMgr.launch {
                loadResources(ctx)
                levelState = State.SETUP
            }
        }

        if (levelState == State.SETUP) {
            setupMainScene(ctx)
            levelState = State.RUNNING
        }
    }

    suspend fun AssetManager.loadResources(ctx: KoolContext) {
        tilesAtlas.load(this)
        runnerAtlas.load(this)
        guardAtlas.load(this)
        holeAtlas.load(this)
        levels.load(LevelSet.CLASSIC)

    }

    fun dispose() {

    }

    lateinit var tileMapShader: TileMapShader
    fun setupMainScene(ctx: KoolContext) {

        tileMapShader = TileMapShader(TileMapShaderConf(tilesAtlas.tileCoords.size))
        +mesh(listOf(Attribute.POSITIONS, Attribute.TEXTURE_COORDS)) {
            generateFullscreenQuad()

            shader = tileMapShader.apply {

                tileSize = Vec2i(tilesAtlas.spec.tileWidth, tilesAtlas.spec.tileHeight)
                this.tiles = tilesAtlas.tex
                tilesAtlas.tileCoords.forEachIndexed { index, vec2i ->
                    this.tileFrames[index] = MutableVec2f(vec2i.x.toFloat(), vec2i.y.toFloat())
                }

                println( "frames: " + tileFrames.joinToString { it.toString() })

                field = Texture2d(TextureProps(
                    format = TexFormat.R,
                    AddressMode.CLAMP_TO_EDGE,
                    AddressMode.CLAMP_TO_EDGE,
                    AddressMode.CLAMP_TO_EDGE,
                    FilterMethod.NEAREST,
                    FilterMethod.NEAREST,
                    mipMapping = false,
                    maxAnisotropy = 1
                ), levels.levels.first().createTileMap())

            }

            onUpdate += {
                tileMapShader.time =  it.time.toFloat()

                with(tileMapShader.fitMatrix) {
                    setIdentity()
                    scale(gameWidth / gameHeight / it.viewport.aspectRatio, -1f, 1f)
                }
            }
        }
    }

    enum class State {
        NEW,
        LOADING,
        SETUP,
        RUNNING
    }
}

