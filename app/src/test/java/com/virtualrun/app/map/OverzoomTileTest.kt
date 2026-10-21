package com.virtualrun.app.map

import org.junit.Assert.*
import org.junit.Test
import org.osmdroid.util.MapTileIndex

class OverzoomTileTest {
    @Test fun allHighZoomLevelsRequestTheCorrectNativeParentInsteadOfBlankEndpointTiles() {
        val parent = MapTileIndex.getTileIndex(18, 215837, 99333)
        for (zoom in 19..22) {
            val factor = 1 shl (zoom - 18)
            val child = MapTileIndex.getTileIndex(zoom, 215837 * factor + factor - 1, 99333 * factor + 1)
            val crop = OverzoomTile.from(child)
            assertEquals(parent, crop.parentIndex)
            assertEquals(256 / factor, crop.size)
            assertEquals(256 - crop.size, crop.left)
            assertEquals(crop.size, crop.top)
            for (mode in MapMode.entries) {
                val url = ChinaMapTileSource(mode).getTileURLString(child)
                assertTrue(url.endsWith("&x=215837&y=99333&z=18"))
                assertFalse(url.contains("z=$zoom"))
            }
        }
    }

    @Test fun everyChildCoversItsOwnAreaWithoutGapsOrOverlap() {
        for (zoom in 19..22) {
            val factor = 1 shl (zoom - 18)
            val pixels = IntArray(256 * 256)
            for (x in 0 until factor) for (y in 0 until factor) {
                val crop = OverzoomTile.from(MapTileIndex.getTileIndex(zoom, 215837 * factor + x, 99333 * factor + y))
                for (row in crop.top until crop.top + crop.size) for (col in crop.left until crop.left + crop.size) {
                    pixels[row * 256 + col]++
                }
            }
            assertTrue("parent coverage at z$zoom", pixels.all { it == 1 })
        }
    }

    @Test fun nativeTilesAreUnchangedAndUnsupportedZoomIsRejected() {
        for (zoom in 0..18) {
            val native = MapTileIndex.getTileIndex(zoom, 0, 0)
            assertEquals(OverzoomTile(native, 0, 0, 256), OverzoomTile.from(native))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OverzoomTile.from(MapTileIndex.getTileIndex(23, 0, 0))
        }
    }
}
