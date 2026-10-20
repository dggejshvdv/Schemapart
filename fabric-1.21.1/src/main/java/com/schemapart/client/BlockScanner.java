package com.schemapart.client;

import com.schemapart.core.BlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Scans every registered block and derives palette metadata from vanilla APIs. */
public final class BlockScanner {
    private static final Set<String> SILK_PATHS = Set.of(
            "glass", "tinted_glass", "glass_pane", "ice", "packed_ice", "blue_ice", "frosted_ice",
            "grass_block", "mycelium", "podzol", "dirt_path", "farmland", "rooted_dirt",
            "snow", "snow_block", "powder_snow", "beehive", "bee_nest", "bookshelf",
            "chiseled_bookshelf", "turtle_egg", "sniffer_egg", "frogspawn", "mushroom_stem",
            "brown_mushroom_block", "red_mushroom_block", "mushroom_block", "chorus_plant",
            "chorus_flower", "spawner", "trial_spawner", "vault", "reinforced_deepslate",
            "cobweb", "dead_bush", "short_grass", "tall_grass", "fern", "large_fern",
            "vine", "glow_lichen", "hanging_roots", "big_dripleaf", "small_dripleaf",
            "spore_blossom", "sea_pickle", "sculk", "sculk_vein", "sculk_catalyst",
            "sculk_sensor", "calibrated_sculk_sensor", "sculk_shrieker", "budding_amethyst",
            "amethyst_cluster", "pointed_dripstone", "decorated_pot", "cocoa", "lily_pad",
            "sweet_berry_bush", "cave_vines", "cave_vines_plant", "twisting_vines",
            "twisting_vines_plant", "weeping_vines", "weeping_vines_plant", "azalea",
            "flowering_azalea", "azalea_leaves", "flowering_azalea_leaves"
    );

    private BlockScanner() {
    }

    public static List<BlockInfo> scan() {
        List<BlockInfo> result = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            if (key == null) continue;
            String id = key.toString();
            BlockState state;
            try {
                state = block.defaultBlockState();
                if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            } catch (Throwable t) {
                continue;
            }

            int color;
            try {
                MapColor mapColor = state.getMapColor(null, BlockPos.ZERO);
                if (mapColor == null || mapColor == MapColor.NONE || mapColor.col == 0) continue;
                color = mapColor.calculateRGBColor(MapColor.Brightness.NORMAL);
            } catch (Throwable t) {
                continue;
            }
            if ((color >>> 24) == 0) continue;

            boolean mineable;
            try {
                mineable = state.getDestroySpeed(null, BlockPos.ZERO) >= 0.0F;
            } catch (Throwable t) {
                mineable = true;
            }
            boolean requiresTool;
            try {
                requiresTool = state.requiresCorrectToolForDrops();
            } catch (Throwable t) {
                requiresTool = false;
            }
            boolean fullBlock;
            try {
                fullBlock = state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            } catch (Throwable t) {
                fullBlock = state.isSolid();
            }
            boolean gravity = block instanceof FallingBlock || id.contains("concrete_powder");
            boolean silk = needsSilkTouch(block, id, state);
            String path = key.getPath();

            Item item = block.asItem();
            String itemId = item == null ? id : BuiltInRegistries.ITEM.getKey(item).toString();
            String displayName;
            try {
                displayName = block.getName().getString();
            } catch (Throwable t) {
                displayName = path;
            }

            result.add(new BlockInfo(id, itemId, displayName, color, mineable, requiresTool, silk,
                    "minecraft".equals(key.getNamespace()), fullBlock, gravity, id));
        }
        result.sort(Comparator.comparing(b -> b.id));
        return result;
    }

    private static boolean needsSilkTouch(Block block, String id, BlockState state) {
        String path = id.substring(id.indexOf(':') + 1);
        if (SILK_PATHS.contains(path)) return true;
        if (path.endsWith("_leaves") || path.endsWith("_glass") || path.endsWith("_glass_pane")
                || path.endsWith("_coral") || path.endsWith("_coral_fan")
                || path.endsWith("_coral_wall_fan") || path.endsWith("_head") || path.endsWith("_skull")) {
            return true;
        }
        if (path.startsWith("potted_") || path.startsWith("infested_") || path.startsWith("waxed_")) {
            return path.startsWith("infested_");
        }
        String simple = block.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return simple.contains("glass") || simple.contains("leaves") || simple.contains("iceblock")
                || simple.contains("grassblock") || simple.contains("mycelium")
                || simple.contains("snowlayer") || simple.contains("spawner");
    }
}
