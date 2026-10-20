package com.schemapart.client;

import com.schemapart.core.ArtSettings;
import com.schemapart.core.BlockInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * JEI-style paginated block whitelist editor.
 *
 * <p>The layout only ever uses the logical GUI size ({@code width} / {@code height}). The
 * palette is always clamped to a 9x6 slot grid; slots stay square, the grid is centered in
 * the free space and every control is anchored to the logical screen edges. The search
 * field moves to its own full-width row below the header on narrow screens and the bottom
 * controls are split into a pagination row plus a compact action row so nothing overlaps.</p>
 */
public final class BlockSelectScreen extends SpScreen {
    /** The palette never grows past a 9x6 JEI-style grid. */
    private static final int MAX_COLS = 9;
    private static final int MAX_ROWS = 6;
    /** Responsive square slot bounds (logical pixels). */
    private static final int MIN_CELL = 24;
    private static final int MAX_CELL = 48;
    /** Below this logical width the search field gets its own full-width row. */
    private static final int INLINE_SEARCH_WIDTH = 600;

    private static final int MARGIN = 8;
    private static final int HEADER_H = 22;
    private static final int SEARCH_H = 18;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_H_SMALL = 18;
    private static final int BOTTOM_PAD = 6;
    private static final int ROW_GAP = 4;
    private static final int ACTION_GAP = 6;

    private final MapArtScreen parent;
    private final List<BlockInfo> visible = new ArrayList<>();
    private final boolean[] enabled;
    private final List<ItemStack> icons = new ArrayList<>();
    private String search = "";
    private boolean searchFocus;
    private int page;

    // Layout, recomputed from the logical GUI size on every frame.
    private int margin = MARGIN;
    private int headerH = HEADER_H;
    private int headerAllX;
    private int headerAllNoX;
    private int headerAllW;
    private int headerBtnY;
    private int headerBtnH;
    private boolean inlineSearch;
    private int searchX;
    private int searchY;
    private int searchW;
    private int searchH;
    private int gridX;
    private int gridY;
    private int cell = MIN_CELL;
    private int cols = MAX_COLS;
    private int rows = MAX_ROWS;
    private int pageSize = MAX_COLS * MAX_ROWS;
    private int pagerY;
    private int actionY;
    private int rowH;
    private int pageCount = 1;

    public BlockSelectScreen(MapArtScreen parent) {
        super(Component.translatable("schemapart.blocks.title"));
        this.parent = parent;
        List<BlockInfo> candidates = new ArrayList<>();
        for (BlockInfo block : SchemapartClient.blocks) {
            if (block.mapColor == 0) continue;
            if (SchemapartClient.settings.mineableMode == ArtSettings.MINE_ONLY && !block.mineable) continue;
            if (SchemapartClient.settings.mineableMode == ArtSettings.MINE_NOT && block.mineable) continue;
            if (SchemapartClient.settings.sourceMode == ArtSettings.SOURCE_VANILLA && !block.vanilla) continue;
            if (SchemapartClient.settings.sourceMode == ArtSettings.SOURCE_MODDED && block.vanilla) continue;
            candidates.add(block);
        }
        candidates.sort(Comparator
                .comparing((BlockInfo b) -> b.namespace().equals("minecraft") ? 0 : 1)
                .thenComparing(BlockInfo::namespace)
                .thenComparing(BlockInfo::path));
        visible.addAll(candidates);
        enabled = new boolean[visible.size()];
        for (int i = 0; i < visible.size(); i++) {
            enabled[i] = !SchemapartClient.settings.disabled.contains(visible.get(i).id);
            icons.add(icon(visible.get(i)));
        }
    }

