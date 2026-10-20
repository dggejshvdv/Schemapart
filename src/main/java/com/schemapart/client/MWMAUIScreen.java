package com.schemapart.client;

import com.schemapart.client.wordart.WordArtDocument;
import com.schemapart.client.wordart.WordArtEngine;
import com.schemapart.client.wordart.WordArtPalette;
import com.schemapart.client.wordart.WordElement;
import com.schemapart.core.FileOps;
import com.schemapart.core.MapArtPlan;
import com.schemapart.core.MapArtService;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Full-screen word-map-art editor.
 *
 * <p>The map is the main surface. Left-clicking a character selects it and opens
 * the right editor; dragging moves it. Right-clicking empty space opens the
 * canvas/add menu. Every canvas cell is 128x128 blocks and the canvas itself is
 * always a whole number of cells.</p>
 */
public final class MWMAUIScreen extends SpScreen {
    private static final int HEADER = 44;
    private static final int FOOTER = 28;
    private static final int MAP = WordArtDocument.MAP_SIZE;
    private static final int ROW = 24;

    private WordArtDocument document;
    private MapArtPlan plan;
    private WordArtEngine.Result result;
    private SpTextures.Entry texture;
    private boolean dirty = true;

    private int areaX = 6;
    private int areaY = HEADER;
    private int areaW;
    private int areaH;
    private double zoom;
    private double panX;
    private double panY;

    private int selected = -1;
    private Mode mode = Mode.NONE;
    private int sidebarScroll;
    private int sidebarMaxScroll;
    private boolean dragging;
    private int dragIndex = -1;
    private double dragOffsetX;
    private double dragOffsetY;
    private double dragStartX;
    private double dragStartY;
    private boolean dragMoved;
    private boolean panning;
    private double panStartX;
    private double panStartY;
    private double panStartPanX;
    private double panStartPanY;

    private boolean contextOpen;
    private int contextX;
    private int contextY;

    private boolean priorityOpen;
    private boolean pendingGenerate;
    private boolean namePromptOpen;
    private String nameText = "";
    private String pendingName = "";

    private String dropdown;
    private int dropdownScroll;
    private List<DropItem> dropdownItems = List.of();
    private int dropdownSelected;
    private IntConsumer dropdownPick;
    private int dropdownX;
    private int dropdownY;
    private int dropdownW;
    private int dropdownH;
    private int dropdownListX;
    private int dropdownListY;
    private int dropdownListW;
    private int dropdownListH;

    private boolean addFocus;
    private String addText = "";
    private int addColor = 0;
    private int addBlock = 0;
    private String addFont = Font.SANS_SERIF;

    private boolean generating;
    private String status = "";
    private int statusColor = MUTED;

    private enum Mode { NONE, ELEMENT, CANVAS, ADD }

    private record DropItem(String label, int color) {
    }

    public MWMAUIScreen() {
        super(Component.translatable("schemapart.wordart.title"));
    }

    @Override
    protected void init() {
        super.init();
        SpPaths.init();
        SchemapartClient.ensureScanned();
        document = WordArtDocument.load(SpPaths.wordSettingsFile);
        dirty = true;
        status = "左键选择 / 拖拽文字，右键空白处打开画布菜单";
        statusColor = MUTED;
    }

    @Override
    public void onClose() {
        saveDocument();
        minecraft.setScreen(new WelcomeScreen());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        frameStart();
        g.fill(0, 0, width, height, 0xD014161A);

        if (dirty) {
            rebuildPreview();
            dirty = false;
        }

        drawHeader(g);
        drawCanvas(g);
        drawFooter(g);

        if (mode != Mode.NONE) drawSidebar(g);
        if (contextOpen) drawContextMenu(g);
        if (priorityOpen) drawPriority(g);
        if (dropdown != null) drawDropdownList(g);
        if (namePromptOpen) drawNamePrompt(g);
    }

    private void drawHeader(GuiGraphics g) {
        g.fill(0, 0, width, HEADER - 1, 0xF01B1E23);
        g.fill(0, HEADER - 1, width, HEADER, BORDER);
        textShadow(g, "MWMAUI", 8, 9, ACCENT);
        String info = document.mapsX + "×" + document.mapsY + " 地图  ·  "
                + document.widthPx() + "×" + document.heightPx();
        text(g, trim(info, Math.max(60, width / 2 - 120)), 58, 11, MUTED);

        int bw = 44;
        int gap = 4;
        int x = width - 8 - bw;
        button(g, x, 9, bw, 24, "返回", true, this::onClose);
        x -= bw + gap;
        button(g, x, 9, bw, 24, "设置", true, () -> minecraft.setScreen(new SettingsScreen(this)));
        x -= bw + gap;
        button(g, x, 9, bw, 24, generating ? "写入…" : "生成", !generating, this::requestGenerate);
        x -= bw + gap;
        button(g, x, 9, bw, 24, "画布", true, this::openCanvasMode);
        x -= bw + gap;
        button(g, x, 9, bw, 24, "添加", true, this::openAddMode);
    }

