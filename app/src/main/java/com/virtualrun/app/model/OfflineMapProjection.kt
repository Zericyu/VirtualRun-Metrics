package com.virtualrun.app.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/** Coordinates in the map's display system. Not an Android location sample. */
data class MapDisplayPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -85.0..85.0) { "纬度无效" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "经度无效" }
    }
}

data class OfflineMapOverlay(
    val outline: List<MapDisplayPoint>,
    val baseline: List<MapDisplayPoint>,
    val singleLapMeters: Double,
    val lapsForTarget: Double,
    val clockwise: Boolean
)

/**
 * A small-area, map-display calibration. The selected centre, long-axis end,
 * and side midpoint define an affine transform in a local tangent plane.
 * Reported distances are map estimates, not survey or physical activity data.
 * No route editor, location provider, recording, or export API is used here.
 */
object OfflineMapProjector {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun project(plan: OfflineLoopPlan, references: List<MapDisplayPoint>): OfflineMapOverlay {
        require(references.size == 3) { "请依次选择场地中心、长轴端点和侧边缘中点" }
        val center = references[0]
        require(abs(center.latitude) <= 80.0) { "此近距离投影不支持极地地区" }
        val longitudeScale = cos(Math.toRadians(center.latitude))

        fun local(point: MapDisplayPoint): Pair<Double, Double> =
            Math.toRadians(wrapLongitude(point.longitude - center.longitude)) * EARTH_RADIUS_METERS * longitudeScale to
                Math.toRadians(point.latitude - center.latitude) * EARTH_RADIUS_METERS

        val (axisEast, axisNorth) = local(references[1])
        val axisLength = hypot(axisEast, axisNorth)
        require(axisLength in 5.0..2_000.0) { "长轴端点需距中心5～2000米，请放大地图重新选点" }
        val (sideEast, sideNorth) = local(references[2])
        val sideLength = hypot(sideEast, sideNorth)
        require(sideLength in 2.0..2_000.0) { "侧边缘点需距中心2～2000米" }
        // Right of the chosen positive long axis. Detect a left-side reference
        // rather than reflecting the curve and silently reversing its direction.
        val signedWidth = (sideEast * axisNorth - sideNorth * axisEast) / axisLength
        require(abs(signedWidth) >= 2.0 && abs(signedWidth) / sideLength >= 0.5) {
            "侧边缘点过于靠近长轴，请选场地左侧或右侧的边缘中点"
        }
        val sideSign = if (signedWidth > 0.0) 1.0 else -1.0
        val halfWidth = plan.baselineOutline.first().xMeters
        val halfLength = plan.baselineOutline.maxOf { it.yMeters }
        require(halfWidth > 0.0 && halfLength > 0.0) { "预览轮廓无效" }

        fun transform(point: OfflineLoopPoint): Pair<Double, Double> =
            sideEast * point.xMeters / (sideSign * halfWidth) + axisEast * point.yMeters / halfLength to
                sideNorth * point.xMeters / (sideSign * halfWidth) + axisNorth * point.yMeters / halfLength

        fun display(point: Pair<Double, Double>): MapDisplayPoint = MapDisplayPoint(
            center.latitude + Math.toDegrees(point.second / EARTH_RADIUS_METERS),
            wrapLongitude(center.longitude + Math.toDegrees(point.first / (EARTH_RADIUS_METERS * longitudeScale)))
        )

        val localOutline = plan.lapOutline.map(::transform)
        val length = localOutline.zipWithNext().sumOf { (a, b) -> hypot(b.first - a.first, b.second - a.second) }
        require(length.isFinite() && length > 0.0) { "投影距离无效，请重新选点" }
        return OfflineMapOverlay(
            outline = localOutline.map(::display),
            baseline = plan.baselineOutline.map { display(transform(it)) },
            singleLapMeters = length,
            lapsForTarget = plan.targetDistanceMeters / length,
            clockwise = plan.clockwise
        )
    }

    private fun wrapLongitude(value: Double): Double = ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
}
