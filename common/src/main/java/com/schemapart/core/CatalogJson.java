package com.schemapart.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Writes the block database consumed by external tools and by the in-game
 * palette screen. It intentionally mirrors the old local-port payload, but as
 * a plain JSON file so no background web server is required.
 */
public final class CatalogJson {
    private CatalogJson() {
    }

    public static void write(Path file, List<BlockInfo> blocks, String minecraftVersion) throws IOException {
        StringBuilder sb = new StringBuilder(blocks.size() * 180);
        sb.append("{\n");
        sb.append("  \"format\": \"schemapart-blocks-v1\",\n");
        sb.append("  \"generatedAt\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"minecraftVersion\": \"").append(escape(minecraftVersion)).append("\",\n");
        sb.append("  \"count\": ").append(blocks.size()).append(",\n");
        sb.append("  \"blocks\": [\n");
        for (int i = 0; i < blocks.size(); i++) {
            BlockInfo b = blocks.get(i);
            sb.append("    {");
            sb.append("\"id\": \"").append(escape(b.id)).append("\", ");
            sb.append("\"item\": \"").append(escape(b.itemId)).append("\", ");
            sb.append("\"name\": \"").append(escape(b.displayName)).append("\", ");
            sb.append("\"texture\": \"").append(escape(b.texture)).append("\", ");
            sb.append("\"mapColor\": \"").append(b.hex()).append("\", ");
            sb.append("\"mapColorArgb\": ").append(b.mapColor).append(", ");
            sb.append("\"mineable\": ").append(b.mineable).append(", ");
            sb.append("\"requiresTool\": ").append(b.requiresTool).append(", ");
            sb.append("\"requiresSilkTouch\": ").append(b.requiresSilkTouch).append(", ");
            sb.append("\"vanilla\": ").append(b.vanilla).append(", ");
            sb.append("\"fullBlock\": ").append(b.fullBlock).append(", ");
            sb.append("\"gravity\": ").append(b.gravity);
            sb.append("}");
            if (i + 1 < blocks.size()) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ]\n");
        sb.append("}\n");
        Files.createDirectories(file.getParent());
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    public static String escape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}
