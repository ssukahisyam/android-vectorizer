package com.vectorizer.app.core.adapter

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.vectorizer.app.core.model.ImageData
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

object AndroidBitmapAdapter {

    /**
     * Converts an Android Bitmap into platform-agnostic ImageData (RGBA byte buffer).
     */
    fun fromBitmap(bitmap: Bitmap): ImageData {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val data = ByteArray(width * height * 4)
        for (i in pixels.indices) {
            val color = pixels[i]
            val a = (color shr 24) and 0xFF
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            val idx = i * 4
            data[idx] = r.toByte()
            data[idx + 1] = g.toByte()
            data[idx + 2] = b.toByte()
            data[idx + 3] = a.toByte()
        }

        return ImageData(width, height, data)
    }

    /**
     * Loads an image safely from an Android content URI, using inSampleSize to prevent OOM
     * if the original image is enormous (e.g., 48MP camera photo).
     */
    fun loadFromUri(context: Context, uri: Uri, maxDimension: Int = 1920): ImageData? {
        val contentResolver = context.contentResolver

        // 1. Decode bounds only
        var origW = 0
        var origH = 0
        contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(stream, null, options)
            origW = options.outWidth
            origH = options.outHeight
        } ?: return null

        if (origW <= 0 || origH <= 0) return null

        // 2. Compute sample size
        var sampleSize = 1
        val maxEdge = max(origW, origH)
        while ((maxEdge / sampleSize) > maxDimension) {
            sampleSize *= 2
        }

        // 3. Decode scaled bitmap
        val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        return fromBitmap(bitmap)
    }

    /**
     * Saves an SVG string to the app's internal cache directory.
     */
    fun saveSvgToCache(context: Context, svgContent: String, fileName: String): File {
        val dir = File(context.cacheDir, "vector_outputs").apply { if (!exists()) mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            out.write(svgContent.toByteArray(Charsets.UTF_8))
        }
        return file
    }

    /**
     * Exports an SVG file to the device's public Downloads / Documents directory via MediaStore (Scoped Storage).
     */
    fun exportToPublicStorage(context: Context, svgContent: String, displayName: String): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/svg+xml")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ImageVectorizer")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val uri = resolver.insert(collection, contentValues) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            out.write(svgContent.toByteArray(Charsets.UTF_8))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        return uri
    }
}
