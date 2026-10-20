package com.schemapart.client.wordart;

import com.schemapart.client.SchemapartClient;
import com.schemapart.core.FileOps;

import java.awt.Font;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Editable word-art document: a canvas made of 128x128 map cells plus any
 * number of independently placed characters/symbols.
 */
public final class WordArtDocument {
    public static final int MAP_SIZE = 128;
    public static final int MIN_MAPS = 1;
    public static final int MAX_MAPS = 16;
    public static final int MAX_CANVAS_SIZE = MAP_SIZE * MAX_MAPS;
    public static final int MAX_ELEMENTS = 512;

    public int mapsX = 2;
    public int mapsY = 1;
    public int backgroundColorIndex = -1;
    public int backgroundBlockTypeIndex = 0;
    public boolean shadow = true;
    public boolean priorityConfirmed = false;

    /** Block-family indices, highest priority first. */
    public final List<Integer> priority = new ArrayList<>(List.of(0, 1, 2, 3, 4));
    public final List<WordElement> elements = new ArrayList<>();

    public static WordArtDocument createDefault() {
        WordArtDocument document = new WordArtDocument();
        document.mapsX = 2;
        document.mapsY = 1;
        WordElement first = new WordElement("你", 0, 0);
        first.colorIndex = WordArtPalette.WHITE_INDEX;
        first.blockTypeIndex = 0;
        document.elements.add(first);
        WordElement second = new WordElement("好", MAP_SIZE, 0);
        second.colorIndex = WordArtPalette.WHITE_INDEX;
        second.blockTypeIndex = 0;
        document.elements.add(second);
        return document;
    }

    public int widthPx() {
        return mapsX * MAP_SIZE;
    }

    public int heightPx() {
        return mapsY * MAP_SIZE;
    }

    public void normalize() {
        mapsX = clamp(mapsX, MIN_MAPS, MAX_MAPS);
        mapsY = clamp(mapsY, MIN_MAPS, MAX_MAPS);
        backgroundColorIndex = clamp(backgroundColorIndex, -1, WordArtPalette.colorCount() - 1);
        backgroundBlockTypeIndex = clamp(backgroundBlockTypeIndex, 0, WordArtPalette.blockTypeCount() - 1);
        if (priority.size() != WordArtPalette.blockTypeCount()) {
            priority.clear();
            for (int i = 0; i < WordArtPalette.blockTypeCount(); i++) priority.add(i);
        }
        boolean[] seen = new boolean[WordArtPalette.blockTypeCount()];
        for (int i = 0; i < priority.size(); i++) {
            int value = clamp(priority.get(i), 0, WordArtPalette.blockTypeCount() - 1);
            if (seen[value]) {
                for (int replacement = 0; replacement < seen.length; replacement++) {
                    if (!seen[replacement]) {
                        value = replacement;
                        break;
                    }
                }
            }
            seen[value] = true;
            priority.set(i, value);
        }
        if (elements.size() > MAX_ELEMENTS) {
            elements.subList(MAX_ELEMENTS, elements.size()).clear();
        }
        for (WordElement element : elements) {
            if (element == null) continue;
            element.clampSize();
            element.clampPosition(widthPx(), heightPx());
        }
    }

    public int priorityRank(int blockTypeIndex) {
        int index = priority.indexOf(blockTypeIndex);
        return index < 0 ? WordArtPalette.blockTypeCount() : index;
    }

