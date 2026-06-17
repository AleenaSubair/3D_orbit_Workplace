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
