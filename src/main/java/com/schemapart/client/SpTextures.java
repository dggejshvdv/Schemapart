package com.schemapart.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.schemapart.core.ColorMath;
import com.schemapart.core.ImageArt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Dynamic textures used for thumbnails and the large map-art preview. */
public final class SpTextures {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, SpTextures.Entry> eldest) {
            if (size() > 48) {
                eldest.getValue().close();
                return true;
            }
            return false;
        }
    };

    private SpTextures() {
    }

    public static final class Entry implements AutoCloseable {
        final Identifier id;
        final int width;
        final int height;
        private boolean closed;

        Entry(Identifier id, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }

        public void draw(GuiGraphics g, int x, int y, int w, int h) {
            if (closed) return;
            g.blit(id, x, y, x + w, y + h, 0.0f, 1.0f, 0.0f, 1.0f);
        }

        public void drawNative(GuiGraphics g, int x, int y) {
            draw(g, x, y, width, height);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }

    public static Entry create(int[] argb, int width, int height) {
        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int c = argb[y * width + x];
                int a = (c >>> 24) & 0xFF;
                int r = (c >>> 16) & 0xFF;
                int g = (c >>> 8) & 0xFF;
                int b = c & 0xFF;
                image.setPixel(x, y, c);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath("schemapart",
                "preview/" + COUNTER.incrementAndGet());
        DynamicTexture texture = new DynamicTexture(() -> "schemapart-preview", image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return new Entry(id, width, height);
    }

    public static Entry fromFile(Path path, int box) {
        String key = path.toAbsolutePath() + "|" + fileStamp(path) + "|" + box;
        Entry cached = CACHE.get(key);
        if (cached != null) return cached;
        try {
            BufferedImage source = ImageArt.read(path);
            BufferedImage scaled = contain(source, box);
            int[] argb = scaled.getRGB(0, 0, scaled.getWidth(), scaled.getHeight(),
                    null, 0, scaled.getWidth());
            Entry entry = create(argb, scaled.getWidth(), scaled.getHeight());
            CACHE.put(key, entry);
            return entry;
        } catch (IOException e) {
            return null;
        }
    }

    public static BufferedImage contain(BufferedImage source, int box) {
        double scale = Math.min(box / (double) source.getWidth(), box / (double) source.getHeight());
        int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage out = new BufferedImage(box, box, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(new java.awt.Color(24, 26, 30));
            g.fillRect(0, 0, box, box);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, (box - w) / 2, (box - h) / 2, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static long fileStamp(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis() ^ Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    public static int[] shadeForBuild(int[] argb, int width, int height, int factor) {
        int[] out = new int[width * factor * height * factor];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int base = argb[y * width + x];
                int hash = (x * 7349 + y * 9151) & 0xFF;
                for (int py = 0; py < factor; py++) {
                    for (int px = 0; px < factor; px++) {
                        double shade = 1.0;
                        if (px == 0 || py == 0) shade = 1.10;
                        if (px == factor - 1 || py == factor - 1) shade = 0.78;
                        if (px == 0 && py == 0) shade = 1.18;
                        if (px == factor - 1 && py == factor - 1) shade = 0.68;
                        shade *= 0.94 + ((hash + px * 37 + py * 61) & 15) / 180.0;
                        int c = ColorMath.multiply(base, shade);
                        int ox = x * factor + px;
                        int oy = y * factor + py;
                        out[oy * width * factor + ox] = c;
                    }
                }
            }
        }
        return out;
    }
}
