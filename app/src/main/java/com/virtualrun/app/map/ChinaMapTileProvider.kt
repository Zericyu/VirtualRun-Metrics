package com.virtualrun.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.ReusableBitmapDrawable
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.modules.NetworkAvailabliltyCheck
import org.osmdroid.tileprovider.modules.TileDownloader
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/** Fetch missing z18 parents even when the camera opens directly at z19-z22. */
class ChinaMapTileProvider(context: Context, source: ChinaMapTileSource) : MapTileProviderBasic(context, source) {
    private val network = NetworkAvailabliltyCheck(context.applicationContext)
    init {
        mTileProviderList.add(OverzoomProvider(source, tileWriter) {
            useDataConnection() && network.networkAvailable
        })
    }

    override fun isDowngradedMode(index: Long): Boolean =
        if (MapTileIndex.getZoom(index) > CHINA_MAP_NATIVE_MAX_ZOOM_LEVEL) {
            !useDataConnection() || !network.networkAvailable
        } else super.isDowngradedMode(index)
}

private class OverzoomProvider(
    initialSource: OnlineTileSourceBase,
    private val writer: IFilesystemCache?,
    private val canDownload: () -> Boolean
) : MapTileModuleProviderBase(2, 40) {
    @Volatile private var source = initialSource
    private val downloader = TileDownloader()
    private val parentLock = Any()

    override fun getName() = "ChinaMapOverzoom"
    override fun getThreadGroupName() = "ChinaMapOverzoom"
    // Cached parents remain usable offline. The download itself checks network availability.
    override fun getUsesDataConnection() = false
    override fun getMinimumZoomLevel() = CHINA_MAP_NATIVE_MAX_ZOOM_LEVEL + 1
    override fun getMaximumZoomLevel() = CHINA_MAP_MAX_ZOOM_LEVEL
    override fun setTileSource(tileSource: ITileSource) {
        if (tileSource is OnlineTileSourceBase) source = tileSource
    }

    override fun getTileLoader(): TileLoader = object : TileLoader() {
        override fun loadTile(index: Long): Drawable? = synchronized(parentLock) {
            val activeSource = source
            val crop = OverzoomTile.from(index)
            val cached = runCatching { writer?.loadTile(activeSource, crop.parentIndex) }.getOrNull()
            val parent = cached ?: if (canDownload()) {
                activeSource.acquire()
                try {
                    downloader.downloadTile(crop.parentIndex, writer, activeSource)
                } finally {
                    activeSource.release()
                }
            } else null
            val bitmapParent = parent as? BitmapDrawable ?: return@synchronized null
            val reusable = parent as? ReusableBitmapDrawable
            reusable?.beginUsingDrawable()
            try {
                val original = bitmapParent.bitmap
                if (original.isRecycled) return@synchronized null
                val tile = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
                Canvas(tile).drawBitmap(
                    original,
                    Rect(crop.left, crop.top, crop.left + crop.size, crop.top + crop.size),
                    Rect(0, 0, 256, 256), Paint(Paint.FILTER_BITMAP_FLAG)
                )
                ReusableBitmapDrawable(tile)
            } finally {
                reusable?.finishUsingDrawable()
            }
        }
    }
}
