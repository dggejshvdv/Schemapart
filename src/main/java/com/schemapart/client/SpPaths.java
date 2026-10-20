package com.schemapart.client;

import com.schemapart.core.ArtSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolves all game/config/data paths from the current settings. */
public final class SpPaths {
    private static boolean ready;
    public static Path gameDir;
    public static Path mapartDir;
    public static Path picturesDir;
    public static Path maticDir;
    public static Path schematicsDir;
    public static Path configDir;
    public static Path settingsFile;
    public static Path wordSettingsFile;
    public static Path blocksJson;

    private SpPaths() {
    }

    public static synchronized void init() {
        if (ready) return;
        gameDir = Minecraft.getInstance().gameDirectory.toPath();
        mapartDir = gameDir.resolve("mapart");
        configDir = FabricLoader.getInstance().getConfigDir();
        settingsFile = configDir.resolve("schemapart.properties");
        wordSettingsFile = configDir.resolve("schemapart-wordart.properties");

        ArtSettings settings = SchemapartClient.settings;
        picturesDir = resolve(settings == null ? "" : settings.picturesPath,
                mapartDir.resolve("pictures"));
        maticDir = resolve(settings == null ? "" : settings.maticPath,
                mapartDir.resolve("matic"));
        schematicsDir = resolve(settings == null ? "" : settings.schematicsPath,
                gameDir.resolve("schematics"));
        blocksJson = mapartDir.resolve("blocks.json");

        try {
            Files.createDirectories(picturesDir);
            Files.createDirectories(maticDir);
            Files.createDirectories(schematicsDir);
            Files.createDirectories(configDir);
        } catch (IOException e) {
            SchemapartClient.LOGGER.error("Could not create Schemapart directories", e);
        }
        ready = true;
    }

    public static synchronized void reload() {
        ready = false;
        init();
    }

    private static Path resolve(String configured, Path fallback) {
        if (configured == null || configured.isBlank()) return fallback;
        try {
            Path path = Paths.get(configured.trim());
            return (path.isAbsolute() ? path : gameDir.resolve(path)).normalize();
        } catch (Exception e) {
            return fallback;
        }
    }

    public static String minecraftVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    public static int dataVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }
}
