package com.vectorizer.app.domain

import android.content.Context
import android.net.Uri
import com.vectorizer.app.core.adapter.AndroidBitmapAdapter
import com.vectorizer.app.core.engine.ImageTracerEngine
import com.vectorizer.app.core.model.VectorPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

object BatchQueueManager {

    private val _items = MutableStateFlow<List<BatchItem>>(emptyList())
    val items: StateFlow<List<BatchItem>> = _items.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _globalPreset = MutableStateFlow(VectorPreset.LOGO_FLAT_GRAPHIC)
    val globalPreset: StateFlow<VectorPreset> = _globalPreset.asStateFlow()

    private var activeJob: Job? = null
    private val engine = ImageTracerEngine()

    fun setGlobalPreset(preset: VectorPreset) {
        _globalPreset.value = preset
        // Update any pending items to this preset
        _items.update { list ->
            list.map { item ->
                if (item.status == BatchStatus.PENDING) item.copy(preset = preset) else item
            }
        }
    }

    fun addUris(uris: List<Uri>, context: Context) {
        val newItems = uris.map { uri ->
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "image_${System.currentTimeMillis()}.png"
            BatchItem(
                id = UUID.randomUUID().toString(),
                uri = uri,
                name = fileName,
                preset = _globalPreset.value,
                status = BatchStatus.PENDING
            )
        }
        _items.update { it + newItems }
    }

    fun removeItem(id: String) {
        _items.update { list -> list.filter { it.id != id } }
    }

    fun clearAll() {
        cancelProcessing()
        _items.value = emptyList()
    }

    fun startProcessing(context: Context, scope: CoroutineScope) {
        if (_isProcessing.value) return
        _isProcessing.value = true

        activeJob = scope.launch(Dispatchers.Default) {
            // Limit concurrency to 2 parallel tasks to protect CPU & memory
            val semaphore = Semaphore(2)

            val currentItems = _items.value.filter { it.status == BatchStatus.PENDING || it.status == BatchStatus.FAILED }

            val jobs = currentItems.map { item ->
                launch {
                    semaphore.withPermit {
                        processSingleItem(context, item)
                    }
                }
            }

            jobs.forEach { it.join() }
            _isProcessing.value = false
        }
    }

    private fun processSingleItem(context: Context, item: BatchItem) {
        updateItemStatus(item.id, BatchStatus.PROCESSING)

        try {
            // 1. Load image safely
            val imageData = AndroidBitmapAdapter.loadFromUri(context, item.uri)
                ?: throw Exception("Gagal membaca berkas gambar")

            // 2. Vectorize using preset
            val options = item.preset.createOptions()
            val result = engine.vectorize(imageData, options)

            // 3. Save to internal cache
            val outFileName = "${item.id}_${item.preset.name.lowercase()}.svg"
            val file = AndroidBitmapAdapter.saveSvgToCache(context, result.svgContent, outFileName)

            // 4. Update success status
            _items.update { list ->
                list.map {
                    if (it.id == item.id) {
                        it.copy(
                            status = BatchStatus.SUCCESS,
                            svgContent = result.svgContent,
                            svgFilePath = file.absolutePath,
                            pathCount = result.pathCount,
                            executionTimeMs = result.executionTimeMs
                        )
                    } else it
                }
            }
        } catch (e: Exception) {
            _items.update { list ->
                list.map {
                    if (it.id == item.id) {
                        it.copy(
                            status = BatchStatus.FAILED,
                            errorMessage = e.message ?: "Terjadi kesalahan saat tracing"
                        )
                    } else it
                }
            }
        }
    }

    fun cancelProcessing() {
        activeJob?.cancel()
        activeJob = null
        _isProcessing.value = false
        _items.update { list ->
            list.map {
                if (it.status == BatchStatus.PROCESSING) it.copy(status = BatchStatus.PENDING) else it
            }
        }
    }

    private fun updateItemStatus(id: String, status: BatchStatus) {
        _items.update { list ->
            list.map { if (it.id == id) it.copy(status = status) else it }
        }
    }
}
