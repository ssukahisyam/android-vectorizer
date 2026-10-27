package com.vectorizer.app.core.model

data class VectorizerOptions(
    var numberOfColors: Int = 16,
    var pathOmit: Float = 8.0f,
    var lineThreshold: Float = 1.0f,
    var quadThreshold: Float = 1.0f,
    var blurRadius: Float = 0.0f,
    var blurDelta: Float = 20.0f,
    var colorQuantCycles: Int = 3,
    var scale: Float = 1.0f,
    var roundCoords: Int = 1,
    var viewBox: Boolean = true,
    var binarize: Boolean = false,
    var binarizeThreshold: Int = -1, // -1 for auto Otsu
    var isPixelArtMode: Boolean = false,
    var removeWhiteBackground: Boolean = false,
    var maxDimension: Int = 1280 // Memory guard downscale cap
)
