# Usage

VirtualRun generates synthetic routes and plays them through Android's mock-location interfaces. Generated trajectories and local estimates are demo data, not measurements of a real activity. The control names below describe the corresponding in-app actions in English.

## Setup

1. Install the APK or build the project using the instructions in the [README](../README.md).
2. Grant location and notification permissions when requested.
3. In Android Developer options, select this app as the mock-location app.
4. Enable system location. Loading an uncached map area requires a network connection.

## Generate and run a route

1. Open **Generate loop preview**. Choose a direction and regenerate the synthetic curve if needed.
2. Select **Confirm preview and pick map points**.
3. Pick three reference points: the field center, one end of its long axis, and the midpoint of one side. Tap the map directly, or move the crosshair and use **Pick map center**.
4. Check the projected shape and estimated distance. Use **Undo** or adjust the third point if necessary, then select **Confirm projection**.
5. Select **Use projection as route**. This replaces the current route and enables **Start**; importing a route does not start location playback.
6. Select **Start**. The action becomes a centered **Stop** button while playback runs.
7. Select **Stop** to finish. A generated loop repeats until stopped, including after the preview's nominal target distance has been reached.

**Start** stays hidden until a generated projection has been imported as a route. Viewing a preview, selecting reference points, or adding manual points does not unlock it. **Clear** removes the route and hides **Start** again.

## Set pace and local estimates

Expand the floating data island and open **Pace settings**. Choose either:

- **Fixed pace:** enter one positive value in minutes:seconds per kilometer, such as `6:00`.
- **Range:** enter the fastest and slowest values, such as `5:45` and `6:15`. The simulated pace varies smoothly within these bounds. Equal values behave as a fixed pace.

There is no preset pace band. Input is precise to the second; the seconds component must be between `00` and `59`, and the fastest value must not exceed the slowest. Smaller pace values mean faster movement.

Save changes before starting. Pace editing is disabled during playback, and saved settings apply to the next run.

Open **Details and parameters** to inspect local totals or configure estimated stride length and energy per kilometer. These assumptions produce local step, cadence, stride, and calorie estimates; they are not hardware sensor readings and are not written to other applications' step counters.

## Map and interface controls

- **Locate** centers on an available device fix. Stop playback before requesting the device's current location. An emulator uses the location supplied by the emulator.
- The map-mode button switches between road and satellite imagery while keeping the camera and route overlays.
- Zoom and center-point controls remain above the bottom action panel. Native imagery ends at zoom level 18; levels 19 through 22 enlarge parent tiles, so additional zoom does not add image detail.
- The base map uses 72% color saturation to keep route lines and location markers readable. Their original colors are retained.
- Tap the floating island to expand or collapse the statistics. Dialogs and projection controls share the warm-gray surfaces, soft-gold accents, and animated transitions.

For implementation details, see [Architecture](architecture.md).
