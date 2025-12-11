package me.az

import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.TextureProps
import de.fabmax.kool.util.Uint8Buffer

typealias AnimSpec = Map<String, List<Int>>
//ui
//typealias FormSpec = List<FieldSpec>
//typealias FieldSpec =
interface AssetManager {
//    fun loadAndPrepareAnims(filaneme: String): AnimSpec
    suspend fun loadAsset(assetPath: String): ByteArray
    suspend fun loadBlob(assetPath: String): Uint8Buffer
    suspend fun loadAndPrepareTexture(s: String, simpleTextureProps: TextureProps): Texture2d
    suspend fun loadAudioClip(assetPath: String): de.fabmax.kool.modules.audio.AudioClip
}

// adapter
object DefaultAssetManager : AssetManager {

    override suspend fun loadAsset(assetPath: String) = loadBlob(assetPath).toArray()
    override suspend fun loadBlob(assetPath: String) = de.fabmax.kool.Assets.loadBlobAsset(assetPath)
    override suspend fun loadAndPrepareTexture(s: String, simpleTextureProps: TextureProps) =
        de.fabmax.kool.Assets.loadTexture2d(s, simpleTextureProps)

    override suspend fun loadAudioClip(assetPath: String) =
        de.fabmax.kool.Assets.loadAudioClip(assetPath = assetPath)
}
