package com.schemapart.client;

import com.schemapart.core.ArtSettings;
import com.schemapart.core.FileOps;
import com.schemapart.core.ImageArt;
import com.schemapart.core.MapArtPlan;
import com.schemapart.core.MapArtService;
import com.schemapart.core.PreparedImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Preview + parameters + schematic generation. */
public final class MapArtScreen extends SpScreen {
    private final Path imagePath;
    private final String imageName;
    private PreparedImage prepared;
    private MapArtPlan plan;
    private SpTextures.Entry mapTexture;
    private SpTextures.Entry buildTexture;
    private int previewMode;
    private String status = "";
    private int statusColor = MUTED;
    private boolean generating;

    private double zoom = 1.0;
    private double panX;
    private double panY;
    private boolean dragging;
    private double lastDragX;
    private double lastDragY;

    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;

    public MapArtScreen(Path imagePath) {
        super(Component.translatable("schemapart.mapart.title"));
        this.imagePath = imagePath;
        this.imageName = FileOps.safeBaseName(imagePath.getFileName().toString());
    }

    @Override
    protected void init() {
        super.init();
        SpPaths.init();
        SchemapartClient.ensureScanned();
        if (prepared == null) {
            refresh();
        }
    }

    private void refresh() {
        try {
            closeTextures();
            prepared = ImageArt.prepare(ImageArt.read(imagePath), SchemapartClient.settings);
            plan = MapArtService.plan(prepared, imageName, SchemapartClient.settings, SchemapartClient.blocks);
            rebuildTextures();
            zoom = 1.0;
            panX = 0;
            panY = 0;
            status = "已生成预览  " + plan.width + "×" + plan.height + "  ·  "
                    + plan.placedBlocks() + " 方块  ·  " + plan.uniqueBlocks() + " 种颜色";
            statusColor = ACCENT;
        } catch (Throwable t) {
            plan = null;
            status = "预览失败: " + shortMessage(t);
            statusColor = DANGER;
            SchemapartClient.LOGGER.error("Preview failed", t);
        }
    }

    public void refreshFromSelection() {
        refresh();
    }

    private void rebuildTextures() {
        closeTextures();
        if (plan == null) return;
        mapTexture = SpTextures.create(plan.previewArgb, plan.width, plan.height);
        int[] shaded = SpTextures.shadeForBuild(plan.previewArgb, plan.width, plan.height, 5);
        buildTexture = SpTextures.create(shaded, plan.width * 5, plan.height * 5);
    }

    private void closeTextures() {
        if (mapTexture != null) mapTexture.close();
        if (buildTexture != null) buildTexture.close();
        mapTexture = null;
        buildTexture = null;
    }

