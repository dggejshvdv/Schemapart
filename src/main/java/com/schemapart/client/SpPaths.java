package com.schemapart.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves all game/config paths once. */
public final class SpPaths {
    private static boolean ready;
    public static Path gameDir;
    public static Path mapartDir;
    public static Path picturesDir;
    public static Path maticDir;
    public static Path configDir;
    public static Path settingsFile;
    public static Path blocksJson;

    private SpPaths() {
    }

    public static synchronized void init() {
        if (ready) return;
        gameDir = Minecraft.getInstance().gameDirectory.toPath();
        mapartDir = gameDir.resolve("mapart");
        picturesDir = mapartDir.resolve("pictures");
        maticDir = mapartDir.resolve("matic");
        configDir = FabricLoader.getInstance().getConfigDir();
        settingsFile = configDir.resolve("schemapart.properties");
        blocksJson = mapartDir.resolve("blocks.json");
        try {
            Files.createDirectories(picturesDir);
            Files.createDirectories(maticDir);
            Files.createDirectories(configDir);
        } catch (IOException e) {
            SchemapartClient.LOGGER.error("Could not create Schemapart directories", e);
        }
        ready = true;
    }

    public static String minecraftVersion() {
        return SharedConstants.getCurrentVersion().name();
    }

    public static int dataVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }
}
