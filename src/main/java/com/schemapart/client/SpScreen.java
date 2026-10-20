package com.schemapart.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Tiny immediate-mode screen base shared by all Schemapart screens. */
public abstract class SpScreen extends Screen {
    protected static final int BG = 0xF014161A;
    protected static final int PANEL = 0xFF20242A;
    protected static final int PANEL_ALT = 0xFF292E36;
    protected static final int BORDER = 0xFF3A414C;
    protected static final int BORDER_HI = 0xFF586170;
    protected static final int TEXT = 0xFFE7ECF3;
    protected static final int MUTED = 0xFF99A3B3;
    protected static final int ACCENT = 0xFF5BD6A6;
    protected static final int ACCENT_DARK = 0xFF2D7F64;
    protected static final int WARN = 0xFFF0B354;
    protected static final int DANGER = 0xFFE06868;

    protected final List<Click> clicks = new ArrayList<>();
    protected int mouseX;
    protected int mouseY;

    protected SpScreen(Component title) {
        super(title);
    }

    protected record Click(int x, int y, int w, int h, int button, Runnable action) {
        boolean hits(double mx, double my, int b) {
            return mx >= x && mx < x + w && my >= y && my < y + h && (button < 0 || button == b);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return minecraft != null && minecraft.hasSingleplayerServer();
    }

    protected void frameStart() {
        clicks.clear();
    }

    protected void clickable(int x, int y, int w, int h, Runnable action) {
        clicks.add(new Click(x, y, w, h, 0, action));
    }

    protected void clickable(int x, int y, int w, int h, int button, Runnable action) {
        clicks.add(new Click(x, y, w, h, button, action));
    }

    protected boolean runClick(double mx, double my, int button) {
        // Later widgets are drawn on top, so iterate backwards.
        for (int i = clicks.size() - 1; i >= 0; i--) {
            Click c = clicks.get(i);
            if (c.hits(mx, my, button)) {
                c.action.run();
                return true;
            }
        }
        return false;
    }

    protected boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    protected void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        outline(g, x, y, w, h, BORDER);
    }

    protected void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    protected void text(GuiGraphics g, String value, int x, int y, int color) {
        g.drawString(font, value, x, y, color, false);
    }

    protected void textShadow(GuiGraphics g, String value, int x, int y, int color) {
        g.drawString(font, value, x, y, color, true);
    }

    protected void textCentered(GuiGraphics g, String value, int cx, int y, int color) {
        g.drawString(font, value, cx - font.width(value) / 2, y, color, false);
    }

    protected void textRight(GuiGraphics g, String value, int right, int y, int color) {
        g.drawString(font, value, right - font.width(value), y, color, false);
    }

    protected String trim(String value, int maxWidth) {
        return font.plainSubstrByWidth(value, maxWidth);
    }

    protected boolean hover(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    protected boolean button(GuiGraphics g, int x, int y, int w, int h, String label, boolean enabled,
                             Runnable action) {
        boolean hovered = enabled && hover(x, y, w, h);
        int fill = !enabled ? 0xFF25282D : hovered ? 0xFF343B45 : 0xFF2B3038;
        g.fill(x, y, x + w, y + h, fill);
        outline(g, x, y, w, h, !enabled ? 0xFF30343A : hovered ? ACCENT : BORDER);
        if (hovered) g.fill(x, y + h - 2, x + w, y + h, ACCENT);
        int color = !enabled ? 0xFF666C76 : TEXT;
        textCentered(g, trim(label, w - 6), x + w / 2, y + (h - 8) / 2, color);
        if (enabled) clickable(x, y, w, h, () -> {
            playClick();
            action.run();
        });
        return hovered;
    }

    protected boolean toggleButton(GuiGraphics g, int x, int y, int w, int h, String label, boolean on,
                                   Runnable action) {
        boolean hovered = hover(x, y, w, h);
        g.fill(x, y, x + w, y + h, on ? (hovered ? 0xFF35765E : 0xFF2B5E4C)
                : (hovered ? 0xFF343B45 : 0xFF2B3038));
        outline(g, x, y, w, h, on ? ACCENT : BORDER);
        textCentered(g, trim(label, w - 6), x + w / 2, y + (h - 8) / 2, on ? 0xFFE9FFF7 : TEXT);
        clickable(x, y, w, h, () -> {
            playClick();
            action.run();
        });
        return hovered;
    }

    protected void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    protected void dim(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xB0000000);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return super.mouseClicked(mx, my, button) || runClick(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && shouldCloseOnEsc()) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    protected Font font() {
        return this.font;
    }
}
