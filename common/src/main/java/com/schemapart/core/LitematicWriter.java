package com.schemapart.core;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Writes Litematica .litematic files (format Version 6 / SubVersion 1).
 * The layout is the classic Litematica bit-array format: contiguous, LSB-first
 * entries, two bits minimum, no padding between longs.
 */
public final class LitematicWriter {
    public static final int LITEMATIC_VERSION = 6;
    public static final int LITEMATIC_SUBVERSION = 1;

    private LitematicWriter() {
    }

    public static void write(Path target, MapArtPlan plan, int dataVersion, String title,
                             String author, String description) throws IOException {
        List<String> paletteIds = new ArrayList<>();
        paletteIds.add("minecraft:air");
        Map<String, Integer> ids = new LinkedHashMap<>();
        ids.put("minecraft:air", 0);
        int[] localToPalette = new int[plan.blocks.size()];
        for (int i = 0; i < plan.blocks.size(); i++) {
            String id = plan.blocks.get(i).id;
            Integer index = ids.get(id);
            if (index == null) {
                index = paletteIds.size();
                ids.put(id, index);
                paletteIds.add(id);
            }
            localToPalette[i] = index;
        }

        long[] blockStates = pack(plan.blockIndex, localToPalette, paletteIds.size());
        int placed = 0;
        for (int i : plan.blockIndex) if (i >= 0 && localToPalette[i] != 0) placed++;

        Nbt.CompoundTag root = new Nbt.CompoundTag();
        root.putInt("Version", LITEMATIC_VERSION);
        root.putInt("SubVersion", LITEMATIC_SUBVERSION);
        root.putInt("MinecraftDataVersion", dataVersion);

        Nbt.CompoundTag enclosing = new Nbt.CompoundTag()
                .putInt("x", plan.width).putInt("y", 1).putInt("z", plan.height);
        long now = System.currentTimeMillis();
        Nbt.CompoundTag metadata = new Nbt.CompoundTag()
                .put("EnclosingSize", enclosing)
                .putString("Author", author == null ? "" : author)
                .putString("Description", description == null ? "" : description)
                .putString("Name", title == null ? "Schemapart" : title)
                .putString("Software", "Schemapart 1.0")
                .putInt("RegionCount", 1)
                .putLong("TimeCreated", now)
                .putLong("TimeModified", now)
                .putInt("TotalBlocks", placed)
                .putInt("TotalVolume", plan.width * plan.height);
        root.put("Metadata", metadata);

        Nbt.ListTag palette = new Nbt.ListTag(Nbt.COMPOUND);
        for (String id : paletteIds) {
            palette.add(new Nbt.CompoundTag().putString("Name", id));
        }

        Nbt.CompoundTag region = new Nbt.CompoundTag();
        region.put("Position", new Nbt.CompoundTag().putInt("x", 0).putInt("y", 0).putInt("z", 0));
        region.put("Size", new Nbt.CompoundTag()
                .putInt("x", plan.width).putInt("y", 1).putInt("z", plan.height));
        region.put("BlockStatePalette", palette);
        region.put("BlockStates", new Nbt.LongArrayTag(blockStates));
        region.put("Entities", new Nbt.ListTag(Nbt.COMPOUND));
        region.put("TileEntities", new Nbt.ListTag(Nbt.COMPOUND));
        region.put("PendingBlockTicks", new Nbt.ListTag(Nbt.COMPOUND));
        region.put("PendingFluidTicks", new Nbt.ListTag(Nbt.COMPOUND));

        Nbt.CompoundTag regions = new Nbt.CompoundTag();
        regions.put("Schemapart", region);
        root.put("Regions", regions);

        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (OutputStream file = Files.newOutputStream(tmp);
             BufferedOutputStream buffered = new BufferedOutputStream(file);
             GZIPOutputStream gzip = new GZIPOutputStream(buffered);
             DataOutputStream out = new DataOutputStream(gzip)) {
            Nbt.writeRoot(root, out);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Packs palette indices into Litematica's contiguous bit array.
     */
    static long[] pack(int[] blockIndices, int[] localToPalette, int paletteSize) {
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1)));
        long mask = (1L << bits) - 1L;
        long[] out = new long[(int) (((long) blockIndices.length * bits + 63L) >>> 6)];
        for (int i = 0; i < blockIndices.length; i++) {
            int index = blockIndices[i];
            long value = index < 0 ? 0L : localToPalette[index] & mask;
            long bit = (long) i * bits;
            int word = (int) (bit >>> 6);
            int offset = (int) (bit & 63L);
            out[word] |= value << offset;
            if (offset + bits > 64) {
                out[word + 1] |= value >>> (64 - offset);
            }
        }
        return out;
    }
}