    private void drawCanvas(GuiGraphics g) {
        areaX = 6;
        areaY = HEADER;
        areaW = width - 12;
        areaH = height - HEADER - FOOTER - 4;
        if (areaW <= 0 || areaH <= 0) return;

        g.fill(areaX, areaY, areaX + areaW, areaY + areaH, 0xFF111317);
        outline(g, areaX, areaY, areaW, areaH, BORDER);

        if (zoom <= 0) resetView();
        double drawW = document.widthPx() * zoom;
        double drawH = document.heightPx() * zoom;
        int ox = (int) Math.round(areaX + (areaW - drawW) / 2 + panX);
        int oy = (int) Math.round(areaY + (areaH - drawH) / 2 + panY);
        int dw = Math.max(1, (int) Math.round(drawW));
        int dh = Math.max(1, (int) Math.round(drawH));

        drawChecker(g, areaX + 1, areaY + 1, areaW - 2, areaH - 2);
        if (texture != null) {
            texture.draw(g, ox, oy, dw, dh);
        }
        if (dw > 20 && dh > 20) {
            for (int mx = 1; mx < document.mapsX; mx++) {
                int x = ox + (int) Math.round(mx * MAP * zoom);
                g.fill(x, oy, x + 1, oy + dh, 0x55FFFFFF);
            }
            for (int my = 1; my < document.mapsY; my++) {
                int y = oy + (int) Math.round(my * MAP * zoom);
                g.fill(ox, y, ox + dw, y + 1, 0x55FFFFFF);
            }
        }
        outline(g, ox, oy, dw, dh, 0xFF6F7B8A);

        boolean allBorders = dragging || dragIndex >= 0;
        for (int i = 0; i < document.elements.size(); i++) {
            WordElement element = document.elements.get(i);
            int ex = ox + (int) Math.round(element.x * zoom);
            int ey = oy + (int) Math.round(element.y * zoom);
            int ew = Math.max(1, (int) Math.round(element.width * zoom));
            int eh = Math.max(1, (int) Math.round(element.height * zoom));
            if (i == selected) {
                outline(g, ex - 1, ey - 1, ew + 2, eh + 2, 0xFF101317);
                outline(g, ex, ey, ew, eh, 0xFFFFFFFF);
            } else if (allBorders) {
                outline(g, ex, ey, ew, eh, 0xCCFFFFFF);
            }
        }
        if (document.elements.isEmpty()) {
            textCentered(g, "画布为空，点击右上角「添加」", areaX + areaW / 2, areaY + areaH / 2, MUTED);
        }
    }

    private void drawChecker(GuiGraphics g, int x, int y, int w, int h) {
        int cell = 8;
        for (int yy = 0; yy < h; yy += cell) {
            for (int xx = 0; xx < w; xx += cell) {
                boolean dark = ((xx / cell) + (yy / cell)) % 2 == 0;
                g.fill(x + xx, y + yy, Math.min(x + xx + cell, x + w),
                        Math.min(y + yy + cell, y + h), dark ? 0xFF1A1E24 : 0xFF20252C);
            }
        }
    }

    private void drawFooter(GuiGraphics g) {
        g.fill(0, height - FOOTER, width, height, 0xF01B1E23);
        g.fill(0, height - FOOTER, width, height - FOOTER + 1, BORDER);
        if (!status.isEmpty()) {
            text(g, trim(status, width - 260), 8, height - 18, statusColor);
        }
        textRight(g, "左键选择/拖拽 · 右键空白菜单 · 滚轮缩放", width - 8, height - 18, MUTED);
    }

    private void drawSidebar(GuiGraphics g) {
        int sidebarW = sidebarWidth();
        int sx = width - sidebarW - 6;
        int sy = HEADER + 4;
        int sh = height - sy - FOOTER - 4;
        panel(g, sx, sy, sidebarW, sh);
        g.fill(sx + 1, sy + 1, sx + sidebarW - 1, sy + 24, 0xFF272C34);
        String title = switch (mode) {
            case ELEMENT -> "编辑文字/符号";
            case CANVAS -> "画布设置";
            case ADD -> "添加文字/符号";
            default -> "";
        };
        text(g, title, sx + 8, sy + 8, TEXT);
        clickable(sx, sy, sidebarW, sh, () -> {
        });
        button(g, sx + sidebarW - 26, sy + 4, 20, 18, "x", true, this::closeSidebar);

        int cx = sx + 8;
        int cw = sidebarW - 16;
        int top = sy + 30;
        int cy = top - sidebarScroll;
        int contentBottom;
        g.enableScissor(sx + 1, top, sx + sidebarW - 1, sy + sh - 3);
        if (mode == Mode.ELEMENT && selected >= 0 && selected < document.elements.size()) {
            contentBottom = drawElementEditor(g, cx, cy, cw);
        } else if (mode == Mode.CANVAS) {
            contentBottom = drawCanvasEditor(g, cx, cy, cw);
        } else {
            contentBottom = drawAddEditor(g, cx, cy, cw);
        }
        g.disableScissor();
        int viewportHeight = sy + sh - 8 - top;
        int contentHeight = contentBottom - (top - sidebarScroll);
        sidebarMaxScroll = Math.max(0, contentHeight - viewportHeight);
        sidebarScroll = clamp(sidebarScroll, 0, sidebarMaxScroll);
        if (sidebarMaxScroll > 0) {
            int barH = Math.max(18, (sh - 8) * (sh - 8) / ((sh - 8) + sidebarMaxScroll));
            int barY = top + ((sh - 8 - barH) * sidebarScroll) / Math.max(1, sidebarMaxScroll);
            g.fill(sx + sidebarW - 5, top, sx + sidebarW - 3, sy + sh - 3, 0xFF14171B);
            g.fill(sx + sidebarW - 5, barY, sx + sidebarW - 3, barY + barH, 0xFF5F6A78);
        }
    }

