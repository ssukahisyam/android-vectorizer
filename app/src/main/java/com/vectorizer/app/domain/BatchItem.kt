package com.vectorizer.app.domain

import android.net.Uri
import com.vectorizer.app.core.model.VectorPreset

enum class BatchStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED
}

data class BatchItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val preset: VectorPreset = VectorPreset.LOGO_FLAT_GRAPHIC,
    val status: BatchStatus = BatchStatus.PENDING,
    val svgContent: String? = null,
    val svgFilePath: String? = null,
    val pathCount: Int = 0,
    val executionTimeMs: Long = 0,
    val errorMessage: String? = null
)
