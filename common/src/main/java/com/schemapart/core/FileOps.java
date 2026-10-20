package com.schemapart.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Filesystem helpers shared by all game versions. */
public final class FileOps {
    public static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "bmp", "gif");
    public static final Set<String> SCHEMATIC_EXTENSIONS = Set.of("litematic", "schem", "schematic", "nbt");

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneId.systemDefault());

    private FileOps() {
    }

    public static void ensureDirectories(Path mapartDir) throws IOException {
        Files.createDirectories(mapartDir.resolve("pictures"));
        Files.createDirectories(mapartDir.resolve("matic"));
    }

    public static List<Path> listFiles(Path dir, Set<String> extensions) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = new ArrayList<>();
            stream.filter(Files::isRegularFile).filter(p -> {
                String name = p.getFileName().toString();
                int dot = name.lastIndexOf('.');
                return dot >= 0 && extensions.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
            }).forEach(files::add);
            files.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
            return files;
        } catch (IOException e) {
            return List.of();
        }
    }

    public static String safeBaseName(String name) {
        String base = name;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        base = base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (base.isEmpty()) base = "mapart";
        if (base.length() > 80) base = base.substring(0, 80);
        return base;
    }

    public static Path unique(Path dir, String baseName, String extension) {
        Path candidate = dir.resolve(baseName + extension);
        int n = 2;
        while (Files.exists(candidate)) {
            candidate = dir.resolve(baseName + "_" + n + extension);
            n++;
        }
        return candidate;
    }

    public static String timestamp() {
        return STAMP.format(Instant.now());
    }

    public static Path copy(Path source, Path dir, String newBase) throws IOException {
        Files.createDirectories(dir);
        Path target = unique(dir, newBase, extension(source));
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        return target;
    }

    public static Path move(Path source, Path dir, String newBase) throws IOException {
        Files.createDirectories(dir);
        Path target = unique(dir, newBase, extension(source));
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
        return target;
    }

    public static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    public static void delete(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
                for (Path p : paths) Files.deleteIfExists(p);
            }
        } else {
            Files.deleteIfExists(path);
        }
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }
}
