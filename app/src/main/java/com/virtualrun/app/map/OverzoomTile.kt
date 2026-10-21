package com.virtualrun.app.map

import org.osmdroid.util.MapTileIndex

/** A display tile above the raster endpoint's native zoom, expressed in parent pixels. */
data class OverzoomTile(val parentIndex: Long, val left: Int, val top: Int, val size: Int) {
    companion object {
        fun from(index: Long): OverzoomTile {
            val zoom = MapTileIndex.getZoom(index)
            require(zoom in CHINA_MAP_MIN_ZOOM_LEVEL..CHINA_MAP_MAX_ZOOM_LEVEL)
            val parentZoom = minOf(zoom, CHINA_MAP_NATIVE_MAX_ZOOM_LEVEL)
            val shift = zoom - parentZoom
            val factor = 1 shl shift
            val size = 256 / factor
            val x = MapTileIndex.getX(index)
            val y = MapTileIndex.getY(index)
            return OverzoomTile(
                MapTileIndex.getTileIndex(parentZoom, x shr shift, y shr shift),
                (x % factor) * size, (y % factor) * size, size
            )
        }
    }
}
