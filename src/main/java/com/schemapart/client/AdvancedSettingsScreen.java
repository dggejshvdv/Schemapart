package com.schemapart.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Advanced settings: configurable data directories and the two-key menu shortcut. */
public final class AdvancedSettingsScreen extends SpScreen {
    private final SettingsScreen parent;
    private int editing = -1;
    private String editText = "";
    private boolean capturing;
    private int captureStep;
    private int captureKey1 = 77;
    private int captureKey2 = 80;
    private String status = "";

    public AdvancedSettingsScreen(SettingsScreen parent) {
        super(Component.translatable("schemapart.settings.advanced"));
        this.parent = parent;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        frameStart();
        renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, 0xD014161A);

        int w = Math.min(560, width - 20);
        int h = Math.min(330, height - 20);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel(g, x, y, w, h);
        g.fill(x + 1, y + 1, x + w - 1, y + 30, 0xFF272C34);
        text(g, "目录与快捷键", x + 14, y + 10, TEXT);
        textRight(g, SpPaths.minecraftVersion(), x + w - 14, y + 10, MUTED);

        int rowH = 32;
        int rowY = y + 40;
        rowY = drawPathRow(g, x, y, w, rowY, rowH, "图片目录", SpPaths.picturesDir, 0);
        rowY = drawPathRow(g, x, y, w, rowY, rowH, "输出目录", SpPaths.maticDir, 1);
        rowY = drawPathRow(g, x, y, w, rowY, rowH, "Litematica 目录", SpPaths.schematicsDir, 2);

        text(g, "打开界面快捷键", x + 16, rowY + 7, TEXT);
        text(g, shortcutLabel(), x + 150, rowY + 7, ACCENT);
        button(g, x + w - 14 - 70, rowY, 70, 22, "更改", true, () -> {
            captureStep = 0;
            captureKey1 = SchemapartClient.settings.shortcutKey1;
            captureKey2 = SchemapartClient.settings.shortcutKey2;
            capturing = true;
        });
        rowY += rowH;

        text(g, "提示：目录支持绝对路径或相对游戏目录的路径；留空恢复默认。",
                x + 16, rowY + 4, MUTED);
        rowY += 20;

        int by = y + h - 32;
        button(g, x + 16, by, 100, 22, "恢复默认", true, this::resetDefaults);
        button(g, x + w - 14 - 80, by, 80, 22, "完成", true, this::onClose);
        if (!status.isEmpty()) {
            text(g, trim(status, w - 180), x + 16, by - 16, ACCENT);
        }

