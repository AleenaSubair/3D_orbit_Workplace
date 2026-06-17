# Orbit3D Workspace - Multi-Model 3D Viewer

Orbit3D Workspace is a high-performance, single-activity Android application built with Kotlin and Google's **Filament-based SceneView 2.2.1** library. The application allows users to place, move, resize, and interact with multiple 3D models simultaneously.

It is heavily optimized to run smoothly on low-end Android devices with limited RAM, weak CPUs, and older GPUs.

---

## Technical Specifications

- **Language**: Kotlin
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 36 (Android 14)
- **Rendering Engine**: Google Filament (via SceneView 2.2.1)
- **UI Architecture**: Single Activity, XML-based Views (optimized with ConstraintLayout & custom drawables)

---

## Core Features

1. **Workspace Dashboard**: 
   - A modern splash and onboarding interface greets users when the workspace is empty.
   - A slide-up horizontal card deck details the 5 pre-bundled high-fidelity `.glb` models (Cigarette, Dragoon, Pokémon Masters Cap, Futuristic Shoes, Retro Glasses), including file sizes.
2. **Draggable & Resizable Containers**:
   - Every model is placed inside a beautiful, custom glassmorphic container card.
   - **Move**: Drag the container anywhere on the screen with one finger.
   - **Resize**: Double-finger pinch-to-zoom on the container or drag the custom corner handle in the bottom-right. Resizing constraints keep containers bounded and proportional (1:1 square aspect ratio).
3. **Isolated Interaction Mode**:
   - Toggling the **Interact** button locks the container's screen placement.
   - **Rotate**: One-finger dragging rotates the 3D model node (yaw and pitch rotation with vertical limits to prevent inversion).
   - **Zoom**: Two-finger pinching zooms the 3D model node (local scale modification) inside the locked container.
4. **Instant Removal**: 
   - Clicking the **✕** close button instantly removes the model and releases all graphic memory.

---

## Performance & Optimization Engineering

Operating multiple 3D viewports simultaneously on low-end devices presents severe bottlenecks in GPU fills, native heap fragmentation, and CPU layout overhead. The following optimization strategies are implemented:

### 1. Automatic Renderer Lifecycle Synchronization
Native Filament rendering pipelines consume background threads and GPU clocks. We bind each `SceneView` instance directly to the Activity's lifecycle:
```kotlin
(context as? LifecycleOwner)?.lifecycle?.let {
    sceneView.lifecycle = it
}
```
When the app is backgrounded, rendering loops are immediately paused, preventing battery drain and background memory pressure.

### 2. Immediate Native Resource Reclamation
Standard JVM garbage collection does not understand native allocations. Simply removing views from the layout leaves textures and geometry buffers allocated in native memory. When a container is closed, we call a custom `.destroy()` routine:
```kotlin
fun destroy() {
    // 1. Detach nodes from the parent scene graph
    modelNode?.parent = null
    modelNode = null
    // 2. Explicitly destroy the Filament scene view renderer
    sceneView.destroy()
}
```
This forces Filament to immediately free graphic buffers, avoiding Out of Memory (OOM) crashes when adding/removing multiple objects.

### 3. Hardware-Accelerated Translations (CPU Bypass)
Moving model containers changes their screen positions. To avoid triggering expensive measure and layout passes down the entire View hierarchy, we set the view's translation properties directly:
```kotlin
x = viewX + deltaX
y = viewY + deltaY
```
Android's Hardware Composer (HWUI) executes these offsets directly on the GPU render thread, guaranteeing a locked 60+ FPS animation cycle during movement.

### 4. Bounded Resizing & Aspect Ratio Clamping
Resizing views does require calling `requestLayout()`. To limit jank:
- Layout widths/heights are clamped between `120dp` (minimum) and `450dp` (maximum) to prevent memory allocation spikes for massive render buffers.
- Resizing forces a 1:1 aspect ratio (`width == height`). Keeping a uniform viewport scale prevents asset distortion and optimizes Filament's orthographic projection calculations.

### 5. Thread-Safe Filament Asset Instantiation
Filament requires all direct calls to its native model loader to be executed on a thread adopted by the engine's `JobSystem`, which defaults to the UI Thread (Main). Initializing the 3D model node on `Dispatchers.Main` prevents native crashes (`reason: This thread has not been adopted` Filament panic):
```kotlin
coroutineScope.launch(Dispatchers.Main) {
    val modelInstance = sceneView.modelLoader.createModelInstance(assetPath)
    val node = ModelNode(modelInstance = modelInstance)
}
```
Because the pre-bundled assets are lightweight (ranging from 1.8 MB to 4.2 MB), loading them synchronously on the Main thread is extremely fast and executes without any visible UI jank or ANR alerts.

---

## How to Build the Project

### Prerequisites
- Android Studio Koala / Ladybug or command-line build tools.
- JDK 11 or higher configured in your environment.
- Android SDK API 24+ installed.

### Build via Gradle Command Line
Navigate to the project directory and run:

**On Windows:**
```powershell
.\gradlew.bat assembleDebug
```

**On macOS / Linux:**
```bash
./gradlew assembleDebug
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Trade-offs Made

1. **Main Thread Model Loading for Small Assets**: Filament requires asset instantiations to run on a thread adopted by its `JobSystem` (typically the UI/Main thread). For the pre-bundled assets (all < 4.5 MB), we initialized models directly on `Dispatchers.Main`. This kept model loading logic simple and synchronous but would temporarily block the UI thread if loading much larger (> 15 MB) models.
2. **Fixed 1:1 Aspect Ratio (Square Containers)**: Clamping containers to a strict square aspect ratio simplified the math for layout offsets, bounding checks, and custom pinch-to-scale. However, it prevents displaying models in wider or taller rectangular viewports.
3. **Translational Coordinate Offsets instead of Window Managers**: We translate standard Android view containers inside a single root RelativeLayout. This keeps layout logic fast and bypasses the overhead of standard window manager transitions, though it restricts the containers to the boundaries of the host activity.

---

## What to Improve with More Time

1. **True Background Asset Parsing**: Offload GLB file reading and parsing to a background thread, passing the constructed binary data or scene-graph components to the UI thread for instantaneous rendering without any frame drops.
2. **Workspace State Persistence**: Use Room or SharedPreferences to serialize and store active containers (their positions, zoom scales, active model IDs, and modes) so the workspace state persists between application launches.
3. **Advanced Camera & Lighting Controls**: Add tools to modify Image-Based Lighting (IBL) intensity, environment maps, directional light positions, and shadow map resolutions directly from a dashboard.
4. **Enhanced Gesture Soft-Limits**: Implement scale and rotation dampening so the 3D model doesn't rotate too fast or zoom in/out past reasonable visual limits.

---

## Known Bugs & Limitations

1. **Simultaneous Render/Memory Thresholds**: Although resource lifecycle reclaiming is immediate upon closing containers, running more than 3 or 4 simultaneous model instances on older/low-end devices (e.g. less than 3 GB RAM or older OpenGL ES engines) can trigger system-level graphic memory limits or thermal throttling.
2. **No Remote Assets**: The current application only supports loading 3D assets bundled inside the application assets directory. Direct loading via HTTPS remote URLs is not implemented.
3. **Overlay Z-Indexing**: When dragging containers, they do not automatically bring themselves to the front layer. The ordering is determined by the creation index in the layout, which could occasionally lead to a container being dragged underneath another unless dynamically reordered.

## A video walkthrough of the App drive link
1.https://drive.google.com/file/d/1eZcyjXr8FKWzAIsm6zPKyseAY9tXJQqF/view?usp=drive_link
