# Changelog

## 1.0.5-metrics.22

- Add an explicit preview, three-point projection, confirmation, and route-import workflow. Show **Start** only after a generated route has been imported; clearing the route hides it again.
- Support custom fixed pace or a fastest-to-slowest range with second-level input. Save settings for the next run and lock pace editing during playback.
- Show local distance, duration, step, cadence, stride, and energy estimates in an expandable floating data island.
- Use warm-gray surfaces and soft-gold accents across the island, dialogs, projection panel, and map actions. Align location and preview actions, and animate the stop button into the center during playback.
- Unify dialog and panel transitions with spring movement, subtle scaling, and backdrop fades. Avoid repeated window configuration and pause glass-background capture while modal content is visible.
- Preserve routes and camera state when switching road and satellite maps. Isolate in-flight tile work and use separate versioned caches to prevent mixed imagery.
- Load native tiles through zoom level 18 and enlarge parent tiles for levels 19 through 22. Reduce base-map saturation to 72% while preserving route and marker colors.
- Convert between GCJ-02 map coordinates and WGS-84 system locations, with a device-location action available in the main and projection interfaces.

Generated routes, offline previews, and local simulation metrics are synthetic/demo data. They do not represent measured physical activity or hardware step-sensor readings.
