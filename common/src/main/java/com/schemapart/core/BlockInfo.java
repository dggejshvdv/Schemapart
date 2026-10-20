package com.schemapart.core;

import java.util.Objects;

/**
 * Immutable description of a placeable block that can be used by the map-art
 * quantizer. All values are collected on the client from the block registry.
 */
public final class BlockInfo {
    public final String id;
    public final String itemId;
    public final String displayName;
    /** ARGB colour used by a vanilla map for this block state. */
    public final int mapColor;
    public final boolean mineable;
    public final boolean requiresTool;
    public final boolean requiresSilkTouch;
    public final boolean vanilla;
    public final boolean fullBlock;
    public final boolean gravity;
    /** Texture/model lookup key. For vanilla and most mods this equals {@link #id}. */
    public final String texture;

    public BlockInfo(String id, String itemId, String displayName, int mapColor,
                     boolean mineable, boolean requiresTool, boolean requiresSilkTouch,
                     boolean vanilla, boolean fullBlock, boolean gravity, String texture) {
        this.id = Objects.requireNonNull(id, "id");
        this.itemId = itemId == null ? id : itemId;
        this.displayName = displayName == null ? id : displayName;
        this.mapColor = mapColor;
        this.mineable = mineable;
        this.requiresTool = requiresTool;
        this.requiresSilkTouch = requiresSilkTouch;
        this.vanilla = vanilla;
        this.fullBlock = fullBlock;
        this.gravity = gravity;
        this.texture = texture == null ? id : texture;
    }

    public String path() {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    public String namespace() {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }

    /** Higher is better when several blocks share exactly the same map colour. */
    public int preferenceScore() {
        int score = 0;
        if (vanilla) score += 12;
        if (mineable) score += 6;
        if (!requiresTool) score += 4;
        if (!requiresSilkTouch) score += 3;
        if (fullBlock) score += 4;
        if (!gravity) score += 2;
        String p = path();
        if (p.contains("concrete")) score += 9;
        if (p.endsWith("_wool")) score += 7;
        if (p.contains("terracotta")) score += 3;
        if (p.equals("stone") || p.equals("deepslate") || p.equals("dirt")
                || p.equals("sand") || p.equals("gravel")) score += 2;
        return score;
    }

    public String hex() {
        return String.format("#%06X", mapColor & 0xFFFFFF);
    }

    @Override
    public String toString() {
        return id;
    }
}
