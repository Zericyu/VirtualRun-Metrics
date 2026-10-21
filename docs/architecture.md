# Architecture

The app uses Kotlin, Jetpack Compose, Material 3, and osmdroid. `MainViewModel` holds route and run state, while a foreground service owns location playback independently of the UI.

## Route and playback flow

```text
Synthetic loop preview
    -> Three map reference points
    -> Projected overlay and distance estimate
    -> Explicit import as a route
    -> Trajectory interpolation
    -> Mock-location delivery
```

`OfflineLoopPlanner` generates a reproducible synthetic curve from a seed and direction. `OfflineMapProjector` maps it to three geographic references and estimates the resulting length. Projection remains a separate overlay until the user imports it. Importing enables the start action without starting the service.

`TrajectoryInterpolator` advances the route using elapsed time, smooths its geometry, and applies the selected fixed or bounded pace profile. Closed routes keep cumulative distance and time across laps. Pace settings are captured when a run starts; the UI locks editing during playback.

## Location and statistics

| Component | Responsibility |
| --- | --- |
| `MockLocationService` | Run lifecycle, foreground notification, state broadcasts, and provider cleanup. |
| `MockLocationPump` | Scheduled trajectory sampling and delivery on a dedicated thread. |
| `FusedLocationAdapters` | Isolated platform-fused and Google Play fused-location channels, with asynchronous delivery and retry handling. |
| `LocationSampleFactory` | Location timestamps, speed, bearing, accuracy, and synthetic observation metadata. |
| `SimulationMetricsCalculator` | Local estimates derived from elapsed time, route distance, and the saved profile. |
| `SimulationStorage` | Saved simulation parameters and the most recent local result. |

Map imagery and map selections use GCJ-02. Playback coordinates are converted to WGS-84 before delivery to Android. Device fixes are converted in the opposite direction for map display.

Local estimates use cumulative totals: steps are distance divided by configured stride, cadence is steps per minute, and energy is distance multiplied by a configurable per-kilometer coefficient. These values are synthetic estimates, not sensor measurements or measured exercise records.

## Map rendering

`ChinaMapTileSource` provides separate road and satellite sources. Network requests use native tiles only through zoom level 18. `ChinaMapTileProvider` loads a level-18 parent when needed and crops it for display at levels 19 through 22.

Changing map mode replaces the complete tile-provider instance. This isolates late requests from the previous mode while preserving camera position and route overlays. Versioned, mode-specific disk-cache names also prevent reuse of older mixed imagery.

A color filter reduces base-map saturation to 72%. Route overlays and location markers retain their own colors.

## Interface and motion

`IslandStyle` defines the shared warm-gray and soft-gold palette. `RunDataIsland` presents compact statistics and expands to show details. `OfflineProjectionControls` guides three-point selection and keeps route-import actions visible. `RunActionControls` animates the active stop button into the horizontal center.

`MotionDialog` retains content until its exit transition finishes and combines spring movement, subtle scaling, and a dimmed backdrop. Window configuration is applied once per dialog window. A single reduced-resolution, blurred map snapshot is shared by glass controls; snapshot capture pauses while modal content is displayed or the statistics island is expanded.

See [Usage](usage.md) for the complete route-generation workflow.
