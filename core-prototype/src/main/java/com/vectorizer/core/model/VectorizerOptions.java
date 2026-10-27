package com.vectorizer.core.model;

/**
 * Options to configure image preprocessing, tracing, and SVG generation.
 */
public class VectorizerOptions {
    public int numberOfColors = 16;
    public float pathOmit = 8.0f;
    public float lineThreshold = 1.0f;
    public float quadThreshold = 1.0f;
    public float blurRadius = 0.0f;
    public float blurDelta = 20.0f;
    public int colorQuantCycles = 3;
    public float scale = 1.0f;
    public int roundCoords = 1;
    public boolean viewBox = true;
    public boolean binarize = false;
    public int binarizeThreshold = -1; // -1 for auto Otsu
    public boolean isPixelArtMode = false;
    public boolean removeWhiteBackground = false;
    public int maxDimension = 1280; // Memory guard downscale cap

    public VectorizerOptions() {}

    public VectorizerOptions copy() {
        VectorizerOptions opt = new VectorizerOptions();
        opt.numberOfColors = this.numberOfColors;
        opt.pathOmit = this.pathOmit;
        opt.lineThreshold = this.lineThreshold;
        opt.quadThreshold = this.quadThreshold;
        opt.blurRadius = this.blurRadius;
        opt.blurDelta = this.blurDelta;
        opt.colorQuantCycles = this.colorQuantCycles;
        opt.scale = this.scale;
        opt.roundCoords = this.roundCoords;
        opt.viewBox = this.viewBox;
        opt.binarize = this.binarize;
        opt.binarizeThreshold = this.binarizeThreshold;
        opt.isPixelArtMode = this.isPixelArtMode;
        opt.removeWhiteBackground = this.removeWhiteBackground;
        opt.maxDimension = this.maxDimension;
        return opt;
    }
}
