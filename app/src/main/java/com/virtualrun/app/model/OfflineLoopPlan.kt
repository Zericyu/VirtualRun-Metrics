package com.virtualrun.app.model

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * A point in an imaginary, local metre-based coordinate system.
 * Positive x points east and positive y points north. It is not a GPS position.
 * [distanceMeters] is cumulative arc length on the generated reference
 * polyline, not a measurement of physical activity. Drawing only the sampled
 * points introduces a small chord approximation, reported separately below.
 */
data class OfflineLoopPoint internal constructor(
    val xMeters: Double,
    val yMeters: Double,
    val distanceMeters: Double
)

/**
 * Pure planning/synthetic data; this model has no connection to location delivery.
 * [baselineOutline] is the unperturbed comparison shape at the same coordinate
 * scale, start, and direction. Its final distance is its own actual geometric
 * perimeter and is not forced to [lapLengthMeters].
 */
data class OfflineLoopPlan internal constructor(
    val lapLengthMeters: Double,
    val targetDistanceMeters: Double,
    val points: List<OfflineLoopPoint>,
    val lapOutline: List<OfflineLoopPoint>,
    val baselineOutline: List<OfflineLoopPoint>,
    val seed: Long,
    val clockwise: Boolean
) {
    val completedLaps: Int get() = floor(targetDistanceMeters / lapLengthMeters).toInt()
    val remainingDistanceMeters: Double get() = targetDistanceMeters % lapLengthMeters

    /** Length of the sampled drawing, which is slightly shorter than the reference polyline. */
    val polylineDistanceMeters: Double = points.zipWithNext().sumOf { (a, b) ->
        hypot(b.xMeters - a.xMeters, b.yMeters - a.yMeters)
    }
}

/**
 * Generates an illustrative stadium shape for an offline preview only.
 *
 * Two straights and two semicircles form the baseline. Two closed, correlated
 * spatial noise fields add small normal offsets throughout the whole loop.
 * Their controls have zero mean and a shared zero at the start, so the result
 * has no seam jump or intentionally added whole-loop displacement. This is
 * illustrative geometric variation, not a model fitted to measured runners.
 * The closed reference polyline is scaled to the requested lap length before
 * it is sampled by arc length. These are synthetic geometric test curves,
 * not a survey or assertion about the dimensions of any real sports field.
 * The offsets do not model or claim the distribution of human/GNSS motion.
 * The east-side midpoint is the start; counterclockwise movement heads north.
 */
object OfflineLoopPlanner {
    const val MIN_LAP_METERS = 100.0
    const val MAX_LAP_METERS = 1_000.0
    const val MIN_TARGET_METERS = 1.0
    const val MAX_TARGET_METERS = 10_000.0
    const val MAX_SAMPLE_SPACING_METERS = 0.125
    private const val REFERENCE_SEGMENTS = 4_096
    private const val DISTANCE_EPSILON = 1e-9
    private const val STRAIGHT_TO_RADIUS_RATIO = 2.3

    fun generate(
        lapLengthMeters: Double = 400.0,
        targetDistanceMeters: Double = 2_000.0,
        seed: Long = Random.nextLong(),
        clockwise: Boolean = false
    ): OfflineLoopPlan {
        require(lapLengthMeters.isFinite() && lapLengthMeters in MIN_LAP_METERS..MAX_LAP_METERS) {
            "Lap length must be finite and between 100 and 1000 metres."
        }
        require(targetDistanceMeters.isFinite() && targetDistanceMeters in MIN_TARGET_METERS..MAX_TARGET_METERS) {
            "Target distance must be finite and between 1 and 10000 metres."
        }
        val curves = buildReferenceCurves(lapLengthMeters, seed)
        return OfflineLoopPlan(
            lapLengthMeters = lapLengthMeters,
            targetDistanceMeters = targetDistanceMeters,
            points = sampleCurve(curves.perturbed, targetDistanceMeters, clockwise),
            lapOutline = sampleCurve(curves.perturbed, lapLengthMeters, clockwise),
            baselineOutline = sampleCurve(curves.baseline, curves.baseline.lapLengthMeters, clockwise),
            seed = seed,
            clockwise = clockwise
        )
    }

