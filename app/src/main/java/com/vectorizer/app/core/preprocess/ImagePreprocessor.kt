package com.vectorizer.app.core.preprocess

import com.vectorizer.app.core.model.ImageData
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImagePreprocessor {

    /**
     * Downscales the image if either dimension exceeds maxDimension, preserving aspect ratio.
     * Prevents high memory consumption and Out-Of-Memory errors on mobile devices during batch runs.
     */
    fun downscaleIfNeeded(src: ImageData, maxDimension: Int): ImageData {
        if (maxDimension <= 0 || (src.width <= maxDimension && src.height <= maxDimension)) {
            return src
        }

        val scale = min(maxDimension.toFloat() / src.width, maxDimension.toFloat() / src.height)
        val targetW = max(1, (src.width * scale).roundToInt())
        val targetH = max(1, (src.height * scale).roundToInt())

        val destData = ByteArray(targetW * targetH * 4)

        for (y in 0 until targetH) {
            val srcY = y / scale
            val y0 = srcY.toInt()
            val y1 = min(y0 + 1, src.height - 1)
            val dy = srcY - y0

            for (x in 0 until targetW) {
                val srcX = x / scale
                val x0 = srcX.toInt()
                val x1 = min(x0 + 1, src.width - 1)
                val dx = srcX - x0

                val destIdx = (y * targetW + x) * 4

                for (c in 0 until 4) {
                    val c00 = src.data[(y0 * src.width + x0) * 4 + c].toInt() and 0xFF
                    val c10 = src.data[(y0 * src.width + x1) * 4 + c].toInt() and 0xFF
                    val c01 = src.data[(y1 * src.width + x0) * 4 + c].toInt() and 0xFF
                    val c11 = src.data[(y1 * src.width + x1) * 4 + c].toInt() and 0xFF

                    val top = c00 * (1 - dx) + c10 * dx
                    val bottom = c01 * (1 - dx) + c11 * dx
                    val value = (top * (1 - dy) + bottom * dy).roundToInt()

                    destData[destIdx + c] = min(255, max(0, value)).toByte()
                }
            }
        }

        return ImageData(targetW, targetH, destData)
    }

    /**
     * Selective Gaussian blur to smooth out flat areas while preserving sharp edges.
     */
    fun applySelectiveBlur(src: ImageData, radius: Float, delta: Float): ImageData {
        val r = radius.roundToInt()
        if (r < 1) return src

        val w = src.width
        val h = src.height
        val outData = ByteArray(src.data.size)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val srcIdx = (y * w + x) * 4
                val sr = src.data[srcIdx].toInt() and 0xFF
                val sg = src.data[srcIdx + 1].toInt() and 0xFF
                val sb = src.data[srcIdx + 2].toInt() and 0xFF
                val sa = src.data[srcIdx + 3].toInt() and 0xFF

                var accR = 0L
                var accG = 0L
                var accB = 0L
                var accA = 0L
                var count = 0

                val minY = max(0, y - r)
                val maxY = min(h - 1, y + r)
                val minX = max(0, x - r)
                val maxX = min(w - 1, x + r)

                for (ny in minY..maxY) {
                    for (nx in minX..maxX) {
                        val nIdx = (ny * w + nx) * 4
                        val nr = src.data[nIdx].toInt() and 0xFF
                        val ng = src.data[nIdx + 1].toInt() and 0xFF
                        val nb = src.data[nIdx + 2].toInt() and 0xFF
                        val na = src.data[nIdx + 3].toInt() and 0xFF

                        val dist = Math.abs(sr - nr) + Math.abs(sg - ng) + Math.abs(sb - nb) + Math.abs(sa - na)
                        if (dist <= delta * 4) {
                            accR += nr
                            accG += ng
                            accB += nb
                            accA += na
                            count++
                        }
                    }
                }

                if (count > 0) {
                    outData[srcIdx] = (accR / count).toByte()
                    outData[srcIdx + 1] = (accG / count).toByte()
                    outData[srcIdx + 2] = (accB / count).toByte()
                    outData[srcIdx + 3] = (accA / count).toByte()
                } else {
                    outData[srcIdx] = src.data[srcIdx]
                    outData[srcIdx + 1] = src.data[srcIdx + 1]
                    outData[srcIdx + 2] = src.data[srcIdx + 2]
                    outData[srcIdx + 3] = src.data[srcIdx + 3]
                }
            }
        }

        return ImageData(w, h, outData)
    }

    /**
     * Binarizes an image into pure black and pure white.
     * Uses Otsu's thresholding if threshold < 0.
     */
    fun applyBinarization(src: ImageData, threshold: Int): ImageData {
        val w = src.width
        val h = src.height
        val totalPixels = w * h

        val gray = IntArray(totalPixels)
        val histogram = IntArray(256)

        for (i in 0 until totalPixels) {
            val idx = i * 4
            val r = src.data[idx].toInt() and 0xFF
            val g = src.data[idx + 1].toInt() and 0xFF
            val b = src.data[idx + 2].toInt() and 0xFF
            val yVal = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
            gray[i] = yVal
            histogram[yVal]++
        }

        val actualThreshold = if (threshold < 0) calculateOtsuThreshold(histogram, totalPixels) else threshold

        val outData = ByteArray(src.data.size)
        for (i in 0 until totalPixels) {
            val idx = i * 4
            val valByte = if (gray[i] <= actualThreshold) 0.toByte() else 255.toByte()
            outData[idx] = valByte
            outData[idx + 1] = valByte
            outData[idx + 2] = valByte
            outData[idx + 3] = 255.toByte()
        }

        return ImageData(w, h, outData)
    }

    private fun calculateOtsuThreshold(histogram: IntArray, total: Int): Int {
        var sum = 0f
        for (i in 0..255) sum += i * histogram[i]

        var sumB = 0f
        var wB = 0
        var varMax = 0f
        var threshold = 128

        for (t in 0..255) {
            wB += histogram[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break

            sumB += (t * histogram[t]).toFloat()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF

            val varBetween = wB.toFloat() * wF.toFloat() * (mB - mF) * (mB - mF)
            if (varBetween > varMax) {
                varMax = varBetween
                threshold = t
            }
        }

        return threshold
    }
}
