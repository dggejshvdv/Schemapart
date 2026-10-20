package com.schemapart.client;

import com.schemapart.core.FileOps;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MAUI: picture browser for the image map-art workflow.
 *
 * <p>The schematic output folder is intentionally not shown here anymore; the
 * generated files are opened from MWMAUI/MMAUI or the system file manager.</p>
 */
public final class MAUIScreen extends SpScreen {
    private static final int CELL_W = 78;
    private static final int CELL_H = 90;

    private List<Path> images = List.of();
    private int scroll;
    private Path selected;
    private Path lastClickPath;
    private long lastClickTime;
    private String status = "";
    private int statusColor = MUTED;

    private Path clipboard;
    private boolean clipboardCut;
    private boolean contextOpen;
    private Path contextTarget;
    private int contextX;
    private int contextY;
    private int contextW = 150;
    private int contextH;

    private Path renameTarget;
    private String renameText = "";

    public MAUIScreen() {
        super(Component.translatable("schemapart.main.title"));
    }

    @Override
    protected void init() {
        super.init();
        SpPaths.init();
        SchemapartClient.ensureScanned();
        reload();
    }

    private void reload() {
        try {
            Files.createDirectories(SpPaths.picturesDir);
        } catch (IOException e) {
            setStatus("无法创建目录: " + e.getMessage(), DANGER);
        }
        images = FileOps.listFiles(SpPaths.picturesDir, FileOps.IMAGE_EXTENSIONS);
        if (selected != null && !Files.exists(selected)) selected = null;
    }

    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        frameStart();
        g.fill(0, 0, width, height, 0xB014161A);

        int headerH = 44;
        int footerH = 26;
        int margin = 8;
        int panelY = headerH + margin;
        int panelH = Math.max(80, height - panelY - footerH - margin);
        drawHeader(g);
        drawBrowser(g, margin, panelY, width - margin * 2, panelH);
        drawFooter(g);

