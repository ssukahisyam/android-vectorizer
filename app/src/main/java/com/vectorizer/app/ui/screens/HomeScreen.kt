package com.vectorizer.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vectorizer.app.core.adapter.AndroidBitmapAdapter
import com.vectorizer.app.core.model.VectorPreset
import com.vectorizer.app.domain.BatchItem
import com.vectorizer.app.domain.BatchQueueManager
import com.vectorizer.app.domain.BatchStatus
import com.vectorizer.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCompare: (BatchItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val items by BatchQueueManager.items.collectAsState()
    val isProcessing by BatchQueueManager.isProcessing.collectAsState()
    val currentPreset by BatchQueueManager.globalPreset.collectAsState()

    // Multi-photo picker launcher (Modern Android Photo Picker)
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 100)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            BatchQueueManager.addUris(uris, context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = PrimaryBlueLight,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Image Vectorizer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { BatchQueueManager.clearAll() }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Hapus Semua",
                                tint = Slate400
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900,
                    titleContentColor = Slate100
                )
            )
        },
        bottomBar = {
            if (items.isNotEmpty()) {
                BottomActionBar(
                    items = items,
                    isProcessing = isProcessing,
                    onStartBatch = { BatchQueueManager.startProcessing(context, coroutineScope) },
                    onCancelBatch = { BatchQueueManager.cancelProcessing() },
                    onExportAll = {
                        var exportCount = 0
                        items.filter { it.status == BatchStatus.SUCCESS && it.svgContent != null }.forEach { item ->
                            val outName = "${item.name.substringBeforeLast('.')}_vector.svg"
                            AndroidBitmapAdapter.exportToPublicStorage(context, item.svgContent!!, outName)
                            exportCount++
                        }
                        Toast.makeText(context, "Berhasil mengekspor $exportCount file ke folder Downloads/ImageVectorizer", Toast.LENGTH_LONG).show()
                    }
                )
            }
        },
        containerColor = Slate900
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Preset Selector Carousel
            PresetSelectorSection(
                selectedPreset = currentPreset,
                onSelectPreset = { BatchQueueManager.setGlobalPreset(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Intake Card (Upload / Picker)
            IntakeCard(
                itemCount = items.size,
                onPickImages = {
                    pickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Batch Items Queue List
            if (items.isEmpty()) {
                EmptyStateView(
                    onPickImages = {
                        pickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        BatchItemCard(
                            item = item,
                            onClick = { onNavigateToCompare(item) },
                            onDelete = { BatchQueueManager.removeItem(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PresetSelectorSection(
    selectedPreset: VectorPreset,
    onSelectPreset: (VectorPreset) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "PILIH MODE VEKTORISASI",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Slate400,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(VectorPreset.values()) { preset ->
                val isSelected = preset == selectedPreset
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(preset.displayName, fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryBlue,
                        selectedLabelColor = Color.White,
                        containerColor = Slate800,
                        labelColor = Slate100
                    )
                )
            }
        }

        // Active preset description hint
        Text(
            text = selectedPreset.description,
            fontSize = 12.sp,
            color = PrimaryBlueLight,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun IntakeCard(
    itemCount: Int,
    onPickImages: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (itemCount > 0) "$itemCount Gambar Terpilih" else "Mulai Tambahkan Gambar",
                    fontWeight = FontWeight.SemiBold,
                    color = Slate100,
                    fontSize = 15.sp
                )
                Text(
                    text = "Bisa memilih banyak gambar sekaligus (Batch)",
                    fontSize = 12.sp,
                    color = Slate400
                )
            }

            Button(
                onClick = onPickImages,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pilih Foto", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun BatchItemCard(
    item: BatchItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate700)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    maxLines = 1,
                    fontWeight = FontWeight.Medium,
                    color = Slate100,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (item.status) {
                        BatchStatus.PENDING -> {
                            StatusBadge(text = "Menunggu", color = Slate400)
                        }
                        BatchStatus.PROCESSING -> {
                            StatusBadge(text = "Memproses...", color = AccentAmber)
                        }
                        BatchStatus.SUCCESS -> {
                            StatusBadge(
                                text = "Selesai (${item.pathCount} paths • ${item.executionTimeMs}ms)",
                                color = AccentGreen
                            )
                        }
                        BatchStatus.FAILED -> {
                            StatusBadge(text = "Gagal", color = AccentRed)
                        }
                    }
                }
            }

            // Delete action
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Hapus",
                    tint = Slate400,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun BottomActionBar(
    items: List<BatchItem>,
    isProcessing: Boolean,
    onStartBatch: () -> Unit,
    onCancelBatch: () -> Unit,
    onExportAll: () -> Unit
) {
    val successCount = items.count { it.status == BatchStatus.SUCCESS }
    val pendingCount = items.count { it.status == BatchStatus.PENDING || it.status == BatchStatus.FAILED }

    Surface(
        color = Slate800,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isProcessing) {
                Button(
                    onClick = onCancelBatch,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Batalkan")
                }
            } else {
                Button(
                    onClick = onStartBatch,
                    enabled = pendingCount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Proses ($pendingCount)")
                }
            }

            if (successCount > 0 && !isProcessing) {
                OutlinedButton(
                    onClick = onExportAll,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export ($successCount)")
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(onPickImages: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Slate800,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Collections,
                    contentDescription = null,
                    tint = PrimaryBlueLight,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Belum Ada Gambar yang Dipilih",
            fontWeight = FontWeight.Bold,
            color = Slate100,
            fontSize = 17.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Pilih satu atau beberapa gambar sekaligus untuk mengubahnya menjadi vektor SVG bersih tanpa garis kusut.",
            color = Slate400,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onPickImages,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Pilih Gambar Sekarang")
        }
    }
}
