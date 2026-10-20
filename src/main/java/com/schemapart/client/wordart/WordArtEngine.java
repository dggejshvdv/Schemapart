package com.schemapart.client.wordart;

import com.schemapart.core.BlockInfo;
import com.schemapart.core.MapArtPlan;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link WordArtDocument} into a 16-colour map-art plan. Every element
 * keeps its own size, position, font, colour and block family; overlapping
 * pixels are resolved with the user's block-family priority.
 */
public final class WordArtEngine {
    private static final byte LAYER_NONE = 0;
    private static final byte LAYER_SHADOW = 1;
    private static final byte LAYER_TEXT = 2;
    private static final int SHADOW = 2;

    private static final String[] FALLBACK_FAMILIES = {
            "Microsoft YaHei", "SimSun", "Noto Sans CJK SC", "Noto Sans SC",
            Font.SANS_SERIF, Font.DIALOG, Font.SERIF, Font.MONOSPACED
    };

    public record Result(MapArtPlan plan, int[] canvasArgb, boolean overlapDifferentFamily, String warning) {
    }

    private WordArtEngine() {
    }

    public static Result build(WordArtDocument document, List<BlockInfo> knownBlocks) {
        document.normalize();
        int width = document.widthPx();
        int height = document.heightPx();
        int total = width * height;
        int[] preview = new int[total];
        int[] blockIndex = new int[total];
        int[] families = new int[total];
        java.util.Arrays.fill(blockIndex, -1);
        java.util.Arrays.fill(families, -1);

        List<BlockInfo> blocks = new ArrayList<>();
        Map<String, Integer> blockIndices = new HashMap<>();
        boolean overlapDifferentFamily = false;
        StringBuilder warning = new StringBuilder();

        int backgroundBlock = -1;
        if (document.backgroundColorIndex >= 0) {
            backgroundBlock = addBlock(blocks, blockIndices,
                    WordArtPalette.blockInfo(document.backgroundColorIndex,
                            document.backgroundBlockTypeIndex, knownBlocks));
            int color = WordArtPalette.color(document.backgroundColorIndex).argb();
            for (int i = 0; i < total; i++) {
                preview[i] = color;
                blockIndex[i] = backgroundBlock;
            }
        }

        for (WordElement element : document.elements) {
            if (element == null || element.text == null || element.text.isEmpty()) continue;
            ElementImage image = renderElement(element);
            if (image.warning != null && warning.indexOf(image.warning) < 0) {
                if (warning.length() > 0) warning.append("; ");
                warning.append(image.warning);
            }
            int family = element.blockTypeIndex;
            for (int py = 0; py < image.height; py++) {
                int cy = element.y + py;
                if (cy < 0 || cy >= height) continue;
                for (int px = 0; px < image.width; px++) {
                    int cx = element.x + px;
                    if (cx < 0 || cx >= width) continue;
                    byte layer = image.layers[py * image.width + px];
                    if (layer == LAYER_NONE) continue;
                    int at = cy * width + cx;
                    int existingFamily = families[at];
                    if (existingFamily >= 0 && existingFamily != family) {
                        overlapDifferentFamily = true;
                        if (document.priorityRank(family) > document.priorityRank(existingFamily)) {
                            continue;
                        }
                    } else if (existingFamily == family) {
                        // Same family: the later element is the visible one.
                    }
                    int colorIndex = layer == LAYER_TEXT
                            ? element.colorIndex : WordArtPalette.BLACK_INDEX;
                    BlockInfo block = WordArtPalette.blockInfo(colorIndex, family, knownBlocks);
                    int blockId = addBlock(blocks, blockIndices, block);
                    blockIndex[at] = blockId;
                    families[at] = family;
                    preview[at] = image.argb[py * image.width + px];
                }
            }
        }

        int[] counts = new int[blocks.size()];
        for (int index : blockIndex) {
            if (index >= 0 && index < counts.length) counts[index]++;
        }
        MapArtPlan plan = new MapArtPlan(document.sourceName(), width, height,
                blockIndex, List.copyOf(blocks), preview, counts);
        return new Result(plan, preview, overlapDifferentFamily,
                warning.length() == 0 ? null : warning.toString());
    }

    public static int[] renderCanvas(WordArtDocument document, List<BlockInfo> knownBlocks) {
        return build(document, knownBlocks).canvasArgb();
    }

