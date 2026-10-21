package com.virtualrun.app.model

import kotlin.math.cos
import org.junit.Assert.*
import org.junit.Test

class OfflineMapProjectionTest {
    private val plan = OfflineLoopPlanner.generate(seed = 18L)
    private val center = MapDisplayPoint(30.0, 120.0)

    private fun offset(east: Double, north: Double, origin: MapDisplayPoint = center): MapDisplayPoint =
        MapDisplayPoint(origin.latitude + Math.toDegrees(north / 6_371_000.0),
            origin.longitude + Math.toDegrees(east / (6_371_000.0 * cos(Math.toRadians(origin.latitude)))))

    private fun area(points: List<MapDisplayPoint>): Double = points.zipWithNext().sumOf { (a, b) ->
        (a.longitude - center.longitude) * (b.latitude - center.latitude) -
            (b.longitude - center.longitude) * (a.latitude - center.latitude)
    }

    @Test fun selectedRightMidpointIsTheSharedStartAndTheOutlineCloses() {
        val side = offset(40.0, 0.0)
        val overlay = OfflineMapProjector.project(plan, listOf(center, offset(0.0, 90.0), side))
        assertEquals(side.latitude, overlay.outline.first().latitude, 1e-9)
        assertEquals(side.longitude, overlay.outline.first().longitude, 1e-9)
        assertEquals(overlay.outline.first(), overlay.outline.last())
        assertEquals(overlay.outline.first(), overlay.baseline.first())
        assertTrue(overlay.singleLapMeters > 300.0 && overlay.singleLapMeters < 550.0)
        assertEquals(2_000.0 / overlay.singleLapMeters, overlay.lapsForTarget, 1e-9)
    }

    @Test fun leftOrRightReferenceDoesNotMirrorTheChosenDirection() {
        val right = listOf(center, offset(0.0, 90.0), offset(40.0, 0.0))
        val left = listOf(center, offset(0.0, 90.0), offset(-40.0, 0.0))
        val a = OfflineMapProjector.project(plan, right)
        val b = OfflineMapProjector.project(plan, left)
        assertTrue(area(a.outline) > 0.0)
        assertEquals(a, b)
        val clockwisePlan = OfflineLoopPlanner.generate(seed = 18L, clockwise = true)
        val reversed = OfflineMapProjector.project(clockwisePlan, left)
        assertTrue(reversed.clockwise)
        assertTrue(area(reversed.outline) < 0.0)
        assertEquals(a.outline.first(), reversed.outline.first())
    }

    @Test fun rotatedAndSkewedReferencesRemainFiniteAndPreserveDirection() {
        val overlay = OfflineMapProjector.project(plan,
            listOf(center, offset(90.0, 0.0), offset(10.0, -40.0)))
        assertTrue(area(overlay.outline) > 0.0)
        assertTrue(overlay.outline.all { it.latitude.isFinite() && it.longitude.isFinite() })
        assertEquals(offset(10.0, -40.0).latitude, overlay.outline.first().latitude, 1e-9)
        assertEquals(offset(10.0, -40.0).longitude, overlay.outline.first().longitude, 1e-9)
    }

    @Test fun doublingBothMapSpansDoublesDistanceInsteadOfClaimingTheOldLength() {
        val a = OfflineMapProjector.project(plan, listOf(center, offset(0.0, 90.0), offset(40.0, 0.0)))
        val b = OfflineMapProjector.project(plan, listOf(center, offset(0.0, 180.0), offset(80.0, 0.0)))
        assertEquals(a.singleLapMeters * 2, b.singleLapMeters, 1e-7)
        assertEquals(a.lapsForTarget / 2, b.lapsForTarget, 1e-7)
    }

    @Test fun missingDuplicateCollinearAndDistantReferencesAreRejected() {
        val invalid = listOf(
            emptyList(), listOf(center), listOf(center, center, offset(40.0, 0.0)),
            listOf(center, offset(0.0, 90.0), offset(0.0, 40.0)),
            listOf(center, offset(0.0, 3_000.0), offset(40.0, 0.0)),
            listOf(center, offset(0.0, 90.0), offset(1.0, 30.0))
        )
        invalid.forEach { references ->
            assertThrows(IllegalArgumentException::class.java) { OfflineMapProjector.project(plan, references) }
        }
        assertThrows(IllegalArgumentException::class.java) { MapDisplayPoint(Double.NaN, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { MapDisplayPoint(90.0, 0.0) }
    }
}
