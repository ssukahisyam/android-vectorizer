package com.vectorizer.app.core.model

data class VectorResult(
    val svgContent: String,
    val pathCount: Int,
    val colorCount: Int,
    val executionTimeMs: Long,
    val width: Int,
    val height: Int
)
