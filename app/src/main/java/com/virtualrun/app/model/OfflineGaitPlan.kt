package com.virtualrun.app.model

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

enum class OfflineGaitPhase { START, STEADY, FINISH }

/** Synthetic values for a scrubbable offline chart, without locations or sensor events. */
data class OfflineGaitSample internal constructor(
    val distanceMeters: Double,
    val elapsedSeconds: Double,
    val cadenceStepsPerMinute: Double,
    val strideCm: Double,
    val stepEquivalent: Double
) {
    val speedMetersPerSecond: Double get() = cadenceStepsPerMinute * strideCm / 6_000.0
    val paceSecondsPerKm: Double get() = 1_000.0 / speedMetersPerSecond
    val averageCadenceSoFar: Double? get() = if (elapsedSeconds > 0.0) stepEquivalent * 60.0 / elapsedSeconds else null
    val averageStrideCmSoFar: Double? get() = if (stepEquivalent > 0.0) distanceMeters * 100.0 / stepEquivalent else null
}

data class OfflineGaitPlan internal constructor(
    val samples: List<OfflineGaitSample>,
    val startDistanceMeters: Double,
    val finishDistanceMeters: Double,
    val seed: Long
) {
    val targetDistanceMeters: Double get() = samples.last().distanceMeters
    val durationSeconds: Double get() = samples.last().elapsedSeconds
    val estimatedSteps: Long get() = floor(samples.last().stepEquivalent).toLong()
    val averageCadence: Double get() = samples.last().stepEquivalent * 60.0 / durationSeconds
    val averageStrideCm: Double get() = targetDistanceMeters * 100.0 / samples.last().stepEquivalent
    val averagePaceSecondsPerKm: Double get() = durationSeconds * 1_000.0 / targetDistanceMeters

    fun phaseAt(distanceMeters: Double): OfflineGaitPhase {
        require(distanceMeters.isFinite())
        return when {
            distanceMeters < startDistanceMeters -> OfflineGaitPhase.START
            distanceMeters >= targetDistanceMeters - finishDistanceMeters -> OfflineGaitPhase.FINISH
            else -> OfflineGaitPhase.STEADY
        }
    }

    /** Interpolates only chart data; a time beyond the plan returns its final sample. */
    fun atElapsed(seconds: Double): OfflineGaitSample {
        require(seconds.isFinite() && seconds >= 0.0)
        if (seconds == 0.0) return samples.first()
        if (seconds >= durationSeconds) return samples.last()
        var low = 0
        var high = samples.lastIndex
        while (low + 1 < high) {
            val mid = (low + high) ushr 1
            if (samples[mid].elapsedSeconds <= seconds) low = mid else high = mid
        }
        val a = samples[low]
        val b = samples[high]
        val fraction = (seconds - a.elapsedSeconds) / (b.elapsedSeconds - a.elapsedSeconds)
        fun between(x: Double, y: Double) = x + (y - x) * fraction
        return OfflineGaitSample(
            between(a.distanceMeters, b.distanceMeters), seconds,
            between(a.cadenceStepsPerMinute, b.cadenceStepsPerMinute),
            between(a.strideCm, b.strideCm), between(a.stepEquivalent, b.stepEquivalent)
        )
    }
}

/** Illustrative three-phase gait, not fitted to real runners or used for location playback. */
object OfflineGaitPlanner {
    const val DEFAULT_AVERAGE_CADENCE = 185.0
    const val DEFAULT_AVERAGE_STRIDE_CM = 73.5

