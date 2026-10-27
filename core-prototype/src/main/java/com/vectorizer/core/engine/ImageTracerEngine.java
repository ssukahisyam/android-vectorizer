package com.vectorizer.core.engine;

import com.vectorizer.core.model.ImageData;
import com.vectorizer.core.model.VectorResult;
import com.vectorizer.core.model.VectorizerOptions;
import com.vectorizer.core.preprocess.ImagePreprocessor;

import java.util.ArrayList;
import java.util.List;

public class ImageTracerEngine {

    // Lookup tables for pathscan
    private static final byte[] PATHSCAN_DIR_LOOKUP = {0, 0, 3, 0, 1, 0, 3, 0, 0, 3, 3, 1, 0, 3, 0, 0};
    private static final boolean[] PATHSCAN_HOLEPATH_LOOKUP = {
        false, false, false, false,
        false, false, false, true,
        false, false, false, true,
        false, true, true, false
    };

    // pathscan_combined_lookup[ arr[py][px] ][ dir ] = [nextarrpypx, nextdir, deltapx, deltapy];
    private static final byte[][][] PATHSCAN_COMBINED_LOOKUP = {
        {{-1, -1, -1, -1}, {-1, -1, -1, -1}, {-1, -1, -1, -1}, {-1, -1, -1, -1}},
        {{ 0,  1,  0, -1}, {-1, -1, -1, -1}, {-1, -1, -1, -1}, { 0,  2, -1,  0}},
        {{-1, -1, -1, -1}, {-1, -1, -1, -1}, { 0,  1,  0, -1}, { 0,  0,  1,  0}},
        {{ 0,  0,  1,  0}, {-1, -1, -1, -1}, { 0,  2, -1,  0}, {-1, -1, -1, -1}},

        {{-1, -1, -1, -1}, { 0,  0,  1,  0}, { 0,  3,  0,  1}, {-1, -1, -1, -1}},
        {{13,  3,  0,  1}, {13,  2, -1,  0}, { 7,  1,  0, -1}, { 7,  0,  1,  0}},
        {{-1, -1, -1, -1}, { 0,  1,  0, -1}, {-1, -1, -1, -1}, { 0,  3,  0,  1}},
        {{ 0,  3,  0,  1}, { 0,  2, -1,  0}, {-1, -1, -1, -1}, {-1, -1, -1, -1}},

        {{ 0,  3,  0,  1}, { 0,  2, -1,  0}, {-1, -1, -1, -1}, {-1, -1, -1, -1}},
        {{-1, -1, -1, -1}, { 0,  1,  0, -1}, {-1, -1, -1, -1}, { 0,  3,  0,  1}},
        {{11,  1,  0, -1}, {14,  0,  1,  0}, {14,  3,  0,  1}, {11,  2, -1,  0}},
        {{-1, -1, -1, -1}, { 0,  0,  1,  0}, { 0,  3,  0,  1}, {-1, -1, -1, -1}},

        {{ 0,  0,  1,  0}, {-1, -1, -1, -1}, { 0,  2, -1,  0}, {-1, -1, -1, -1}},
        {{-1, -1, -1, -1}, {-1, -1, -1, -1}, { 0,  1,  0, -1}, { 0,  0,  1,  0}},
        {{ 0,  1,  0, -1}, {-1, -1, -1, -1}, {-1, -1, -1, -1}, { 0,  2, -1,  0}},
        {{-1, -1, -1, -1}, {-1, -1, -1, -1}, {-1, -1, -1, -1}, {-1, -1, -1, -1}}
    };

    public static class IndexedImage {
        public final int width, height;
        public final int[][] array;
        public final int[][] palette; // [paletteLength][4] RGBA (0..255)
        public List<List<List<Double[]>>> layers;

        public IndexedImage(int width, int height, int[][] array, int[][] palette) {
            this.width = width;
            this.height = height;
            this.array = array;
            this.palette = palette;
        }
    }