    private data class Vertex(val x: Double, val y: Double)

    private data class ReferenceCurve(
        val vertices: List<Vertex>,
        val distances: DoubleArray,
        val lapLengthMeters: Double
    )

    private data class PreviewCurves(val perturbed: ReferenceCurve, val baseline: ReferenceCurve)

    private data class BaselinePoint(val position: Vertex, val outwardNormal: Vertex)

    private class ClosedSpatialNoise(random: Random, count: Int) {
        private val controls = DoubleArray(count) { random.nextDouble(-1.0, 1.0) }.apply {
            this[0] = 0.0
            val mean = drop(1).average()
            for (index in 1 until size) this[index] -= mean
            val maximum = maxOf { kotlin.math.abs(it) }.coerceAtLeast(1.0)
            for (index in indices) this[index] /= maximum
        }

        fun sample(fraction: Double): Double {
            val position = fraction * controls.size
            val index = floor(position).toInt()
            val t = position - index
            fun control(offset: Int): Double = controls[Math.floorMod(index + offset, controls.size)]
            val a = control(-1)
            val b = control(0)
            val c = control(1)
            val d = control(2)
            // Closed Catmull-Rom interpolation preserves continuous tangents,
            // including where the last control wraps around to the first.
            return 0.5 * ((2.0 * b) + (-a + c) * t +
                (2.0 * a - 5.0 * b + 4.0 * c - d) * t * t +
                (-a + 3.0 * b - 3.0 * c + d) * t * t * t)
        }
    }

    private fun buildReferenceCurves(lapLengthMeters: Double, seed: Long): PreviewCurves {
        val random = Random(seed)
        // Unit bend radius; each straight has length 2.3. These proportions
        // describe a geometric diagram, not a particular real-world stadium.
        val baselineLength = 2.0 * STRAIGHT_TO_RADIUS_RATIO + 2.0 * PI
        val broadVariation = ClosedSpatialNoise(random, 48)
        val fineVariation = ClosedSpatialNoise(random, 128)
        // Keep variation around the loop, with gentler broad offsets and less fine waviness.
        val broadAmplitude = random.nextDouble(0.028, 0.044)
        val fineAmplitude = random.nextDouble(0.0026, 0.0052)
        val baseline = (0 until REFERENCE_SEGMENTS).map { index ->
            val distance = baselineLength * index / REFERENCE_SEGMENTS
            stadiumPointAt(distance)
        }
        val raw = baseline.mapIndexed { index, base ->
            val fraction = index.toDouble() / REFERENCE_SEGMENTS
            val offset = broadAmplitude * broadVariation.sample(fraction) +
                fineAmplitude * fineVariation.sample(fraction)
            Vertex(
                base.position.x + base.outwardNormal.x * offset,
                base.position.y + base.outwardNormal.y * offset
            )
        }
        val closed = raw + raw.first()
        val rawLength = closed.zipWithNext().sumOf { (a, b) -> hypot(b.x - a.x, b.y - a.y) }
        val scale = lapLengthMeters / rawLength
        val baselineVertices = baseline.map { it.position }
        return PreviewCurves(
            perturbed = scaledReferenceCurve(closed, scale, lapLengthMeters),
            // Keep the same scale as the perturbed curve, rather than
            // independently resizing the grey baseline to a forced 400 m.
            baseline = scaledReferenceCurve(baselineVertices + baselineVertices.first(), scale)
        )
    }