    private record ElementImage(int width, int height, int[] argb, byte[] layers,
                                int fontSize, String warning) {
    }

    private static ElementImage renderElement(WordElement element) {
        int width = Math.max(1, element.width);
        int height = Math.max(1, element.height);
        String text = element.text;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        Graphics2D maskGraphics = mask.createGraphics();
        try {
            setup(graphics);
            setup(maskGraphics);
            maskGraphics.setColor(Color.BLACK);
            maskGraphics.fillRect(0, 0, width, height);

            String family = resolveFamily(element.fontFamily, text);
            String warning = family.equalsIgnoreCase(element.fontFamily)
                    ? null : "字体不含该字符，已回退到 " + family;
            int margin = Math.max(1, Math.min(width, height) / 32);
            Fit fit = fit(graphics, family, text, width, height, margin);
            graphics.setFont(fit.font);
            maskGraphics.setFont(fit.font);

            int x = (width - fit.textWidth) / 2;
            int baseline = (height - fit.textHeight) / 2 + fit.ascent;
            if (element.shadow) {
                graphics.setColor(Color.BLACK);
                maskGraphics.setColor(new Color(0xFF808080, true));
                graphics.drawString(text, x + SHADOW, baseline + SHADOW);
                maskGraphics.drawString(text, x + SHADOW, baseline + SHADOW);
            }
            graphics.setColor(new Color(WordArtPalette.color(element.colorIndex).argb(), true));
            maskGraphics.setColor(Color.WHITE);
            graphics.drawString(text, x, baseline);
            maskGraphics.drawString(text, x, baseline);

            int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
            int[] maskRgb = mask.getRGB(0, 0, width, height, null, 0, width);
            return new ElementImage(width, height, argb, classify(maskRgb), fit.font.getSize(), warning);
        } finally {
            graphics.dispose();
            maskGraphics.dispose();
        }
    }

    private record Fit(Font font, int textWidth, int textHeight, int ascent) {
    }

    private static Fit fit(Graphics2D graphics, String family, String text,
                           int width, int height, int margin) {
        Fit best = null;
        int max = Math.max(8, Math.max(width, height) * 2);
        for (int size = 1; size <= max; size++) {
            Font font = new Font(family, Font.PLAIN, size);
            FontMetrics metrics = graphics.getFontMetrics(font);
            int textWidth = metrics.stringWidth(text);
            int textHeight = metrics.getAscent() + metrics.getDescent();
            if (textWidth > width - margin * 2 || textHeight > height - margin * 2) break;
            best = new Fit(font, textWidth, textHeight, metrics.getAscent());
        }
        if (best == null) {
            Font font = new Font(family, Font.PLAIN, 1);
            FontMetrics metrics = graphics.getFontMetrics(font);
            best = new Fit(font, metrics.stringWidth(text),
                    metrics.getAscent() + metrics.getDescent(), metrics.getAscent());
        }
        return best;
    }

    private static void setup(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    }

    private static byte[] classify(int[] maskRgb) {
        byte[] layers = new byte[maskRgb.length];
        for (int i = 0; i < maskRgb.length; i++) {
            int red = (maskRgb[i] >>> 16) & 0xFF;
            if (red >= 192) layers[i] = LAYER_TEXT;
            else if (red >= 48) layers[i] = LAYER_SHADOW;
        }
        return layers;
    }

    private static String resolveFamily(String chosen, String sample) {
        if (chosen == null || chosen.isBlank()) chosen = Font.SANS_SERIF;
        if (supports(chosen, sample)) return chosen;
        for (String fallback : FALLBACK_FAMILIES) {
            if (supports(fallback, sample)) return fallback;
        }
        return Font.SANS_SERIF;
    }

    private static boolean supports(String family, String sample) {
        if (sample == null || sample.isBlank()) return true;
        try {
            return new Font(family, Font.PLAIN, 12).canDisplayUpTo(sample) < 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int addBlock(List<BlockInfo> blocks, Map<String, Integer> indexById, BlockInfo block) {
        Integer existing = indexById.get(block.id);
        if (existing != null) return existing;
        int index = blocks.size();
        blocks.add(block);
        indexById.put(block.id, index);
        return index;
    }
}