    /** Recomputes every layout rectangle from the current logical GUI size. */
    private void layout() {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        margin = Math.min(MARGIN, Math.max(1, w / 8));

        // Header: title left, compact all/none buttons pinned to the top-right corner.
        headerH = HEADER_H;
        headerBtnH = 16;
        headerBtnY = Math.max(2, (headerH - headerBtnH) / 2);
        headerAllW = Math.max(40, font.width("全不选") + 12);
        headerAllNoX = w - margin - headerAllW;
        headerAllX = headerAllNoX - 4 - headerAllW;

        // Bottom: at most two rows anchored to the bottom edge.
        rowH = h >= 240 ? BUTTON_H : BUTTON_H_SMALL;
        actionY = h - BOTTOM_PAD - rowH;
        pagerY = actionY - ROW_GAP - rowH;

        // Search: inline in the header when wide enough, otherwise its own full-width row.
        inlineSearch = w >= INLINE_SEARCH_WIDTH;
        searchH = SEARCH_H;
        if (inlineSearch) {
            int titleEnd = margin + font.width("BLOCK PALETTE") + 12;
            int right = headerAllX - 10;
            int span = right - titleEnd;
            searchW = Math.min(420, span);
            if (searchW < 140) {
                inlineSearch = false;
            } else {
                searchX = titleEnd + (span - searchW) / 2;
                searchY = (headerH - searchH) / 2;
            }
        }
        if (!inlineSearch) {
            searchX = margin;
            searchW = Math.max(1, w - 2 * margin);
            searchY = headerH + 2;
        }

        // Grid: square cells, at most 9x6, centered in the free area.
        int gridTop = inlineSearch ? headerH + 8 : searchY + searchH + 5;
        int gridBottom = pagerY - 6;
        int availW = Math.max(1, w - 2 * margin);
        int availH = Math.max(1, gridBottom - gridTop);

        cell = Math.min(MAX_CELL, Math.min(availW / MAX_COLS, availH / MAX_ROWS));
        if (cell < MIN_CELL) {
            // Very short screens: shrink below the preferred size instead of overflowing.
            cell = Math.max(1, Math.min(MIN_CELL, Math.min(availW, availH)));
        }
        cols = Math.max(1, Math.min(MAX_COLS, availW / cell));
        rows = Math.max(1, Math.min(MAX_ROWS, availH / cell));
        pageSize = Math.max(1, cols * rows);
        gridX = margin + Math.max(0, (availW - cols * cell) / 2);
        gridY = gridTop + Math.max(0, (availH - rows * cell) / 2);
    }

