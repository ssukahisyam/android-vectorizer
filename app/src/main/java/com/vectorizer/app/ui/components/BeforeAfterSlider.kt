package com.vectorizer.app.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vectorizer.app.ui.theme.PrimaryBlueLight
import com.vectorizer.app.ui.theme.Slate900
import java.io.File
import kotlin.math.roundToInt

@Composable
fun BeforeAfterSlider(
    rasterUri: Uri,
    vectorSvgFile: File?,
    modifier: Modifier = Modifier
) {
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E293B))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val splitPositionX = widthPx * splitFraction
        val density = LocalDensity.current

        // 1. Right Layer: Vectorized SVG
        Box(modifier = Modifier.fillMaxSize()) {
            if (vectorSvgFile != null && vectorSvgFile.exists()) {
                AsyncImage(
                    model = vectorSvgFile,
                    contentDescription = "Vector SVG",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Vektor sedang diproses...",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }

            // Badge: Vektor
            Surface(
                color = PrimaryBlueLight.copy(alpha = 0.85f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Text(
                    text = "VEKTOR (SVG)",
                    color = Slate900,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // 2. Left Layer: Original Raster (Clipped to split fraction)
        val leftClipShape = object : Shape {
            override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val path = Path().apply {
                    reset()
                    moveTo(0f, 0f)
                    lineTo(size.width * splitFraction, 0f)
                    lineTo(size.width * splitFraction, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                return Outline.Generic(path)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(leftClipShape)
        ) {
            AsyncImage(
                model = rasterUri,
                contentDescription = "Original Raster",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // Badge: Asli
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = "ASLI (RASTER)",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // 3. Divider Line
        val splitDp = with(density) { splitPositionX.toDp() }
        Box(
            modifier = Modifier
                .offset { IntOffset(splitPositionX.roundToInt(), 0) }
                .width(2.dp)
                .fillMaxSize()
                .background(Color.White)
        )

        // 4. Center Drag Handle
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier
                .offset {
                    IntOffset(
                        (splitPositionX - with(density) { 20.dp.toPx() }).roundToInt(),
                        (constraints.maxHeight / 2 - with(density) { 20.dp.toPx() }).roundToInt()
                    )
                }
                .size(40.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newPos = (splitPositionX + dragAmount.x).coerceIn(0f, widthPx)
                        splitFraction = newPos / widthPx
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Drag Divider",
                    tint = Slate900,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
