package com.vectorizer.core.model;

/**
 * Curated preset modes tailored to different graphic types,
 * solving the problem of overly messy/convoluted paths ("garis mbulet").
 */
public enum VectorPreset {
    LOGO_FLAT_GRAPHIC(
        "Logo & Clipart",
        "Menghaluskan kurva Bezier, membersihkan bintik kompresi, dan meratakan warna bidang agar garis tidak kusut."
    ),
    LINE_ART_SKETCH(
        "Line Art & Sketsa",
        "Khusus gambar pensil, pulpen, sketsa, dan tanda tangan. Binarisasi adaptif otomatis mencegah garis berlubang (double-outline)."
    ),
    COLOR_DETAILED(
        "Foto / Ilustrasi Warna",
        "Menangkap gradasi dan detail warna kaya untuk foto serta ilustrasi dengan clustering multi-layer."
    ),
    PIXEL_ART(
        "Pixel Art / Retro",
        "Menjaga sudut siku-siku 90 derajat tetap tajam dan kotak, tanpa kurva melengkung yang merusak estetika retro."
    ),
    MONOCHROME_SILHOUETTE(
        "Siluet & Monokrom",
        "Kontras 1-bit tinggi dengan pembersihan bintik kotoran ekstrem untuk stempel, siluet, dan cetakan sablon."
    );

    public final String displayName;
    public final String description;

    VectorPreset(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public VectorizerOptions createOptions() {
        VectorizerOptions opt = new VectorizerOptions();
        switch (this) {
            case LOGO_FLAT_GRAPHIC:
                opt.numberOfColors = 8;
                opt.blurRadius = 2.0f;
                opt.blurDelta = 25.0f;
                opt.pathOmit = 16.0f;       // Discard small noise loops
                opt.lineThreshold = 1.0f;
                opt.quadThreshold = 1.0f;
                opt.roundCoords = 1;
                opt.removeWhiteBackground = false;
                break;

            case LINE_ART_SKETCH:
                opt.binarize = true;
                opt.binarizeThreshold = -1; // Auto Otsu threshold
                opt.numberOfColors = 2;
                opt.pathOmit = 12.0f;
                opt.lineThreshold = 0.5f;
                opt.quadThreshold = 0.8f;
                opt.removeWhiteBackground = true;
                break;

            case COLOR_DETAILED:
                opt.numberOfColors = 24;
                opt.blurRadius = 1.0f;
                opt.blurDelta = 16.0f;
                opt.pathOmit = 6.0f;
                opt.lineThreshold = 0.8f;
                opt.quadThreshold = 1.0f;
                opt.colorQuantCycles = 4;
                opt.roundCoords = 1;
                opt.removeWhiteBackground = false;
                break;

            case PIXEL_ART:
                opt.isPixelArtMode = true;
                opt.numberOfColors = 16;
                opt.blurRadius = 0.0f;
                opt.pathOmit = 0.0f;        // Keep every single pixel
                opt.lineThreshold = 0.0f;
                opt.quadThreshold = 0.0f;
                opt.roundCoords = 0;        // Exact coordinates
                opt.removeWhiteBackground = false;
                break;

            case MONOCHROME_SILHOUETTE:
                opt.binarize = true;
                opt.binarizeThreshold = -1;
                opt.numberOfColors = 2;
                opt.pathOmit = 32.0f;       // Aggressive speckle filter
                opt.lineThreshold = 1.2f;
                opt.quadThreshold = 1.2f;
                opt.removeWhiteBackground = true;
                break;
        }
        return opt;
    }
}