    private int drawElementEditor(GuiGraphics g, int x, int y, int w) {
        WordElement element = document.elements.get(selected);
        text(g, "字符「" + element.text + "」", x, y, ACCENT);
        y += 20;
        text(g, element.categoryLabel() + " · 最小 " + element.minSizeLabel(), x, y, MUTED);
        y += 22;

        text(g, "文字颜色", x, y, MUTED);
        y += 14;
        dropdown(g, "element.color", x, y, w, ROW, elementColorLabel(element),
                colorItems(false), element.colorIndex, value -> {
                    element.colorIndex = value;
                    elementChanged();
                });
        y += ROW + 4;

        text(g, "方块系列", x, y, MUTED);
        y += 14;
        dropdown(g, "element.block", x, y, w, ROW,
                WordArtPalette.blockType(element.blockTypeIndex).zhName(),
                blockItems(), element.blockTypeIndex, value -> {
                    element.blockTypeIndex = value;
                    document.priorityConfirmed = false;
                    elementChanged();
                });
        y += ROW + 4;

        text(g, "字体", x, y, MUTED);
        y += 14;
        dropdown(g, "element.font", x, y, w, ROW, trim(element.fontFamily, w - 12),
                fontItems(), fontIndex(element.fontFamily), value -> {
                    element.fontFamily = FONTS.get(clamp(value, 0, FONTS.size() - 1));
                    elementChanged();
                });
        y += ROW + 8;

        text(g, "尺寸", x, y, MUTED);
        y += 16;
        y = sizeRow(g, x, y, w, "宽", element.width, element.minimumSize()[0],
                value -> {
                    element.width = value;
                    elementChanged();
                });
        y = sizeRow(g, x, y, w, "高", element.height, element.minimumSize()[1],
                value -> {
                    element.height = value;
                    elementChanged();
                });
        y += 4;
        toggleButton(g, x, y, w, ROW, "阴影: " + (element.shadow ? "开" : "关"), element.shadow, () -> {
            element.shadow = !element.shadow;
            elementChanged();
        });
        y += ROW + 8;
        button(g, x, y, w, ROW, "删除此文字/符号", true, () -> {
            document.elements.remove(selected);
            selected = -1;
            mode = Mode.NONE;
            saveDocument();
            dirty = true;
        });
        y += ROW + 8;
        text(g, "左侧拖动可直接移动位置", x, y, MUTED);
        y += 18;
        return y;
    }

    private int drawCanvasEditor(GuiGraphics g, int x, int y, int w) {
        text(g, "画布大小（1 格 = 128×128）", x, y, ACCENT);
        y += 20;
        y = mapCountRow(g, x, y, w, "宽", document.mapsX, value -> {
            document.mapsX = value;
            canvasChanged();
        });
        y = mapCountRow(g, x, y, w, "高", document.mapsY, value -> {
            document.mapsY = value;
            canvasChanged();
        });
        y += 4;
        text(g, "背景颜色", x, y, MUTED);
        y += 14;
        int bg = document.backgroundColorIndex < 0 ? 0 : document.backgroundColorIndex + 1;
        dropdown(g, "canvas.background", x, y, w, ROW,
                backgroundLabel(), backgroundItems(), bg, value -> {
                    document.backgroundColorIndex = value - 1;
                    canvasChanged();
                });
        y += ROW + 8;
        text(g, "背景方块", x, y, MUTED);
        y += 14;
        dropdown(g, "canvas.block", x, y, w, ROW,
                WordArtPalette.blockType(document.backgroundBlockTypeIndex).zhName(),
                blockItems(), document.backgroundBlockTypeIndex, value -> {
                    document.backgroundBlockTypeIndex = value;
                    canvasChanged();
                });
        y += ROW + 8;
        toggleButton(g, x, y, w, ROW, "全部阴影: " + (document.shadow ? "开" : "关"),
                document.shadow, () -> {
                    document.shadow = !document.shadow;
                    for (WordElement element : document.elements) element.shadow = document.shadow;
                    canvasChanged();
                });
        y += ROW + 8;
        button(g, x, y, w, ROW, "编辑方块优先级", true, () -> {
            priorityOpen = true;
            pendingGenerate = false;
        });
        y += ROW + 8;
        text(g, "长宽为 128 的整倍数；元素不得小于最小尺寸", x, y, MUTED);
        y += 18;
        text(g, "当前: " + document.widthPx() + "×" + document.heightPx()
                + " 像素 / " + document.mapsX + "×" + document.mapsY + " 地图", x, y, MUTED);
        y += 18;
        return y;
    }

