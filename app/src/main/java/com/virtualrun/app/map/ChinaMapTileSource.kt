package com.virtualrun.app.map

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

const val CHINA_MAP_MIN_ZOOM_LEVEL = 0

// AutoNavi returns a tiny blank placeholder tile from z19 onward for this raster endpoint.
// Keep network requests capped at z18, then let osmdroid crop and enlarge cached parent tiles
// for a few extra display levels instead of requesting those blank placeholders.
const val CHINA_MAP_NATIVE_MAX_ZOOM_LEVEL = 18
const val CHINA_MAP_MAX_ZOOM_LEVEL = 22

/**
 * 高德地图 (AMap/AutoNavi) 瓦片源
 * 国内直连非常快，无需 VPN
 */
enum class MapMode {
    ROAD, SATELLITE;

    fun next() = if (this == ROAD) SATELLITE else ROAD

    companion object {
        fun fromPreference(value: String?) = entries.firstOrNull { it.name == value } ?: ROAD
    }
}

class ChinaMapTileSource(val mode: MapMode = MapMode.ROAD) : OnlineTileSourceBase(
    // Earlier releases could save an in-flight ROAD download under SATELLITE after
    // setTileSource. A new namespace keeps those persistent mixed tiles out of use.
    if (mode == MapMode.ROAD) "AMapRoadV2" else "AMapSatelliteV2",
    CHINA_MAP_MIN_ZOOM_LEVEL, CHINA_MAP_NATIVE_MAX_ZOOM_LEVEL, 256, ".png",
    if (mode == MapMode.SATELLITE) Array(4) { index ->
        "https://webst0${index + 1}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=6"
    } else arrayOf(
        "https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8",
        "https://webrd02.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8",
        "https://webrd03.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8",
        "https://webrd04.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8"
    )
) {
    override fun getTileURLString(pTileIndex: Long): String {
        val nativeIndex = OverzoomTile.from(pTileIndex).parentIndex
        return "${baseUrl}&x=${MapTileIndex.getX(nativeIndex)}&y=${MapTileIndex.getY(nativeIndex)}&z=${MapTileIndex.getZoom(nativeIndex)}"
    }
}
