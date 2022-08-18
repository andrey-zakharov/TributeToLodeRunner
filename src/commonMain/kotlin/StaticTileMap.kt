import de.fabmax.kool.scene.Group

data class StaticTileMapSpec(
    val width: Int = 28, // in tiles
    val height: Int = 16

)

class StaticTileMap(spec: StaticTileMapSpec, name: String?): Group(name) {

}