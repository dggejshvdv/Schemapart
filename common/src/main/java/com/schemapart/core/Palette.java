package com.schemapart.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the effective one-block-per-map-colour palette. */
public final class Palette {
    public static final class Entry {
        public final BlockInfo block;
        public final int argb;
        public final double[] lab;

        Entry(BlockInfo block, int argb) {
            this.block = block;
            this.argb = argb;
            this.lab = ColorMath.toLab(argb);
        }
    }

    private Palette() {
    }

    public static List<Entry> build(List<BlockInfo> all, ArtSettings settings) {
        Map<Integer, BlockInfo> best = new LinkedHashMap<>();
        List<BlockInfo> candidates = new ArrayList<>(settings.filtered(all));
        candidates.sort(Comparator.comparing(b -> b.id));
        for (BlockInfo block : candidates) {
            if (block.mapColor == 0 || (block.mapColor >>> 24) == 0) continue;
            BlockInfo old = best.get(block.mapColor);
            if (old == null || block.preferenceScore() > old.preferenceScore()
                    || (block.preferenceScore() == old.preferenceScore() && block.id.compareTo(old.id) < 0)) {
                best.put(block.mapColor, block);
            }
        }
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<Integer, BlockInfo> e : best.entrySet()) {
            entries.add(new Entry(e.getValue(), e.getKey()));
        }
        entries.sort(Comparator.comparingInt(e -> e.argb));
        return entries;
    }
}
