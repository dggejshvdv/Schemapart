package com.schemapart.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Maps every scaled picture pixel onto the closest map colour. */
public final class Quantizer {
    private Quantizer() {
    }

    public static MapArtPlan quantize(String sourceName, PreparedImage image, List<Palette.Entry> palette,
                                      boolean dither) {
        if (palette.isEmpty()) {
            throw new IllegalStateException("No enabled blocks have a map colour.");
        }
        int n = image.width * image.height;
        int[] index = new int[n];
        int[] preview = new int[n];
        int[] counts = new int[palette.size()];
        Map<Integer, Integer> cache = new HashMap<>(4096);

        if (!dither) {
            for (int i = 0; i < n; i++) {
                int argb = image.argb[i];
                int best = nearest(palette, argb, cache);
                index[i] = best;
                preview[i] = palette.get(best).argb;
                counts[best]++;
            }
        } else {
            double[] errR = new double[image.width + 2];
            double[] errG = new double[image.width + 2];
            double[] errB = new double[image.width + 2];
            double[] nextR = new double[image.width + 2];
            double[] nextG = new double[image.width + 2];
            double[] nextB = new double[image.width + 2];
            for (int y = 0; y < image.height; y++) {
                java.util.Arrays.fill(nextR, 0);
                java.util.Arrays.fill(nextG, 0);
                java.util.Arrays.fill(nextB, 0);
                for (int x = 0; x < image.width; x++) {
                    int i = y * image.width + x;
                    int src = image.argb[i];
                    int r = ColorMath.clamp((int) Math.round(ColorMath.red(src) + errR[x + 1]));
                    int g = ColorMath.clamp((int) Math.round(ColorMath.green(src) + errG[x + 1]));
                    int b = ColorMath.clamp((int) Math.round(ColorMath.blue(src) + errB[x + 1]));
                    int best = nearest(palette, ColorMath.rgb(r, g, b), cache);
                    int out = palette.get(best).argb;
                    index[i] = best;
                    preview[i] = out;
                    counts[best]++;
                    double dr = r - ColorMath.red(out);
                    double dg = g - ColorMath.green(out);
                    double db = b - ColorMath.blue(out);
                    errR[x + 2] += dr * 7.0 / 16.0;
                    errG[x + 2] += dg * 7.0 / 16.0;
                    errB[x + 2] += db * 7.0 / 16.0;
                    nextR[x] += dr * 3.0 / 16.0;
                    nextG[x] += dg * 3.0 / 16.0;
                    nextB[x] += db * 3.0 / 16.0;
                    nextR[x + 1] += dr * 5.0 / 16.0;
                    nextG[x + 1] += dg * 5.0 / 16.0;
                    nextB[x + 1] += db * 5.0 / 16.0;
                    nextR[x + 2] += dr * 1.0 / 16.0;
                    nextG[x + 2] += dg * 1.0 / 16.0;
                    nextB[x + 2] += db * 1.0 / 16.0;
                }
                double[] swap;
                swap = errR; errR = nextR; nextR = swap;
                swap = errG; errG = nextG; nextG = swap;
                swap = errB; errB = nextB; nextB = swap;
            }
        }

        List<BlockInfo> blocks = new ArrayList<>(palette.size());
        for (Palette.Entry e : palette) blocks.add(e.block);
        return new MapArtPlan(sourceName, image.width, image.height, index, blocks, preview, counts);
    }

    public static int nearest(List<Palette.Entry> palette, int argb, Map<Integer, Integer> cache) {
        Integer cached = cache.get(argb);
        if (cached != null) return cached;
        double[] lab = ColorMath.toLab(argb);
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < palette.size(); i++) {
            Palette.Entry e = palette.get(i);
            double d = ColorMath.labDistanceSq(lab, e.lab[0], e.lab[1], e.lab[2]);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        cache.put(argb, best);
        return best;
    }
}

