package com.example.a3d_model_viewer

import java.util.UUID

data class SceneModel(
    val id: String = UUID.randomUUID().toString(),
    val assetPath: String,
    var x: Float = 100f,
    var y: Float = 100f,
    var width: Int = 400,
    var height: Int = 400,
    var isInteractive: Boolean = false,
)
