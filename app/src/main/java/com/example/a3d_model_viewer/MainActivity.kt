package com.example.a3d_model_viewer

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope

class MainActivity : AppCompatActivity() {

    private lateinit var modelCanvas: FrameLayout
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var dashboardPanel: LinearLayout
    private lateinit var btnToggleDashboard: Button
    private lateinit var btnClearCanvas: Button
    private lateinit var btnEmptyStateAdd: Button

    private val sceneModels = mutableListOf<SceneModel>()
    private val containerViews = mutableListOf<ContainerViewModel>()

    private val modelAvailable = listOf(
        "models/cigarette.glb",
        "models/dragoon_.glb", // Fixed typo (was dragon.glb)
        "models/pokemon_masters_cap.glb",
        "models/shoes.glb",
        "models/steampunk_glasses.glb"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        modelCanvas = findViewById(R.id.modelCanvas)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        dashboardPanel = findViewById(R.id.dashboardPanel)
        btnToggleDashboard = findViewById(R.id.btnToggleDashboard)
        btnClearCanvas = findViewById(R.id.btnClearCanvas)
        btnEmptyStateAdd = findViewById(R.id.btnEmptyStateAdd)

        // Toggle library panel
        btnToggleDashboard.setOnClickListener {
            if (dashboardPanel.visibility == View.VISIBLE) {
                dashboardPanel.visibility = View.GONE
            } else {
                dashboardPanel.visibility = View.VISIBLE
            }
        }

        // Empty state CTA
        btnEmptyStateAdd.setOnClickListener {
            dashboardPanel.visibility = View.VISIBLE
        }

        // Clear canvas
        btnClearCanvas.setOnClickListener {
            clearWorkspace()
        }

        // Bind dashboard card add buttons
        findViewById<Button>(R.id.btnAddCigarette).setOnClickListener { addModelToScene(modelAvailable[0]) }
        findViewById<Button>(R.id.btnAddDragoon).setOnClickListener { addModelToScene(modelAvailable[1]) }
        findViewById<Button>(R.id.btnAddPokemonCap).setOnClickListener { addModelToScene(modelAvailable[2]) }
        findViewById<Button>(R.id.btnAddShoes).setOnClickListener { addModelToScene(modelAvailable[3]) }
        findViewById<Button>(R.id.btnAddGlasses).setOnClickListener { addModelToScene(modelAvailable[4]) }

        updateEmptyStateVisibility()
    }

    private fun addModelToScene(assetPath: String) {
        // Hide empty state once a model is added
        emptyStateLayout.visibility = View.GONE

        // Make model containers big enough to see (600x600 pixels starting size)
        val newSceneModel = SceneModel(
            assetPath = assetPath,
            x = (50..300).random().toFloat(),
            y = (150..400).random().toFloat(),
            width = 600,
            height = 600
        )

        val newContainer = ContainerViewModel(
            context = this,
            sceneModel = newSceneModel,
            onCloseClicked = {
                removeModel(newSceneModel)
            },
            coroutineScope = lifecycleScope
        )

        modelCanvas.addView(newContainer)
        sceneModels.add(newSceneModel)
        containerViews.add(newContainer)
        updateEmptyStateVisibility()
    }

    private fun removeModel(model: SceneModel) {
        val index = sceneModels.indexOf(model)
        if (index != -1) {
            val container = containerViews[index]
            modelCanvas.removeView(container)
            container.destroy() // Custom cleanup method to release memory immediately
            containerViews.removeAt(index)
            sceneModels.removeAt(index)
        }
        updateEmptyStateVisibility()
    }

    private fun clearWorkspace() {
        containerViews.forEach { container ->
            modelCanvas.removeView(container)
            container.destroy()
        }
        containerViews.clear()
        sceneModels.clear()
        updateEmptyStateVisibility()
    }

    private fun updateEmptyStateVisibility() {
        if (sceneModels.isEmpty()) {
            emptyStateLayout.visibility = View.VISIBLE
        } else {
            emptyStateLayout.visibility = View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
        // Pause rendering for all active scene views to save battery and GPU cycles
        containerViews.forEach { it.pauseRendering() }
    }

    override fun onResume() {
        super.onResume();
        // Resume rendering for all active scene views
        containerViews.forEach { it.resumeRendering() }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release all Filament graphic contexts
        clearWorkspace()
    }
}