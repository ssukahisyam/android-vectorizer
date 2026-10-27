package com.vectorizer.core.test;

import com.vectorizer.core.adapter.DesktopImageAdapter;
import com.vectorizer.core.engine.ImageTracerEngine;
import com.vectorizer.core.model.ImageData;
import com.vectorizer.core.model.VectorPreset;
import com.vectorizer.core.model.VectorResult;
import com.vectorizer.core.model.VectorizerOptions;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BenchmarkRunner {

    static class RunRecord {
        String imageName;
        VectorPreset preset;
        long durationMs;
        int pathCount;
        int colorCount;
        long svgSizeBytes;
        String svgFileName;
    }

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   BATCH IMAGE-TO-VECTOR PROTOTYPE BENCHMARK     ");
        System.out.println("=================================================\n");

        ImageTracerEngine engine = new ImageTracerEngine();
        File outputDir = new File("vectorizer-core-prototype/output");
        if (!outputDir.exists()) outputDir.mkdirs();

        // Collect test images from ref_imagetracerjava
        List<File> testFiles = new ArrayList<>();
        File panda = new File("ref_imagetracerjava/panda.png");
        File smiley = new File("ref_imagetracerjava/smiley.png");
        if (panda.exists()) testFiles.add(panda);
        if (smiley.exists()) testFiles.add(smiley);

        File testImgDir = new File("ref_imagetracerjava/testimages");
        if (testImgDir.exists() && testImgDir.isDirectory()) {
            File[] files = testImgDir.listFiles((dir, name) -> name.endsWith(".png") || name.endsWith(".jpg"));
            if (files != null) {
                // Add up to 3 diverse sample images (e.g. 1.png, 2.png, 3.png)
                for (int i = 0; i < Math.min(3, files.length); i++) {
                    testFiles.add(files[i]);
                }
            }
        }

        if (testFiles.isEmpty()) {
            System.err.println("No test images found!");
            return;
        }

        System.out.printf("Loaded %d test images for benchmark.%n%n", testFiles.size());

        List<RunRecord> records = new ArrayList<>();

        for (File imgFile : testFiles) {
            String baseName = imgFile.getName().replace(".", "_");
            System.out.printf("--- Testing: %s ---%n", imgFile.getName());

            try {
                ImageData rawImage = DesktopImageAdapter.loadImage(imgFile);
                System.out.printf("Dimensions: %dx%d px%n", rawImage.width, rawImage.height);

                for (VectorPreset preset : VectorPreset.values()) {
                    VectorizerOptions options = preset.createOptions();
                    VectorResult result = engine.vectorize(rawImage, options);

                    String outFileName = String.format("%s_%s.svg", baseName, preset.name().toLowerCase());
                    File outFile = new File(outputDir, outFileName);
                    DesktopImageAdapter.saveSVG(result.svgContent, outFile);

                    RunRecord rec = new RunRecord();
                    rec.imageName = imgFile.getName();
                    rec.preset = preset;
                    rec.durationMs = result.executionTimeMs;
                    rec.pathCount = result.pathCount;
                    rec.colorCount = result.colorCount;
                    rec.svgSizeBytes = outFile.length();
                    rec.svgFileName = outFileName;
                    records.add(rec);

                    System.out.printf("  [%-22s] Time: %4d ms | Paths: %4d | Colors: %2d | SVG Size: %6.1f KB%n",
                            preset.displayName, rec.durationMs, rec.pathCount, rec.colorCount, rec.svgSizeBytes / 1024.0);
                }
                System.out.println();
            } catch (Exception e) {
                System.err.printf("Error testing %s: %s%n", imgFile.getName(), e.getMessage());
                e.printStackTrace();
            }
        }

        // Generate HTML visual comparison report
        try {
            generateHtmlReport(outputDir, testFiles, records);
            System.out.println("=================================================");
            System.out.println(" Visual Comparison Dashboard Generated!");
            System.out.println(" Location: vectorizer-core-prototype/output/index.html");
            System.out.println("=================================================");
        } catch (IOException e) {
            System.err.println("Failed to write HTML report: " + e.getMessage());
        }
    }

    private static void generateHtmlReport(File outputDir, List<File> testFiles, List<RunRecord> records) throws IOException {
        File htmlFile = new File(outputDir, "index.html");
        try (FileWriter w = new FileWriter(htmlFile)) {
            w.write("<!DOCTYPE html>\n<html lang=\"id\">\n<head>\n");
            w.write("<meta charset=\"UTF-8\">\n<title>Hasil Kalibrasi Mode Vektorisasi</title>\n");
            w.write("<style>\n");
            w.write("  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 24px; }\n");
            w.write("  h1 { margin-bottom: 8px; font-size: 24px; color: #38bdf8; }\n");
            w.write("  p.desc { color: #94a3b8; margin-top: 0; margin-bottom: 32px; font-size: 14px; }\n");
            w.write("  .card { background: #1e293b; border-radius: 12px; padding: 20px; margin-bottom: 28px; border: 1px solid #334155; }\n");
            w.write("  .card-title { font-size: 18px; font-weight: 600; margin-bottom: 16px; color: #e2e8f0; }\n");
            w.write("  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; }\n");
            w.write("  .item-box { background: #0f172a; border-radius: 8px; padding: 12px; border: 1px solid #334155; display: flex; flex-direction: column; align-items: center; }\n");
            w.write("  .item-title { font-size: 13px; font-weight: 600; margin-bottom: 8px; color: #38bdf8; text-align: center; }\n");
            w.write("  .preview-wrapper { width: 100%; height: 180px; display: flex; align-items: center; justify-content: center; background-image: linear-gradient(45deg, #1e293b 25%, transparent 25%), linear-gradient(-45deg, #1e293b 25%, transparent 25%), linear-gradient(45deg, transparent 75%, #1e293b 75%), linear-gradient(-45deg, transparent 75%, #1e293b 75%); background-size: 16px 16px; background-position: 0 0, 0 8px, 8px -8px, -8px 0px; border-radius: 6px; overflow: hidden; }\n");
            w.write("  .preview-wrapper img { max-width: 90%; max-height: 90%; object-fit: contain; }\n");
            w.write("  .metrics { margin-top: 10px; font-size: 11px; color: #94a3b8; width: 100%; display: flex; justify-content: space-between; }\n");
            w.write("  .badge { background: #0284c7; color: white; padding: 2px 6px; border-radius: 4px; font-size: 10px; }\n");
            w.write("</style>\n</head>\n<body>\n");
            w.write("  <h1>Evaluasi & Kalibrasi Mode Vektorisasi</h1>\n");
            w.write("  <p class=\"desc\">Perbandingan visual antara gambar raster asli dan output SVG dari masing-masing mode preset. Perhatikan kebersihan garis, hilangnya bintik noise, dan bentuk kurva.</p>\n");

            for (File img : testFiles) {
                String imgName = img.getName();
                w.write(String.format("  <div class=\"card\">\n    <div class=\"card-title\">Uji Sampel: %s</div>\n    <div class=\"grid\">\n", imgName));

                // Original image box
                w.write("      <div class=\"item-box\" style=\"border-color: #38bdf8;\">\n");
                w.write("        <div class=\"item-title\"><span class=\"badge\" style=\"background:#64748b;\">ASLI</span> Raster</div>\n");
                // Copy/point image path
                w.write(String.format("        <div class=\"preview-wrapper\"><img src=\"../../%s\" alt=\"Original\"></div>\n", img.getPath().replace('\\', '/')));
                w.write("        <div class=\"metrics\"><span>Input Bitmap</span></div>\n");
                w.write("      </div>\n");

                // Preset boxes
                for (RunRecord r : records) {
                    if (r.imageName.equals(imgName)) {
                        w.write("      <div class=\"item-box\">\n");
                        w.write(String.format("        <div class=\"item-title\"><span class=\"badge\">MODE</span> %s</div>\n", r.preset.displayName));
                        w.write(String.format("        <div class=\"preview-wrapper\"><img src=\"%s\" alt=\"%s\"></div>\n", r.svgFileName, r.preset.name()));
                        w.write(String.format("        <div class=\"metrics\"><span>Time: %d ms</span><span>Paths: %d</span><span>Size: %.1f KB</span></div>\n",
                                r.durationMs, r.pathCount, r.svgSizeBytes / 1024.0));
                        w.write("      </div>\n");
                    }
                }

                w.write("    </div>\n  </div>\n");
            }

            w.write("</body>\n</html>\n");
        }
    }
}