    private static ItemStack icon(BlockInfo block) {
        try {
            Identifier id = Identifier.tryParse(block.itemId);
            if (id != null) {
                Item item = BuiltInRegistries.ITEM.getValue(id);
                if (item != null) return new ItemStack(item);
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    private List<Integer> filteredIndices() {
        List<Integer> out = new ArrayList<>();
        String q = search.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < visible.size(); i++) {
            BlockInfo b = visible.get(i);
            if (q.isEmpty() || b.id.toLowerCase(Locale.ROOT).contains(q)
                    || b.displayName.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(i);
            }
        }
        return out;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        frameStart();
        g.fill(0, 0, width, height, 0xD014161A);

        layout();
        List<Integer> filtered = filteredIndices();
        pageCount = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        page = Math.min(page, pageCount - 1);

        // Header bar.
        g.fill(0, 0, width, headerH, 0xF01B1E23);
        g.fill(0, headerH - 1, width, headerH, BORDER);
        int titleY = (headerH - 8) / 2;
        textShadow(g, "BLOCK PALETTE", margin, titleY, ACCENT);
        if (!inlineSearch) {
            int hintX = margin + font.width("BLOCK PALETTE") + 12;
            int hintW = headerAllX - 8 - hintX;
            if (hintW > 40) {
                text(g, trim("方块启用清单  ·  ESC 返回并自动保存默认清单", hintW), hintX, titleY, MUTED);
            }
        }
        button(g, headerAllX, headerBtnY, headerAllW, headerBtnH, "全选", true, () -> {
            for (int idx : filtered) enabled[idx] = true;
        });
        button(g, headerAllNoX, headerBtnY, headerAllW, headerBtnH, "全不选", true, () -> {
            for (int idx : filtered) enabled[idx] = false;
        });

        // Search box.
        g.fill(searchX, searchY, searchX + searchW, searchY + searchH, 0xFF15181D);
        outline(g, searchX, searchY, searchW, searchH, searchFocus ? ACCENT : BORDER);
        String hint = search.isEmpty() ? "搜索方块 id / 名称…" : search;
        text(g, trim(hint, searchW - 12), searchX + 6, searchY + (searchH - 8) / 2,
                search.isEmpty() ? MUTED : TEXT);
        if (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = searchX + 6 + font.width(trim(search, searchW - 12));
            g.fill(cx, searchY + 3, cx + 1, searchY + searchH - 3, ACCENT);
        }
        clickable(searchX, searchY, searchW, searchH, () -> searchFocus = true);

        // Dynamic grid: PAGE_SIZE = cols * rows, cells stay square and centered.
        int start = page * pageSize;
        int end = Math.min(filtered.size(), start + pageSize);
        for (int slot = 0; slot < pageSize; slot++) {
            int index = start + slot;
            int cx = gridX + (slot % cols) * cell;
            int cy = gridY + (slot / cols) * cell;
            int inner = Math.max(2, cell - 3);
            if (index >= end) {
                g.fill(cx + 1, cy + 1, cx + inner, cy + inner, 0xFF202329);
                outline(g, cx + 1, cy + 1, inner, inner, 0xFF2A2E35);
                continue;
            }
            int blockIndex = filtered.get(index);
            boolean on = enabled[blockIndex];
            boolean hovered = hover(cx + 1, cy + 1, inner, inner);
            int bg = on ? (hovered ? 0xFF3C806A : 0xFF2C5A4A) : (hovered ? 0xFF373B43 : 0xFF292C33);
            g.fill(cx + 1, cy + 1, cx + inner, cy + inner, bg);
            outline(g, cx + 1, cy + 1, inner, inner, on ? ACCENT : hovered ? BORDER_HI : 0xFF343941);
            ItemStack icon = icons.get(blockIndex);
            if (!icon.isEmpty() && cell >= 18) {
                g.item(icon, cx + (cell - 16) / 2, cy + (cell - 16) / 2);
            } else {
                int color = visible.get(blockIndex).mapColor;
                int inset = Math.max(2, cell / 4);
                g.fill(cx + inset, cy + inset, cx + cell - inset, cy + cell - inset, color);
            }
            if (on && inner >= 8) {
                g.fill(cx + inner - 5, cy + 2, cx + inner, cy + 7, ACCENT);
            }
            int bi = blockIndex;
            clickable(cx + 1, cy + 1, inner, inner, () -> enabled[bi] = !enabled[bi]);
        }

        // Pagination row: 上一页 / page / 下一页, centered.
        String pageLabel = (page + 1) + " / " + pageCount;
        int pagerW = Math.max(40, Math.min(56, (width - 2 * margin - font.width(pageLabel) - 2 * ROW_GAP) / 2));
        int labelW = Math.max(36, font.width(pageLabel));
        int pagerTotal = pagerW * 2 + ROW_GAP * 2 + labelW;
        int pagerStart = Math.max(margin, (width - pagerTotal) / 2);
        int labelX = pagerStart + pagerW + ROW_GAP;
        int nextX = labelX + labelW + ROW_GAP;
        int rowTextY = pagerY + (rowH - 8) / 2;
        int hintRoom = pagerStart - margin - 10;
        if (inlineSearch && hintRoom > 80) {
            text(g, trim("ESC 返回并自动保存默认清单", hintRoom), margin, rowTextY, MUTED);
        }
        button(g, pagerStart, pagerY, pagerW, rowH, "上一页", page > 0, () -> page--);
        textCentered(g, pageLabel, labelX + labelW / 2, rowTextY, TEXT);
        button(g, nextX, pagerY, pagerW, rowH, "下一页", page < pageCount - 1, () -> page++);

        // Action row: status on the left, 确认 / 保存默认 pinned to the right.
        int enabledCount = 0;
        for (int i = 0; i < visible.size(); i++) if (enabled[i]) enabledCount++;
        String status = "已启用 " + enabledCount + " / " + visible.size()
                + "  ·  当前筛选 " + filtered.size();
        int okW = Math.max(48, font.width("确认") + 20);
        int saveW = Math.max(64, font.width("保存默认") + 16);
        int saveX = width - margin - saveW;
        int okX = saveX - ACTION_GAP - okW;
        int actionTextY = actionY + (rowH - 8) / 2;
        int statusRoom = okX - margin - 8;
        if (statusRoom > 0) {
            text(g, trim(status, statusRoom), margin, actionTextY, MUTED);
        }
        button(g, okX, actionY, okW, rowH, "确认", true, () -> {
            apply();
            back();
        });
        button(g, saveX, actionY, saveW, rowH, "保存默认", true, () -> {
            apply();
            SchemapartClient.settings.applyAsDefault();
            SchemapartClient.saveSettings();
            back();
        });

        // Hover tooltip, clamped to the logical screen.
        int hoveredSlot = hoveredSlot(start, end);
        if (hoveredSlot >= 0) {
            BlockInfo b = visible.get(filtered.get(hoveredSlot));
            drawTooltip(g, b, mouseX, mouseY);
        }
    }

    private int hoveredSlot(int start, int end) {
        for (int slot = 0; slot < pageSize; slot++) {
            int index = start + slot;
            if (index >= end) break;
            int inner = Math.max(2, cell - 3);
            int cx = gridX + (slot % cols) * cell + 1;
            int cy = gridY + (slot / cols) * cell + 1;
            if (hover(cx, cy, inner, inner)) return index;
        }
        return -1;
    }

    private void drawTooltip(GuiGraphicsExtractor g, BlockInfo b, int mx, int my) {
        String line1 = b.displayName + "  (" + b.id + ")";
        String line2 = "颜色 " + b.hex() + "  ·  " + (b.mineable ? "可挖掘" : "不可挖掘")
                + "  ·  " + (b.requiresTool ? "需要正确工具" : "无需工具")
                + (b.requiresSilkTouch ? "  ·  精准采集" : "");
        String line3 = b.vanilla ? "原版方块" : "模组方块";
        int maxW = Math.max(60, width - 12);
        int w = Math.min(maxW, Math.max(font.width(line1),
                Math.max(font.width(line2), font.width(line3))) + 12);
        int h = 42;
        int x = Math.max(6, Math.min(mx + 10, width - w - 6));
        int y = Math.max(6, Math.min(my - h - 8, Math.max(6, height - h - 6)));
        g.fill(x, y, x + w, y + h, 0xF0181B20);
        outline(g, x, y, w, h, BORDER_HI);
        text(g, trim(line1, w - 12), x + 6, y + 5, TEXT);
        text(g, trim(line2, w - 12), x + 6, y + 17, MUTED);
        text(g, trim(line3, w - 12), x + 6, y + 29, b.vanilla ? ACCENT : WARN);
    }

    private void apply() {
        // Keep disabled entries for blocks hidden by the current mode/source filter.
        for (int i = 0; i < visible.size(); i++) {
            SchemapartClient.settings.setEnabled(visible.get(i).id, enabled[i]);
        }
    }

    private void back() {
        minecraft.gui.setScreen(parent);
        parent.refreshFromSelection();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        if (button == 0 && !inside(mx, my, gridX, gridY, cell * cols, cell * rows)
                && !inside(mx, my, searchX, searchY, searchW, searchH)) {
            searchFocus = false;
        }
        return super.mouseClicked(event, doubleClick) || runClick(mx, my, button);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (searchFocus) {
            if (keyCode == 259) {
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                searchFocus = false;
                return true;
            }
        }
        if (keyCode == 256) {
            if (SchemapartClient.settings.autoSaveSelectionOnEsc) {
                apply();
                SchemapartClient.settings.applyAsDefault();
                SchemapartClient.saveSettings();
            } else {
                apply();
            }
            back();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char codePoint = (char) event.codepoint();
        if (searchFocus) {
            if (codePoint >= 32 && codePoint != 127 && search.length() < 60) {
                search += codePoint;
                page = 0;
            }
            return true;
        }
        return super.charTyped(event);
    }
}
