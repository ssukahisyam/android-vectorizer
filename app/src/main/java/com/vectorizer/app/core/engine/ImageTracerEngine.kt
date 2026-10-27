package com.vectorizer.app.core.engine

import com.vectorizer.app.core.model.ImageData
import com.vectorizer.app.core.model.VectorResult
import com.vectorizer.app.core.model.VectorizerOptions
import com.vectorizer.app.core.preprocess.ImagePreprocessor
import java.util.ArrayList
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class ImageTracerEngine {

    companion object {
        private val PATHSCAN_DIR_LOOKUP = byteArrayOf(0, 0, 3, 0, 1, 0, 3, 0, 0, 3, 3, 1, 0, 3, 0, 0)
        private val PATHSCAN_HOLEPATH_LOOKUP = booleanArrayOf(
            false, false, false, false,
            false, false, false, true,
            false, false, false, true,
            false, true, true, false
        )

        private val PATHSCAN_COMBINED_LOOKUP = arrayOf(
            arrayOf(byteArrayOf(-1, -1, -1, -1), byteArrayOf(-1, -1, -1, -1), byteArrayOf(-1, -1, -1, -1), byteArrayOf(-1, -1, -1, -1)),
            arrayOf(byteArrayOf(0, 1, 0, -1), byteArrayOf(-1, -1, -1, -1), byteArrayOf(-1, -1, -1, -1), byteArrayOf(0, 2, -1, 0)),
            arrayOf(byteArrayOf(-1, -1, -1, -1), byteArrayOf(-1, -1, -1, -1), byteArrayOf(0, 1, 0, -1), byteArrayOf(0, 0, 1, 0)),
            arrayOf(byteArrayOf(0, 0, 1, 0), byteArrayOf(-1, -1, -1, -1), byteArrayOf(0, 2, -1, 0), byteArrayOf(-1, -1, -1, -1)),

            arrayOf(byteArrayOf(-1, -1, -1, -1), byteArrayOf(0, 0, 1, 0), byteArrayOf(0, 3, 0, 1), byteArrayOf(-1, -1, -1, -1)),
            arrayOf(byteArrayOf(13, 3, 0, 1), byteArrayOf(13, 2, -1, 0), byteArrayOf(7, 1, 0, -1), byteArrayOf(7, 0, 1, 0)),
            arrayOf(byteArrayOf(-1, -1, -1, -1), byteArrayOf(0, 1, 0, -1), byteArrayOf(-1, -1, -1, -1), byteArrayOf(0, 3, 0, 1)),
            arrayOf(byteArrayOf(0, 3, 0, 1), byteArrayOf(0, 2, -1, 0), byteArrayOf(-1, -1, -1, -1), byteArrayOf(-1, -1, -1, -1)),

            arrayOf(byteArrayOf(0, 3, 0, 1), byteArrayOf(0, 2, -1, 0), byteArrayOf(-1, -1, -1, -1), byteArrayOf(-1, -1, -1, -1)),
            arrayOf(byteArrayOf(-1, -1, -1, -1), byteArrayOf(0, 1, 0, -1), byteArrayOf(-1, -1, -1, -1), byteArrayOf(0, 3, 0, 1)),
            arrayOf(byteArrayOf(11, 1, 0, -1), byteArrayOf(14, 0, 1, 0), byteArrayOf(14, 3, 0, 1), byteArrayOf(11, 2, -1, 0)),
            arrayOf(byteArrayOf(-1, -1, -1, -1), byteArrayOf(0, 0, 1, 0), byteArrayOf(0, 3, 0, 1), byteArrayOf(-1, -1, -1, -1)),

            arrayOf(byteArrayOf(0, 0, 1, 0), byteArrayOf(-1, -1, -1, -1), byteArrayOf(0, 2, -1, 0), byteArrayOf(-1, -1, -1, -1)),
            arrayOf(byteArrayOf(-1, -1, -1, -1), byteArrayOf(-1, -1, -1, -1), byteArrayOf(0, 1, 0, -1), byteArrayOf(0, 0, 1, 0)),
            arrayOf(byteArrayOf(0, 1, 0, -1), byteArrayOf(-1, -1, -1, -1), byteArrayOf(-1, -1, -1, -1), byteArrayOf(0, 2, -1, 0)),
            arrayOf(byteArrayOf(-1, -1, -1, -1), byteArrayOf(-1, -1, -1, -1), byteArrayOf(-1, -1, -1, -1), byteArrayOf(-1, -1, -1, -1))
        )
    }

    class IndexedImage(
        val width: Int,
        val height: Int,
        val array: Array<IntArray>,
        val palette: Array<IntArray>
    ) {
        var layers: MutableList<MutableList<MutableList<DoubleArray>>> = mutableListOf()
    }

    fun vectorize(inputImage: ImageData, options: VectorizerOptions): VectorResult {
        val startTime = System.currentTimeMillis()

        // 1. Memory guard downscaling
        var processedImage = ImagePreprocessor.downscaleIfNeeded(inputImage, options.maxDimension)

        // 2. Preprocessing
        if (options.binarize) {
            processedImage = ImagePreprocessor.applyBinarization(processedImage, options.binarizeThreshold)
        } else if (options.blurRadius > 0) {
            processedImage = ImagePreprocessor.applySelectiveBlur(processedImage, options.blurRadius, options.blurDelta)
        }

        // 3. Color quantization
        val ii = quantizeColors(processedImage, options)

        // 4. Layering
        val layers = layering(ii)

        // 5. Pathscan
        val batchPaths = mutableListOf<MutableList<MutableList<IntArray>>>()
        for (layer in layers) {
            batchPaths.add(pathscan(layer, options.pathOmit))
        }

        // 6. Interpolation
        val batchInternodes = mutableListOf<MutableList<MutableList<DoubleArray>>>()
        for (paths in batchPaths) {
            batchInternodes.add(internodes(paths))
        }

        // 7. Curve tracing
        val tracedLayers = mutableListOf<MutableList<MutableList<DoubleArray>>>()
        for (internodePaths in batchInternodes) {
            val tracedPaths = mutableListOf<MutableList<DoubleArray>>()
            for (path in internodePaths) {
                if (options.isPixelArtMode) {
                    tracedPaths.add(tracePixelArtPath(path))
                } else {
                    tracedPaths.add(tracePath(path, options.lineThreshold, options.quadThreshold))
                }
            }
            tracedLayers.add(tracedPaths)
        }
        ii.layers = tracedLayers

        // 8. Generate SVG
        var totalPaths = 0
        for (l in tracedLayers) {
            totalPaths += l.size
        }

        val svgContent = generateSVG(ii, options, processedImage.width, processedImage.height)
        val duration = System.currentTimeMillis() - startTime

        return VectorResult(
            svgContent = svgContent,
            pathCount = totalPaths,
            colorCount = ii.palette.size,
            executionTimeMs = duration,
            width = processedImage.width,
            height = processedImage.height
        )
    }

    private fun quantizeColors(img: ImageData, options: VectorizerOptions): IndexedImage {
        val numColors = options.numberOfColors
        val w = img.width
        val h = img.height

        val arr = Array(h + 2) { IntArray(w + 2) }
        for (j in 0 until (h + 2)) {
            arr[j][0] = -1
            arr[j][w + 1] = -1
        }
        for (i in 0 until (w + 2)) {
            arr[0][i] = -1
            arr[h + 1][i] = -1
        }

        val palette: Array<IntArray> = if (options.binarize) {
            arrayOf(
                intArrayOf(0, 0, 0, 255),
                intArrayOf(255, 255, 255, 255)
            )
        } else {
            samplePalette(img, numColors)
        }

        val cycles = if (options.binarize) 1 else options.colorQuantCycles
        val paletteAcc = Array(palette.size) { LongArray(5) }

        for (cnt in 0 until cycles) {
            if (cnt > 0 && !options.binarize) {
                for (k in palette.indices) {
                    if (paletteAcc[k][4] > 0) {
                        palette[k][0] = (paletteAcc[k][0] / paletteAcc[k][4]).toInt()
                        palette[k][1] = (paletteAcc[k][1] / paletteAcc[k][4]).toInt()
                        palette[k][2] = (paletteAcc[k][2] / paletteAcc[k][4]).toInt()
                        palette[k][3] = (paletteAcc[k][3] / paletteAcc[k][4]).toInt()
                    }
                }
            }

            for (k in palette.indices) {
                paletteAcc[k].fill(0)
            }

            for (j in 0 until h) {
                for (i in 0 until w) {
                    val idx = (j * w + i) * 4
                    val r = img.data[idx].toInt() and 0xFF
                    val g = img.data[idx + 1].toInt() and 0xFF
                    val b = img.data[idx + 2].toInt() and 0xFF
                    val a = img.data[idx + 3].toInt() and 0xFF

                    var minDistance = Int.MAX_VALUE
                    var closestIdx = 0

                    for (k in palette.indices) {
                        val dr = abs(palette[k][0] - r)
                        val dg = abs(palette[k][1] - g)
                        val db = abs(palette[k][2] - b)
                        val da = abs(palette[k][3] - a)
                        val dist = dr + dg + db + (da * 3)

                        if (dist < minDistance) {
                            minDistance = dist
                            closestIdx = k
                        }
                    }

                    paletteAcc[closestIdx][0] += r.toLong()
                    paletteAcc[closestIdx][1] += g.toLong()
                    paletteAcc[closestIdx][2] += b.toLong()
                    paletteAcc[closestIdx][3] += a.toLong()
                    paletteAcc[closestIdx][4]++

                    arr[j + 1][i + 1] = closestIdx
                }
            }
        }

        return IndexedImage(w, h, arr, palette)
    }

    private fun samplePalette(img: ImageData, numColors: Int): Array<IntArray> {
        val palette = Array(numColors) { IntArray(4) }
        val totalPixels = img.width * img.height
        for (i in 0 until numColors) {
            val pixelIdx = (Math.random() * totalPixels).toInt()
            val idx = pixelIdx * 4
            palette[i][0] = img.data[idx].toInt() and 0xFF
            palette[i][1] = img.data[idx + 1].toInt() and 0xFF
            palette[i][2] = img.data[idx + 2].toInt() and 0xFF
            palette[i][3] = img.data[idx + 3].toInt() and 0xFF
        }
        return palette
    }

    private fun layering(ii: IndexedImage): Array<Array<IntArray>> {
        val aw = ii.array[0].size
        val ah = ii.array.size
        val layers = Array(ii.palette.size) { Array(ah) { IntArray(aw) } }

        for (j in 1 until (ah - 1)) {
            for (i in 1 until (aw - 1)) {
                val valColor = ii.array[j][i]
                if (valColor < 0) continue

                val n1 = if (ii.array[j - 1][i - 1] == valColor) 1 else 0
                val n2 = if (ii.array[j - 1][i] == valColor) 1 else 0
                val n3 = if (ii.array[j - 1][i + 1] == valColor) 1 else 0
                val n4 = if (ii.array[j][i - 1] == valColor) 1 else 0
                val n5 = if (ii.array[j][i + 1] == valColor) 1 else 0
                val n6 = if (ii.array[j + 1][i - 1] == valColor) 1 else 0
                val n7 = if (ii.array[j + 1][i] == valColor) 1 else 0
                val n8 = if (ii.array[j + 1][i + 1] == valColor) 1 else 0

                layers[valColor][j + 1][i + 1] = 1 + (n5 * 2) + (n8 * 4) + (n7 * 8)
                if (n4 == 0) layers[valColor][j + 1][i] = 2 + (n7 * 4) + (n6 * 8)
                if (n2 == 0) layers[valColor][j][i + 1] = (n3 * 2) + (n5 * 4) + 8
                if (n1 == 0) layers[valColor][j][i] = (n2 * 2) + 4 + (n4 * 8)
            }
        }
        return layers
    }

    private fun pathscan(arr: Array<IntArray>, pathOmit: Float): MutableList<MutableList<IntArray>> {
        val paths = mutableListOf<MutableList<IntArray>>()
        val w = arr[0].size
        val h = arr.size

        for (j in 0 until h) {
            for (i in 0 until w) {
                if (arr[j][i] != 0 && arr[j][i] != 15) {
                    var px = i
                    var py = j
                    val thisPath = mutableListOf<IntArray>()
                    paths.add(thisPath)
                    var pathFinished = false

                    val nodeType = arr[py][px]
                    var dir = PATHSCAN_DIR_LOOKUP[nodeType].toInt()
                    val holePath = PATHSCAN_HOLEPATH_LOOKUP[nodeType]

                    while (!pathFinished) {
                        thisPath.add(intArrayOf(px - 1, py - 1, arr[py][px]))

                        val lookup = PATHSCAN_COMBINED_LOOKUP[arr[py][px]][dir]
                        arr[py][px] = lookup[0].toInt()
                        dir = lookup[1].toInt()
                        px += lookup[2].toInt()
                        py += lookup[3].toInt()

                        if (px < 0 || py < 0 || px >= w || py >= h) {
                            pathFinished = true
                            paths.remove(thisPath)
                            break
                        }

                        if ((px - 1) == thisPath[0][0] && (py - 1) == thisPath[0][1]) {
                            pathFinished = true
                            if (holePath || thisPath.size < pathOmit) {
                                paths.remove(thisPath)
                            }
                        }
                    }
                }
            }
        }
        return paths
    }

    private fun internodes(paths: MutableList<MutableList<IntArray>>): MutableList<MutableList<DoubleArray>> {
        val ins = mutableListOf<MutableList<DoubleArray>>()

        for (path in paths) {
            val thisInp = mutableListOf<DoubleArray>()
            ins.add(thisInp)
            val pathLen = path.size

            for (pcnt in 0 until pathLen) {
                val nextIdx = (pcnt + 1) % pathLen
                val nextIdx2 = (pcnt + 2) % pathLen

                val pp1 = path[pcnt]
                val pp2 = path[nextIdx]
                val pp3 = path[nextIdx2]

                val thisX = (pp1[0] + pp2[0]) / 2.0
                val thisY = (pp1[1] + pp2[1]) / 2.0
                val nextX = (pp2[0] + pp3[0]) / 2.0
                val nextY = (pp2[1] + pp3[1]) / 2.0

                val dir = if (thisX < nextX) {
                    if (thisY < nextY) 1.0 else if (thisY > nextY) 7.0 else 0.0
                } else if (thisX > nextX) {
                    if (thisY < nextY) 3.0 else if (thisY > nextY) 5.0 else 4.0
                } else {
                    if (thisY < nextY) 2.0 else if (thisY > nextY) 6.0 else 8.0
                }

                thisInp.add(doubleArrayOf(thisX, thisY, dir))
            }
        }
        return ins
    }

    private fun tracePixelArtPath(path: MutableList<DoubleArray>): MutableList<DoubleArray> {
        val segments = mutableListOf<DoubleArray>()
        val len = path.size
        for (i in 0 until len) {
            val p1 = path[i]
            val p2 = path[(i + 1) % len]
            segments.add(doubleArrayOf(1.0, p1[0], p1[1], p2[0], p2[1], 0.0, 0.0))
        }
        return segments
    }

    private fun tracePath(path: MutableList<DoubleArray>, lineThreshold: Float, quadThreshold: Float): MutableList<DoubleArray> {
        var pcnt = 0
        val pathLen = path.size
        val smp = mutableListOf<DoubleArray>()

        while (pcnt < pathLen) {
            val segType1 = path[pcnt][2]
            var segType2 = -1.0
            var seqEnd = pcnt + 1

            while (seqEnd < pathLen &&
                (path[seqEnd][2] == segType1 || path[seqEnd][2] == segType2 || segType2 == -1.0)
            ) {
                if (path[seqEnd][2] != segType1 && segType2 == -1.0) {
                    segType2 = path[seqEnd][2]
                }
                seqEnd++
            }

            smp.addAll(fitSeq(path, lineThreshold, quadThreshold, pcnt, seqEnd % pathLen))
            pcnt = if (seqEnd >= pathLen) pathLen else seqEnd
        }
        return smp
    }

    private fun fitSeq(
        path: MutableList<DoubleArray>,
        lThres: Float,
        qThres: Float,
        seqStart: Int,
        seqEnd: Int
    ): MutableList<DoubleArray> {
        val segments = mutableListOf<DoubleArray>()
        val pathLen = path.size
        if (seqEnd >= pathLen || seqEnd < 0) return segments

        var errorPoint = seqStart
        var curvePass = true
        var errorVal = 0.0
        var tl = (seqEnd - seqStart).toDouble()
        if (tl < 0) tl += pathLen.toDouble()
        if (tl == 0.0) return segments

        val vx = (path[seqEnd][0] - path[seqStart][0]) / tl
        val vy = (path[seqEnd][1] - path[seqStart][1]) / tl

        var pcnt = (seqStart + 1) % pathLen
        while (pcnt != seqEnd) {
            var pl = (pcnt - seqStart).toDouble()
            if (pl < 0) pl += pathLen.toDouble()
            val px = path[seqStart][0] + (vx * pl)
            val py = path[seqStart][1] + (vy * pl)
            val dist2 = ((path[pcnt][0] - px) * (path[pcnt][0] - px)) +
                ((path[pcnt][1] - py) * (path[pcnt][1] - py))

            if (dist2 > lThres) curvePass = false
            if (dist2 > errorVal) {
                errorPoint = pcnt
                errorVal = dist2
            }
            pcnt = (pcnt + 1) % pathLen
        }

        if (curvePass) {
            segments.add(
                doubleArrayOf(
                    1.0,
                    path[seqStart][0], path[seqStart][1],
                    path[seqEnd][0], path[seqEnd][1],
                    0.0, 0.0
                )
            )
            return segments
        }

        val fitPoint = errorPoint
        curvePass = true
        errorVal = 0.0

        var t = (fitPoint - seqStart) / tl
        if (t < 0) t += 1.0
        val t1 = (1.0 - t) * (1.0 - t)
        var t2 = 2.0 * (1.0 - t) * t
        val t3 = t * t

        if (abs(t2) < 0.0001) t2 = 0.0001
        val cpx = (((t1 * path[seqStart][0]) + (t3 * path[seqEnd][0])) - path[fitPoint][0]) / -t2
        val cpy = (((t1 * path[seqStart][1]) + (t3 * path[seqEnd][1])) - path[fitPoint][1]) / -t2

        pcnt = (seqStart + 1) % pathLen
        while (pcnt != seqEnd) {
            var pt = (pcnt - seqStart) / tl
            if (pt < 0) pt += 1.0
            val pt1 = (1.0 - pt) * (1.0 - pt)
            val pt2 = 2.0 * (1.0 - pt) * pt
            val pt3 = pt * pt

            val px = (pt1 * path[seqStart][0]) + (pt2 * cpx) + (pt3 * path[seqEnd][0])
            val py = (pt1 * path[seqStart][1]) + (pt2 * cpy) + (pt3 * path[seqEnd][1])
            val dist2 = ((path[pcnt][0] - px) * (path[pcnt][0] - px)) +
                ((path[pcnt][1] - py) * (path[pcnt][1] - py))

            if (dist2 > qThres) curvePass = false
            if (dist2 > errorVal) {
                errorPoint = pcnt
                errorVal = dist2
            }
            pcnt = (pcnt + 1) % pathLen
        }

        if (curvePass) {
            segments.add(
                doubleArrayOf(
                    2.0,
                    path[seqStart][0], path[seqStart][1],
                    cpx, cpy,
                    path[seqEnd][0], path[seqEnd][1]
                )
            )
            return segments
        }

        val splitPoint = (fitPoint + errorPoint) / 2
        segments.addAll(fitSeq(path, lThres, qThres, seqStart, splitPoint))
        segments.addAll(fitSeq(path, lThres, qThres, splitPoint, seqEnd))
        return segments
    }

    private fun generateSVG(ii: IndexedImage, options: VectorizerOptions, w: Int, h: Int): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
        if (options.viewBox) {
            sb.append(String.format("viewBox=\"0 0 %d %d\" width=\"%d\" height=\"%d\"", w, h, w, h))
        } else {
            sb.append(String.format("width=\"%d\" height=\"%d\"", w, h))
        }
        sb.append(" version=\"1.1\">\n")

        for (layerIdx in ii.layers.indices) {
            val layer = ii.layers[layerIdx]
            if (layer.isEmpty()) continue

            val color = ii.palette[layerIdx]
            val r = color[0]
            val g = color[1]
            val b = color[2]
            val a = color[3]

            if (options.removeWhiteBackground && r >= 245 && g >= 245 && b >= 245) {
                continue
            }

            val fillHex = String.format("#%02X%02X%02X", r, g, b)
            val opacityAttr = if (a < 255) String.format(" fill-opacity=\"%.2f\"", a / 255.0f) else ""

            for (path in layer) {
                if (path.isEmpty()) continue

                sb.append("  <path fill=\"").append(fillHex).append("\"").append(opacityAttr).append(" d=\"")
                val firstSeg = path[0]
                sb.append("M ").append(formatCoord(firstSeg[1], options.roundCoords)).append(" ")
                    .append(formatCoord(firstSeg[2], options.roundCoords)).append(" ")

                for (seg in path) {
                    if (seg[0] == 1.0) {
                        sb.append("L ").append(formatCoord(seg[3], options.roundCoords)).append(" ")
                            .append(formatCoord(seg[4], options.roundCoords)).append(" ")
                    } else if (seg[0] == 2.0) {
                        sb.append("Q ").append(formatCoord(seg[3], options.roundCoords)).append(" ")
                            .append(formatCoord(seg[4], options.roundCoords)).append(" ")
                            .append(formatCoord(seg[5], options.roundCoords)).append(" ")
                            .append(formatCoord(seg[6], options.roundCoords)).append(" ")
                    }
                }
                sb.append("Z\" />\n")
            }
        }

        sb.append("</svg>\n")
        return sb.toString()
    }

    private fun formatCoord(value: Double, decimalPlaces: Int): String {
        if (decimalPlaces <= 0) {
            return value.roundToLong().toString()
        }
        val factor = 10.0.pow(decimalPlaces.toDouble())
        val rounded = (value * factor).roundToLong() / factor
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }
}
