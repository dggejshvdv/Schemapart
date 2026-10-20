package com.schemapart.client.wordart;

import java.awt.Font;

/** One independently editable character/symbol placed on the word-art canvas. */
public final class WordElement {
    public String text = "你";
    public String fontFamily = Font.SANS_SERIF;
    public int colorIndex = 0;
    public int blockTypeIndex = 0;
    public int x;
    public int y;
    public int width = WordArtDocument.MAP_SIZE;
    public int height = WordArtDocument.MAP_SIZE;
    public boolean shadow = true;

    public WordElement() {
    }

    public WordElement(String text, int x, int y) {
        this.text = text;
        this.x = x;
        this.y = y;
        clampSize();
    }

    public WordElement copy() {
        WordElement copy = new WordElement();
        copy.text = text;
        copy.fontFamily = fontFamily;
        copy.colorIndex = colorIndex;
        copy.blockTypeIndex = blockTypeIndex;
        copy.x = x;
        copy.y = y;
        copy.width = width;
        copy.height = height;
        copy.shadow = shadow;
        return copy;
    }

    /** Minimum editable size for this character, in canvas pixels. */
    public int[] minimumSize() {
        return minimumSize(text);
    }

    public void clampSize() {
        int[] minimum = minimumSize();
        width = clamp(width, minimum[0], WordArtDocument.MAX_CANVAS_SIZE);
        height = clamp(height, minimum[1], WordArtDocument.MAX_CANVAS_SIZE);
    }

    public void clampPosition(int canvasWidth, int canvasHeight) {
        x = clamp(x, 0, Math.max(0, canvasWidth - width));
        y = clamp(y, 0, Math.max(0, canvasHeight - height));
    }

    public String categoryLabel() {
        int cp = firstCodePoint();
        if (Character.isLowerCase(cp)) return "小写";
        if (Character.isUpperCase(cp)) return "大写";
        if (isCjk(cp)) return "文字";
        if (isFullWidth(cp)) return "全角";
        if (Character.isLetterOrDigit(cp)) return "文字";
        return "半角符号";
    }

    public String minSizeLabel() {
        int[] minimum = minimumSize();
        return minimum[0] + "×" + minimum[1];
    }

    public int firstCodePoint() {
        if (text == null || text.isEmpty()) return ' ';
        return text.codePointAt(0);
    }

    public static int[] minimumSize(String text) {
        int cp = (text == null || text.isEmpty()) ? ' ' : text.codePointAt(0);
        if (Character.isLowerCase(cp)) return new int[]{32, 32};
        if (Character.isUpperCase(cp)) return new int[]{32, 64};
        if (isCjk(cp)) return new int[]{64, 64};
        if (isFullWidth(cp)) return new int[]{32, 32};
        if (Character.isLetterOrDigit(cp)) return new int[]{64, 64};
        if (cp < 0x80) return new int[]{16, 32};
        return new int[]{32, 32};
    }

    public static boolean isCjk(int cp) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HANGUL_JAMO
                || block == Character.UnicodeBlock.BOPOMOFO;
    }

    public static boolean isFullWidth(int cp) {
        if (cp >= 0xFF00 && cp <= 0xFFEF) return true;
        if (cp >= 0x3000 && cp <= 0x303F) return true;
        if (cp >= 0xFE10 && cp <= 0xFE6F) return true;
        if (cp >= 0x20000 && cp <= 0x3FFFF) return true;
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
