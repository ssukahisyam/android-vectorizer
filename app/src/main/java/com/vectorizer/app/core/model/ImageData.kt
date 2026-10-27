package com.vectorizer.app.core.model

class ImageData(
    val width: Int,
    val height: Int,
    val data: ByteArray
) {
    fun copy(): ImageData {
        val copyData = ByteArray(data.size)
        System.arraycopy(data, 0, copyData, 0, data.size)
        return ImageData(width, height, copyData)
    }

    fun getPixelChannel(x: Int, y: Int, channel: Int): Int {
        val idx = ((y * width) + x) * 4 + channel
        return data[idx].toInt() and 0xFF
    }

    fun setPixelRGBA(x: Int, y: Int, r: Int, g: Int, b: Int, a: Int) {
        val idx = ((y * width) + x) * 4
        data[idx] = r.toByte()
        data[idx + 1] = g.toByte()
        data[idx + 2] = b.toByte()
        data[idx + 3] = a.toByte()
    }
}