    private static String shortMessage(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.isBlank()) m = t.getClass().getSimpleName();
        return m.length() > 90 ? m.substring(0, 90) + "…" : m;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        frameStart();
        renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, 0xC014161A);

        g.fill(0, 0, width, 40, 0xF01B1E23);
        g.fill(0, 39, width, 40, BORDER);
        textShadow(g, "MAP ART", 10, 9, ACCENT);
        text(g, trim(imageName, Math.max(120, width / 3)), 86, 11, MUTED);
        textRight(g, "ESC 返回文件浏览器", width - 10, 12, MUTED);

        int margin = 8;
        int footer = 26;
        int panelY = 46;
        int panelH = Math.max(150, height - panelY - footer - 8);
        int leftW = Math.max(190, Math.min(width - 160, (int) ((width - margin * 2) * 0.64)));
        int rightX = margin + leftW + 8;
        int rightW = Math.max(110, width - margin - rightX);
        boolean compact = panelH < 265;
        boolean narrow = rightW < 235;

        panel(g, margin, panelY, leftW, panelH);
        g.fill(margin + 1, panelY + 1, margin + leftW - 1, panelY + 31, 0xFF272C34);
        int btnY = panelY + 5;
        int tbW = Math.max(38, Math.min(60, (leftW - 28) / 4));
        int tbX = margin + 8;
        toggleButton(g, tbX, btnY, tbW, 20, "地图", previewMode == 0, () -> previewMode = 0);
        tbX += tbW + 4;
        toggleButton(g, tbX, btnY, tbW, 20, "原理图", previewMode == 1, () -> previewMode = 1);
        tbX += tbW + 4;
        button(g, tbX, btnY, tbW, 20, "刷新", true, () -> {
            refresh();
            resetView();
        });
        tbX += tbW + 4;
        button(g, tbX, btnY, tbW, 20, "复位", true, this::resetView);
        previewX = margin + 8;
        previewY = panelY + 36;
        previewW = leftW - 16;
        previewH = panelH - 44;
        drawPreview(g);
        drawFooter(g, margin, width - margin);

        panel(g, rightX, panelY, rightW, panelH);
        g.fill(rightX + 1, panelY + 1, rightX + rightW - 1, panelY + 31, 0xFF272C34);
        text(g, "参数", rightX + 10, panelY + 11, TEXT);
        int y = panelY + (compact ? 34 : 42);
        int labelX = rightX + 12;
        int controlX = rightX + rightW - 12;
        int controlW = Math.min(170, Math.max(52, rightW - 100));
        int rowH = compact ? 23 : 30;
        y = parameterRow(g, labelX, y, controlX, controlW, rowH, narrow, "精度（方块:像素）",
                SchemapartClient.settings.precisionLabel(), () -> {
                    SchemapartClient.settings.cyclePrecision();
                    afterSettingChange();
                });
        y = parameterRow(g, labelX, y, controlX, controlW, rowH, narrow, "方块能否被挖掘",
                mineableLabel(), () -> {
                    SchemapartClient.settings.cycleMineable();
                    afterSettingChange();
                });
        y = parameterRow(g, labelX, y, controlX, controlW, rowH, narrow, "方块来源",
                sourceLabel(), () -> {
                    SchemapartClient.settings.cycleSource();
                    afterSettingChange();
                });

        int selectedCount = SchemapartClient.settings.filtered(SchemapartClient.blocks).size();
        y += compact ? 4 : 6;
        int selectH = compact ? 18 : 24;
        button(g, labelX, y, rightW - 24, selectH,
                "选择方块  (" + selectedCount + " / " + SchemapartClient.blocks.size() + ")", true,
                () -> minecraft.setScreen(new BlockSelectScreen(this)));
        y += selectH + (compact ? 4 : 8);
        if (!compact) {
            toggleButton(g, labelX, y, rightW - 24, 22, "抖动（Floyd–Steinberg）: "
                    + (SchemapartClient.settings.dither ? "开" : "关"), SchemapartClient.settings.dither, () -> {
                SchemapartClient.settings.dither = !SchemapartClient.settings.dither;
                afterSettingChange();
            });
            y += 30;
        }

        if (!compact && plan != null) {
            int mapsX = (plan.width + 127) / 128;
            int mapsY = (plan.height + 127) / 128;
            text(g, "尺寸", labelX, y, MUTED);
            textRight(g, plan.width + " × " + plan.height + " · " + mapsX + "×" + mapsY + " 图", controlX, y, TEXT);
            y += 15;
            text(g, "方块 / 颜色", labelX, y, MUTED);
            textRight(g, String.format(Locale.ROOT, "%,d", plan.placedBlocks()) + " · " + plan.uniqueBlocks() + " 色", controlX, y, TEXT);
        } else if (!compact) {
            text(g, "没有可用方块，请检查筛选。", labelX, y, DANGER);
        }

        int startW = rightW - 24;
        int startH = compact ? 24 : 28;
        int startY = panelY + panelH - startH - 6;
        button(g, rightX + 12, startY, startW, startH,
                generating ? "正在生成…" : "开始制作", !generating && plan != null, this::generate);
    }

    private int parameterRow(GuiGraphics g, int x, int y, int controlRight, int controlW,
                             int rowH, boolean narrow, String label, String value, Runnable action) {
        if (narrow) {
            text(g, label, x, y, MUTED);
            button(g, x, y + 9, controlRight - x, Math.max(14, rowH - 9), value, true, action);
        } else {
            text(g, label, x, y + 7, MUTED);
            button(g, controlRight - controlW, y, controlW, 22, value, true, action);
        }
        return y + rowH;
    }

    private String mineableLabel() {
        return switch (SchemapartClient.settings.mineableMode) {
            case ArtSettings.MINE_ONLY -> "仅可挖掘";
            case ArtSettings.MINE_NOT -> "仅不可挖掘";
            default -> "全部";
        };
    }

    private String sourceLabel() {
        return switch (SchemapartClient.settings.sourceMode) {
            case ArtSettings.SOURCE_VANILLA -> "仅原版";
            case ArtSettings.SOURCE_MODDED -> "仅模组";
            default -> "全部";
        };
    }

    private void afterSettingChange() {
        SchemapartClient.saveSettings();
        refresh();
    }

    private void drawPreview(GuiGraphics g) {
        g.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0xFF171A1F);
        outline(g, previewX, previewY, previewW, previewH, BORDER);
        SpTextures.Entry texture = previewMode == 0 ? mapTexture : buildTexture;
        if (texture == null) {
            textCentered(g, "无预览", previewX + previewW / 2, previewY + previewH / 2 - 4, MUTED);
            return;
        }
        double fit = Math.min((previewW - 24) / (double) texture.width,
                (previewH - 24) / (double) texture.height);
        double scale = Math.max(0.01, fit * zoom);
        int dw = Math.max(1, (int) Math.round(texture.width * scale));
        int dh = Math.max(1, (int) Math.round(texture.height * scale));
        int cx = previewX + previewW / 2 + (int) Math.round(panX);
        int cy = previewY + previewH / 2 + (int) Math.round(panY);
        int dx = cx - dw / 2;
        int dy = cy - dh / 2;

        g.enableScissor(previewX + 1, previewY + 1, previewX + previewW - 1, previewY + previewH - 1);
        drawChecker(g, previewX, previewY, previewW, previewH);
        texture.draw(g, dx, dy, dw, dh);
        outline(g, dx - 1, dy - 1, dw + 2, dh + 2, 0x805BD6A6);
        g.disableScissor();

        if (inside(mouseX, mouseY, previewX, previewY, previewW, previewH) && plan != null) {
            int tx = (int) ((mouseX - dx) / (double) dw * plan.width);
            int ty = (int) ((mouseY - dy) / (double) dh * plan.height);
            if (tx >= 0 && ty >= 0 && tx < plan.width && ty < plan.height) {
                int idx = plan.blockIndex[ty * plan.width + tx];
                String name = plan.blocks.get(idx).id;
                String label = tx + ", " + ty + "  ·  " + name;
                int tw = font.width(label) + 8;
                g.fill(previewX + 4, previewY + previewH - 18, previewX + 4 + tw,
                        previewY + previewH - 4, 0xD015181D);
                text(g, label, previewX + 8, previewY + previewH - 14, TEXT);
            }
        }
    }

    private void drawChecker(GuiGraphics g, int x, int y, int w, int h) {
        int cell = 12;
        for (int yy = 0; yy < h; yy += cell) {
            for (int xx = 0; xx < w; xx += cell) {
                int c = (((xx / cell) + (yy / cell)) & 1) == 0 ? 0xFF1D2127 : 0xFF20252C;
                g.fill(x + xx, y + yy, Math.min(x + w, x + xx + cell), Math.min(y + h, y + yy + cell), c);
            }
        }
    }

    private void drawFooter(GuiGraphics g, int left, int right) {
        g.fill(0, height - 24, width, height, 0xF01B1E23);
        g.fill(0, height - 25, width, height - 24, BORDER);
        text(g, status.isEmpty() ? "右键拖动预览 · 滚轮缩放" : status, left, height - 17, statusColor);
        textRight(g, "右键拖动 / 滚轮缩放", right, height - 17, MUTED);
    }

    private void resetView() {
        zoom = 1.0;
        panX = 0;
        panY = 0;
    }

    private void generate() {
        if (plan == null || generating) return;
        generating = true;
        status = "正在写入 Litematica 原理图…";
        statusColor = WARN;
        MapArtPlan snapshot = plan;
        CompletableFuture.runAsync(() -> {
            try {
                MapArtService.Result result = MapArtService.write(snapshot, SpPaths.maticDir, SpPaths.dataVersion());
                boolean copied = false;
                if (SchemapartClient.settings.copyToLitematica
                        && FabricLoader.getInstance().isModLoaded("litematica")) {
                    Path schematics = SpPaths.gameDir.resolve("schematics");
                    Files.createDirectories(schematics);
                    Path target = FileOps.unique(schematics,
                            FileOps.safeBaseName(result.path().getFileName().toString()), ".litematic");
                    Files.copy(result.path(), target, StandardCopyOption.COPY_ATTRIBUTES);
                    copied = true;
                }
                boolean finalCopied = copied;
                minecraft.execute(() -> {
                    generating = false;
                    status = "已生成: " + result.path().getFileName()
                            + (finalCopied ? "  ·  已复制到投影模组" : "");
                    statusColor = ACCENT;
                });
            } catch (Throwable t) {
                minecraft.execute(() -> {
                    generating = false;
                    status = "生成失败: " + shortMessage(t);
                    statusColor = DANGER;
                });
                SchemapartClient.LOGGER.error("Generation failed", t);
            }
        });
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (dragging) dragging = false;
        if (inside(mx, my, previewX, previewY, previewW, previewH)) {
            if (button == 1) {
                dragging = true;
                lastDragX = mx;
                lastDragY = my;
                return true;
            }
            if (button == 0) return true;
        }
        return super.mouseClicked(mx, my, button) || runClick(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 1) dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (dragging && button == 1) {
            panX += mx - lastDragX;
            panY += my - lastDragY;
            lastDragX = mx;
            lastDragY = my;
            return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double deltaX, double deltaY) {
        if (inside(mx, my, previewX, previewY, previewW, previewH)) {
            double oldZoom = zoom;
            double factor = Math.exp(deltaY * 0.18);
            zoom = Mth.clamp(zoom * factor, 0.15, 12.0);
            double ratio = zoom / oldZoom;
            double cx = previewX + previewW / 2.0;
            double cy = previewY + previewH / 2.0;
            panX = (panX + (mx - cx)) * ratio - (mx - cx);
            panY = (panY + (my - cy)) * ratio - (my - cy);
            return true;
        }
        return super.mouseScrolled(mx, my, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        closeTextures();
        minecraft.setScreen(new MainScreen());
    }
}
