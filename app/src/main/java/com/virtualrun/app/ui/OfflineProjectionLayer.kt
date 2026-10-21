package com.virtualrun.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.virtualrun.app.model.MapDisplayPoint
import com.virtualrun.app.model.OfflineMapOverlay
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Only draws an independent overlay; does not modify the editable route. */
@Composable
fun OfflineProjectionLayer(map: MapView?, references: List<MapDisplayPoint>, projection: OfflineMapOverlay?) {
    DisposableEffect(map, references, projection) {
        val folder = FolderOverlay()
        if (map != null && references.isNotEmpty()) {
            if (projection != null) {
                folder.add(Polyline(map).apply {
                    setOnClickListener { _, _, _ -> false }
                    setPoints(projection.baseline.map { GeoPoint(it.latitude, it.longitude) })
                    outlinePaint.apply {
                        color = Color.parseColor("#8491A3")
                        strokeWidth = 3f
                        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
                    }
                })
                folder.add(Polyline(map).apply {
                    setOnClickListener { _, _, _ -> false }
                    setPoints(projection.outline.map { GeoPoint(it.latitude, it.longitude) })
                    outlinePaint.color = Color.parseColor("#8051D3")
                    outlinePaint.strokeWidth = 5f
                })
            }
            if (references.size >= 2) {
                val links = listOf(references[1], references[0]) + references.drop(2)
                folder.add(Polyline(map).apply {
                    setOnClickListener { _, _, _ -> false }
                    setPoints(links.map { GeoPoint(it.latitude, it.longitude) })
                    outlinePaint.color = Color.parseColor("#9B91AA")
                    outlinePaint.strokeWidth = 2f
                    outlinePaint.pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
                })
            }
            fun addMarker(point: MapDisplayPoint, label: String, title: String, color: Int) {
                folder.add(Marker(map).apply {
                    position = GeoPoint(point.latitude, point.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    infoWindow = null
                    val size = (30 * map.resources.displayMetrics.density).toInt().coerceAtLeast(30)
                    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    Canvas(bitmap).apply {
                        paint.color = Color.WHITE
                        drawCircle(size / 2f, size / 2f, size / 2f, paint)
                        paint.color = color
                        drawCircle(size / 2f, size / 2f, size * 0.43f, paint)
                        paint.color = Color.WHITE
                        paint.textSize = size * 0.48f
                        paint.textAlign = Paint.Align.CENTER
                        paint.isFakeBoldText = true
                        drawText(label, size / 2f, size / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
                    }
                    icon = BitmapDrawable(map.resources, bitmap)
                    setOnMarkerClickListener { _, _ ->
                        Toast.makeText(map.context, title, Toast.LENGTH_SHORT).show()
                        true
                    }
                })
            }
            val titles = listOf("场地中心", "长轴端点", "侧边缘中点")
            references.forEachIndexed { index, point ->
                addMarker(point, "${index + 1}", titles[index], Color.parseColor("#8051D3"))
            }
            projection?.outline?.firstOrNull()?.let {
                addMarker(it, "起", "合成曲线起点", Color.parseColor("#F27653"))
            }
            map.overlays.add(folder)
            map.invalidate()
        }
        onDispose {
            map?.overlays?.remove(folder)
            map?.invalidate()
        }
    }
}
