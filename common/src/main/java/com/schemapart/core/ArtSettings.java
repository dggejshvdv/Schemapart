package com.schemapart.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Persistent settings plus the user's block whitelist. Stored as a properties
 * file so the core does not need a JSON library.
 */
public final class ArtSettings {
    public static final double[] RATIOS = {0.5d, 1.0d, 2.0d, 4.0d, 8.0d};
    public static final String[] RATIO_LABELS = {"1:0.5", "1:1", "1:2", "1:4", "1:8"};

    public static final int MINE_ALL = 0;
    public static final int MINE_ONLY = 1;
    public static final int MINE_NOT = 2;

    public static final int SOURCE_ALL = 0;
    public static final int SOURCE_VANILLA = 1;
    public static final int SOURCE_MODDED = 2;

    public int precisionIndex = 1;
    public int mineableMode = MINE_ALL;
    public int sourceMode = SOURCE_ALL;
    public boolean dither;
    public boolean autoSaveSelectionOnEsc = true;
    public boolean copyToLitematica = true;
    /** Optional absolute or game-directory-relative directories; blank = defaults. */
    public String picturesPath = "";
    public String maticPath = "";
    public String schematicsPath = "";
    /** GLFW key codes for the two-key open-menu chord. */
    public int shortcutKey1 = 77; // M
    public int shortcutKey2 = 80; // P
    /** Disabled block ids for the current session. New blocks are enabled by default. */
    public final Set<String> disabled = new LinkedHashSet<>();
    /** Disabled block ids stored as the user default. */
    public final Set<String> defaultDisabled = new LinkedHashSet<>();

    public double pixelsPerBlock() {
        return RATIOS[Math.max(0, Math.min(RATIOS.length - 1, precisionIndex))];
    }

    public double blocksPerPixel() {
        return 1.0d / pixelsPerBlock();
    }

    public String precisionLabel() {
        return RATIO_LABELS[Math.max(0, Math.min(RATIO_LABELS.length - 1, precisionIndex))];
    }

    public void cyclePrecision() {
        precisionIndex = (precisionIndex + 1) % RATIOS.length;
    }

    public void cycleMineable() {
        mineableMode = (mineableMode + 1) % 3;
    }

    public void cycleSource() {
        sourceMode = (sourceMode + 1) % 3;
    }

    public boolean isEnabled(BlockInfo block) {
        return !disabled.contains(block.id);
    }

    public boolean passesMode(BlockInfo block) {
        if (mineableMode == MINE_ONLY && !block.mineable) return false;
        if (mineableMode == MINE_NOT && block.mineable) return false;
        if (sourceMode == SOURCE_VANILLA && !block.vanilla) return false;
        if (sourceMode == SOURCE_MODDED && block.vanilla) return false;
        return isEnabled(block);
    }

    public List<BlockInfo> filtered(List<BlockInfo> all) {
        List<BlockInfo> out = new ArrayList<>();
        for (BlockInfo b : all) {
            if (b.mapColor != 0 && passesMode(b)) out.add(b);
        }
        return out;
    }

    public void setEnabled(String id, boolean enabled) {
        if (enabled) disabled.remove(id);
        else disabled.add(id);
    }

    public void setAll(List<BlockInfo> all, boolean enabled) {
        disabled.clear();
        if (!enabled) {
            for (BlockInfo b : all) disabled.add(b.id);
        }
    }

    public void applyAsDefault() {
        defaultDisabled.clear();
        defaultDisabled.addAll(disabled);
    }

    public void save(Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("precision", Integer.toString(precisionIndex));
        p.setProperty("mineableMode", Integer.toString(mineableMode));
        p.setProperty("sourceMode", Integer.toString(sourceMode));
        p.setProperty("dither", Boolean.toString(dither));
        p.setProperty("autoSaveSelectionOnEsc", Boolean.toString(autoSaveSelectionOnEsc));
        p.setProperty("copyToLitematica", Boolean.toString(copyToLitematica));
        p.setProperty("disabled", String.join("\n", defaultDisabled));
        p.setProperty("picturesPath", picturesPath == null ? "" : picturesPath);
        p.setProperty("maticPath", maticPath == null ? "" : maticPath);
        p.setProperty("schematicsPath", schematicsPath == null ? "" : schematicsPath);
        p.setProperty("shortcutKey1", Integer.toString(shortcutKey1));
        p.setProperty("shortcutKey2", Integer.toString(shortcutKey2));
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "Schemapart settings. Do not edit while the game is running.");
        }
    }

    public static ArtSettings load(Path file) {
        ArtSettings s = new ArtSettings();
        if (!Files.isRegularFile(file)) return s;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException ignored) {
            return s;
        }
        s.precisionIndex = clampInt(p.getProperty("precision"), 1, 0, RATIOS.length - 1);
        s.mineableMode = clampInt(p.getProperty("mineableMode"), 0, 0, 2);
        s.sourceMode = clampInt(p.getProperty("sourceMode"), 0, 0, 2);
        s.dither = Boolean.parseBoolean(p.getProperty("dither", "false"));
        s.autoSaveSelectionOnEsc = Boolean.parseBoolean(p.getProperty("autoSaveSelectionOnEsc", "true"));
        s.copyToLitematica = Boolean.parseBoolean(p.getProperty("copyToLitematica", "true"));
        s.picturesPath = p.getProperty("picturesPath", "");
        s.maticPath = p.getProperty("maticPath", "");
        s.schematicsPath = p.getProperty("schematicsPath", "");
        s.shortcutKey1 = clampInt(p.getProperty("shortcutKey1"), 77, 1, 1000);
        s.shortcutKey2 = clampInt(p.getProperty("shortcutKey2"), 80, 1, 1000);
        String raw = p.getProperty("disabled", "");
        for (String line : raw.split("\\R")) {
            if (!line.isBlank()) {
                s.disabled.add(line.trim());
                s.defaultDisabled.add(line.trim());
            }
        }
        return s;
    }

    private static int clampInt(String raw, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw)));
        } catch (Exception e) {
            return fallback;
        }
    }
}
