package com.vectorizer.core.model;

public class VectorResult {
    public final String svgContent;
    public final int pathCount;
    public final int colorCount;
    public final long executionTimeMs;
    public final int width;
    public final int height;

    public VectorResult(String svgContent, int pathCount, int colorCount, long executionTimeMs, int width, int height) {
        this.svgContent = svgContent;
        this.pathCount = pathCount;
        this.colorCount = colorCount;
        this.executionTimeMs = executionTimeMs;
        this.width = width;
        this.height = height;
    }
}