    private int drawAddEditor(GuiGraphics g, int x, int y, int w) {
        text(g, "输入文字或符号", x, y, ACCENT);
        y += 18;
        int fieldH = ROW;
        boolean hovered = hover(x, y, w, fieldH);
        g.fill(x, y, x + w, y + fieldH, addFocus ? 0xFF343B45 : 0xFF20242A);
        outline(g, x, y, w, fieldH, addFocus ? ACCENT : hovered ? 0xFF6F7A88 : BORDER);
        text(g, addText.isEmpty() ? "点击输入…" : addText, x + 6, y + 8,
                addText.isEmpty() ? MUTED : TEXT);
        if (addFocus && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caret = x + 6 + font.width(addText);
            g.fill(caret, y + 6, caret + 1, y + fieldH - 6, ACCENT);
        }
        clickable(x, y, w, fieldH, () -> addFocus = true);
        y += fieldH + 8;

        text(g, "颜色", x, y, MUTED);
        y += 14;
        dropdown(g, "add.color", x, y, w, ROW,
                WordArtPalette.color(addColor).zhName(), colorItems(false), addColor,
                value -> addColor = value);
        y += ROW + 4;
        text(g, "方块系列", x, y, MUTED);
        y += 14;
        dropdown(g, "add.block", x, y, w, ROW,
                WordArtPalette.blockType(addBlock).zhName(), blockItems(), addBlock,
                value -> addBlock = value);
        y += ROW + 4;
        text(g, "字体", x, y, MUTED);
        y += 14;
        dropdown(g, "add.font", x, y, w, ROW, trim(addFont, w - 12),
                fontItems(), fontIndex(addFont),
                value -> addFont = FONTS.get(clamp(value, 0, FONTS.size() - 1)));
        y += ROW + 8;
        button(g, x, y, w, ROW, "添加到画布", !addText.isBlank(), () -> addTextToCanvas(addText));
        y += ROW + 8;
        text(g, "每个字符默认 128×128；画布不足时自动扩展", x, y, MUTED);
        y += 18;
        return y;
    }

    private int sizeRow(GuiGraphics g, int x, int y, int w, String label, int value, int minimum,
                        IntConsumer setter) {
        text(g, label + "≥" + minimum, x, y + 7, MUTED);
        int bw = 20;
        int minusX = x + 46;
        int plusX = x + w - bw;
        button(g, minusX, y, bw, ROW, "-", value > minimum,
                () -> setter.accept(clamp(value - 16, minimum, WordArtDocument.MAX_CANVAS_SIZE)));
        textCentered(g, Integer.toString(value), (minusX + bw + plusX) / 2, y + 8, TEXT);
        button(g, plusX, y, bw, ROW, "+", value + 16 <= WordArtDocument.MAX_CANVAS_SIZE,
                () -> setter.accept(clamp(value + 16, minimum, WordArtDocument.MAX_CANVAS_SIZE)));
        return y + ROW + 4;
    }

    private int mapCountRow(GuiGraphics g, int x, int y, int w, String label, int value,
                            IntConsumer setter) {
        text(g, label, x, y + 7, MUTED);
        int bx = x + 30;
        button(g, bx, y, 22, ROW, "−", value > WordArtDocument.MIN_MAPS,
                () -> setter.accept(clamp(value - 1, WordArtDocument.MIN_MAPS, WordArtDocument.MAX_MAPS)));
        textCentered(g, value + " 格", bx + 24 + 36, y + 8, TEXT);
        button(g, bx + 24 + 72, y, 22, ROW, "+", value < WordArtDocument.MAX_MAPS,
                () -> setter.accept(clamp(value + 1, WordArtDocument.MIN_MAPS, WordArtDocument.MAX_MAPS)));
        return y + ROW + 4;
    }

    private void drawContextMenu(GuiGraphics g) {
        String[] items = {"添加文字/符号", "画布设置", "方块优先级", "生成地图画"};
        int w = 150;
        int h = items.length * 20 + 8;
        contextX = clamp(contextX, 4, Math.max(4, width - w - 4));
        contextY = clamp(contextY, 4, Math.max(4, height - h - 4));
        panel(g, contextX, contextY, w, h);
        clickable(contextX, contextY, w, h, () -> {
        });
        for (int i = 0; i < items.length; i++) {
            int y = contextY + 4 + i * 20;
            boolean hovered = inside(mouseX, mouseY, contextX + 2, y, w - 4, 20);
            if (hovered) g.fill(contextX + 2, y, contextX + w - 2, y + 20, 0xFF343B45);
            text(g, items[i], contextX + 8, y + 6, hovered ? ACCENT : TEXT);
            int index = i;
            clickable(contextX + 2, y, w - 4, 20, () -> {
                playClick();
                contextOpen = false;
                switch (index) {
                    case 0 -> openAddMode();
                    case 1 -> openCanvasMode();
                    case 2 -> {
                        pendingGenerate = false;
                        priorityOpen = true;
                    }
                    case 3 -> requestGenerate();
                    default -> {
                    }
                }
            });
        }
    }

