package com.virtualrun.app.model

import kotlin.math.abs
import kotlin.math.floor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineGaitPlanTest {
    @Test
    fun startAndFinishAreSlowerThanTheStableMiddle() {
        val plan = OfflineGaitPlanner.generate(seed = 12L)
        val start = plan.samples.first()
        val middle = plan.atElapsed(plan.durationSeconds * 0.5)
        val finish = plan.samples.last()
        assertEquals(OfflineGaitPhase.START, plan.phaseAt(start.distanceMeters))
        assertEquals(OfflineGaitPhase.STEADY, plan.phaseAt(middle.distanceMeters))
        assertEquals(OfflineGaitPhase.FINISH, plan.phaseAt(finish.distanceMeters))
        for (edge in listOf(start, finish)) {
            assertTrue(edge.cadenceStepsPerMinute < middle.cadenceStepsPerMinute)
            assertTrue(edge.strideCm < middle.strideCm)
            assertTrue(edge.speedMetersPerSecond < middle.speedMetersPerSecond)
        }
        assertTrue(plan.samples[30].cadenceStepsPerMinute > start.cadenceStepsPerMinute)
        assertTrue(plan.samples[plan.samples.lastIndex - 40].strideCm > finish.strideCm)
    }

    @Test
    fun stableSectionHasSmallBoundedContinuousVariationAcrossSeeds() {
        for (seed in 0L..10L) {
            val plan = OfflineGaitPlanner.generate(seed = seed)
            val steady = plan.samples.filter { plan.phaseAt(it.distanceMeters) == OfflineGaitPhase.STEADY }
            assertTrue(steady.all { it.cadenceStepsPerMinute in 180.0..190.0 && it.strideCm in 70.0..76.0 })
            assertTrue(steady.maxOf { it.cadenceStepsPerMinute } - steady.minOf { it.cadenceStepsPerMinute } > 1.0)
            assertTrue(steady.maxOf { it.strideCm } - steady.minOf { it.strideCm } > 0.5)
            for ((a, b) in plan.samples.zipWithNext()) {
                assertTrue(abs(a.cadenceStepsPerMinute - b.cadenceStepsPerMinute) < 3.0)
                assertTrue(abs(a.strideCm - b.strideCm) < 1.0)
            }
        }
    }

    @Test
    fun stepsTimeDistanceAndSpeedAreConsistent() {
        val plan = OfflineGaitPlanner.generate(seed = 8L)
        assertEquals(2_000.0, plan.targetDistanceMeters, 0.0)
        assertEquals(0.0, plan.samples.first().elapsedSeconds, 0.0)
        assertEquals(0.0, plan.samples.first().stepEquivalent, 0.0)
        for ((a, b) in plan.samples.zipWithNext()) {
            assertTrue(b.elapsedSeconds > a.elapsedSeconds && b.stepEquivalent > a.stepEquivalent)
            val distance = b.distanceMeters - a.distanceMeters
            val time = b.elapsedSeconds - a.elapsedSeconds
            assertEquals(distance / time, 2.0 / (1.0 / a.speedMetersPerSecond + 1.0 / b.speedMetersPerSecond), 1e-9)
            assertEquals(1_000.0, b.speedMetersPerSecond * b.paceSecondsPerKm, 1e-9)
        }
        assertEquals(floor(plan.samples.last().stepEquivalent).toLong(), plan.estimatedSteps)
        assertEquals(plan.targetDistanceMeters / plan.durationSeconds,
            plan.averageCadence * plan.averageStrideCm / 6_000.0, 1e-10)
        assertEquals(plan.durationSeconds * 1_000.0 / plan.targetDistanceMeters, plan.averagePaceSecondsPerKm, 1e-10)
    }

    @Test
    fun scrubbingInterpolatesAndClampsOnlyBeyondTheEnd() {
        val plan = OfflineGaitPlanner.generate(seed = 8L)
        val a = plan.samples[15]
        val b = plan.samples[16]
        val mid = plan.atElapsed((a.elapsedSeconds + b.elapsedSeconds) / 2.0)
        assertEquals((a.distanceMeters + b.distanceMeters) / 2.0, mid.distanceMeters, 1e-9)
        assertEquals((a.cadenceStepsPerMinute + b.cadenceStepsPerMinute) / 2.0, mid.cadenceStepsPerMinute, 1e-9)
        assertEquals(plan.samples.first(), plan.atElapsed(0.0))
        assertEquals(plan.samples.last(), plan.atElapsed(plan.durationSeconds + 10.0))
    }

    @Test
    fun seedReproducesTheProcessAndOtherSeedsChangeTheMiddle() {
        val a = OfflineGaitPlanner.generate(seed = 8L)
        assertEquals(a, OfflineGaitPlanner.generate(seed = 8L))
        val b = OfflineGaitPlanner.generate(seed = 9L)
        assertTrue(a.samples.zip(b.samples).any { (p, q) -> abs(p.cadenceStepsPerMinute - q.cadenceStepsPerMinute) > 0.1 })
        assertEquals(a.averageCadence, b.averageCadence, 1e-9)
        assertEquals(a.averageStrideCm, b.averageStrideCm, 1e-9)
    }

    @Test
    fun requestedFullPlanAveragesArePreservedAcrossSeedsAndDistances() {
        for (target in listOf(1.25, 850.25, 2_000.0, 10_000.0)) {
            for (seed in 0L..10L) {
                val plan = OfflineGaitPlanner.generate(target, seed)
                assertEquals(185.0, plan.averageCadence, 1e-9)
                assertEquals(73.5, plan.averageStrideCm, 1e-9)
                assertEquals(target * 6_000.0 / (185.0 * 73.5), plan.durationSeconds, 1e-8)
            }
        }
        val otherTarget = OfflineGaitPlanner.generate(seed = 8L, averageCadenceTarget = 180.0, averageStrideTargetCm = 72.0)
        assertEquals(180.0, otherTarget.averageCadence, 1e-9)
        assertEquals(72.0, otherTarget.averageStrideCm, 1e-9)
    }

    @Test
    fun cumulativeAveragesFollowTheCursorAndConvergeToTheFullPlan() {
        val plan = OfflineGaitPlanner.generate(seed = 8L)
        assertEquals(null, plan.samples.first().averageCadenceSoFar)
        assertEquals(null, plan.samples.first().averageStrideCmSoFar)
        val early = plan.atElapsed(plan.durationSeconds * 0.01)
        val middle = plan.atElapsed(plan.durationSeconds * 0.5)
        assertTrue(early.averageCadenceSoFar!! < middle.averageCadenceSoFar!!)
        assertTrue(early.averageStrideCmSoFar!! < middle.averageStrideCmSoFar!!)
        assertEquals(plan.averageCadence, plan.samples.last().averageCadenceSoFar!!, 1e-10)
        assertEquals(plan.averageStrideCm, plan.samples.last().averageStrideCmSoFar!!, 1e-10)
    }

    @Test
    fun shortFractionalAndLongPlansRemainFiniteWithBoundedSamples() {
        for (target in listOf(1.0, 1.25, 850.25, 10_000.0)) {
            val plan = OfflineGaitPlanner.generate(target, seed = 2L)
            assertEquals(target, plan.targetDistanceMeters, 1e-9)
            assertTrue(plan.samples.size <= 10_002)
            assertTrue(plan.samples.all { it.speedMetersPerSecond.isFinite() && it.speedMetersPerSecond > 0.0 })
            assertTrue(plan.averageCadence.isFinite() && plan.averageStrideCm.isFinite())
            assertTrue(plan.startDistanceMeters + plan.finishDistanceMeters < target)
        }
    }

    @Test
    fun rejectsInvalidDistancesAndScrubTimes() {
        for (target in listOf(0.0, -1.0, 10_000.01, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { OfflineGaitPlanner.generate(target) }
        }
        val plan = OfflineGaitPlanner.generate(seed = 2L)
        for (time in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { plan.atElapsed(time) }
        }
        for (cadence in listOf(99.0, 221.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { OfflineGaitPlanner.generate(averageCadenceTarget = cadence) }
        }
        for (stride in listOf(29.0, 151.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { OfflineGaitPlanner.generate(averageStrideTargetCm = stride) }
        }
    }
}
