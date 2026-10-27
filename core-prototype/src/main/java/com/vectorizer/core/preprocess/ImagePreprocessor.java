package com.vectorizer.core.preprocess;

import com.vectorizer.core.model.ImageData;

/**
 * Preprocessing utilities:
 * 1. Adaptive Downscaling (OOM Protection for batch processing)
 * 2. Selective Gaussian Blur (removes JPEG noise without blurring sharp edges)
 * 3. Otsu Adaptive Binarization (eliminates gradient rings in line art & sketches)
 */
public class ImagePreprocessor {

    /**
     * Downscales the image if either dimension exceeds maxDimension, preserving aspect ratio.
     * Prevents high memory consumption and Out-Of-Memory errors on mobile devices.
     */
    public static ImageData downscaleIfNeeded(ImageData src, int maxDimension) {
        if (maxDimension <= 0 || (src.width <= maxDimension && src.height <= maxDimension)) {
            return src;
        }

        float scale = Math.min((float) maxDimension / src.width, (float) maxDimension / src.height);
        int targetW = Math.max(1, Math.round(src.width * scale));
        int targetH = Math.max(1, Math.round(src.height * scale));

        byte[] destData = new byte[targetW * targetH * 4];

        // Bilinear interpolation
        for (int y = 0; y < targetH; y++) {
            float srcY = y / scale;
            int y0 = (int) srcY;
            int y1 = Math.min(y0 + 1, src.height - 1);
            float dy = srcY - y0;

            for (int x = 0; x < targetW; x++) {
                float srcX = x / scale;
                int x0 = (int) srcX;
                int x1 = Math.min(x0 + 1, src.width - 1);
                float dx = srcX - x0;

                int destIdx = (y * targetW + x) * 4;

                for (int c = 0; c < 4; c++) {
                    int c00 = src.data[(y0 * src.width + x0) * 4 + c] & 0xFF;
                    int c10 = src.data[(y0 * src.width + x1) * 4 + c] & 0xFF;
                    int c01 = src.data[(y1 * src.width + x0) * 4 + c] & 0xFF;
                    int c11 = src.data[(y1 * src.width + x1) * 4 + c] & 0xFF;

                    float top = c00 * (1 - dx) + c10 * dx;
                    float bottom = c01 * (1 - dx) + c11 * dx;
                    int value = Math.round(top * (1 - dy) + bottom * dy);

                    destData[destIdx + c] = (byte) Math.min(255, Math.max(0, value));
                }
            }
        }

        return new ImageData(targetW, targetH, destData);
    }

    /**
     * Selective Gaussian blur to smooth out flat areas while preserving sharp edges.
     * Prevents color quantization from creating jagged "stepped" color rings in flat logos.
     */
    public static ImageData applySelectiveBlur(ImageData src, float radius, float delta) {
        int r = Math.round(radius);
        if (r < 1) return src;

        int w = src.width;
        int h = src.height;
        byte[] outData = new byte[src.data.length];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int srcIdx = (y * w + x) * 4;
                int sr = src.data[srcIdx] & 0xFF;
                int sg = src.data[srcIdx + 1] & 0xFF;
                int sb = src.data[srcIdx + 2] & 0xFF;
                int sa = src.data[srcIdx + 3] & 0xFF;

                long accR = 0, accG = 0, accB = 0, accA = 0;
                int count = 0;

                int minY = Math.max(0, y - r);
                int maxY = Math.min(h - 1, y + r);
                int minX = Math.max(0, x - r);
                int maxX = Math.min(w - 1, x + r);

                for (int ny = minY; ny <= maxY; ny++) {
                    for (int nx = minX; nx <= maxX; nx++) {
                        int nIdx = (ny * w + nx) * 4;
                        int nr = src.data[nIdx] & 0xFF;
                        int ng = src.data[nIdx + 1] & 0xFF;
                        int nb = src.data[nIdx + 2] & 0xFF;
                        int na = src.data[nIdx + 3] & 0xFF;

                        // Check color distance
                        int dist = Math.abs(sr - nr) + Math.abs(sg - ng) + Math.abs(sb - nb) + Math.abs(sa - na);
                        if (dist <= delta * 4) {
                            accR += nr;
                            accG += ng;
                            accB += nb;
                            accA += na;
                            count++;
                        }
                    }
                }

                if (count > 0) {
                    outData[srcIdx] = (byte) (accR / count);
                    outData[srcIdx + 1] = (byte) (accG / count);
                    outData[srcIdx + 2] = (byte) (accB / count);
                    outData[srcIdx + 3] = (byte) (accA / count);
                } else {
                    outData[srcIdx] = src.data[srcIdx];
                    outData[srcIdx + 1] = src.data[srcIdx + 1];
                    outData[srcIdx + 2] = src.data[srcIdx + 2];
                    outData[srcIdx + 3] = src.data[srcIdx + 3];
                }
            }
        }

        return new ImageData(w, h, outData);
    }

    /**
     * Binarizes an image into pure black and pure white.
     * Uses Otsu's thresholding if threshold < 0.
     * Critical for Line Art to avoid multi-band stroke loops.
     */
    public static ImageData applyBinarization(ImageData src, int threshold) {
        int w = src.width;
        int h = src.height;
        int totalPixels = w * h;

        int[] gray = new int[totalPixels];
        int[] histogram = new int[256];

        for (int i = 0; i < totalPixels; i++) {
            int idx = i * 4;
            int r = src.data[idx] & 0xFF;
            int g = src.data[idx + 1] & 0xFF;
            int b = src.data[idx + 2] & 0xFF;
            int yVal = (int) (0.299f * r + 0.587f * g + 0.114f * b);
            gray[i] = yVal;
            histogram[yVal]++;
        }

        int actualThreshold = threshold;
        if (actualThreshold < 0) {
            actualThreshold = calculateOtsuThreshold(histogram, totalPixels);
        }

        byte[] outData = new byte[src.data.length];
        for (int i = 0; i < totalPixels; i++) {
            int idx = i * 4;
            byte val = (gray[i] <= actualThreshold) ? (byte) 0 : (byte) 255;
            outData[idx] = val;
            outData[idx + 1] = val;
            outData[idx + 2] = val;
            outData[idx + 3] = (byte) 255; // Fully opaque
        }

        return new ImageData(w, h, outData);
    }

    /**
     * Otsu's threshold algorithm: finds the optimal threshold that minimizes intra-class variance.
     */
    private static int calculateOtsuThreshold(int[] histogram, int total) {
        float sum = 0;
        for (int i = 0; i < 256; i++) sum += i * histogram[i];

        float sumB = 0;
        int wB = 0;
        int wF = 0;
        float varMax = 0;
        int threshold = 128;

        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;
            wF = total - wB;
            if (wF == 0) break;

            sumB += (float) (t * histogram[t]);
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;

            float varBetween = (float) wB * (float) wF * (mB - mF) * (mB - mF);
            if (varBetween > varMax) {
                varMax = varBetween;
                threshold = t;
            }
        }

        return threshold;
    }
}