        if (editing >= 0) drawEditModal(g);
        if (capturing) drawCaptureModal(g);
    }

    private int drawPathRow(GuiGraphics g, int x, int y, int w, int rowY, int rowH,
                            String label, java.nio.file.Path path, int index) {
        text(g, label, x + 16, rowY + 7, TEXT);
        text(g, trim(path.toString(), w - 230), x + 112, rowY + 7, MUTED);
        button(g, x + w - 14 - 70, rowY, 70, 22, "更改", true, () -> {
            editing = index;
            editText = currentPath(index);
        });
        if (index < 2) {
            g.fill(x + 14, rowY + rowH - 1, x + w - 14, rowY + rowH, 0x22FFFFFF);
        }
        return rowY + rowH;
    }

    private String currentPath(int index) {
        return switch (index) {
            case 0 -> SchemapartClient.settings.picturesPath;
            case 1 -> SchemapartClient.settings.maticPath;
            default -> SchemapartClient.settings.schematicsPath;
        };
    }

    private void drawEditModal(GuiGraphics g) {
        dim(g);
        int w = Math.min(460, width - 20);
        int h = 126;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel(g, x, y, w, h);
        text(g, "修改目录", x + 14, y + 12, TEXT);
        text(g, "输入绝对路径，或相对游戏目录的路径；留空恢复默认", x + 14, y + 28, MUTED);
        int fx = x + 14;
        int fy = y + 50;
        g.fill(fx, fy, x + w - 14, fy + 22, 0xFF15181D);
        outline(g, fx, fy, x + w - 14 - fx, 22, ACCENT);
        text(g, trim(editText, w - 40), fx + 6, fy + 7, TEXT);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = fx + 6 + font.width(trim(editText, w - 40));
            g.fill(cx, fy + 5, cx + 1, fy + 19, ACCENT);
        }
        int bw = 92;
        button(g, x + w - 14 - bw * 2 - 8, y + h - 32, bw, 22, "取消", true,
                () -> editing = -1);
        button(g, x + w - 14 - bw, y + h - 32, bw, 22, "应用", true, this::applyPath);
    }

    private void drawCaptureModal(GuiGraphics g) {
        dim(g);
        int w = Math.min(460, width - 20);
        int h = 136;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel(g, x, y, w, h);
        text(g, "设置打开界面的快捷键", x + 14, y + 12, TEXT);
        text(g, captureStep == 0 ? "请按下第一个键（ESC 取消）" : "请按下第二个键（ESC 取消）",
                x + 14, y + 32, MUTED);
        String first = keyLabel(captureStep == 0 ? captureKey1 : captureKey2);
        text(g, "当前: " + first + " + " + (captureStep == 0 ? "?" : keyLabel(captureKey2)),
                x + 14, y + 54, ACCENT);
        button(g, x + w - 14 - 92, y + h - 32, 92, 22, "取消", true, () -> capturing = false);
    }

    private void applyPath() {
        String value = editText == null ? "" : editText.trim();
        switch (editing) {
            case 0 -> SchemapartClient.settings.picturesPath = value;
            case 1 -> SchemapartClient.settings.maticPath = value;
            case 2 -> SchemapartClient.settings.schematicsPath = value;
            default -> {
            }
        }
        editing = -1;
        SchemapartClient.saveSettings();
        SpPaths.reload();
        status = "目录已更新";
    }

    private void resetDefaults() {
        SchemapartClient.settings.picturesPath = "";
        SchemapartClient.settings.maticPath = "";
        SchemapartClient.settings.schematicsPath = "";
        SchemapartClient.settings.shortcutKey1 = 77;
        SchemapartClient.settings.shortcutKey2 = 80;
        SchemapartClient.saveSettings();
        SpPaths.reload();
        status = "已恢复默认目录与快捷键";
    }

    private String shortcutLabel() {
        return keyLabel(SchemapartClient.settings.shortcutKey1) + " + "
                + keyLabel(SchemapartClient.settings.shortcutKey2);
    }

    private String keyLabel(int keyCode) {
        try {
            return InputConstants.Type.KEYSYM.getOrCreate(Math.max(1, keyCode))
                    .getDisplayName().getString();
        } catch (Throwable t) {
            return Integer.toString(keyCode);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (editing >= 0 || capturing) {
            return runClick(mx, my, button) || super.mouseClicked(mx, my, button);
        }
        return super.mouseClicked(mx, my, button) || runClick(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (capturing) {
            if (keyCode == 256) {
                capturing = false;
                return true;
            }
            if (captureStep == 0) {
                captureKey1 = keyCode;
                captureStep = 1;
            } else {
                SchemapartClient.settings.shortcutKey1 = captureKey1;
                SchemapartClient.settings.shortcutKey2 = keyCode;
                SchemapartClient.saveSettings();
                capturing = false;
                status = "快捷键已更新: " + shortcutLabel();
            }
            return true;
        }
        if (editing >= 0) {
            if (keyCode == 256) {
                editing = -1;
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                applyPath();
                return true;
            }
            if (keyCode == 259) {
                if (!editText.isEmpty()) editText = editText.substring(0, editText.length() - 1);
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editing >= 0) {
            if (codePoint >= 32 && codePoint != 127 && editText.length() < 240) {
                editText += codePoint;
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        SchemapartClient.saveSettings();
        minecraft.setScreen(parent);
    }
}