    private void drawPriority(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xC0000000);
        int w = Math.min(390, width - 20);
        int h = 26 + 18 + 5 * 26 + 20 + 28 + 16;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel(g, x, y, w, h);
        clickable(x, y, w, h, () -> {
        });
        textCentered(g, "方块优先级", x + w / 2, y + 10, ACCENT);
        textCentered(g, "文字重叠且材质不同，越靠上越优先", x + w / 2, y + 28, MUTED);
        int rowY = y + 48;
        for (int i = 0; i < document.priority.size(); i++) {
            int family = document.priority.get(i);
            int ry = rowY + i * 26;
            int rowIndex = i;
            g.fill(x + 10, ry, x + w - 10, ry + 24, i % 2 == 0 ? 0xFF242A32 : 0xFF20252C);
            text(g, (i + 1) + ". " + WordArtPalette.blockType(family).zhName(), x + 18, ry + 8, TEXT);
            int bx = x + w - 86;
            button(g, bx, ry + 2, 24, 20, "↑", i > 0, () -> swapPriority(rowIndex, rowIndex - 1));
            button(g, bx + 28, ry + 2, 24, 20, "↓", i < document.priority.size() - 1,
                    () -> swapPriority(rowIndex, rowIndex + 1));
        }
        int by = y + h - 34;
        button(g, x + 12, by, 90, 24, "取消", true, () -> {
            priorityOpen = false;
            pendingGenerate = false;
        });
        button(g, x + w - 102, by, 90, 24, "确定", true, () -> {
            document.priorityConfirmed = true;
            priorityOpen = false;
            saveDocument();
            rebuildPreview();
            dirty = false;
            if (pendingGenerate) {
                pendingGenerate = false;
                generateNow(pendingName);
            }
        });
    }

    private void swapPriority(int a, int b) {
        if (a < 0 || b < 0 || a >= document.priority.size() || b >= document.priority.size()) return;
        int value = document.priority.get(a);
        document.priority.set(a, document.priority.get(b));
        document.priority.set(b, value);
        saveDocument();
        dirty = true;
    }

    private void dropdown(GuiGraphics g, String key, int x, int y, int w, int h,
                          String label, List<DropItem> items, int selectedIndex, IntConsumer pick) {
        boolean open = key.equals(dropdown);
        boolean hovered = hover(x, y, w, h);
        g.fill(x, y, x + w, y + h, open ? 0xFF343B45 : hovered ? 0xFF2E353F : 0xFF20242A);
        outline(g, x, y, w, h, open ? ACCENT : hovered ? 0xFF6F7A88 : BORDER);
        int textX = x + 6;
        if (selectedIndex >= 0 && selectedIndex < items.size() && items.get(selectedIndex).color() >= 0) {
            int color = items.get(selectedIndex).color();
            g.fill(x + 5, y + 5, x + 17, y + h - 5, 0xFF000000 | (color & 0xFFFFFF));
            textX = x + 22;
        }
        text(g, trim(label, w - (textX - x) - 18), textX, y + 8, TEXT);
        textRight(g, open ? "^" : "v", x + w - 5, y + 8, MUTED);
        clickable(x, y, w, h, () -> {
            playClick();
            if (key.equals(dropdown)) {
                dropdown = null;
            } else {
                dropdown = key;
                dropdownScroll = 0;
            }
        });
        if (open) {
            dropdownX = x;
            dropdownY = y;
            dropdownW = w;
            dropdownH = h;
            dropdownItems = items;
            dropdownSelected = selectedIndex;
            dropdownPick = pick;
        }
    }

    private void drawDropdownList(GuiGraphics g) {
        if (dropdown == null || dropdownItems.isEmpty()) return;
        int visible = Math.min(8, dropdownItems.size());
        dropdownScroll = clamp(dropdownScroll, 0, Math.max(0, dropdownItems.size() - visible));
        int rowH = 18;
        int listH = visible * rowH + 4;
        int x = clamp(dropdownX, 4, Math.max(4, width - dropdownW - 4));
        int y = dropdownY + dropdownH + 2;
        if (y + listH > height - 34) y = dropdownY - listH - 2;
        y = clamp(y, 4, Math.max(4, height - listH - 4));
        dropdownListX = x;
        dropdownListY = y;
        dropdownListW = dropdownW;
        dropdownListH = listH;
        panel(g, x, y, dropdownW, listH);
        clickable(x, y, dropdownW, listH, () -> {
        });
        for (int row = 0; row < visible; row++) {
            int index = dropdownScroll + row;
            if (index >= dropdownItems.size()) break;
            int iy = y + 2 + row * rowH;
            boolean selectedItem = index == dropdownSelected;
            boolean hovered = inside(mouseX, mouseY, x + 2, iy, dropdownW - 4, rowH);
            if (selectedItem || hovered) {
                g.fill(x + 2, iy, x + dropdownW - 2, iy + rowH,
                        selectedItem ? 0xFF344B42 : 0xFF343B45);
            }
            DropItem item = dropdownItems.get(index);
            int tx = x + 6;
            if (item.color() >= 0) {
                g.fill(x + 5, iy + 4, x + 15, iy + rowH - 4, 0xFF000000 | (item.color() & 0xFFFFFF));
                tx = x + 20;
            }
            text(g, trim(item.label(), dropdownW - (tx - x) - 8), tx, iy + 5,
                    selectedItem ? ACCENT : TEXT);
            int chosen = index;
            clickable(x + 2, iy, dropdownW - 4, rowH, () -> {
                playClick();
                if (dropdownPick != null) dropdownPick.accept(chosen);
                dropdown = null;
            });
        }
        if (dropdownItems.size() > visible) {
            textRight(g, "滚轮翻页", x + dropdownW - 4, y + listH - 1, MUTED);
        }
    }

    private List<DropItem> colorItems(boolean withAir) {
        List<DropItem> items = new ArrayList<>();
        if (withAir) items.add(new DropItem("空气", -1));
        for (WordArtPalette.Color color : WordArtPalette.COLORS) {
            items.add(new DropItem(color.zhName(), color.rgb()));
        }
        return items;
    }

    private List<DropItem> backgroundItems() {
        return colorItems(true);
    }

    private List<DropItem> blockItems() {
        List<DropItem> items = new ArrayList<>();
        for (WordArtPalette.BlockType type : WordArtPalette.BLOCK_TYPES) {
            items.add(new DropItem(type.zhName(), -1));
        }
        return items;
    }

    private List<DropItem> fontItems() {
        List<DropItem> items = new ArrayList<>(FONTS.size());
        for (String family : FONTS) items.add(new DropItem(family, -1));
        return items;
    }

    private String elementColorLabel(WordElement element) {
        return WordArtPalette.color(element.colorIndex).zhName();
    }

    private String backgroundLabel() {
        return document.backgroundColorIndex < 0
                ? "空气" : WordArtPalette.color(document.backgroundColorIndex).zhName();
    }

    private int fontIndex(String family) {
        for (int i = 0; i < FONTS.size(); i++) {
            if (FONTS.get(i).equalsIgnoreCase(family)) return i;
        }
        return 0;
    }

    private static final List<String> FONTS = loadFonts();

    private static List<String> loadFonts() {
        List<String> names = new ArrayList<>();
        names.add(Font.SANS_SERIF);
        try {
            String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames(Locale.ROOT);
            if (available != null) {
                for (String name : available) {
                    if (name != null && !name.isBlank() && !names.contains(name)) names.add(name);
                }
            }
        } catch (Throwable ignored) {
        }
        names.sort(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)));
        if (names.size() > 120) names = new ArrayList<>(names.subList(0, 120));
        return List.copyOf(names);
    }

    private void requestGenerate() {
        if (dirty) {
            rebuildPreview();
            dirty = false;
        }
        if (document.elements.isEmpty()) {
            status = "画布为空，先添加文字或符号";
            statusColor = WARN;
            return;
        }
        nameText = document.sourceName();
        namePromptOpen = true;
    }

    private void confirmNamePrompt() {
        String name = nameText == null ? "" : nameText.trim();
        if (name.isEmpty()) {
            status = "文件名不能为空";
            statusColor = WARN;
            return;
        }
        namePromptOpen = false;
        pendingName = FileOps.safeBaseName(name);
        continueGenerate();
    }

    private void continueGenerate() {
        if (result != null && result.overlapDifferentFamily() && !document.priorityConfirmed) {
            pendingGenerate = true;
            priorityOpen = true;
            return;
        }
        generateNow(pendingName);
    }

    private void drawNamePrompt(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xC0000000);
        int w = 360;
        int h = 126;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel(g, x, y, w, h);
        clickable(x, y, w, h, () -> {
        });
        text(g, "命名投影文件", x + 14, y + 12, TEXT);
        text(g, "输入文件名，回车继续，ESC 取消", x + 14, y + 28, MUTED);
        int fx = x + 14;
        int fy = y + 50;
        g.fill(fx, fy, x + w - 14, fy + 22, 0xFF15181D);
        outline(g, fx, fy, x + w - 14 - fx, 22, ACCENT);
        text(g, trim(nameText, w - 40), fx + 6, fy + 7, TEXT);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = fx + 6 + font.width(trim(nameText, w - 40));
            g.fill(cx, fy + 5, cx + 1, fy + 19, ACCENT);
        }
        int bw = 92;
        button(g, x + w - 14 - bw * 2 - 8, y + h - 32, bw, 22, "取消", true,
                () -> namePromptOpen = false);
        button(g, x + w - 14 - bw, y + h - 32, bw, 22, "继续", true, this::confirmNamePrompt);
    }

    private void generateNow(String name) {
        if (plan == null) {
            dirty = true;
            status = "正在生成预览…";
            return;
        }
        generating = true;
        status = "正在写入 .litematic…";
        statusColor = MUTED;
        String baseName = name == null || name.isBlank() ? document.sourceName() : name;
        MapArtPlan snapshot = new MapArtPlan(FileOps.safeBaseName(baseName), plan.width, plan.height,
                plan.blockIndex, plan.blocks, plan.previewArgb, plan.counts);
        new Thread(() -> {
            try {
                MapArtService.Result written = MapArtService.write(snapshot, SpPaths.maticDir,
                        SpPaths.dataVersion());
                boolean copied = false;
                if (SchemapartClient.settings.copyToLitematica
                        && FabricLoader.getInstance().isModLoaded("litematica")) {
                    Path schematics = SpPaths.schematicsDir;
                    Files.createDirectories(schematics);
                    Path target = FileOps.unique(schematics,
                            FileOps.safeBaseName(written.path().getFileName().toString()), ".litematic");
                    Files.copy(written.path(), target, StandardCopyOption.COPY_ATTRIBUTES);
                    copied = true;
                }
                String text = "已生成: " + written.path().getFileName()
                        + "  (" + snapshot.placedBlocks() + " 方块)"
                        + (copied ? " · 已复制到 schematics" : "");
                minecraft.execute(() -> {
                    generating = false;
                    status = text;
                    statusColor = ACCENT;
                });
            } catch (Throwable t) {
                SchemapartClient.LOGGER.error("Word-art generation failed", t);
                minecraft.execute(() -> {
                    generating = false;
                    status = "生成失败: " + t.getMessage();
                    statusColor = DANGER;
                });
            }
        }, "Schemapart-WordArt").start();
    }

    private void rebuildPreview() {
        if (document == null) return;
        try {
            document.normalize();
            result = WordArtEngine.build(document, SchemapartClient.blocks);
            plan = result.plan();
            closeTexture();
            texture = SpTextures.create(plan.previewArgb, plan.width, plan.height);
            if (result.overlapDifferentFamily() && !document.priorityConfirmed) {
                status = "检测到不同材质重叠，生成时会询问优先级";
                statusColor = WARN;
            } else if (result.warning() != null) {
                status = result.warning();
                statusColor = WARN;
            } else {
                status = plan.width + "×" + plan.height + "  ·  "
                        + plan.placedBlocks() + " 方块  ·  " + plan.uniqueBlocks() + " 种方块";
                statusColor = ACCENT;
            }
        } catch (Throwable t) {
            plan = null;
            closeTexture();
            status = "预览失败: " + t.getMessage();
            statusColor = DANGER;
            SchemapartClient.LOGGER.error("Word-art preview failed", t);
        }
    }

    private void closeTexture() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }

    private void resetView() {
        double fit = Math.min(areaW / (double) document.widthPx(),
                areaH / (double) document.heightPx());
        zoom = Math.max(0.05, fit * 0.92);
        panX = 0;
        panY = 0;
    }

    private void elementChanged() {
        WordElement element = document.elements.get(selected);
        if (element != null) {
            element.clampSize();
            element.clampPosition(document.widthPx(), document.heightPx());
        }
        saveDocument();
        dirty = true;
    }

    private void canvasChanged() {
        document.normalize();
        for (WordElement element : document.elements) {
            element.clampPosition(document.widthPx(), document.heightPx());
        }
        saveDocument();
        zoom = 0;
        dirty = true;
    }

    private void addTextToCanvas(String text) {
        if (text == null || text.isBlank()) {
            status = "请输入文字或符号";
            statusColor = WARN;
            return;
        }
        List<WordElement> added = document.addText(text, addFont, addColor, addBlock, document.shadow);
        if (added.isEmpty()) {
            status = "没有可添加的字符";
            statusColor = WARN;
            return;
        }
        document.normalize();
        selected = document.elements.size() - 1;
        mode = Mode.ELEMENT;
        addText = "";
        zoom = 0;
        saveDocument();
        dirty = true;
    }

    private void openAddMode() {
        mode = Mode.ADD;
        selected = -1;
        addFocus = true;
        sidebarScroll = 0;
        dropdown = null;
    }

    private void openCanvasMode() {
        mode = Mode.CANVAS;
        selected = -1;
        addFocus = false;
        sidebarScroll = 0;
        dropdown = null;
    }

    private void closeSidebar() {
        mode = Mode.NONE;
        selected = -1;
        addFocus = false;
        dropdown = null;
        saveDocument();
    }

    private void saveDocument() {
        if (document != null) document.save(SpPaths.wordSettingsFile);
    }

    private double screenToCanvasX(double sx) {
        double drawW = document.widthPx() * zoom;
        double ox = areaX + (areaW - drawW) / 2 + panX;
        return (sx - ox) / zoom;
    }

    private double screenToCanvasY(double sy) {
        double drawH = document.heightPx() * zoom;
        double oy = areaY + (areaH - drawH) / 2 + panY;
        return (sy - oy) / zoom;
    }

    private int elementAt(double sx, double sy) {
        double cx = screenToCanvasX(sx);
        double cy = screenToCanvasY(sy);
        for (int i = document.elements.size() - 1; i >= 0; i--) {
            WordElement element = document.elements.get(i);
            if (cx >= element.x && cx < element.x + element.width
                    && cy >= element.y && cy < element.y + element.height) {
                return i;
            }
        }
        return -1;
    }

    private boolean overCanvas(double mx, double my) {
        return mx >= areaX && mx < areaX + areaW && my >= areaY && my < areaY + areaH;
    }

    private int sidebarWidth() {
        return Math.min(210, Math.max(180, width / 3));
    }

    private boolean overSidebar(double mx, double my) {
        int sidebarW = sidebarWidth();
        int sx = width - sidebarW - 6;
        int sy = HEADER + 4;
        int sh = height - sy - FOOTER - 4;
        return mode != Mode.NONE && mx >= sx && mx < sx + sidebarW && my >= sy && my < sy + sh;
    }

    private boolean overDropdownList(double mx, double my) {
        return dropdown != null
                && mx >= dropdownListX && mx < dropdownListX + dropdownListW
                && my >= dropdownListY && my < dropdownListY + dropdownListH;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        if (namePromptOpen) {
            return runClick(mx, my, button) || super.mouseClicked(event, doubleClick);
        }
        if (priorityOpen) {
            return runClick(mx, my, button) || super.mouseClicked(event, doubleClick);
        }
        if (contextOpen && !inside(mx, my, contextX, contextY, 150, 4 * 20 + 8)) {
            contextOpen = false;
        }
        if (runClick(mx, my, button)) return true;
        if (dropdown != null && !overDropdownList(mx, my)
                && !inside(mx, my, dropdownX, dropdownY, dropdownW, dropdownH)) {
            dropdown = null;
        }

        if (overSidebar(mx, my)) return true;
        if (!overCanvas(mx, my)) return super.mouseClicked(event, doubleClick);

        if (button == 1) {
            int hit = elementAt(mx, my);
            if (hit >= 0) {
                selected = hit;
                mode = Mode.ELEMENT;
                addFocus = false;
                dropdown = null;
            } else {
                selected = -1;
                mode = Mode.NONE;
                addFocus = false;
                dropdown = null;
                contextOpen = true;
                contextX = (int) mx;
                contextY = (int) my;
            }
            return true;
        }
        if (button == 0) {
            int hit = elementAt(mx, my);
            if (hit >= 0) {
                selected = hit;
                mode = Mode.ELEMENT;
                addFocus = false;
                dropdown = null;
                WordElement element = document.elements.get(hit);
                dragIndex = hit;
                dragging = true;
                dragMoved = false;
                dragOffsetX = screenToCanvasX(mx) - element.x;
                dragOffsetY = screenToCanvasY(my) - element.y;
                dragStartX = mx;
                dragStartY = my;
            } else {
                selected = -1;
                mode = Mode.NONE;
                addFocus = false;
                contextOpen = false;
                panning = true;
                panStartX = mx;
                panStartY = my;
                panStartPanX = panX;
                panStartPanY = panY;
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        if (dragging && dragIndex >= 0 && dragIndex < document.elements.size()) {
            if (!dragMoved && Math.abs(mx - dragStartX) <= 3 && Math.abs(my - dragStartY) <= 3) {
                return true;
            }
            dragMoved = true;
            WordElement element = document.elements.get(dragIndex);
            element.x = (int) Math.round(screenToCanvasX(mx) - dragOffsetX);
            element.y = (int) Math.round(screenToCanvasY(my) - dragOffsetY);
            element.clampPosition(document.widthPx(), document.heightPx());
            dirty = true;
            return true;
        }
        if (panning) {
            panX = panStartPanX + mx - panStartX;
            panY = panStartPanY + my - panStartY;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            dragIndex = -1;
            if (dragMoved) saveDocument();
            return true;
        }
        if (panning) {
            panning = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double deltaX, double deltaY) {
        if (dropdown != null) {
            int visible = Math.min(8, dropdownItems.size());
            dropdownScroll = clamp(dropdownScroll - (int) Math.signum(deltaY),
                    0, Math.max(0, dropdownItems.size() - visible));
            return true;
        }
        if (overSidebar(mx, my)) {
            sidebarScroll = clamp(sidebarScroll - (int) Math.signum(deltaY) * 18,
                    0, sidebarMaxScroll);
            return true;
        }
        if (overCanvas(mx, my)) {
            double oldZoom = zoom;
            zoom = clamp(zoom * (deltaY > 0 ? 1.15 : 1.0 / 1.15), 0.03, 8.0);
            double factor = zoom / oldZoom;
            panX = (mx - areaX - areaW / 2.0) * (1 - factor) + panX * factor;
            panY = (my - areaY - areaH / 2.0) * (1 - factor) + panY * factor;
            return true;
        }
        return super.mouseScrolled(mx, my, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (namePromptOpen) {
            if (keyCode == 256) {
                namePromptOpen = false;
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                confirmNamePrompt();
                return true;
            }
            if (keyCode == 259) {
                if (!nameText.isEmpty()) nameText = nameText.substring(0, nameText.length() - 1);
                return true;
            }
            return true;
        }
        if (keyCode == 256) {
            if (addFocus) {
                addFocus = false;
                return true;
            }
            if (dropdown != null) {
                dropdown = null;
                return true;
            }
            if (priorityOpen) {
                priorityOpen = false;
                pendingGenerate = false;
                return true;
            }
            if (contextOpen) {
                contextOpen = false;
                return true;
            }
            if (mode != Mode.NONE) {
                closeSidebar();
                return true;
            }
            onClose();
            return true;
        }
        if (addFocus) {
            if (keyCode == 259 && !addText.isEmpty()) {
                addText = addText.substring(0, addText.length() - 1);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                addTextToCanvas(addText);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char codePoint = (char) event.codepoint();
        if (namePromptOpen) {
            if (codePoint >= 32 && codePoint != 127 && nameText.length() < 80) {
                nameText += codePoint;
            }
            return true;
        }
        if (addFocus) {
            if (!Character.isISOControl(codePoint) && addText.length() < 100) {
                addText += codePoint;
            }
            return true;
        }
        return super.charTyped(event);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
