package com.schemapart.client.wordart;

import com.schemapart.core.BlockInfo;

import java.util.List;

/**
 * The 16 Minecraft map colours, the four block families supported by the word
 * map-art generator, and the mapping from a palette colour to a real block id.
 *
 * <p>Colour keys follow the classic Minecraft names; the {@code silver} colour
 * is called {@code light_gray} in modern block ids, so it is translated when a
 * block id is built.</p>
 */
public final class WordArtPalette {
    /** Background index meaning "no block" (air). */
    public static final int AIR = -1;
    /** Index of the black colour, used for the shadow. */
    public static final int BLACK_INDEX = 15;
    /** Index of the white colour, the default text colour. */
    public static final int WHITE_INDEX = 0;

    /** One of the 16 Minecraft map colours. */
    public record Color(String id, String zhName, String abbr, int rgb) {
        public int argb() {
            return 0xFF000000 | rgb;
        }

        public int r() {
            return (rgb >>> 16) & 0xFF;
        }

        public int g() {
            return (rgb >>> 8) & 0xFF;
        }

        public int b() {
            return rgb & 0xFF;
        }
    }

    /** One of the four supported block families. */
    public record BlockType(String suffix, String zhName) {
    }

    public static final List<Color> COLORS = List.of(
            new Color("white", "白色", "W", 0xFFFFFF),
            new Color("orange", "橙色", "O", 0xFFA300),
            new Color("magenta", "品红色", "M", 0xFF00FF),
            new Color("light_blue", "淡蓝色", "LB", 0x00BFFF),
            new Color("yellow", "黄色", "Y", 0xFFFF00),
            new Color("lime", "黄绿色", "L", 0x00FF00),
            new Color("pink", "粉红色", "P", 0xFFC0CB),
            new Color("gray", "灰色", "Gy", 0x808080),
            new Color("silver", "淡灰色", "S", 0xC0C0C0),
            new Color("cyan", "青色", "C", 0x00FFFF),
            new Color("purple", "紫色", "V", 0x800080),
            new Color("blue", "蓝色", "B", 0x0000FF),
            new Color("brown", "棕色", "Br", 0xA52A2A),
            new Color("green", "绿色", "G", 0x008000),
            new Color("red", "红色", "R", 0xFF0000),
            new Color("black", "黑色", "Bl", 0x000000)
    );

    public static final List<BlockType> BLOCK_TYPES = List.of(
            new BlockType("wool", "羊毛"),
            new BlockType("terracotta", "陶瓦"),
            new BlockType("carpet", "羊毛地毯"),
            new BlockType("concrete", "混凝土"),
            new BlockType("concrete_powder", "混凝土粉末")
    );

    private WordArtPalette() {
    }

    public static int colorCount() {
        return COLORS.size();
    }

    public static Color color(int index) {
        return COLORS.get(clamp(index, 0, COLORS.size() - 1));
    }

    public static int blockTypeCount() {
        return BLOCK_TYPES.size();
    }

    public static BlockType blockType(int index) {
        return BLOCK_TYPES.get(clamp(index, 0, BLOCK_TYPES.size() - 1));
    }

    /** Colour part of a block id, translating Minecraft's legacy {@code silver}. */
    public static String blockColorName(int colorIndex) {
        String id = color(colorIndex).id();
        return "silver".equals(id) ? "light_gray" : id;
    }

    /** Full block id such as {@code minecraft:light_gray_concrete}. */
    public static String blockId(int colorIndex, int blockTypeIndex) {
        return "minecraft:" + blockColorName(colorIndex) + "_" + blockType(blockTypeIndex).suffix();
    }

    /**
     * Resolves a block from the scanned registry, or builds a synthetic
     * {@link BlockInfo} when the registry does not contain it.
     */
    public static BlockInfo blockInfo(int colorIndex, int blockTypeIndex, List<BlockInfo> known) {
        Color color = color(colorIndex);
        BlockType type = blockType(blockTypeIndex);
        String id = blockId(colorIndex, blockTypeIndex);
        if (known != null) {
            for (BlockInfo block : known) {
                if (block != null && id.equals(block.id)) return block;
            }
        }
        boolean carpet = "carpet".equals(type.suffix());
        boolean powder = "concrete_powder".equals(type.suffix());
        return new BlockInfo(id, id, color.zhName() + type.zhName(), color.argb(),
                true, false, false, true, !carpet, powder, id);
    }

    /** Nearest of the 16 colours for an arbitrary RGB value. */
    public static Color nearestColor(int rgb) {
        int r = (rgb >>> 16) & 0xFF;
        int g = (rgb >>> 8) & 0xFF;
        int b = rgb & 0xFF;
        Color best = COLORS.get(0);
        int bestDistance = Integer.MAX_VALUE;
        for (Color color : COLORS) {
            int dr = r - color.r();
            int dg = g - color.g();
            int db = b - color.b();
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = color;
            }
        }
        return best;
    }

    /** Index of a colour id, or {@code -1}. */
    public static int indexOf(String id) {
        if (id == null) return -1;
        for (int i = 0; i < COLORS.size(); i++) {
            if (COLORS.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
