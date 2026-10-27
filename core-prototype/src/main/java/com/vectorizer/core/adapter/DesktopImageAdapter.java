package com.vectorizer.core.adapter;

import com.vectorizer.core.model.ImageData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Desktop I/O adapter using Java ImageIO.
 * (On Android, this is replaced by an AndroidBitmapAdapter using android.graphics.Bitmap).
 */
public class DesktopImageAdapter {

    public static ImageData loadImage(File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Failed to decode image from: " + file.getAbsolutePath());
        }
        return fromBufferedImage(image);
    }

    public static ImageData fromBufferedImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] data = new byte[width * height * 4];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                int idx = (y * width + x) * 4;
                data[idx] = (byte) r;
                data[idx + 1] = (byte) g;
                data[idx + 2] = (byte) b;
                data[idx + 3] = (byte) a;
            }
        }

        return new ImageData(width, height, data);
    }

    public static void saveSVG(String svgContent, File outputFile) throws IOException {
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(svgContent);
        }
    }
}