    /**
     * Executes the full vectorization pipeline.
     */
    public VectorResult vectorize(ImageData inputImage, VectorizerOptions options) {
        long startTime = System.currentTimeMillis();

        // 1. Memory guard downscaling
        ImageData processedImage = ImagePreprocessor.downscaleIfNeeded(inputImage, options.maxDimension);

        // 2. Preprocessing
        if (options.binarize) {
            processedImage = ImagePreprocessor.applyBinarization(processedImage, options.binarizeThreshold);
        } else if (options.blurRadius > 0) {
            processedImage = ImagePreprocessor.applySelectiveBlur(processedImage, options.blurRadius, options.blurDelta);
        }

        // 3. Color quantization
        IndexedImage ii = quantizeColors(processedImage, options);

        // 4. Layering & Edge detection
        int[][][] layers = layering(ii);

        // 5. Pathscan with noise filter (pathOmit)
        List<List<List<Integer[]>>> batchPaths = new ArrayList<>();
        for (int[][] layer : layers) {
            batchPaths.add(pathscan(layer, options.pathOmit));
        }

        // 6. Interpolation (internodes)
        List<List<List<Double[]>>> batchInternodes = new ArrayList<>();
        for (List<List<Integer[]>> paths : batchPaths) {
            batchInternodes.add(internodes(paths));
        }

        // 7. Curve tracing
        List<List<List<Double[]>>> tracedLayers = new ArrayList<>();
        for (List<List<Double[]>> internodePaths : batchInternodes) {
            List<List<Double[]>> tracedPaths = new ArrayList<>();
            for (List<Double[]> path : internodePaths) {
                if (options.isPixelArtMode) {
                    tracedPaths.add(tracePixelArtPath(path));
                } else {
                    tracedPaths.add(tracePath(path, options.lineThreshold, options.quadThreshold));
                }
            }
            tracedLayers.add(tracedPaths);
        }
        ii.layers = tracedLayers;

        // 8. Generate SVG
        int totalPaths = 0;
        for (List<List<Double[]>> l : tracedLayers) {
            totalPaths += l.size();
        }

        String svgContent = generateSVG(ii, options, processedImage.width, processedImage.height);
        long duration = System.currentTimeMillis() - startTime;

        return new VectorResult(
            svgContent,
            totalPaths,
            ii.palette.length,
            duration,
            processedImage.width,
            processedImage.height
        );
    }