    public WordElement findElementAt(int x, int y) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            WordElement element = elements.get(i);
            if (element == null) continue;
            if (x >= element.x && x < element.x + element.width
                    && y >= element.y && y < element.y + element.height) {
                return element;
            }
        }
        return null;
    }

    public int indexOf(WordElement element) {
        return elements.indexOf(element);
    }

    /**
     * Adds one element per Unicode code point and places it at the next free
     * 128x128 cell. The canvas grows when it runs out of cells.
     */
    public List<WordElement> addText(String text, String fontFamily, int colorIndex,
                                     int blockTypeIndex, boolean shadow) {
        List<WordElement> added = new ArrayList<>();
        if (text == null || text.isBlank()) return added;
        int cp;
        for (int i = 0; i < text.length() && elements.size() < MAX_ELEMENTS; i += Character.charCount(cp)) {
            cp = text.codePointAt(i);
            if (Character.isISOControl(cp)) continue;
            String value = new String(Character.toChars(cp));
            WordElement element = new WordElement(value, 0, 0);
            element.fontFamily = fontFamily == null ? Font.SANS_SERIF : fontFamily;
            element.colorIndex = colorIndex;
            element.blockTypeIndex = blockTypeIndex;
            element.shadow = shadow;
            place(element);
            elements.add(element);
            added.add(element);
        }
        return added;
    }

    /** Places an element at the first free map cell, growing the canvas if needed. */
    public void place(WordElement element) {
        element.clampSize();
        for (int y = 0; y < mapsY; y++) {
            for (int x = 0; x < mapsX; x++) {
                int px = x * MAP_SIZE;
                int py = y * MAP_SIZE;
                if (isFree(px, py, MAP_SIZE, MAP_SIZE)) {
                    element.x = px;
                    element.y = py;
                    return;
                }
            }
        }
        while (mapsX < MAX_MAPS) {
            mapsX++;
            for (int y = 0; y < mapsY; y++) {
                int px = (mapsX - 1) * MAP_SIZE;
                int py = y * MAP_SIZE;
                if (isFree(px, py, MAP_SIZE, MAP_SIZE)) {
                    element.x = px;
                    element.y = py;
                    return;
                }
            }
        }
        while (mapsY < MAX_MAPS) {
            mapsY++;
            for (int x = 0; x < mapsX; x++) {
                int px = x * MAP_SIZE;
                int py = (mapsY - 1) * MAP_SIZE;
                if (isFree(px, py, MAP_SIZE, MAP_SIZE)) {
                    element.x = px;
                    element.y = py;
                    return;
                }
            }
        }
        element.x = 0;
        element.y = 0;
    }

    private boolean isFree(int x, int y, int width, int height) {
        for (WordElement other : elements) {
            if (other == null) continue;
            if (x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y) {
                return false;
            }
        }
        return true;
    }

    public String sourceName() {
        StringBuilder builder = new StringBuilder("wordart");
        for (WordElement element : elements) {
            if (builder.length() > 32) break;
            if (element != null && element.text != null && !element.text.isBlank()) {
                builder.append('_').append(element.text);
            }
        }
        return FileOps.safeBaseName(builder.toString());
    }

    public static WordArtDocument load(Path file) {
        if (file == null || !Files.isRegularFile(file)) return createDefault();
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            SchemapartClient.LOGGER.error("Could not load word-art document", e);
            return createDefault();
        }
        WordArtDocument document = new WordArtDocument();
        document.mapsX = intProperty(properties, "canvasX", document.mapsX);
        document.mapsY = intProperty(properties, "canvasY", document.mapsY);
        document.backgroundColorIndex = intProperty(properties, "background", -1);
        document.backgroundBlockTypeIndex = intProperty(properties, "backgroundBlock", 0);
        document.shadow = Boolean.parseBoolean(properties.getProperty("shadow", "true"));
        document.priorityConfirmed = Boolean.parseBoolean(properties.getProperty("priorityConfirmed", "false"));
        String priority = properties.getProperty("priority");
        if (priority != null && !priority.isBlank()) {
            document.priority.clear();
            for (String part : priority.split(",")) {
                if (part.isBlank()) continue;
                document.priority.add(intValue(part, 0));
            }
        }
        int count = intProperty(properties, "elements", 0);
        for (int i = 0; i < count && i < MAX_ELEMENTS; i++) {
            String prefix = "element." + i + ".";
            WordElement element = new WordElement();
            element.text = properties.getProperty(prefix + "text", "");
            if (element.text.isEmpty()) continue;
            element.fontFamily = properties.getProperty(prefix + "font", Font.SANS_SERIF);
            element.colorIndex = intProperty(properties, prefix + "color", 0);
            element.blockTypeIndex = intProperty(properties, prefix + "block", 0);
            element.x = intProperty(properties, prefix + "x", 0);
            element.y = intProperty(properties, prefix + "y", 0);
            element.width = intProperty(properties, prefix + "width", MAP_SIZE);
            element.height = intProperty(properties, prefix + "height", MAP_SIZE);
            element.shadow = Boolean.parseBoolean(properties.getProperty(prefix + "shadow", "true"));
            element.clampSize();
            document.elements.add(element);
        }
        if (document.elements.isEmpty() && count == 0) {
            return createDefault();
        }
        document.normalize();
        return document;
    }

    public void save(Path file) {
        if (file == null) return;
        normalize();
        Properties properties = new Properties();
        properties.setProperty("canvasX", Integer.toString(mapsX));
        properties.setProperty("canvasY", Integer.toString(mapsY));
        properties.setProperty("background", Integer.toString(backgroundColorIndex));
        properties.setProperty("backgroundBlock", Integer.toString(backgroundBlockTypeIndex));
        properties.setProperty("shadow", Boolean.toString(shadow));
        properties.setProperty("priorityConfirmed", Boolean.toString(priorityConfirmed));
        StringBuilder priorityValue = new StringBuilder();
        for (int i = 0; i < priority.size(); i++) {
            if (i > 0) priorityValue.append(',');
            priorityValue.append(priority.get(i));
        }
        properties.setProperty("priority", priorityValue.toString());
        properties.setProperty("elements", Integer.toString(elements.size()));
        for (int i = 0; i < elements.size(); i++) {
            WordElement element = elements.get(i);
            String prefix = "element." + i + ".";
            properties.setProperty(prefix + "text", element.text == null ? "" : element.text);
            properties.setProperty(prefix + "font", element.fontFamily == null ? Font.SANS_SERIF : element.fontFamily);
            properties.setProperty(prefix + "color", Integer.toString(element.colorIndex));
            properties.setProperty(prefix + "block", Integer.toString(element.blockTypeIndex));
            properties.setProperty(prefix + "x", Integer.toString(element.x));
            properties.setProperty(prefix + "y", Integer.toString(element.y));
            properties.setProperty(prefix + "width", Integer.toString(element.width));
            properties.setProperty(prefix + "height", Integer.toString(element.height));
            properties.setProperty(prefix + "shadow", Boolean.toString(element.shadow));
        }
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                properties.store(writer, "Schemapart word map-art document");
            }
        } catch (IOException e) {
            SchemapartClient.LOGGER.error("Could not save word-art document", e);
        }
    }

    private static int intProperty(Properties properties, String key, int fallback) {
        return intValue(properties.getProperty(key), fallback);
    }

    private static int intValue(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
