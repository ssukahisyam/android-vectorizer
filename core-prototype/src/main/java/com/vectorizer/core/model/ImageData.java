package com.vectorizer.core.model;

/**
 * Platform-independent container for raw RGBA image data.
 * Compatible with both desktop Java (BufferedImage) and Android (Bitmap).
 */
public class ImageData {
    public final int width;
    public final int height;
    /** Raw pixel byte buffer in RGBA format: R, G, B, A, R, G, B, A, ... */
    public final byte[] data;

    public ImageData(int width, int height, byte[] data) {
        this.width = width;
        this.height = height;
        this.data = data;
    }

    public ImageData copy() {
        byte[] copyData = new byte[data.length];
        System.arraycopy(data, 0, copyData, 0, data.length);
        return new ImageData(width, height, copyData);
    }

    /**
     * Get unsigned color component at pixel coordinate (x, y).
     * channel: 0=R, 1=G, 2=B, 3=A
     */
    public int getPixelChannel(int x, int y, int channel) {
        int idx = ((y * width) + x) * 4 + channel;
        return data[idx] & 0xFF;
    }

    public void setPixelRGBA(int x, int y, int r, int g, int b, int a) {
        int idx = ((y * width) + x) * 4;
        data[idx] = (byte) r;
        data[idx + 1] = (byte) g;
        data[idx + 2] = (byte) b;
        data[idx + 3] = (byte) a;
    }
}
