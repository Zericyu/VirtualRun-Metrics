package com.virtualrun.app.model

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineLoopPlanTest {
    @Test
    fun defaultPlanCoversFiveCounterclockwiseLapsAndReturnsToStart() {
        val plan = OfflineLoopPlanner.generate(seed = 20L)

        assertEquals(400.0, plan.lapLengthMeters, 0.0)
        assertEquals(2_000.0, plan.targetDistanceMeters, 0.0)
        assertEquals(5, plan.completedLaps)
        assertEquals(0.0, plan.remainingDistanceMeters, 0.0)
        assertEquals(0.0, plan.points.first().distanceMeters, 0.0)
        assertEquals(2_000.0, plan.points.last().distanceMeters, 0.0)
        assertEquals(plan.points.first().xMeters, plan.points.last().xMeters, 0.0)
        assertEquals(plan.points.first().yMeters, plan.points.last().yMeters, 0.0)

        // In an east/north coordinate system, positive signed area is counterclockwise.
        assertTrue(signedDoubleArea(plan.lapOutline) > 0.0)
        assertTrue(plan.points[1].yMeters > plan.points.first().yMeters)
    }

    @Test
    fun cumulativeDistancesAndSampledGeometryStayContinuousAcrossLapBoundaries() {
        for (lapLength in listOf(100.0, 333.3, 400.0, 1_000.0)) {
            val plan = OfflineLoopPlanner.generate(lapLength, 2_000.25, seed = 16L)
            for ((a, b) in plan.points.zipWithNext()) {
                val distanceIncrement = b.distanceMeters - a.distanceMeters
                val chordLength = hypot(b.xMeters - a.xMeters, b.yMeters - a.yMeters)
                assertTrue(distanceIncrement > 0.0)
                assertTrue(distanceIncrement <= OfflineLoopPlanner.MAX_SAMPLE_SPACING_METERS + 1e-9)
                assertTrue(chordLength > 0.0)
                assertTrue(chordLength <= distanceIncrement + 1e-9)
                assertTrue(abs(distanceIncrement - chordLength) < 0.003)
                assertTrue(a.xMeters.isFinite() && a.yMeters.isFinite())
            }
            assertTrue(plan.polylineDistanceMeters <= plan.targetDistanceMeters + 1e-8)
            assertTrue(plan.targetDistanceMeters - plan.polylineDistanceMeters < plan.targetDistanceMeters * 0.0006)
        }
    }

    @Test
    fun partialFinalLapKeepsTheRequestedEndpointAndDoesNotArtificiallyClose() {
        val plan = OfflineLoopPlanner.generate(400.0, 850.25, seed = 22L)

        assertEquals(2, plan.completedLaps)
        assertEquals(50.25, plan.remainingDistanceMeters, 0.0)
        assertEquals(850.25, plan.points.last().distanceMeters, 0.0)
        val lastInterval = plan.points.last().distanceMeters - plan.points[plan.points.lastIndex - 1].distanceMeters
        assertTrue(lastInterval > 0.0 && lastInterval <= OfflineLoopPlanner.MAX_SAMPLE_SPACING_METERS + 1e-9)
        val start = plan.points.first()
        val end = plan.points.last()
        assertTrue(hypot(end.xMeters - start.xMeters, end.yMeters - start.yMeters) > 40.0)

        val shortPlan = OfflineLoopPlanner.generate(400.0, 1.25, seed = 22L)
        assertEquals(1.25, shortPlan.points.last().distanceMeters, 0.0)
        assertTrue(shortPlan.points.last().yMeters > 0.0)
        assertTrue(hypot(shortPlan.points.last().xMeters - start.xMeters, shortPlan.points.last().yMeters - start.yMeters) < 1.251)
    }

    @Test
    fun validBoundariesRemainFiniteAndSamplingIsBounded() {
        for (lapLength in listOf(OfflineLoopPlanner.MIN_LAP_METERS, OfflineLoopPlanner.MAX_LAP_METERS)) {
            for (target in listOf(OfflineLoopPlanner.MIN_TARGET_METERS, OfflineLoopPlanner.MAX_TARGET_METERS)) {
                val plan = OfflineLoopPlanner.generate(lapLength, target, seed = 17L)
                assertEquals(target, plan.points.last().distanceMeters, 0.0)
                assertTrue(plan.points.size <= 80_102)
                assertTrue(plan.points.all { it.xMeters.isFinite() && it.yMeters.isFinite() })
                assertTrue(plan.polylineDistanceMeters.isFinite())
                assertFalse(plan.lapOutline.isEmpty())
            }
        }
    }

    @Test
    fun invalidOrUnboundedInputsAreRejected() {
        for (lapLength in listOf(-1.0, 0.0, 99.99, 1_000.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                OfflineLoopPlanner.generate(lapLengthMeters = lapLength)
            }
        }
        for (target in listOf(-1.0, 0.0, 0.99, 10_000.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                OfflineLoopPlanner.generate(targetDistanceMeters = target)
            }
        }
    }

    @Test
    fun aSeedReproducesTheCurveAndDifferentSeedsChangeTheGeometry() {
        val a = OfflineLoopPlanner.generate(seed = 10L)
        val repeated = OfflineLoopPlanner.generate(seed = 10L)
        val other = OfflineLoopPlanner.generate(seed = 11L)

        assertEquals(a, repeated)
        assertEquals(10L, a.seed)
        assertTrue(a.lapOutline.zip(other.lapOutline).any { (p, q) ->
            hypot(p.xMeters - q.xMeters, p.yMeters - q.yMeters) > 0.1
        })
        assertEquals(a.targetDistanceMeters, other.targetDistanceMeters, 0.0)
        assertTrue(abs(a.polylineDistanceMeters - a.targetDistanceMeters) < 0.1)
        assertTrue(abs(other.polylineDistanceMeters - other.targetDistanceMeters) < 0.1)
    }

    @Test
    fun directionReversesTheSameGeometryWhileKeepingTheSameStart() {
        val counterclockwise = OfflineLoopPlanner.generate(333.3, 333.3, seed = 8L)
        val clockwise = OfflineLoopPlanner.generate(333.3, 333.3, seed = 8L, clockwise = true)

        assertFalse(counterclockwise.clockwise)
        assertTrue(clockwise.clockwise)
        assertTrue(signedDoubleArea(counterclockwise.lapOutline) > 0.0)
        assertTrue(signedDoubleArea(clockwise.lapOutline) < 0.0)
        assertTrue(signedDoubleArea(counterclockwise.baselineOutline) > 0.0)
        assertTrue(signedDoubleArea(clockwise.baselineOutline) < 0.0)
        assertEquals(counterclockwise.points.first(), clockwise.points.first())
        assertEquals(counterclockwise.points.first(), counterclockwise.baselineOutline.first())
        assertEquals(clockwise.points.first(), clockwise.baselineOutline.first())
        for ((a, b) in counterclockwise.lapOutline.zip(clockwise.lapOutline.reversed())) {
            assertEquals(a.xMeters, b.xMeters, 1e-9)
            assertEquals(a.yMeters, b.yMeters, 1e-9)
        }
        for ((a, b) in counterclockwise.baselineOutline.zip(clockwise.baselineOutline.reversed())) {
            assertEquals(a.xMeters, b.xMeters, 1e-9)
            assertEquals(a.yMeters, b.yMeters, 1e-9)
        }

        val partialClockwise = OfflineLoopPlanner.generate(400.0, 50.25, seed = 8L, clockwise = true)
        val sameSeedFullLap = OfflineLoopPlanner.generate(400.0, 400.0, seed = 8L)
        assertEquals(50.25, partialClockwise.points.last().distanceMeters, 0.0)
        assertTrue(partialClockwise.points.last().yMeters < 0.0)
        assertEquals(sameSeedFullLap.points.first(), partialClockwise.points.first())
    }

    @Test
    fun wholeRouteVariationPreservesTheTwoLongStraightCorridors() {
        for (seed in 0L..20L) {
            val plan = OfflineLoopPlanner.generate(seed = seed)
            // Variation must remain within the straight corridors instead of
            // destroying the recognizable track geometry.
            val radius = plan.points.first().xMeters
            val centralSections = plan.lapOutline.filter { abs(it.yMeters) < radius * 0.9 }
            val right = centralSections.filter { it.xMeters > 0.0 }
            val left = centralSections.filter { it.xMeters < 0.0 }
            assertTrue(right.size > 50)
            assertTrue(left.size > 50)

            assertTrue(right.maxOf { abs(it.xMeters - radius) } < radius * 0.1)
            assertTrue(left.maxOf { abs(it.xMeters + radius) } < radius * 0.1)
            assertTrue(right.maxOf { it.yMeters } - right.minOf { it.yMeters } > radius * 1.7)
            assertTrue(left.maxOf { it.yMeters } - left.minOf { it.yMeters } > radius * 1.7)
        }
    }

    @Test
    fun syntheticDifferencesAreDistributedAroundTheWholeLoop() {
        val plan = OfflineLoopPlanner.generate(seed = 10L)
        assertEquals(plan.lapOutline.first(), plan.baselineOutline.first())
        assertEquals(plan.baselineOutline.first().xMeters, plan.baselineOutline.last().xMeters, 0.0)
        assertEquals(plan.baselineOutline.first().yMeters, plan.baselineOutline.last().yMeters, 0.0)

        val comparisonPoints = plan.lapOutline.filterIndexed { index, _ -> index % 32 == 0 }
        val nearestBaselineDistances = comparisonPoints.map { point ->
            plan.baselineOutline.minOf { baseline -> hypot(point.xMeters - baseline.xMeters, point.yMeters - baseline.yMeters) }
        }
        assertTrue("Synthetic differences should be visible at metre scale", nearestBaselineDistances.max() > 1.0)
        assertTrue(nearestBaselineDistances.max() < 3.5)
        for (quarter in 0..3) {
            val distances = comparisonPoints.zip(nearestBaselineDistances).filter { (point, _) ->
                point.distanceMeters >= quarter * 100.0 && point.distanceMeters < (quarter + 1) * 100.0
            }.map { it.second }
            assertTrue("Variation must extend into quarter $quarter", distances.count { it > 0.25 } > distances.size * 0.25)
        }

        val baselineDrawingLength = plan.baselineOutline.zipWithNext().sumOf { (a, b) ->
            hypot(b.xMeters - a.xMeters, b.yMeters - a.yMeters)
        }
        val baselineArcLength = plan.baselineOutline.last().distanceMeters
        assertTrue(baselineArcLength > 360.0 && baselineArcLength < 410.0)
        assertTrue(baselineArcLength >= baselineDrawingLength - 1e-8)
        assertTrue(baselineArcLength - baselineDrawingLength < 0.02)
    }

    @Test
    fun distributedVariationHasNoTangentBreakAtTheLoopSeam() {
        for (seed in listOf(3L, 21L, 80L)) {
            for (clockwise in listOf(false, true)) {
                val points = OfflineLoopPlanner.generate(seed = seed, clockwise = clockwise).lapOutline
                val start = points.first()
                val incoming = points[points.lastIndex - 1]
                val outgoing = points[1]
                val ax = start.xMeters - incoming.xMeters
                val ay = start.yMeters - incoming.yMeters
                val bx = outgoing.xMeters - start.xMeters
                val by = outgoing.yMeters - start.yMeters
                val normalizedDot = (ax * bx + ay * by) / (hypot(ax, ay) * hypot(bx, by))
                assertTrue("Closed curve must have a continuous seam tangent", normalizedDot > 0.995)
            }
        }
    }

    private fun signedDoubleArea(points: List<OfflineLoopPoint>): Double = points.zipWithNext().sumOf { (a, b) ->
        a.xMeters * b.yMeters - b.xMeters * a.yMeters
    }
}
