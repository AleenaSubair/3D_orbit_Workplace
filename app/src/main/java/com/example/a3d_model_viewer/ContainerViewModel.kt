package com.example.a3d_model_viewer

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import io.github.sceneview.SceneView
import io.github.sceneview.node.ModelNode
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContainerViewModel(
    context: Context,
    private val sceneModel: SceneModel,
    private val onCloseClicked: () -> Unit,
    private val coroutineScope: CoroutineScope
): FrameLayout(context) {

    private lateinit var sceneView: SceneView
    private lateinit var interactBtn: Button
    private lateinit var closeBtn: Button
    private lateinit var resizeHandle: ImageView
    private lateinit var progressBar: ProgressBar
    
    private var modelNode: ModelNode? = null

    // Touch gesture tracking variables
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var viewX = 0f
    private var viewY = 0f
    
    private var isDragging = false
    private var isResizingFromCorner = false
    private var isPinching = false

    private var initialDistance = 0f
    private var initialWidth = 0
    private var initialHeight = 0
    private var initialModelScaleX = 1.0f

    private var currentYaw = 0f
    private var currentPitch = 0f

    init {
        // Apply starting dimensions (e.g. 600x600 px)
        layoutParams = LayoutParams(
            sceneModel.width,
            sceneModel.height
        )

        x = sceneModel.x
        y = sceneModel.y
        
        setupUI()
        loadModels()
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun setupUI() {
        // Apply glassmorphic background & rounded corners
        setBackgroundResource(R.drawable.card_background)
        clipToOutline = true

        // 3D Scene View
        sceneView = SceneView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            // Bind lifecycle if context is a LifecycleOwner for automatic rendering pause/resume
            (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.let {
                this.lifecycle = it
            }
        }
        addView(sceneView)

        // Progress spinner for loading feedback
        progressBar = ProgressBar(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).also {
                it.gravity = Gravity.CENTER
            }
            visibility = View.VISIBLE
        }
        addView(progressBar)

        // Close button: styled circular red button
        closeBtn = Button(context).apply {
            text = "✕"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = context.getDrawable(R.drawable.close_button_bg)
            layoutParams = LayoutParams(dpToPx(28), dpToPx(28)).also {
                it.gravity = Gravity.TOP or Gravity.END
                it.setMargins(0, dpToPx(8), dpToPx(8), 0)
            }
        }
        addView(closeBtn)

        // Interact button: pill capsule style toggler
        interactBtn = Button(context).apply {
            text = "Interact"
            textSize = 10f
            setTextColor(Color.WHITE)
            background = context.getDrawable(R.drawable.interact_inactive_bg)
            // horizontal padding 12dp, vertical padding 4dp
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(32)).also {
                it.gravity = Gravity.TOP or Gravity.START
                it.setMargins(dpToPx(8), dpToPx(8), 0, 0)
            }
        }
        addView(interactBtn)

        // Resize corner handle: bottom-right diagonal handle
        resizeHandle = ImageView(context).apply {
            setImageResource(R.drawable.ic_resize_handle)
            layoutParams = LayoutParams(dpToPx(20), dpToPx(20)).also {
                it.gravity = Gravity.BOTTOM or Gravity.END
                it.setMargins(0, 0, dpToPx(4), dpToPx(4))
            }
        }
        addView(resizeHandle)

        closeBtn.setOnClickListener {
            onCloseClicked()
        }

        interactBtn.setOnClickListener {
            sceneModel.isInteractive = !sceneModel.isInteractive
            if (sceneModel.isInteractive) {
                interactBtn.text = "Manipulating"
                interactBtn.setTextColor(Color.parseColor("#0A0E17"))
                interactBtn.setBackgroundResource(R.drawable.interact_active_bg)
            } else {
                interactBtn.text = "Interact"
                interactBtn.setTextColor(Color.WHITE)
                interactBtn.setBackgroundResource(R.drawable.interact_inactive_bg)
            }
        }
    }

    private fun loadModels() {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                progressBar.visibility = View.VISIBLE

                val modelInstance = sceneView.modelLoader.createModelInstance(
                    assetFileLocation = sceneModel.assetPath
                )

                val node = ModelNode(modelInstance = modelInstance).apply {
                    // Position slightly offset backwards to fit nicely in Filament workspace camera
                    scale = Scale(1.5f, 1.5f, 1.5f)
                }
                this@ContainerViewModel.modelNode = node
                sceneView.addChildNode(node)
                progressBar.visibility = View.GONE
            } catch (e: Exception) {
                e.printStackTrace()
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun getDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        ev ?: return false

        // Buttons must directly handle their own touch events
        if (isTouchOnView(interactBtn, ev) || isTouchOnView(closeBtn, ev)) {
            return false
        }

        // If interaction mode is active, intercept touch to manipulate the 3D model node
        if (sceneModel.isInteractive) {
            return true
        }

        // In normal mode, touch on the resize handle should not be intercepted as a drag-move
        if (isTouchOnView(resizeHandle, ev)) {
            return true
        }

        return true
    }

    private fun isTouchOnView(view: View, ev: MotionEvent): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val x = ev.rawX
        val y = ev.rawY
        return x >= location[0] && x <= location[0] + view.width &&
                y >= location[1] && y <= location[1] + view.height
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointerCount = event.pointerCount

        if (sceneModel.isInteractive) {
            // --- INTERACTION MODE ---
            if (pointerCount == 2) {
                // Two-finger pinch zoom 3D model node
                when (event.actionMasked) {
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        initialDistance = getDistance(event)
                        initialModelScaleX = modelNode?.scale?.x ?: 1.5f
                        isPinching = true
                        isDragging = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isPinching && initialDistance > 10f) {
                            val currentDist = getDistance(event)
                            val factor = currentDist / initialDistance
                            val newScale = (initialModelScaleX * factor).coerceIn(0.2f, 6.0f)
                            modelNode?.scale = Scale(newScale, newScale, newScale)
                        }
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        isPinching = false
                    }
                }
            } else if (pointerCount == 1 && !isPinching) {
                // One-finger drag rotate 3D model node
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                        currentYaw = modelNode?.rotation?.y ?: 0f
                        currentPitch = modelNode?.rotation?.x ?: 0f
                        isDragging = true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDragging) {
                            val dx = event.x - lastTouchX
                            val dy = event.y - lastTouchY
                            lastTouchX = event.x
                            lastTouchY = event.y

                            // Update rotation angles based on sensitivity
                            currentYaw += dx * 0.4f
                            currentPitch += dy * 0.4f
                            currentPitch = currentPitch.coerceIn(-85f, 85f) // Prevent vertical flipping

                            modelNode?.rotation = Rotation(currentPitch, currentYaw, 0f)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                    }
                }
            }
        } else {
            // --- NORMAL MODE ---
            if (pointerCount == 2) {
                // Two-finger pinch scale container size
                when (event.actionMasked) {
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        initialDistance = getDistance(event)
                        initialWidth = layoutParams.width
                        initialHeight = layoutParams.height
                        isPinching = true
                        isDragging = false
                        isResizingFromCorner = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isPinching && initialDistance > 10f) {
                            val currentDist = getDistance(event)
                            val factor = currentDist / initialDistance
                            
                            // Adjust container size maintaining 1:1 aspect ratio
                            val newSize = (initialWidth * factor).toInt().coerceIn(dpToPx(120), dpToPx(450))
                            val params = layoutParams
                            params.width = newSize
                            params.height = newSize
                            layoutParams = params
                        }
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        isPinching = false
                    }
                }
            } else if (pointerCount == 1 && !isPinching) {
                // One-finger move container OR corner drag resize
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                        viewX = x
                        viewY = y

                        // Check if touch is near the bottom-right corner resize handle (within 80px target)
                        val touchXInView = event.x
                        val touchYInView = event.y
                        if (touchXInView >= (width - dpToPx(40)) && touchYInView >= (height - dpToPx(40))) {
                            isResizingFromCorner = true
                            initialWidth = layoutParams.width
                            initialHeight = layoutParams.height
                            isDragging = false
                        } else {
                            isDragging = true
                            isResizingFromCorner = false
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isResizingFromCorner) {
                            val deltaX = event.rawX - lastTouchX
                            // Keep 1:1 square ratio
                            val newSize = (initialWidth + deltaX).toInt().coerceIn(dpToPx(120), dpToPx(450))
                            val params = layoutParams
                            params.width = newSize
                            params.height = newSize
                            layoutParams = params
                        } else if (isDragging) {
                            val deltaX = event.rawX - lastTouchX
                            val deltaY = event.rawY - lastTouchY
                            x = viewX + deltaX
                            y = viewY + deltaY
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        isResizingFromCorner = false
                        
                        sceneModel.x = x
                        sceneModel.y = y
                        sceneModel.width = layoutParams.width
                        sceneModel.height = layoutParams.height
                    }
                }
            }
        }
        return true
    }

    // Expose lifecycle hooks for performance optimization
    fun pauseRendering() {
        // Automatically handled via lifecycle binding
    }

    fun resumeRendering() {
        // Automatically handled via lifecycle binding
    }

    fun destroy() {
        try {
            // Remove view from parent
            val parentView = parent
            if (parentView is android.view.ViewGroup) {
                parentView.removeView(this)
            }
            // Release ModelNode
            modelNode?.let { node ->
                node.parent = null
            }
            modelNode = null
            // Reclaim Filament resources
            sceneView.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}


