package com.vectorizer.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vectorizer.app.core.adapter.AndroidBitmapAdapter
import com.vectorizer.app.core.engine.ImageTracerEngine
import com.vectorizer.app.core.model.VectorPreset
import com.vectorizer.app.domain.BatchItem
import com.vectorizer.app.ui.components.BeforeAfterSlider
import com.vectorizer.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    item: BatchItem,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activePreset by remember { mutableStateOf(item.preset) }
    var currentSvgFile by remember { mutableStateOf(item.svgFilePath?.let { File(it) }) }
    var currentPathCount by remember { mutableIntStateOf(item.pathCount) }
    var currentDuration by remember { mutableLongStateOf(item.executionTimeMs) }
    var isRetracing by remember { mutableStateOf(false) }

    fun retraceWithPreset(preset: VectorPreset) {
        activePreset = preset
        isRetracing = true

        coroutineScope.launch {
            val result = withContext(Dispatchers.Default) {
                try {
                    val imgData = AndroidBitmapAdapter.loadFromUri(context, item.uri)
                        ?: return@withContext null
                    val engine = ImageTracerEngine()
                    val res = engine.vectorize(imgData, preset.createOptions())
                    val file = AndroidBitmapAdapter.saveSvgToCache(
                        context,
                        res.svgContent,
                        "${item.id}_${preset.name.lowercase()}.svg"
                    )
                    Pair(res, file)
                } catch (e: Exception) {
                    null
                }
            }

            if (result != null) {
                currentSvgFile = result.second
                currentPathCount = result.first.pathCount
                currentDuration = result.first.executionTimeMs
            }
            isRetracing = false
        }
    }

    // Trigger initial trace if not yet completed
    LaunchedEffect(Unit) {
        if (currentSvgFile == null || !currentSvgFile!!.exists()) {
            retraceWithPreset(activePreset)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = item.name,
                        maxLines = 1,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            currentSvgFile?.let { file ->
                                val svgText = file.readText()
                                val outName = "${item.name.substringBeforeLast('.')}_${activePreset.name.lowercase()}.svg"
                                AndroidBitmapAdapter.exportToPublicStorage(context, svgText, outName)
                                Toast.makeText(context, "SVG disimpan ke Downloads/ImageVectorizer", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = currentSvgFile != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Simpan SVG",
                            tint = if (currentSvgFile != null) PrimaryBlueLight else Slate400
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900,
                    titleContentColor = Slate100,
                    navigationIconContentColor = Slate100
                )
            )
        },
        containerColor = Slate900
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 1. Interactive Before/After Split Comparison Slider
            BeforeAfterSlider(
                rasterUri = item.uri,
                vectorSvgFile = currentSvgFile,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Metrics Bar
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricItem(label = "WAKTU", value = if (isRetracing) "..." else "${currentDuration}ms")
                    MetricItem(label = "PATHS", value = if (isRetracing) "..." else "$currentPathCount")
                    MetricItem(
                        label = "UKURAN SVG",
                        value = if (isRetracing || currentSvgFile == null) "..." else String.format("%.1f KB", currentSvgFile!!.length() / 1024.0)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Mode Switcher
            Text(
                text = "UJI COBA MODE LAIN",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Slate400
            )

            Spacer(modifier = Modifier.height(8.dp))

            VectorPreset.values().forEach { preset ->
                val isSelected = preset == activePreset
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Slate700 else Slate800
                    ),
                    shape = RoundedCornerShape(10.dp),
                    onClick = { if (!isRetracing) retraceWithPreset(preset) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { if (!isRetracing) retraceWithPreset(preset) },
                            colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlueLight)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = preset.displayName,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate100,
                                fontSize = 14.sp
                            )
                            Text(
                                text = preset.description,
                                fontSize = 11.sp,
                                color = Slate400
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 10.sp, color = Slate400, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 15.sp, color = Slate100, fontWeight = FontWeight.SemiBold)
    }
}