    private IndexedImage quantizeColors(ImageData img, VectorizerOptions options) {
        int numColors = options.numberOfColors;
        int w = img.width;
        int h = img.height;

        int[][] arr = new int[h + 2][w + 2];
        for (int j = 0; j < (h + 2); j++) {
            arr[j][0] = -1;
            arr[j][w + 1] = -1;
        }
        for (int i = 0; i < (w + 2); i++) {
            arr[0][i] = -1;
            arr[h + 1][i] = -1;
        }

        int[][] palette;
        if (options.binarize) {
            palette = new int[][] {
                {0, 0, 0, 255},       // Black (stroke)
                {255, 255, 255, 255}  // White (background)
            };
        } else {
            palette = samplePalette(img, numColors);
        }

        int cycles = options.binarize ? 1 : options.colorQuantCycles;
        long[][] paletteAcc = new long[palette.length][5];

        for (int cnt = 0; cnt < cycles; cnt++) {
            if (cnt > 0 && !options.binarize) {
                for (int k = 0; k < palette.length; k++) {
                    if (paletteAcc[k][4] > 0) {
                        palette[k][0] = (int) (paletteAcc[k][0] / paletteAcc[k][4]);
                        palette[k][1] = (int) (paletteAcc[k][1] / paletteAcc[k][4]);
                        palette[k][2] = (int) (paletteAcc[k][2] / paletteAcc[k][4]);
                        palette[k][3] = (int) (paletteAcc[k][3] / paletteAcc[k][4]);
                    }
                }
            }

            for (int k = 0; k < palette.length; k++) {
                paletteAcc[k][0] = 0;
                paletteAcc[k][1] = 0;
                paletteAcc[k][2] = 0;
                paletteAcc[k][3] = 0;
                paletteAcc[k][4] = 0;
            }

            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    int idx = (j * w + i) * 4;
                    int r = img.data[idx] & 0xFF;
                    int g = img.data[idx + 1] & 0xFF;
                    int b = img.data[idx + 2] & 0xFF;
                    int a = img.data[idx + 3] & 0xFF;

                    int minDistance = Integer.MAX_VALUE;
                    int closestIdx = 0;

                    for (int k = 0; k < palette.length; k++) {
                        int dr = Math.abs(palette[k][0] - r);
                        int dg = Math.abs(palette[k][1] - g);
                        int db = Math.abs(palette[k][2] - b);
                        int da = Math.abs(palette[k][3] - a);
                        int dist = dr + dg + db + (da * 3);

                        if (dist < minDistance) {
                            minDistance = dist;
                            closestIdx = k;
                        }
                    }

                    paletteAcc[closestIdx][0] += r;
                    paletteAcc[closestIdx][1] += g;
                    paletteAcc[closestIdx][2] += b;
                    paletteAcc[closestIdx][3] += a;
                    paletteAcc[closestIdx][4]++;

                    arr[j + 1][i + 1] = closestIdx;
                }
            }
        }

        return new IndexedImage(w, h, arr, palette);
    }

    private int[][] samplePalette(ImageData img, int numColors) {
        int[][] palette = new int[numColors][4];
        int totalPixels = img.width * img.height;
        for (int i = 0; i < numColors; i++) {
            int pixelIdx = (int) (Math.random() * totalPixels);
            int idx = pixelIdx * 4;
            palette[i][0] = img.data[idx] & 0xFF;
            palette[i][1] = img.data[idx + 1] & 0xFF;
            palette[i][2] = img.data[idx + 2] & 0xFF;
            palette[i][3] = img.data[idx + 3] & 0xFF;
        }
        return palette;
    }

    private int[][][] layering(IndexedImage ii) {
        int aw = ii.array[0].length;
        int ah = ii.array.length;
        int[][][] layers = new int[ii.palette.length][ah][aw];

        for (int j = 1; j < (ah - 1); j++) {
            for (int i = 1; i < (aw - 1); i++) {
                int val = ii.array[j][i];
                if (val < 0) continue;

                int n1 = ii.array[j - 1][i - 1] == val ? 1 : 0;
                int n2 = ii.array[j - 1][i    ] == val ? 1 : 0;
                int n3 = ii.array[j - 1][i + 1] == val ? 1 : 0;
                int n4 = ii.array[j    ][i - 1] == val ? 1 : 0;
                int n5 = ii.array[j    ][i + 1] == val ? 1 : 0;
                int n6 = ii.array[j + 1][i - 1] == val ? 1 : 0;
                int n7 = ii.array[j + 1][i    ] == val ? 1 : 0;
                int n8 = ii.array[j + 1][i + 1] == val ? 1 : 0;

                layers[val][j + 1][i + 1] = 1 + (n5 * 2) + (n8 * 4) + (n7 * 8);
                if (n4 == 0) layers[val][j + 1][i    ] = 2 + (n7 * 4) + (n6 * 8);
                if (n2 == 0) layers[val][j    ][i + 1] = (n3 * 2) + (n5 * 4) + 8;
                if (n1 == 0) layers[val][j    ][i    ] = (n2 * 2) + 4 + (n4 * 8);
            }
        }
        return layers;
    }

    private List<List<Integer[]>> pathscan(int[][] arr, float pathOmit) {
        List<List<Integer[]>> paths = new ArrayList<>();
        int w = arr[0].length;
        int h = arr.length;

        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if (arr[j][i] != 0 && arr[j][i] != 15) {
                    int px = i, py = j;
                    List<Integer[]> thisPath = new ArrayList<>();
                    paths.add(thisPath);
                    boolean pathFinished = false;

                    int nodeType = arr[py][px];
                    int dir = PATHSCAN_DIR_LOOKUP[nodeType];
                    boolean holePath = PATHSCAN_HOLEPATH_LOOKUP[nodeType];

                    while (!pathFinished) {
                        thisPath.add(new Integer[] {px - 1, py - 1, (int) arr[py][px]});

                        byte[] lookup = PATHSCAN_COMBINED_LOOKUP[arr[py][px]][dir];
                        arr[py][px] = lookup[0];
                        dir = lookup[1];
                        px += lookup[2];
                        py += lookup[3];

                        if (px < 0 || py < 0 || px >= w || py >= h) {
                            pathFinished = true;
                            paths.remove(thisPath);
                            break;
                        }

                        if ((px - 1) == thisPath.get(0)[0] && (py - 1) == thisPath.get(0)[1]) {
                            pathFinished = true;
                            if (holePath || thisPath.size() < pathOmit) {
                                paths.remove(thisPath);
                            }
                        }
                    }
                }
            }
        }
        return paths;
    }

    private List<List<Double[]>> internodes(List<List<Integer[]>> paths) {
        List<List<Double[]>> ins = new ArrayList<>();

        for (List<Integer[]> path : paths) {
            List<Double[]> thisInp = new ArrayList<>();
            ins.add(thisInp);
            int pathLen = path.size();

            for (int pcnt = 0; pcnt < pathLen; pcnt++) {
                int nextIdx = (pcnt + 1) % pathLen;
                int nextIdx2 = (pcnt + 2) % pathLen;

                Integer[] pp1 = path.get(pcnt);
                Integer[] pp2 = path.get(nextIdx);
                Integer[] pp3 = path.get(nextIdx2);

                double thisX = (pp1[0] + pp2[0]) / 2.0;
                double thisY = (pp1[1] + pp2[1]) / 2.0;
                double nextX = (pp2[0] + pp3[0]) / 2.0;
                double nextY = (pp2[1] + pp3[1]) / 2.0;

                double dir;
                if (thisX < nextX) {
                    if (thisY < nextY) dir = 1.0; // SE
                    else if (thisY > nextY) dir = 7.0; // NE
                    else dir = 0.0; // E
                } else if (thisX > nextX) {
                    if (thisY < nextY) dir = 3.0; // SW
                    else if (thisY > nextY) dir = 5.0; // NW
                    else dir = 4.0; // W
                } else {
                    if (thisY < nextY) dir = 2.0; // S
                    else if (thisY > nextY) dir = 6.0; // N
                    else dir = 8.0;
                }

                thisInp.add(new Double[] {thisX, thisY, dir});
            }
        }
        return ins;
    }

    /**
     * For pixel art mode: preserve orthogonal 90-degree step boundaries without smoothing.
     */
    private List<Double[]> tracePixelArtPath(List<Double[]> path) {
        List<Double[]> segments = new ArrayList<>();
        int len = path.size();
        for (int i = 0; i < len; i++) {
            Double[] p1 = path.get(i);
            Double[] p2 = path.get((i + 1) % len);
            // Linear segment: type=1.0, x1, y1, x2, y2, 0, 0
            segments.add(new Double[] {1.0, p1[0], p1[1], p2[0], p2[1], 0.0, 0.0});
        }
        return segments;
    }

    private List<Double[]> tracePath(List<Double[]> path, float lineThreshold, float quadThreshold) {
        int pcnt = 0;
        int pathLen = path.size();
        List<Double[]> smp = new ArrayList<>();

        while (pcnt < pathLen) {
            double segType1 = path.get(pcnt)[2];
            double segType2 = -1;
            int seqEnd = pcnt + 1;

            while ((seqEnd < pathLen) &&
                   (path.get(seqEnd)[2] == segType1 || path.get(seqEnd)[2] == segType2 || segType2 == -1)) {
                if (path.get(seqEnd)[2] != segType1 && segType2 == -1) {
                    segType2 = path.get(seqEnd)[2];
                }
                seqEnd++;
            }

            smp.addAll(fitSeq(path, lineThreshold, quadThreshold, pcnt, seqEnd % pathLen));
            pcnt = (seqEnd >= pathLen) ? pathLen : seqEnd;
        }
        return smp;
    }

    private List<Double[]> fitSeq(List<Double[]> path, float lThres, float qThres, int seqStart, int seqEnd) {
        List<Double[]> segments = new ArrayList<>();
        int pathLen = path.size();
        if (seqEnd >= pathLen || seqEnd < 0) return segments;

        int errorPoint = seqStart;
        boolean curvePass = true;
        double errorVal = 0;
        double tl = seqEnd - seqStart;
        if (tl < 0) tl += pathLen;
        if (tl == 0) return segments;

        double vx = (path.get(seqEnd)[0] - path.get(seqStart)[0]) / tl;
        double vy = (path.get(seqEnd)[1] - path.get(seqStart)[1]) / tl;

        // Try straight line fit
        int pcnt = (seqStart + 1) % pathLen;
        while (pcnt != seqEnd) {
            double pl = pcnt - seqStart;
            if (pl < 0) pl += pathLen;
            double px = path.get(seqStart)[0] + (vx * pl);
            double py = path.get(seqStart)[1] + (vy * pl);
            double dist2 = ((path.get(pcnt)[0] - px) * (path.get(pcnt)[0] - px)) +
                           ((path.get(pcnt)[1] - py) * (path.get(pcnt)[1] - py));

            if (dist2 > lThres) curvePass = false;
            if (dist2 > errorVal) {
                errorPoint = pcnt;
                errorVal = dist2;
            }
            pcnt = (pcnt + 1) % pathLen;
        }

        if (curvePass) {
            segments.add(new Double[] {
                1.0,
                path.get(seqStart)[0], path.get(seqStart)[1],
                path.get(seqEnd)[0], path.get(seqEnd)[1],
                0.0, 0.0
            });
            return segments;
        }

        // Try quadratic spline fit through errorPoint
        int fitPoint = errorPoint;
        curvePass = true;
        errorVal = 0;

        double t = (fitPoint - seqStart) / tl;
        if (t < 0) t += 1.0;
        double t1 = (1.0 - t) * (1.0 - t);
        double t2 = 2.0 * (1.0 - t) * t;
        double t3 = t * t;

        if (Math.abs(t2) < 0.0001) t2 = 0.0001; // Avoid divide by zero
        double cpx = (((t1 * path.get(seqStart)[0]) + (t3 * path.get(seqEnd)[0])) - path.get(fitPoint)[0]) / -t2;
        double cpy = (((t1 * path.get(seqStart)[1]) + (t3 * path.get(seqEnd)[1])) - path.get(fitPoint)[1]) / -t2;

        pcnt = (seqStart + 1) % pathLen;
        while (pcnt != seqEnd) {
            double pt = (pcnt - seqStart) / tl;
            if (pt < 0) pt += 1.0;
            double pt1 = (1.0 - pt) * (1.0 - pt);
            double pt2 = 2.0 * (1.0 - pt) * pt;
            double pt3 = pt * pt;

            double px = (pt1 * path.get(seqStart)[0]) + (pt2 * cpx) + (pt3 * path.get(seqEnd)[0]);
            double py = (pt1 * path.get(seqStart)[1]) + (pt2 * cpy) + (pt3 * path.get(seqEnd)[1]);
            double dist2 = ((path.get(pcnt)[0] - px) * (path.get(pcnt)[0] - px)) +
                           ((path.get(pcnt)[1] - py) * (path.get(pcnt)[1] - py));

            if (dist2 > qThres) curvePass = false;
            if (dist2 > errorVal) {
                errorPoint = pcnt;
                errorVal = dist2;
            }
            pcnt = (pcnt + 1) % pathLen;
        }

        if (curvePass) {
            segments.add(new Double[] {
                2.0,
                path.get(seqStart)[0], path.get(seqStart)[1],
                cpx, cpy,
                path.get(seqEnd)[0], path.get(seqEnd)[1]
            });
            return segments;
        }

        // Recursive split
        int splitPoint = (fitPoint + errorPoint) / 2;
        segments.addAll(fitSeq(path, lThres, qThres, seqStart, splitPoint));
        segments.addAll(fitSeq(path, lThres, qThres, splitPoint, seqEnd));
        return segments;
    }

    private String generateSVG(IndexedImage ii, VectorizerOptions options, int w, int h) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ");
        if (options.viewBox) {
            sb.append(String.format("viewBox=\"0 0 %d %d\" width=\"%d\" height=\"%d\"", w, h, w, h));
        } else {
            sb.append(String.format("width=\"%d\" height=\"%d\"", w, h));
        }
        sb.append(" version=\"1.1\">\n");

        for (int layerIdx = 0; layerIdx < ii.layers.size(); layerIdx++) {
            List<List<Double[]>> layer = ii.layers.get(layerIdx);
            if (layer.isEmpty()) continue;

            int[] color = ii.palette[layerIdx];
            int r = color[0];
            int g = color[1];
            int b = color[2];
            int a = color[3];

            // If requested, skip rendering pure white background layer
            if (options.removeWhiteBackground && r >= 245 && g >= 245 && b >= 245) {
                continue;
            }

            String fillHex = String.format("#%02X%02X%02X", r, g, b);
            String opacityAttr = (a < 255) ? String.format(" fill-opacity=\"%.2f\"", a / 255.0f) : "";

            for (List<Double[]> path : layer) {
                if (path.isEmpty()) continue;

                sb.append("  <path fill=\"").append(fillHex).append("\"").append(opacityAttr).append(" d=\"");
                Double[] firstSeg = path.get(0);
                sb.append("M ").append(formatCoord(firstSeg[1], options.roundCoords)).append(" ")
                  .append(formatCoord(firstSeg[2], options.roundCoords)).append(" ");

                for (Double[] seg : path) {
                    if (seg[0] == 1.0) {
                        // Line: L x2 y2
                        sb.append("L ").append(formatCoord(seg[3], options.roundCoords)).append(" ")
                          .append(formatCoord(seg[4], options.roundCoords)).append(" ");
                    } else if (seg[0] == 2.0) {
                        // Quadratic Bezier: Q cx cy x2 y2
                        sb.append("Q ").append(formatCoord(seg[3], options.roundCoords)).append(" ")
                          .append(formatCoord(seg[4], options.roundCoords)).append(" ")
                          .append(formatCoord(seg[5], options.roundCoords)).append(" ")
                          .append(formatCoord(seg[6], options.roundCoords)).append(" ");
                    }
                }
                sb.append("Z\" />\n");
            }
        }

        sb.append("</svg>\n");
        return sb.toString();
    }

    private String formatCoord(double val, int decimalPlaces) {
        if (decimalPlaces <= 0) {
            return String.valueOf(Math.round(val));
        }
        double factor = Math.pow(10, decimalPlaces);
        double rounded = Math.round(val * factor) / factor;
        if (rounded == (long) rounded) {
            return String.format("%d", (long) rounded);
        }
        return String.valueOf(rounded);
    }
}
