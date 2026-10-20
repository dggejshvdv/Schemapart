package com.schemapart.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Minimal in-game settings page (no Cloth Config dependency). */
public final class SettingsScreen extends SpScreen {
    private final SpScreen parent;
    private String status = "";

    public SettingsScreen(SpScreen parent) {
        super(Component.translatable("schemapart.settings.title"));
        this.parent = parent;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        frameStart();
        renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, 0xD014161A);

        int w = Math.max(300, Math.min(560, width - 24));
        int h = Math.max(200, Math.min(320, height - 24));
        int x = (width - w) / 2;
        int y = Math.max(6, (height - h) / 2);
        boolean compact = h < 300;
        int headerH = compact ? 30 : 34;
        int rowH = compact ? 26 : 30;
        int toggleW = compact ? 64 : 74;
        int toggleH = compact ? 20 : 22;

        panel(g, x, y, w, h);
        g.fill(x + 1, y + 1, x + w - 1, y + headerH, 0xFF272C34);
        text(g, "Schemapart 设置", x + 14, y + (compact ? 10 : 12), TEXT);
        textRight(g, SpPaths.minecraftVersion() + "  ·  " + SchemapartClient.blocks.size() + " 方块",
                x + w - 14, y + (compact ? 10 : 12), MUTED);

        int rowY = y + headerH + 8;
        drawToggle(g, x, y, w, rowY, rowH, toggleW, toggleH, "ESC 自动保存方块清单",
                SchemapartClient.settings.autoSaveSelectionOnEsc, () -> {
                    SchemapartClient.settings.autoSaveSelectionOnEsc =
                            !SchemapartClient.settings.autoSaveSelectionOnEsc;
                    SchemapartClient.saveSettings();
                });
        rowY += rowH;
        drawToggle(g, x, y, w, rowY, rowH, toggleW, toggleH, "复制原理图到 Litematica",
                SchemapartClient.settings.copyToLitematica, () -> {
                    SchemapartClient.settings.copyToLitematica =
                            !SchemapartClient.settings.copyToLitematica;
                    SchemapartClient.saveSettings();
                });
        rowY += rowH;
        drawToggle(g, x, y, w, rowY, rowH, toggleW, toggleH, "默认开启 Floyd–Steinberg 抖动",
                SchemapartClient.settings.dither, () -> {
                    SchemapartClient.settings.dither = !SchemapartClient.settings.dither;
                    SchemapartClient.saveSettings();
                });
        rowY += rowH + (compact ? 2 : 6);

        int actionH = compact ? 20 : 24;
        int actionY = y + h - actionH - (compact ? 8 : 12);
        int rescanW = compact ? 54 : 110;
        int advancedW = compact ? 112 : 140;
        int doneW = compact ? 64 : 80;
        int gap = 6;
        int actionX = x + 16;
        button(g, actionX, actionY, rescanW, actionH, compact ? "重扫" : "重新扫描方块", true, () -> {
            SchemapartClient.rescan();
            status = "扫描完成: " + SchemapartClient.blocks.size() + " 个方块";
        });
        actionX += rescanW + gap;
        button(g, actionX, actionY, advancedW, actionH, "目录/快捷键", true,
                () -> minecraft.setScreen(new AdvancedSettingsScreen(this)));
        button(g, x + w - 14 - doneW, actionY, doneW, actionH, "完成", true, this::onClose);
        if (!status.isEmpty()) {
            text(g, trim(status, w - 160), x + 16, actionY - 15, ACCENT);
        }
    }

    private void drawToggle(GuiGraphics g, int x, int y, int w, int rowY, int rowH, int toggleW,
                            int toggleH, String label, boolean on, Runnable action) {
        text(g, trim(label, w - toggleW - 44), x + 16, rowY + (rowH - 8) / 2, TEXT);
        toggleButton(g, x + w - 14 - toggleW, rowY + (rowH - toggleH) / 2, toggleW, toggleH,
                on ? "开启" : "关闭", on, action);
    }

    @Override
    public void onClose() {
        SchemapartClient.saveSettings();
        minecraft.setScreen(parent);
    }
}
