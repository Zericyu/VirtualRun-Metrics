package com.virtualrun.app.ui

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.model.LatLng
import com.virtualrun.app.map.CHINA_MAP_MAX_ZOOM_LEVEL
import com.virtualrun.app.map.CHINA_MAP_MIN_ZOOM_LEVEL
import com.virtualrun.app.map.ChinaMapTileSource
import com.virtualrun.app.map.ChinaMapTileProvider
import com.virtualrun.app.map.MapMode
import com.virtualrun.app.map.CoordinateConverter
import com.virtualrun.app.model.MapDisplayPoint
import com.virtualrun.app.model.OfflineLoopPlanner
import com.virtualrun.app.model.OfflineMapProjector
import com.virtualrun.app.model.OfflineMapOverlay
import com.virtualrun.app.service.MockLocationService
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val PlannedRouteColor = Color(0xFF1E88E5)
private val ActualTrailColor = Color(0xFFFF7043)
private val RunGreen = Color(0xFF367862)
private val StopRed = Color(0xFFB34B60)

private enum class RunUiErrorAction {
    DISMISS,
    APP_SETTINGS,
    DEVELOPER_SETTINGS
}

private data class RunUiError(
    val message: String,
    val action: RunUiErrorAction
)

private data class ProjectionPanelContent(
    val anchorCount: Int = 0,
    val confirmed: Boolean = false,
    val projection: Result<OfflineMapOverlay>? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OSMapScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val routePoints by viewModel.routePoints.collectAsState()
    val hasGeneratedRoute by viewModel.hasGeneratedRoute.collectAsState()
    val basePace by viewModel.basePace.collectAsState()
    val routeDistanceMeters = remember(routePoints) { viewModel.getCurrentRouteDistance() }
    val routeDurationSeconds = remember(routePoints, basePace) { viewModel.getCurrentRouteDuration() }
    val isRunning by viewModel.isRunning.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val lapCount by viewModel.lapCount.collectAsState()
    val isLoopMode by viewModel.isLoopMode.collectAsState()
    val simulationProfile by viewModel.simulationProfile.collectAsState()
    val simulationMetrics by viewModel.simulationMetrics.collectAsState()
    val deliverySummary by viewModel.deliverySummary.collectAsState()
    val deliveryDetails by viewModel.deliveryDetails.collectAsState()
    val deliveryWarning by viewModel.deliveryWarning.collectAsState()
    val latestIsRunning by rememberUpdatedState(isRunning)
    val latestIsLoopMode by rememberUpdatedState(isLoopMode)
    val screenEntrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { screenEntrance.animateTo(1f, tween(280)) }

    var mapView by remember { mutableStateOf<MapView?>(null) }
    val mapTone = remember { ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.72f) }) }
    val mapPreferences = remember(context) { context.getSharedPreferences("map_display", Context.MODE_PRIVATE) }
    var mapMode by remember { mutableStateOf(MapMode.fromPreference(mapPreferences.getString("mode", null))) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }
    var routeMarkers by remember { mutableStateOf<FolderOverlay?>(null) }
    var actualTrail by remember { mutableStateOf<Polyline?>(null) }
    val rawTrailPoints = remember { mutableListOf<GeoPoint>() }
    var currentMarker by remember { mutableStateOf<Marker?>(null) }
    var markerAnimator by remember { mutableStateOf<ValueAnimator?>(null) }
    var pendingLocationListener by remember { mutableStateOf<LocationListener?>(null) }
    var deviceLocation by remember { mutableStateOf<Location?>(null) }
    var locationRequestShowErrors by remember { mutableStateOf(false) }
    var lastCameraFollowAt by remember { mutableLongStateOf(0L) }
    var wasRunning by remember { mutableStateOf(false) }
    var uiError by remember { mutableStateOf<RunUiError?>(null) }
    var showSimulationMetrics by remember { mutableStateOf(false) }
    var showPaceSettings by remember { mutableStateOf(false) }
    var showDeliveryDetails by remember { mutableStateOf(false) }
    var showOfflineLoopPreview by rememberSaveable { mutableStateOf(false) }
    var islandExpanded by rememberSaveable { mutableStateOf(false) }
    val modalShowing = showOfflineLoopPreview || showSimulationMetrics || showPaceSettings || showDeliveryDetails || uiError != null
    val glassBackdrop = rememberMapGlassBackdrop(mapView, !islandExpanded && !modalShowing, mapMode)
    var retainedUiError by remember { mutableStateOf<RunUiError?>(null) }
    LaunchedEffect(uiError) { if (uiError != null) retainedUiError = uiError }
    var bottomControlsHeightPx by remember { mutableIntStateOf(0) }

    var projectionSeed by rememberSaveable { mutableStateOf<Long?>(null) }
    var projectionClockwise by rememberSaveable { mutableStateOf(false) }
    var projectionReferenceValues by rememberSaveable { mutableStateOf<List<Double>>(emptyList()) }
    var projectionConfirmed by rememberSaveable { mutableStateOf(false) }
    val projectionActive = projectionSeed != null
    LaunchedEffect(showOfflineLoopPreview, projectionActive) {
        if (showOfflineLoopPreview || projectionActive) islandExpanded = false
    }
    val projectionReferences = remember(projectionReferenceValues) {
        projectionReferenceValues.chunked(2).map { MapDisplayPoint(it[0], it[1]) }
    }
    val projectionPlan = remember(projectionSeed, projectionClockwise) {
        projectionSeed?.let { OfflineLoopPlanner.generate(seed = it, clockwise = projectionClockwise) }
    }
    val projectionResult = remember(projectionPlan, projectionReferences) {
        if (projectionPlan != null && projectionReferences.size == 3) {
            runCatching { OfflineMapProjector.project(projectionPlan, projectionReferences) }
        } else null
    }
    val projectionPanel = ProjectionPanelContent(projectionReferences.size, projectionConfirmed, projectionResult)
    var retainedProjectionPanel by remember { mutableStateOf(ProjectionPanelContent()) }
    SideEffect { if (projectionActive) retainedProjectionPanel = projectionPanel }
    val latestProjectionActive by rememberUpdatedState(projectionActive)
    val pickMapPoint by rememberUpdatedState<(GeoPoint) -> Unit>({ point ->
        if (projectionActive) {
            if (!projectionConfirmed) {
                val reference = runCatching { MapDisplayPoint(point.latitude, point.longitude) }.getOrNull()
                if (reference == null) {
                    uiError = RunUiError("当前地图坐标无法用于此投影，请移到有效区域", RunUiErrorAction.DISMISS)
                } else {
                    val retained = if (projectionReferences.size == 3) projectionReferenceValues.take(4) else projectionReferenceValues
                    projectionReferenceValues = retained + listOf(reference.latitude, reference.longitude)
                }
            }
        } else if (!latestIsRunning && !latestIsLoopMode) {
            viewModel.addRoutePoint(LatLng(point.latitude, point.longitude))
        }
    })

    OfflineProjectionLayer(mapView, projectionReferences, projectionResult?.getOrNull())

    val animatedProgress = remember { Animatable(0f) }
    var animatedProgressLap by remember { mutableIntStateOf(0) }
    val actionColor by animateColorAsState(
        targetValue = if (isRunning) StopRed else RunGreen,
        animationSpec = tween(250),
        label = "run action color"
    )
    val locationButtonOffset by animateDpAsState(
        targetValue = if (bottomControlsHeightPx > 0) {
            with(density) { -bottomControlsHeightPx.toDp() + 8.dp }
        } else {
            (-258).dp
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "location button offset"
    )

    fun animateToDeviceLocation(location: Location) {
        val gcjLocation = CoordinateConverter.wgs84ToGcj02(location.latitude, location.longitude)
        mapView?.controller?.animateTo(GeoPoint(gcjLocation.first, gcjLocation.second), 18.0, 1000L)
    }

    fun cachedDeviceLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused")
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .firstOrNull { !it.isFromMockProvider }
    }

    fun requestDeviceLocation(showErrors: Boolean, moveCamera: Boolean = true) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (showErrors) uiError = RunUiError("请先授予位置权限", RunUiErrorAction.APP_SETTINGS)
            return
        }
        if (latestIsRunning) {
            if (showErrors) Toast.makeText(context, "请先停止模拟，再定位设备当前位置", Toast.LENGTH_SHORT).show()
            return
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        pendingLocationListener?.let { runCatching { manager.removeUpdates(it) } }
        pendingLocationListener = null
        cachedDeviceLocation()?.let { location ->
            deviceLocation = Location(location)
            if (moveCamera) animateToDeviceLocation(location)
        }
        val enabled = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (enabled.isEmpty()) {
            if (showErrors) uiError = RunUiError("系统定位未开启，请开启定位后重试", RunUiErrorAction.DISMISS)
            return
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (pendingLocationListener !== this || location.isFromMockProvider || latestIsRunning) return
                // A stale network fix must not replace an available GPS fix.
                if (location.provider == LocationManager.GPS_PROVIDER || deviceLocation == null) {
                    deviceLocation = Location(location)
                    if (moveCamera) animateToDeviceLocation(location)
                }
                if (location.provider == LocationManager.GPS_PROVIDER || LocationManager.GPS_PROVIDER !in enabled) {
                    runCatching { manager.removeUpdates(this) }
                    pendingLocationListener = null
                }
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        locationRequestShowErrors = showErrors
        pendingLocationListener = listener
        val requested = enabled.map { provider ->
            runCatching { manager.requestSingleUpdate(provider, listener, Looper.getMainLooper()) }.isSuccess
        }.any { it }
        if (!requested) {
            pendingLocationListener = null
            if (showErrors) uiError = RunUiError("未能请求定位，请检查系统定位设置", RunUiErrorAction.DISMISS)
        }
    }

    val latestLocate by rememberUpdatedState<(Boolean, Boolean) -> Unit>({ showErrors, moveCamera ->
        requestDeviceLocation(showErrors, moveCamera)
    })

    LaunchedEffect(mapView, projectionActive) {
        if (mapView != null && !latestIsRunning && projectionReferences.isEmpty()) {
            requestDeviceLocation(false, projectionActive || !mapPreferences.contains("center_lat"))
        }
    }

    LaunchedEffect(pendingLocationListener) {
        val listener = pendingLocationListener ?: return@LaunchedEffect
        delay(12_000L)
        if (pendingLocationListener === listener) {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            runCatching { manager.removeUpdates(listener) }
            pendingLocationListener = null
            if (locationRequestShowErrors && deviceLocation == null) {
                uiError = RunUiError("暂未获取到位置，请检查系统定位；虚拟机需要先设置模拟器位置", RunUiErrorAction.DISMISS)
            }
        }
    }

    LaunchedEffect(mapView, deviceLocation, isRunning) {
        val map = mapView ?: return@LaunchedEffect
        map.overlays.removeAll { it is Marker && it.id == "device-location" }
        val location = deviceLocation
        if (location != null && !isRunning) {
            val display = CoordinateConverter.wgs84ToGcj02(location.latitude, location.longitude)
            map.overlays.add(Marker(map).apply {
                id = "device-location"
                position = GeoPoint(display.first, display.second)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor("#1E88E5"))
                    setStroke(4, android.graphics.Color.WHITE)
                    setSize((18 * density.density).roundToInt(), (18 * density.density).roundToInt())
                }
                infoWindow = null
                setOnMarkerClickListener { _, _ ->
                    Toast.makeText(context, "设备当前位置", Toast.LENGTH_SHORT).show()
                    true
                }
            })
        }
        map.invalidate()
    }

    LaunchedEffect(progress, lapCount, isRunning) {
        if (!isRunning) {
            animatedProgress.snapTo(0f)
        } else if (lapCount != animatedProgressLap || progress + 0.05f < animatedProgress.value) {
            animatedProgress.snapTo(progress)
        } else {
            animatedProgress.animateTo(progress, tween(850, easing = LinearEasing))
        }
        animatedProgressLap = lapCount
    }

    LaunchedEffect(isRunning, mapView) {
        if (isRunning && !wasRunning) {
            markerAnimator?.cancel()
            rawTrailPoints.clear()
            actualTrail?.let { mapView?.overlays?.remove(it) }
            actualTrail = null
            lastCameraFollowAt = 0L
        }
        wasRunning = isRunning
    }

    val lifecycleMap = mapView
    DisposableEffect(lifecycleOwner, lifecycleMap) {
        val activeMap = lifecycleMap
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    activeMap?.onResume()
                    if (deviceLocation == null && !latestIsRunning) latestLocate(false, true)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    activeMap?.onPause()
                    activeMap?.let { map ->
                        mapPreferences.edit().putString("center_lat", map.mapCenter.latitude.toString())
                            .putString("center_lon", map.mapCenter.longitude.toString())
                            .putFloat("zoom", map.zoomLevelDouble.toFloat()).apply()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) activeMap?.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            markerAnimator?.cancel()
            activeMap?.onDetach()
        }
    }

    val activeLocationRequest = pendingLocationListener
    DisposableEffect(activeLocationRequest) {
        val listener = activeLocationRequest
        onDispose {
            if (listener != null) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                runCatching { locationManager.removeUpdates(listener) }
            }
        }
    }

    LaunchedEffect(mapView, routePoints) {
        val mv = mapView ?: return@LaunchedEffect
        routePolyline?.let(mv.overlays::remove)
        routeMarkers?.let(mv.overlays::remove)
        routePolyline = null
        routeMarkers = null

        if (routePoints.size >= 2) {
            routePolyline = Polyline(mv).apply {
                routePoints.forEach { addPoint(GeoPoint(it.latitude, it.longitude)) }
                outlinePaint.apply {
                    color = PlannedRouteColor.toArgb()
                    strokeWidth = 6f
                }
            }.also(mv.overlays::add)
        }

        if (routePoints.isNotEmpty()) {
            routeMarkers = FolderOverlay().also { folder ->
                routePoints.forEachIndexed { index, point ->
                    // Dense generated curves need a start marker, not thousands of overlapping markers.
                    if (routePoints.size > 100 && index != 0) return@forEachIndexed
                    val marker = Marker(mv).apply {
                        position = GeoPoint(point.latitude, point.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        infoWindow = null
                        icon = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(if (index == 0) android.graphics.Color.parseColor("#2EAD65") else android.graphics.Color.parseColor("#1E88E5"))
                            setStroke(if (index == 0) 3 else 2, android.graphics.Color.WHITE)
                            val targetDp = if (index == 0) 28 else 22
                            val targetPx = (targetDp * context.resources.displayMetrics.density).roundToInt()
                            setSize(targetPx, targetPx)
                        }
                        setOnMarkerClickListener { _, _ ->
                            if (index == 0 && !latestIsRunning && !latestProjectionActive && routePoints.size >= 3) {
                                viewModel.closeLoop()
                            }
                            true
                        }
                    }
                    folder.add(marker)
                }
                mv.overlays.add(folder)
            }
        }

        if (routePoints.isEmpty() && !latestIsRunning) {
            actualTrail?.let(mv.overlays::remove)
            actualTrail = null
            rawTrailPoints.clear()
        }
        // 路线编辑后重新建立稳定层级：计划线 < 路线点 < 原始实跑线 < 当前定位点。
        actualTrail?.let {
            mv.overlays.remove(it)
            mv.overlays.add(it)
        }
        currentMarker?.let {
            mv.overlays.remove(it)
            mv.overlays.add(it)
        }
        mv.invalidate()
    }

    LaunchedEffect(mapView, currentLocation) {
        val mv = mapView ?: return@LaunchedEffect
        val loc = currentLocation
        if (loc == null) {
            markerAnimator?.cancel()
            currentMarker?.let(mv.overlays::remove)
            currentMarker = null
            mv.invalidate()
            return@LaunchedEffect
        }

        val gcjLoc = CoordinateConverter.wgs84ToGcj02(loc.latitude, loc.longitude)
        val target = GeoPoint(gcjLoc.first, gcjLoc.second)
        val previousTrailPoint = rawTrailPoints.lastOrNull()
        if (previousTrailPoint == null || previousTrailPoint.latitude != target.latitude || previousTrailPoint.longitude != target.longitude) {
            rawTrailPoints.add(target)
            if (rawTrailPoints.size > 3600) rawTrailPoints.removeAt(0)
        }

        val trail = actualTrail ?: Polyline(mv).apply {
            outlinePaint.apply {
                color = ActualTrailColor.toArgb()
                strokeWidth = 6f
            }
        }.also {
            actualTrail = it
            mv.overlays.add(it)
        }
        trail.setPoints(rawTrailPoints.toList())

        val marker = currentMarker ?: Marker(mv).apply {
            position = target
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#FF7043"))
                setStroke(5, android.graphics.Color.WHITE)
                setSize(44, 44)
            }
        }.also {
            currentMarker = it
            mv.overlays.add(it)
        }

        markerAnimator?.cancel()
        val start = GeoPoint(marker.position.latitude, marker.position.longitude)
        markerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 880L
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                marker.position = GeoPoint(
                    start.latitude + (target.latitude - start.latitude) * fraction,
                    start.longitude + (target.longitude - start.longitude) * fraction
                )
                mv.invalidate()
            }
            start()
        }

        val now = SystemClock.elapsedRealtime()
        if (isRunning && now - lastCameraFollowAt >= 2500L) {
            mv.controller.animateTo(target, mv.zoomLevelDouble, 900L)
            lastCameraFollowAt = now
        }
        mv.invalidate()
    }

    Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = screenEntrance.value }) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = if (projectionActive) "投影参考点地图" else "路线地图。移动地图后双击，在地图中心添加路线点"
                    onClick(label = if (projectionActive) "选取地图中心作为投影参考点" else "在地图中心添加路线点") {
                        val center = mapView?.mapCenter
                        if (!latestIsRunning && center != null && (!latestIsLoopMode || latestProjectionActive)) {
                            pickMapPoint(GeoPoint(center.latitude, center.longitude))
                            true
                        } else {
                            false
                        }
                    }
                },
            factory = { ctx ->
                MapView(ctx, ChinaMapTileProvider(ctx, ChinaMapTileSource(mapMode))).apply {
                    overlayManager.tilesOverlay.setColorFilter(mapTone)
                    setMinZoomLevel(CHINA_MAP_MIN_ZOOM_LEVEL.toDouble())
                    setMaxZoomLevel(CHINA_MAP_MAX_ZOOM_LEVEL.toDouble())
                    setMultiTouchControls(true)
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    val cached = cachedDeviceLocation()
                    if (cached != null) deviceLocation = Location(cached)
                    val savedLat = mapPreferences.getString("center_lat", null)?.toDoubleOrNull()
                    val savedLon = mapPreferences.getString("center_lon", null)?.toDoubleOrNull()
                    val deviceCenter = cached?.let {
                        val display = CoordinateConverter.wgs84ToGcj02(it.latitude, it.longitude)
                        GeoPoint(display.first, display.second)
                    }
                    val center = projectionReferences.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
                        ?: if (savedLat != null && savedLon != null) GeoPoint(savedLat, savedLon) else deviceCenter
                        ?: GeoPoint(39.9042, 116.4074)
                    val zoom = if (projectionReferences.isNotEmpty()) 18.0 else
                        mapPreferences.getFloat("zoom", if (deviceCenter != null) 18f else 16f).toDouble()
                    controller.setZoom(zoom.coerceIn(CHINA_MAP_MIN_ZOOM_LEVEL.toDouble(), CHINA_MAP_MAX_ZOOM_LEVEL.toDouble()))
                    controller.setCenter(center)
                    overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                pickMapPoint(p)
                            }
                            return p != null
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    }))
                    mapView = this
                }
            },
            update = { view ->
                if ((view.tileProvider.tileSource as? ChinaMapTileSource)?.mode != mapMode) {
                    // Tile memory keys contain coordinates, not map mode. Replacing the entire
                    // provider isolates late downloads and overzoom tasks from the previous mode.
                    // MapView detaches the old provider and changes only its base tiles overlay.
                    val zoom = view.zoomLevelDouble
                    val center = GeoPoint(view.mapCenter.latitude, view.mapCenter.longitude)
                    view.setTileProvider(ChinaMapTileProvider(view.context, ChinaMapTileSource(mapMode)))
                    view.overlayManager.tilesOverlay.setColorFilter(mapTone)
                    view.setMinZoomLevel(CHINA_MAP_MIN_ZOOM_LEVEL.toDouble())
                    view.setMaxZoomLevel(CHINA_MAP_MAX_ZOOM_LEVEL.toDouble())
                    view.controller.setZoom(zoom)
                    view.controller.setCenter(center)
                    view.invalidate()
                }
            }
        )

        AnimatedVisibility(
            visible = islandExpanded,
            enter = fadeIn(tween(220)), exit = fadeOut(tween(160))
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.10f)).clickable { islandExpanded = false })
        }
        RunDataIsland(
            metrics = simulationMetrics,
            paceSettings = simulationProfile.pace,
            isRunning = isRunning,
            expanded = islandExpanded,
            isLoop = isLoopMode,
            lap = lapCount,
            routePointCount = routePoints.size,
            routeDistance = routeDistanceMeters,
            routeDuration = routeDurationSeconds,
            progress = animatedProgress.value,
            deliverySummary = deliverySummary,
            deliveryWarning = deliveryWarning,
            onExpandedChange = { islandExpanded = it },
            onParameters = { showSimulationMetrics = true },
            onPaceSettings = { showPaceSettings = true },
            onDelivery = { showDeliveryDetails = true },
            onCloseLoop = viewModel::closeLoop,
            onReverse = viewModel::reverseRouteDirection,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
        )

        AnimatedVisibility(
            visible = !islandExpanded,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp),
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 8 },
            exit = fadeOut(tween(100))
        ) {
            GlassMapButton(
                description = if (mapMode == MapMode.ROAD) "切换到卫星地图" else "切换到普通地图",
                backdrop = glassBackdrop,
                onClick = {
                    mapMode = mapMode.next()
                    mapPreferences.edit().putString("mode", mapMode.name).apply()
                }
            ) {
                Text(if (mapMode == MapMode.ROAD) "卫星" else "普通", fontSize = 12.sp)
            }
        }
        AnimatedVisibility(
            visible = !islandExpanded,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp)
                .offset(y = locationButtonOffset),
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 8 },
            exit = fadeOut(tween(100))
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassMapButton(
                    description = "放大", backdrop = glassBackdrop, shape = CircleShape,
                    onClick = {
                        mapView?.let { map ->
                            if (map.canZoomIn()) map.controller.zoomIn()
                            else Toast.makeText(context, "已达到地图最大缩放级别", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Icon(Icons.Default.Add, null, Modifier.size(20.dp)) }
                GlassMapButton(
                    description = "缩小", backdrop = glassBackdrop, shape = CircleShape,
                    onClick = { mapView?.controller?.zoomOut() }
                ) { Icon(Icons.Default.Remove, null, Modifier.size(20.dp)) }
                if (!isRunning && ((!projectionActive && !isLoopMode) || (projectionActive && !projectionConfirmed))) {
                    GlassMapButton(
                        description = if (projectionActive) "选取地图中心作为参考点" else "在地图中心添加路线点",
                        backdrop = glassBackdrop, shape = CircleShape,
                        onClick = {
                            mapView?.mapCenter?.let { center -> pickMapPoint(GeoPoint(center.latitude, center.longitude)) }
                        }
                    ) { Icon(Icons.Default.AddLocation, null, Modifier.size(20.dp)) }
                }
            }
        }
        AnimatedVisibility(
            visible = projectionActive,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 4.dp)
                .offset(y = locationButtonOffset),
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 8 },
            exit = fadeOut(tween(120))
        ) {
            MapLocateButton(
                backdrop = glassBackdrop,
                locating = pendingLocationListener != null,
                onClick = { requestDeviceLocation(true) }
            )
        }

        if (projectionActive && !projectionConfirmed) {
            Icon(
                Icons.Default.Add,
                contentDescription = "投影取点十字中心",
                modifier = Modifier.align(Alignment.Center).size(28.dp),
                tint = Color(0xFF8051D3)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .onSizeChanged { bottomControlsHeightPx = it.height }
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = projectionActive,
                transitionSpec = {
                    (fadeIn(tween(180, delayMillis = 50)) +
                        slideInVertically(spring(dampingRatio = 0.9f, stiffness = 360f)) { it / 4 } +
                        scaleIn(spring(dampingRatio = 0.9f, stiffness = 360f), initialScale = 0.97f,
                            transformOrigin = TransformOrigin(0.5f, 1f))) togetherWith
                        (fadeOut(tween(130)) + slideOutVertically(tween(200)) { it / 6 } +
                            scaleOut(tween(180), targetScale = 0.98f, transformOrigin = TransformOrigin(0.5f, 1f)))
                }, label = "map action panel"
            ) { projecting ->
            Column {
            if (projecting) {
                // Keep the confirmed content unchanged while the sheet animates away.
                val panel = if (projectionActive) projectionPanel else retainedProjectionPanel
                OfflineProjectionControls(
                    anchorCount = panel.anchorCount,
                    confirmed = panel.confirmed,
                    projection = panel.projection,
                    onPickCenter = { if (projectionActive) mapView?.mapCenter?.let { pickMapPoint(GeoPoint(it.latitude, it.longitude)) } },
                    onUndo = { projectionReferenceValues = projectionReferenceValues.dropLast(2) },
                    onReset = { projectionReferenceValues = emptyList(); projectionConfirmed = false },
                    onConfirm = { if (projectionResult?.isSuccess == true) projectionConfirmed = true },
                    onUseRoute = useRoute@{
                        if (!projectionActive) return@useRoute
                        val overlay = projectionResult?.getOrNull()
                        if (overlay != null && viewModel.useProjectedRoute(overlay)) {
                            projectionSeed = null
                            projectionReferenceValues = emptyList()
                            projectionConfirmed = false
                            Toast.makeText(context, "投影已设为循环路线，可点击开始", Toast.LENGTH_SHORT).show()
                        } else {
                            uiError = RunUiError("未能设置路线，请停止模拟并重新检查投影", RunUiErrorAction.DISMISS)
                        }
                    },
                    onClose = { projectionSeed = null; projectionReferenceValues = emptyList(); projectionConfirmed = false }
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = if (isRunning || routePoints.isNotEmpty()) 12.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MapLocateButton(
                        backdrop = glassBackdrop,
                        locating = pendingLocationListener != null,
                        onClick = { requestDeviceLocation(true) }
                    )
                    MapPreviewButton(
                        backdrop = glassBackdrop,
                        onClick = { showOfflineLoopPreview = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                AnimatedVisibility(
                    visible = isRunning || routePoints.isNotEmpty(),
                    enter = fadeIn(tween(180)) + expandVertically(tween(240)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(200))
                ) {
                RunActionControls(
                    isRunning = isRunning,
                    canStart = hasGeneratedRoute && routePoints.size >= 2,
                    hasRoute = routePoints.isNotEmpty(),
                    actionColor = actionColor,
                    onClear = { viewModel.clearRoute() },
                    onAction = {
                            if (isRunning) {
                                viewModel.stopRunning(context)
                            } else if (routePoints.size >= 2) {
                                if (!MockLocationService.isMockEnabled(context)) {
                                    uiError = RunUiError(
                                        "请先在开发者选项中「选择模拟位置信息应用」为本应用",
                                        RunUiErrorAction.DEVELOPER_SETTINGS
                                    )
                                } else viewModel.startRunning(context)
                            } else {
                                uiError = RunUiError("请至少选择2个点", RunUiErrorAction.DISMISS)
                            }
                    }
                )
                }
            }
            }
            }
        }
    }

    run {
        OfflineLoopPreviewDialog(
            visible = showOfflineLoopPreview,
            onDismiss = { showOfflineLoopPreview = false },
            projectionEnabled = !isRunning,
            onProjectToMap = { plan ->
                if (!latestIsRunning) {
                    projectionSeed = plan.seed
                    projectionClockwise = plan.clockwise
                    projectionReferenceValues = emptyList()
                    projectionConfirmed = false
                    showOfflineLoopPreview = false
                }
            }
        )
    }

    run {
        SimulationMetricsDialog(
            visible = showSimulationMetrics,
            metrics = simulationMetrics,
            profile = simulationProfile,
            isRunning = isRunning,
            onPaceSettings = { showPaceSettings = true },
            onProfileChanged = viewModel::setSimulationProfile,
            onDismiss = { showSimulationMetrics = false }
        )
    }

    run {
        MotionAlertDialog(
            visible = showDeliveryDetails,
            onDismissRequest = { showDeliveryDetails = false },
            title = { Text("定位投递状态") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(deliveryDetails.ifBlank { deliverySummary })
                }
            },
            confirmButton = { TextButton(onClick = { showDeliveryDetails = false }) { Text("关闭") } }
        )
    }

    PaceSettingsDialog(
        visible = showPaceSettings,
        pace = simulationProfile.pace,
        isRunning = isRunning,
        onSave = { viewModel.setSimulationProfile(simulationProfile.copy(pace = it)) },
        onDismiss = { showPaceSettings = false }
    )

    (uiError ?: retainedUiError)?.let { error ->
        MotionAlertDialog(
            visible = uiError != null,
            onDismissRequest = { uiError = null },
            title = { Text("提示") },
            text = { Text(error.message) },
            confirmButton = {
                TextButton(onClick = {
                    uiError = null
                    when (error.action) {
                        RunUiErrorAction.DISMISS -> Unit
                        RunUiErrorAction.APP_SETTINGS -> try {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } catch (_: Exception) {
                            Toast.makeText(context, "请手动打开应用权限设置", Toast.LENGTH_SHORT).show()
                        }
                        RunUiErrorAction.DEVELOPER_SETTINGS -> try {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                        } catch (_: Exception) {
                            Toast.makeText(context, "请手动打开开发者选项", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text(if (error.action == RunUiErrorAction.DISMISS) "知道了" else "去设置")
                }
            },
            dismissButton = if (error.action == RunUiErrorAction.DISMISS) null else {
                { TextButton(onClick = { uiError = null }) { Text("取消") } }
            }
        )
    }
}

fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", minutes, secs)
}
