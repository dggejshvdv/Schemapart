package com.schemapart.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** End-to-end picture -> Litematica schematic pipeline. */
public final class MapArtService {
    private MapArtService() {
    }

    public static MapArtPlan plan(Path picture, ArtSettings settings, List<BlockInfo> blocks) throws IOException {
        PreparedImage prepared = ImageArt.prepare(ImageArt.read(picture), settings);
        List<Palette.Entry> palette = Palette.build(blocks, settings);
        return Quantizer.quantize(FileOps.safeBaseName(picture.getFileName().toString()),
                prepared, palette, settings.dither);
    }

    public static MapArtPlan plan(PreparedImage prepared, String sourceName, ArtSettings settings,
                                  List<BlockInfo> blocks) {
        List<Palette.Entry> palette = Palette.build(blocks, settings);
        return Quantizer.quantize(sourceName, prepared, palette, settings.dither);
    }

    public static Result generate(Path picture, ArtSettings settings, List<BlockInfo> blocks,
                                  Path maticDir, int dataVersion) throws IOException {
        MapArtPlan plan = plan(picture, settings, blocks);
        return write(plan, maticDir, dataVersion);
    }

    public static Result write(MapArtPlan plan, Path maticDir, int dataVersion) throws IOException {
        String base = FileOps.safeBaseName(plan.sourceName) + "_" + plan.width + "x" + plan.height
                + "_" + FileOps.timestamp();
        Path target = FileOps.unique(maticDir, base, ".litematic");
        LitematicWriter.write(target, plan, dataVersion, plan.sourceName,
                "Schemapart", "Map art " + plan.width + "x" + plan.height
                        + ", " + plan.placedBlocks() + " blocks, " + plan.uniqueBlocks() + " map colours");
        return new Result(target, plan);
    }

    public record Result(Path path, MapArtPlan plan) {
    }
}
