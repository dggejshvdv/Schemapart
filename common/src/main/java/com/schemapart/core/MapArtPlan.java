package com.schemapart.core;

import java.util.ArrayList;
import java.util.List;

/** Final quantized map-art plan. */
public final class MapArtPlan {
    public final String sourceName;
    public final int width;
    public final int height;
    /** One index per output block into {@link #blocks}. */
    public final int[] blockIndex;
    public final List<BlockInfo> blocks;
    /** ARGB map-colour preview, width * height. */
    public final int[] previewArgb;
    /** Number of placed blocks per palette entry. */
    public final int[] counts;

    public MapArtPlan(String sourceName, int width, int height, int[] blockIndex,
                      List<BlockInfo> blocks, int[] previewArgb, int[] counts) {
        this.sourceName = sourceName;
        this.width = width;
        this.height = height;
        this.blockIndex = blockIndex;
        this.blocks = blocks;
        this.previewArgb = previewArgb;
        this.counts = counts;
    }

    public int placedBlocks() {
        int total = 0;
        for (int c : counts) total += c;
        return total;
    }

    public int uniqueBlocks() {
        return blocks.size();
    }

    public List<BlockInfo> usedBlocks() {
        List<BlockInfo> used = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) used.add(blocks.get(i));
        }
        return used;
    }
}
