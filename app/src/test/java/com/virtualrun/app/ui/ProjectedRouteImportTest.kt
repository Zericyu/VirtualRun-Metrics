package com.virtualrun.app.ui

import com.virtualrun.app.model.MapDisplayPoint
import com.virtualrun.app.model.OfflineLoopPlanner
import com.virtualrun.app.model.OfflineMapProjector
import org.junit.Assert.*
import org.junit.Test

class ProjectedRouteImportTest {
    private fun projection(clockwise: Boolean = false) = OfflineMapProjector.project(
        OfflineLoopPlanner.generate(seed = 18L, clockwise = clockwise),
        listOf(MapDisplayPoint(30.0, 120.0), MapDisplayPoint(30.0008, 120.0),
            MapDisplayPoint(30.0, 120.0004))
    )

    @Test fun denseOutlineBecomesACompleteClosedRouteWithoutPrematureAutoClosing() {
        val overlay = projection()
        val model = MainViewModel()
        assertFalse(model.hasGeneratedRoute.value)
        assertTrue(model.useProjectedRoute(overlay))
        assertTrue(model.hasGeneratedRoute.value)
        val points = model.routePoints.value
        assertTrue(points.size > 1000)
        assertEquals(overlay.outline.size, points.size)
        overlay.outline.zip(points).forEach { (source, route) ->
            assertEquals(source.latitude, route.latitude, 1e-12)
            assertEquals(source.longitude, route.longitude, 1e-12)
        }
        assertEquals(points.first(), points.last())
        assertTrue(model.isLoopMode.value)
        assertFalse(model.isRunning.value)
    }

    @Test fun replacementPreservesTheSelectedDirectionAndDoesNotAppendOldPoints() {
        val model = MainViewModel()
        val forward = projection()
        assertTrue(model.useProjectedRoute(forward))
        val reverse = projection(clockwise = true)
        assertTrue(model.useProjectedRoute(reverse))
        assertEquals(reverse.outline.size, model.routePoints.value.size)
        assertEquals(forward.outline[1].latitude, model.routePoints.value[model.routePoints.value.lastIndex - 1].latitude, 1e-12)
        assertEquals(reverse.outline[1].longitude, model.routePoints.value[1].longitude, 1e-12)
    }

    @Test fun invalidImportLeavesTheExistingRouteIntact() {
        val model = MainViewModel()
        val valid = projection()
        assertTrue(model.useProjectedRoute(valid))
        val before = model.routePoints.value
        for (invalid in listOf(valid.copy(outline = emptyList()),
            valid.copy(outline = valid.outline.dropLast(1)),
            valid.copy(outline = List(4) { valid.outline.first() }),
            valid.copy(singleLapMeters = Double.NaN))) {
            assertFalse(model.useProjectedRoute(invalid))
            assertEquals(before, model.routePoints.value)
            assertTrue(model.isLoopMode.value)
            assertTrue(model.hasGeneratedRoute.value)
        }
        model.reverseRouteDirection()
        assertTrue(model.hasGeneratedRoute.value)
        model.clearRoute()
        assertFalse(model.hasGeneratedRoute.value)
        assertTrue(model.routePoints.value.isEmpty())
    }
}
