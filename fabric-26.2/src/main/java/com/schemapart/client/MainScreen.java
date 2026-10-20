package com.schemapart.client;

import com.schemapart.core.FileOps;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Two-pane picture / schematic browser. */
public final class MainScreen extends SpScreen {
    private static final int CELL_W = 78;
    private static final int CELL_H = 90;

    private List<Path> images = List.of();
    private List<Path> schematics = List.of();
    private int leftScroll;
    private int rightScroll;
    private Path selected;
    private Path lastClickPath;
    private long lastClickTime;
    private String status = "";
    private int statusColor = MUTED;

    private Path clipboard;
    private boolean clipboardCut;
    private Side contextSide;
    private Path contextTarget;
    private int contextX;
    private int contextY;
    private int contextW = 124;
    private int contextH;

    private Path renameTarget;
    private String renameText = "";
    private boolean renameFocus;

    private enum Side {LEFT, RIGHT}

    public MainScreen() {
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
        images = FileOps.listFiles(SpPaths.picturesDir, FileOps.IMAGE_EXTENSIONS);
        schematics = FileOps.listFiles(SpPaths.maticDir, FileOps.SCHEMATIC_EXTENSIONS);
        if (selected != null && !Files.exists(selected)) selected = null;
    }

    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        frameStart();
        g.fill(0, 0, width, height, 0xB014161A);

        int headerH = 44;
        int footerH = 26;
        int margin = 8;
        int gap = 8;
        int panelY = headerH + margin;
        int panelH = Math.max(80, height - panelY - footerH - margin);
        int leftW = Math.max(180, (width - margin * 2 - gap) / 2);
        int rightW = Math.max(160, width - margin * 2 - gap - leftW);
        int leftX = margin;
        int rightX = leftX + leftW + gap;

        drawHeader(g, margin, leftW, rightX, rightW);
        drawBrowser(g, leftX, panelY, leftW, panelH, Side.LEFT, images);
        drawBrowser(g, rightX, panelY, rightW, panelH, Side.RIGHT, schematics);
        drawFooter(g, margin, width - margin, height);