    private fun scaledReferenceCurve(raw: List<Vertex>, scale: Double, exactLength: Double? = null): ReferenceCurve {
        val vertices = raw.map { Vertex(it.x * scale, it.y * scale) }
        val distances = DoubleArray(vertices.size)
        for (index in 1 until vertices.size) {
            val previous = vertices[index - 1]
            val current = vertices[index]
            distances[index] = distances[index - 1] + hypot(current.x - previous.x, current.y - previous.y)
        }
        if (exactLength != null) {
            // Only removes floating-point summation drift, not geometric distance.
            distances[distances.lastIndex] = exactLength
        }
        return ReferenceCurve(vertices, distances, distances.last())
    }

    private fun stadiumPointAt(distance: Double): BaselinePoint {
        val halfStraight = STRAIGHT_TO_RADIUS_RATIO / 2.0
        val topArcEnd = halfStraight + PI
        val leftStraightEnd = topArcEnd + STRAIGHT_TO_RADIUS_RATIO
        val bottomArcEnd = leftStraightEnd + PI
        return when {
            distance < halfStraight -> BaselinePoint(Vertex(1.0, distance), Vertex(1.0, 0.0))
            distance < topArcEnd -> {
                val angle = distance - halfStraight
                BaselinePoint(Vertex(cos(angle), halfStraight + sin(angle)), Vertex(cos(angle), sin(angle)))
            }
            distance < leftStraightEnd -> BaselinePoint(
                Vertex(-1.0, halfStraight - (distance - topArcEnd)), Vertex(-1.0, 0.0)
            )
            distance < bottomArcEnd -> {
                val angle = PI + distance - leftStraightEnd
                BaselinePoint(Vertex(cos(angle), -halfStraight + sin(angle)), Vertex(cos(angle), sin(angle)))
            }
            else -> BaselinePoint(Vertex(1.0, -halfStraight + distance - bottomArcEnd), Vertex(1.0, 0.0))
        }
    }

    private fun sampleCurve(curve: ReferenceCurve, totalDistanceMeters: Double, clockwise: Boolean): List<OfflineLoopPoint> {
        // An integer number of equal intervals fits each lap, so opposite
        // directions visit the same full-lap sample locations in reverse order.
        val intervalsPerLap = ceil(curve.lapLengthMeters / MAX_SAMPLE_SPACING_METERS).toInt()
        val spacing = curve.lapLengthMeters / intervalsPerLap
        val samplesBeforeEnd = floor((totalDistanceMeters - DISTANCE_EPSILON) / spacing).toInt()
        val points = ArrayList<OfflineLoopPoint>(samplesBeforeEnd + 2)
        for (index in 0..samplesBeforeEnd) {
            points.add(pointAt(index * spacing, curve, clockwise))
        }
        // Preserve the exact requested final distance, including a partial lap.
        points.add(pointAt(totalDistanceMeters, curve, clockwise))
        return points.toList()
    }

    private fun pointAt(distanceMeters: Double, curve: ReferenceCurve, clockwise: Boolean): OfflineLoopPoint {
        val lap = curve.lapLengthMeters
        val remainder = distanceMeters % lap
        if (remainder < DISTANCE_EPSILON || lap - remainder < DISTANCE_EPSILON) {
            return OfflineLoopPoint(curve.vertices.first().x, curve.vertices.first().y, distanceMeters)
        }
        val curveDistance = if (clockwise) lap - remainder else remainder
        var lower = 0
        var upper = curve.distances.lastIndex
        while (lower + 1 < upper) {
            val middle = (lower + upper) ushr 1
            if (curve.distances[middle] <= curveDistance) lower = middle else upper = middle
        }
        val a = curve.vertices[lower]
        val b = curve.vertices[upper]
        val fraction = (curveDistance - curve.distances[lower]) / (curve.distances[upper] - curve.distances[lower])
        return OfflineLoopPoint(
            a.x + (b.x - a.x) * fraction,
            a.y + (b.y - a.y) * fraction,
            distanceMeters
        )
    }
}
