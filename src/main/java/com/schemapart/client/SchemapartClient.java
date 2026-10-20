package com.schemapart.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.schemapart.core.ArtSettings;
import com.schemapart.core.BlockInfo;
import com.schemapart.core.CatalogJson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SchemapartClient implements ClientModInitializer {
    public static final String MOD_ID = "schemapart";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ArtSettings settings = new ArtSettings();
    public static List<BlockInfo> blocks = new ArrayList<>();
    public static boolean scanned;
    private static boolean chordHeld;

    @Override
    public void onInitializeClient() {
        SpPaths.init();
        settings = ArtSettings.load(SpPaths.settingsFile);
        ensureScanned();
        LOGGER.info("Schemapart ready, {} map-colour blocks scanned", blocks.size());
    }

    public static void ensureScanned() {
        if (scanned) return;
        try {
            blocks = BlockScanner.scan();
            scanned = !blocks.isEmpty();
            if (scanned) {
                CatalogJson.write(SpPaths.blocksJson, blocks, SpPaths.minecraftVersion());
            }
        } catch (Throwable t) {
            LOGGER.error("Block scan failed", t);
        }
    }

    public static void rescan() {
        scanned = false;
        ensureScanned();
    }

    public static void saveSettings() {
        try {
            settings.save(SpPaths.settingsFile);
        } catch (IOException e) {
            LOGGER.error("Could not save settings", e);
        }
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            chordHeld = false;
            return;
        }
        long window = mc.getWindow().getWindow();
        boolean down = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_M)
                && InputConstants.isKeyDown(window, GLFW.GLFW_KEY_P);
        if (down && !chordHeld && mc.screen == null) {
            ensureScanned();
            SpPaths.init();
            mc.setScreen(new MainScreen());
        }
        chordHeld = down;
    }

    public static void openMapArt(java.nio.file.Path image) {
        Minecraft.getInstance().setScreen(new MapArtScreen(image));
    }
}