        if (contextSide != null) drawContext(g);
        if (renameTarget != null) drawRename(g);
    }

    private void drawHeader(GuiGraphicsExtractor g, int x, int leftW, int rightX, int rightW) {
        g.fill(0, 0, width, 40, 0xF01B1E23);
        g.fill(0, 39, width, 40, BORDER);
        textShadow(g, "SCHEMAPART", x, 9, ACCENT);
        int btnW = 64;
        int btnH = 22;
        int bx = x + 92;
        button(g, bx, 9, btnW, btnH, "设置", true, () -> minecraft.gui.setScreen(new SettingsScreen(this)));
        String counts = "图片 " + images.size() + "   ·   原理图 " + schematics.size();
        textRight(g, counts, rightX + rightW, 12, MUTED);
    }

    private void drawFooter(GuiGraphicsExtractor g, int left, int right, int height) {
        g.fill(0, height - 24, width, height, 0xF01B1E23);
        g.fill(0, height - 25, width, height - 24, BORDER);
        if (!status.isEmpty()) {
            text(g, status, left, height - 17, statusColor);
        } else {
            text(g, "双击图片开始制作 · 右键文件管理 · 空白处右键粘贴", left, height - 17, MUTED);
        }
        if (clipboard != null) {
            String clip = (clipboardCut ? "剪切: " : "复制: ") + clipboard.getFileName();
            textRight(g, clip, right, height - 17, clipboardCut ? WARN : ACCENT);
        }
    }

    private void drawBrowser(GuiGraphicsExtractor g, int x, int y, int w, int h, Side side, List<Path> files) {
        panel(g, x, y, w, h);
        String title = side == Side.LEFT ? "图片  ·  pictures" : "原理图  ·  matic";
        g.fill(x + 1, y + 1, x + w - 1, y + 24, 0xFF272C34);
        text(g, title, x + 9, y + 8, TEXT);
        textRight(g, files.size() + "", x + w - 9, y + 8, MUTED);

        int contentY = y + 25;
        int contentH = h - 26;
        int pad = 8;
        int cols = Math.max(1, (w - pad * 2 + 6) / (CELL_W + 6));
        int rows = (files.size() + cols - 1) / cols;
        int contentTotal = rows * CELL_H;
        int scroll = side == Side.LEFT ? leftScroll : rightScroll;
        int maxScroll = Math.max(0, contentTotal - contentH + pad);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        if (side == Side.LEFT) leftScroll = scroll; else rightScroll = scroll;

        // Blank-area right click = paste.
        clickable(x + 2, contentY, w - 4, contentH - 2, 1, () -> openBlankContext(side, x + 2, contentY));
        clickable(x + 2, contentY, w - 4, contentH - 2, 0, () -> {
            selected = null;
            contextSide = null;
        });

        g.enableScissor(x + 2, contentY, x + w - 2, y + h - 2);
        if (files.isEmpty()) {
            String line1 = side == Side.LEFT ? "把 PNG / JPG 图片放进" : "生成的原理图会出现在这里";
            String line2 = side == Side.LEFT ? "mapart/pictures 文件夹" : "mapart/matic 文件夹";
            textCentered(g, line1, x + w / 2, contentY + contentH / 2 - 14, MUTED);
            textCentered(g, line2, x + w / 2, contentY + contentH / 2 + 2, MUTED);
        }
        int startRow = scroll / CELL_H;
        int endRow = Math.min(rows, startRow + contentH / CELL_H + 2);
        for (int row = startRow; row < endRow; row++) {
            for (int col = 0; col < cols; col++) {
                int index = row * cols + col;
                if (index >= files.size()) break;
                Path path = files.get(index);
                int cx = x + pad + col * (CELL_W + 6);
                int cy = contentY + pad + row * CELL_H - scroll;
                drawFileCell(g, cx, cy, side, path);
            }
        }
        g.disableScissor();
        drawScrollbar(g, x + w - 4, contentY + 2, contentH - 4, scroll, maxScroll);
    }

    private void drawFileCell(GuiGraphicsExtractor g, int x, int y, Side side, Path path) {
        boolean selectedCell = path.equals(selected);
        boolean hovered = hover(x, y, CELL_W, CELL_H - 2);
        int fill = selectedCell ? 0xFF2E4A40 : hovered ? 0xFF2E343C : 0xFF24282F;
        g.fill(x, y, x + CELL_W, y + CELL_H - 2, fill);
        outline(g, x, y, CELL_W, CELL_H - 2, selectedCell ? ACCENT : hovered ? BORDER_HI : BORDER);

        int thumb = 64;
        int tx = x + (CELL_W - thumb) / 2;
        int ty = y + 4;
        if (side == Side.LEFT) {
            SpTextures.Entry entry = SpTextures.fromFile(path, thumb);
            if (entry != null) entry.draw(g, tx, ty, thumb, thumb);
            else drawMissing(g, tx, ty, thumb);
        } else {
            drawSchematicIcon(g, tx, ty, thumb, path);
        }

        String name = path.getFileName().toString();
        String shown = trim(name, CELL_W - 8);
        textCentered(g, shown, x + CELL_W / 2, y + CELL_H - 18, hovered ? TEXT : MUTED);

        clickable(x, y, CELL_W, CELL_H - 2, 0, () -> {
            selected = path;
            contextSide = null;
            long now = System.currentTimeMillis();
            boolean doubleClick = path.equals(lastClickPath) && now - lastClickTime < 360;
            lastClickPath = path;
            lastClickTime = now;
            if (doubleClick) {
                if (side == Side.LEFT) {
                    SchemapartClient.openMapArt(path);
                } else {
                    showSchematicInfo(path);
                }
            }
        });
        clickable(x, y, CELL_W, CELL_H - 2, 1, () -> {
            selected = path;
            contextSide = side;
            contextTarget = path;
            contextX = mouseX;
            contextY = mouseY;
            contextH = 4 * 20 + 8;
        });
    }

    private void drawSchematicIcon(GuiGraphicsExtractor g, int x, int y, int size, Path path) {
        g.fill(x, y, x + size, y + size, 0xFF1A1D22);
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int c = ((row + col) & 1) == 0 ? 0xFF2E3C36 : 0xFF27302C;
                g.fill(x + 4 + col * 7, y + 6 + row * 7, x + 4 + col * 7 + 6, y + 6 + row * 7 + 6, c);
            }
        }
        g.fill(x + 8, y + 58, x + size - 8, y + 64, 0xFF1A1D22);
        String ext = FileOps.extension(path).replace(".", "").toUpperCase(Locale.ROOT);
        textCentered(g, ext, x + size / 2, y + 54, ACCENT);
    }

    private void drawMissing(GuiGraphicsExtractor g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0xFF1A1D22);
        textCentered(g, "?", x + size / 2, y + size / 2 - 4, DANGER);
    }

    private void drawScrollbar(GuiGraphicsExtractor g, int x, int y, int h, int scroll, int maxScroll) {
        if (maxScroll <= 0) return;
        g.fill(x, y, x + 2, y + h, 0xFF1A1D22);
        int thumbH = Math.max(18, (int) ((h - 2) * (h / (double) (h + maxScroll))));
        int thumbY = y + (int) ((h - thumbH) * (scroll / (double) maxScroll));
        g.fill(x, thumbY, x + 2, thumbY + thumbH, BORDER_HI);
    }

    private void openBlankContext(Side side, int x, int y) {
        contextSide = side;
        contextTarget = null;
        contextX = mouseX;
        contextY = mouseY;
        contextH = 28;
    }

    private void drawContext(GuiGraphicsExtractor g) {
        List<String> labels = new ArrayList<>();
        if (contextTarget == null) {
            labels.add("粘贴");
            contextH = 28;
        } else {
            labels.add("重命名");
            labels.add("剪切");
            labels.add("复制");
            labels.add("删除");
            contextH = labels.size() * 20 + 8;
        }
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
            paste();
            contextSide = null;
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
        contextSide = null;
    }

    private void paste() {
        if (clipboard == null || contextSide == null) return;
        Path dir = contextSide == Side.LEFT ? SpPaths.picturesDir : SpPaths.maticDir;
        String ext = FileOps.extension(clipboard);
        String bareExt = ext.length() > 1 ? ext.substring(1).toLowerCase(Locale.ROOT) : "";
        boolean compatible = contextSide == Side.LEFT
                ? FileOps.IMAGE_EXTENSIONS.contains(bareExt)
                : FileOps.SCHEMATIC_EXTENSIONS.contains(bareExt);
        if (!compatible) {
            setStatus("文件类型与目标文件夹不匹配", DANGER);
            return;
        }
        try {
            Path result = clipboardCut
                    ? FileOps.move(clipboard, dir, FileOps.safeBaseName(clipboard.getFileName().toString()))
                    : FileOps.copy(clipboard, dir, FileOps.safeBaseName(clipboard.getFileName().toString()));
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
        renameFocus = true;
        contextSide = null;
    }

    private void drawRename(GuiGraphicsExtractor g) {
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
        outline(g, fieldX, fieldY, x + w - 14, fieldY + 22, ACCENT);
        String shown = trim(renameText, w - 40);
        text(g, shown, fieldX + 6, fieldY + 7, TEXT);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = fieldX + 6 + font.width(shown);
            g.fill(cx, fieldY + 5, cx + 1, fieldY + 19, ACCENT);
        }
        int bw = 92;
        button(g, x + w - 14 - bw * 2 - 8, y + h - 34, bw, 22, "取消", true, () -> {
            renameTarget = null;
        });
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

    private void showSchematicInfo(Path path) {
        try {
            setStatus(path.getFileName() + "  ·  " + FileOps.humanSize(Files.size(path))
                    + "  ·  可直接放入 Litematica 的 schematics 文件夹", ACCENT);
        } catch (IOException e) {
            setStatus("无法读取: " + e.getMessage(), DANGER);
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
        if (contextSide != null) {
            boolean inMenu = inside(mx, my, contextX, contextY, contextW, contextH);
            if (inMenu) {
                runClick(mx, my, button);
            } else {
                contextSide = null;
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick) || runClick(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double deltaX, double deltaY) {
        if (inside(mx, my, 0, 44, width / 2, height - 70)) {
            leftScroll = Math.max(0, leftScroll - (int) (deltaY * 32));
            return true;
        }
        if (inside(mx, my, width / 2, 44, width / 2, height - 70)) {
            rightScroll = Math.max(0, rightScroll - (int) (deltaY * 32));
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
    public void onClose() {
        SchemapartClient.saveSettings();
        super.onClose();
    }
}