    fun generate(
        targetDistanceMeters: Double = 2_000.0,
        seed: Long = Random.nextLong(),
        averageCadenceTarget: Double = DEFAULT_AVERAGE_CADENCE,
        averageStrideTargetCm: Double = DEFAULT_AVERAGE_STRIDE_CM
    ): OfflineGaitPlan {
        require(targetDistanceMeters.isFinite() && targetDistanceMeters in 1.0..10_000.0)
        require(averageCadenceTarget.isFinite() && averageCadenceTarget in 100.0..220.0)
        require(averageStrideTargetCm.isFinite() && averageStrideTargetCm in 30.0..150.0)
        val random = Random(seed)
        val cadenceNoise = SmoothNoise(targetDistanceMeters, random)
        val strideNoise = SmoothNoise(targetDistanceMeters, random)
        val startDistance = minOf(60.0, targetDistanceMeters * 0.12)
        val finishDistance = minOf(80.0, targetDistanceMeters * 0.15)
        val intervals = ceil(targetDistanceMeters / minOf(1.0, targetDistanceMeters / 500.0)).toInt()
        val samples = ArrayList<OfflineGaitSample>(intervals + 1)
        for (index in 0..intervals) {
            val distance = targetDistanceMeters * index / intervals
            val steadyCadence = 185.0 + 1.5 * cadenceNoise.at(distance)
            val steadyStride = 73.5 + 1.0 * strideNoise.at(distance)
            val startBlend = smoothStep((distance / startDistance).coerceIn(0.0, 1.0))
            val finishBlend = smoothStep(((distance - (targetDistanceMeters - finishDistance)) /
                finishDistance).coerceIn(0.0, 1.0))
            val cadence = mix(mix(170.0, steadyCadence, startBlend), 165.0, finishBlend)
            val stride = mix(mix(62.0, steadyStride, startBlend), 60.0, finishBlend)
            val previous = samples.lastOrNull()
            val distanceIncrement = distance - (previous?.distanceMeters ?: 0.0)
            // Integrate reciprocal speed and reciprocal stride over distance.
            // This keeps steps, elapsed time and average metrics consistent.
            val elapsed = if (previous == null) 0.0 else previous.elapsedSeconds +
                distanceIncrement * 3_000.0 * (1.0 / (previous.cadenceStepsPerMinute * previous.strideCm) +
                    1.0 / (cadence * stride))
            val steps = if (previous == null) 0.0 else previous.stepEquivalent +
                distanceIncrement * 50.0 * (1.0 / previous.strideCm + 1.0 / stride)
            samples.add(OfflineGaitSample(distance, elapsed, cadence, stride, steps))
        }
        val raw = OfflineGaitPlan(samples.toList(), startDistance, finishDistance, seed)
        val cadenceScale = averageCadenceTarget / raw.averageCadence
        val strideScale = averageStrideTargetCm / raw.averageStrideCm
        // Rescale the actual curve and its integrals, rather than replacing displayed averages.
        // Scaling stride divides steps by that scale; scaling both divides time by their product.
        val normalized = raw.samples.map { sample ->
            sample.copy(
                elapsedSeconds = sample.elapsedSeconds / (cadenceScale * strideScale),
                cadenceStepsPerMinute = sample.cadenceStepsPerMinute * cadenceScale,
                strideCm = sample.strideCm * strideScale,
                stepEquivalent = sample.stepEquivalent / strideScale
            )
        }
        return OfflineGaitPlan(normalized, startDistance, finishDistance, seed)
    }

    private fun mix(a: Double, b: Double, fraction: Double) = a + (b - a) * fraction
    private fun smoothStep(t: Double) = t * t * (3.0 - 2.0 * t)

    private class SmoothNoise(distance: Double, random: Random) {
        private val knots = buildList {
            var position = 0.0
            add(position to random.nextDouble(-1.0, 1.0))
            while (position < distance) {
                position += random.nextDouble(30.0, 65.0)
                add(position to random.nextDouble(-1.0, 1.0))
            }
        }

        fun at(distance: Double): Double {
            var low = 0
            var high = knots.lastIndex
            while (low + 1 < high) {
                val mid = (low + high) ushr 1
                if (knots[mid].first <= distance) low = mid else high = mid
            }
            val a = knots[low]
            val b = knots[high]
            val fraction = ((distance - a.first) / (b.first - a.first)).coerceIn(0.0, 1.0)
            return mix(a.second, b.second, smoothStep(fraction))
        }
    }
}