        if (contextOpen) drawContext(g);
        if (renameTarget != null) drawRename(g);
    }

    private void drawHeader(GuiGraphics g) {
        g.fill(0, 0, width, 40, 0xF01B1E23);
        g.fill(0, 39, width, 40, BORDER);
        textShadow(g, "SCHEMAPART · MAUI", 8, 9, ACCENT);

        int bw = 44;
        int gap = 4;
        int x = width - 8 - bw;
        button(g, x, 9, bw, 24, "返回", true, this::onClose);
        x -= bw + gap;
        button(g, x, 9, bw, 24, "设置", true, () -> minecraft.setScreen(new SettingsScreen(this)));
        x -= bw + gap;
        button(g, x, 9, 52, 24, "打开目录", true, this::openPicturesFolder);
        x -= 52 + gap;
        button(g, x, 9, 44, 24, "刷新", true, () -> {
            reload();
            setStatus("已刷新: " + images.size() + " 张图片", ACCENT);
        });
        textRight(g, "图片 " + images.size(), width - 8 - (bw * 2 + 52 + 44 + gap * 3) - 12, 12, MUTED);
    }

    private void drawBrowser(GuiGraphics g, int x, int y, int w, int h) {
        panel(g, x, y, w, h);
        g.fill(x + 1, y + 1, x + w - 1, y + 24, 0xFF272C34);
        text(g, "图片  ·  pictures", x + 9, y + 8, TEXT);
        textRight(g, images.size() + "", x + w - 9, y + 8, MUTED);

        int contentY = y + 25;
        int contentH = h - 26;
        int pad = 8;
        int cols = Math.max(1, (w - pad * 2 + 6) / (CELL_W + 6));
        int rows = (images.size() + cols - 1) / cols;
        int contentTotal = rows * CELL_H;
        int maxScroll = Math.max(0, contentTotal - contentH + pad);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        clickable(x + 2, contentY, w - 4, contentH - 2, 1, () -> {
            contextOpen = true;
            contextTarget = null;
            contextX = mouseX;
            contextY = mouseY;
        });
        clickable(x + 2, contentY, w - 4, contentH - 2, 0, () -> {
            selected = null;
            contextOpen = false;
        });

        g.enableScissor(x + 2, contentY, x + w - 2, y + h - 2);
        if (images.isEmpty()) {
            textCentered(g, "把 PNG / JPG 图片拖进窗口", x + w / 2, contentY + contentH / 2 - 24, MUTED);
            textCentered(g, "或右键空白处 → 打开文件夹", x + w / 2, contentY + contentH / 2 - 8, MUTED);
            textCentered(g, trim(SpPaths.picturesDir.toString(), w - 24),
                    x + w / 2, contentY + contentH / 2 + 12, 0xFF66707D);
        }
        int startRow = scroll / CELL_H;
        int endRow = Math.min(rows, startRow + contentH / CELL_H + 2);
        for (int row = startRow; row < endRow; row++) {
            for (int col = 0; col < cols; col++) {
                int index = row * cols + col;
                if (index >= images.size()) break;
                Path path = images.get(index);
                int cx = x + pad + col * (CELL_W + 6);
                int cy = contentY + pad + row * CELL_H - scroll;
                drawFileCell(g, cx, cy, path);
            }
        }
        g.disableScissor();
        drawScrollbar(g, x + w - 4, contentY + 2, contentH - 4, scroll, maxScroll);
    }

    private void drawFileCell(GuiGraphics g, int x, int y, Path path) {
        boolean selectedCell = path.equals(selected);
        boolean hovered = hover(x, y, CELL_W, CELL_H - 2);
        int fill = selectedCell ? 0xFF2E4A40 : hovered ? 0xFF2E343C : 0xFF24282F;
        g.fill(x, y, x + CELL_W, y + CELL_H - 2, fill);
        outline(g, x, y, CELL_W, CELL_H - 2, selectedCell ? ACCENT : hovered ? BORDER_HI : BORDER);

        int thumb = 64;
        int tx = x + (CELL_W - thumb) / 2;
        int ty = y + 4;
        SpTextures.Entry entry = SpTextures.fromFile(path, thumb);
        if (entry != null) entry.draw(g, tx, ty, thumb, thumb);
        else drawMissing(g, tx, ty, thumb);

        String shown = trim(path.getFileName().toString(), CELL_W - 8);
        textCentered(g, shown, x + CELL_W / 2, y + CELL_H - 18, hovered ? TEXT : MUTED);

        clickable(x, y, CELL_W, CELL_H - 2, 0, () -> {
            selected = path;
            contextOpen = false;
            long now = System.currentTimeMillis();
            boolean doubleClick = path.equals(lastClickPath) && now - lastClickTime < 360;
            lastClickPath = path;
            lastClickTime = now;
            if (doubleClick) SchemapartClient.openMapArt(path);
        });
        clickable(x, y, CELL_W, CELL_H - 2, 1, () -> {
            selected = path;
            contextOpen = true;
            contextTarget = path;
            contextX = mouseX;
            contextY = mouseY;
        });
    }

    private void drawMissing(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0xFF1A1D22);
        textCentered(g, "?", x + size / 2, y + size / 2 - 4, DANGER);
    }

    private void drawScrollbar(GuiGraphics g, int x, int y, int h, int scroll, int maxScroll) {
        if (maxScroll <= 0) return;
        g.fill(x, y, x + 2, y + h, 0xFF1A1D22);
        int thumbH = Math.max(18, (int) ((h - 2) * (h / (double) (h + maxScroll))));
        int thumbY = y + (int) ((h - thumbH) * (scroll / (double) maxScroll));
        g.fill(x, thumbY, x + 2, thumbY + thumbH, BORDER_HI);
    }

    private void drawContext(GuiGraphics g) {
        List<String> labels = new ArrayList<>();
        if (contextTarget == null) {
            labels.add("打开文件夹");
            labels.add("粘贴");
            labels.add("刷新");
            labels.add("设置");
        } else {
            labels.add("重命名");
            labels.add("剪切");
            labels.add("复制");
            labels.add("删除");
        }
        contextW = 150;
        contextH = labels.size() * 20 + 8;
        int x = Math.max(4, Math.min(contextX, width - contextW - 4));
        int y = Math.max(4, Math.min(contextY, height - contextH - 4));
        contextX = x;
        contextY = y;
        g.fill(x + 2, y + 3, x + contextW + 2, y + contextH + 3, 0x70000000);
        g.fill(x, y, x + contextW, y + contextH, 0xFF1F232A);
        outline(g, x, y, contextW, contextH, BORDER_HI);
        for (int i = 0; i < labels.size(); i++) {
            int ry = y + 4 + i * 20;
            boolean hovered = inside(mouseX, mouseY, x + 4, ry, contextW - 8, 18);
            if (hovered) g.fill(x + 4, ry, x + contextW - 4, ry + 18, 0xFF313944);
            int color = labels.get(i).equals("删除") ? DANGER : TEXT;
            text(g, labels.get(i), x + 12, ry + 5, color);
            int index = i;
            clickable(x + 4, ry, contextW - 8, 18, 0, () -> contextAction(labels.get(index)));
        }
    }

    private void contextAction(String label) {
        playClick();
        if (contextTarget == null) {
            switch (label) {
                case "打开文件夹" -> openPicturesFolder();
                case "粘贴" -> paste();
                case "刷新" -> {
                    reload();
                    setStatus("已刷新: " + images.size() + " 张图片", ACCENT);
                }
                case "设置" -> minecraft.setScreen(new SettingsScreen(this));
                default -> {
                }
            }
            contextOpen = false;
            return;
        }
        Path target = contextTarget;
        switch (label) {
            case "重命名" -> beginRename(target);
            case "剪切" -> {
                clipboard = target;
                clipboardCut = true;
                setStatus("已剪切: " + target.getFileName(), WARN);
            }
            case "复制" -> {
                clipboard = target;
                clipboardCut = false;
                setStatus("已复制: " + target.getFileName(), ACCENT);
            }
            case "删除" -> {
                try {
                    FileOps.delete(target);
                    if (target.equals(selected)) selected = null;
                    reload();
                    setStatus("已删除: " + target.getFileName(), DANGER);
                } catch (IOException e) {
                    setStatus("删除失败: " + e.getMessage(), DANGER);
                }
            }
            default -> {
            }
        }
        contextOpen = false;
    }

    private void openPicturesFolder() {
        try {
            Files.createDirectories(SpPaths.picturesDir);
            Util.getPlatform().openPath(SpPaths.picturesDir);
            setStatus("已打开: " + SpPaths.picturesDir, ACCENT);
        } catch (Throwable t) {
            setStatus("打开文件夹失败: " + t.getMessage(), DANGER);
        }
    }

    private void paste() {
        if (clipboard == null) {
            setStatus("剪贴板为空", WARN);
            return;
        }
        try {
            Path result = clipboardCut
                    ? FileOps.move(clipboard, SpPaths.picturesDir,
                    FileOps.safeBaseName(clipboard.getFileName().toString()))
                    : FileOps.copy(clipboard, SpPaths.picturesDir,
                    FileOps.safeBaseName(clipboard.getFileName().toString()));
            setStatus((clipboardCut ? "已移动: " : "已粘贴: ") + result.getFileName(),
                    clipboardCut ? WARN : ACCENT);
            if (clipboardCut) {
                clipboard = null;
                clipboardCut = false;
            }
            reload();
        } catch (IOException e) {
            setStatus("粘贴失败: " + e.getMessage(), DANGER);
        }
    }

    private void beginRename(Path target) {
        renameTarget = target;
        renameText = target.getFileName().toString();
        contextOpen = false;
    }

    private void drawRename(GuiGraphics g) {
        dim(g);
        int w = 320;
        int h = 112;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel(g, x, y, w, h);
        text(g, "重命名", x + 14, y + 12, TEXT);
        text(g, "输入新文件名，回车确认，ESC 取消", x + 14, y + 28, MUTED);
        int fieldX = x + 14;
        int fieldY = y + 48;
        g.fill(fieldX, fieldY, x + w - 14, fieldY + 22, 0xFF15181D);
        outline(g, fieldX, fieldY, x + w - 14 - fieldX, 22, ACCENT);
        String shown = trim(renameText, w - 40);
        text(g, shown, fieldX + 6, fieldY + 7, TEXT);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = fieldX + 6 + font.width(shown);
            g.fill(cx, fieldY + 5, cx + 1, fieldY + 19, ACCENT);
        }
        int bw = 92;
        button(g, x + w - 14 - bw * 2 - 8, y + h - 34, bw, 22, "取消", true, () -> renameTarget = null);
        button(g, x + w - 14 - bw, y + h - 34, bw, 22, "确认", true, this::finishRename);
    }

    private void finishRename() {
        if (renameTarget == null) return;
        String name = renameText.trim();
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.startsWith(".")) {
            setStatus("文件名无效", DANGER);
            return;
        }
        String currentExt = FileOps.extension(renameTarget);
        if (!name.contains(".") && !currentExt.isEmpty()) name += currentExt;
        Path target = renameTarget.resolveSibling(name);
        try {
            Files.move(renameTarget, target);
            if (renameTarget.equals(selected)) selected = target;
            setStatus("已重命名: " + name, ACCENT);
            renameTarget = null;
            reload();
        } catch (IOException e) {
            setStatus("重命名失败: " + e.getMessage(), DANGER);
        }
    }

    private void drawFooter(GuiGraphics g) {
        g.fill(0, height - 24, width, height, 0xF01B1E23);
        g.fill(0, height - 25, width, height - 24, BORDER);
        if (!status.isEmpty()) {
            text(g, trim(status, width - 230), 8, height - 17, statusColor);
        } else {
            text(g, "双击制作 · 右键管理 · 拖入图片即可导入", 8, height - 17, MUTED);
        }
        if (clipboard != null) {
            String clip = (clipboardCut ? "剪切: " : "复制: ") + clipboard.getFileName();
            textRight(g, clip, width - 8, height - 17, clipboardCut ? WARN : ACCENT);
        } else {
            textRight(g, "右键空白 → 打开文件夹", width - 8, height - 17, MUTED);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        if (renameTarget != null) {
            boolean left = button == 0;
            if (left && !inside(mx, my, (width - 320) / 2, (height - 112) / 2, 320, 112)) {
                renameTarget = null;
                return true;
            }
            return runClick(mx, my, button) || left;
        }
        if (contextOpen) {
            if (inside(mx, my, contextX, contextY, contextW, contextH)) {
                runClick(mx, my, button);
            } else {
                contextOpen = false;
            }
            return true;
        }
        if (runClick(mx, my, button)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double deltaX, double deltaY) {
        if (inside(mx, my, 0, 44, width, height - 70)) {
            scroll = Math.max(0, scroll - (int) (deltaY * 32));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (renameTarget != null) {
            if (keyCode == 256) {
                renameTarget = null;
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                finishRename();
                return true;
            }
            if (keyCode == 259) {
                if (!renameText.isEmpty()) {
                    renameText = renameText.substring(0, renameText.length() - 1);
                }
                return true;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char codePoint = (char) event.codepoint();
        if (renameTarget != null) {
            if (codePoint >= 32 && codePoint != 127 && renameText.length() < 120) {
                renameText += codePoint;
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths == null || paths.isEmpty()) return;
        int imported = 0;
        for (Path path : paths) {
            try {
                if (path == null || !Files.isRegularFile(path)) continue;
                String ext = FileOps.extension(path).toLowerCase(Locale.ROOT);
                if (ext.length() < 2 || !FileOps.IMAGE_EXTENSIONS.contains(ext.substring(1))) continue;
                FileOps.copy(path, SpPaths.picturesDir, FileOps.safeBaseName(path.getFileName().toString()));
                imported++;
            } catch (IOException e) {
                setStatus("导入失败: " + e.getMessage(), DANGER);
            }
        }
        reload();
        setStatus(imported > 0 ? "已导入 " + imported + " 张图片" : "没有可导入的图片", imported > 0 ? ACCENT : WARN);
    }

    @Override
    public void onClose() {
        SchemapartClient.saveSettings();
        minecraft.setScreen(new WelcomeScreen());
    }
}
