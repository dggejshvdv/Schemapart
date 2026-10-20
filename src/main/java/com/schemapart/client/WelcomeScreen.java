package com.schemapart.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Root chooser shown before the two map-art workflows. */
public final class WelcomeScreen extends SpScreen {
    public WelcomeScreen() {
        super(Component.translatable("schemapart.welcome.title"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        frameStart();
        g.fill(0, 0, width, height, 0xD014161A);

        textCentered(g, "SCHEMAPART", width / 2, 22, ACCENT);
        textCentered(g, "选择制作模式", width / 2, 40, MUTED);

        int margin = Math.max(14, width / 22);
        int gap = Math.max(12, width / 40);
        int cardW = Math.max(150, (width - margin * 2 - gap) / 2);
        int cardH = Math.max(130, Math.min(height - 96, 300));
        int totalW = cardW * 2 + gap;
        int leftX = (width - totalW) / 2;
        int rightX = leftX + cardW + gap;
        int cardY = Math.max(58, (height - cardH) / 2 + 8);

        drawWelcomeCard(g, leftX, cardY, cardW, cardH, true, "你好", "文字地图画制作",
                "把任意文字排成 128×128 地图画");
        drawWelcomeCard(g, rightX, cardY, cardW, cardH, false, null, "地图画制作",
                "把图片转换成地图画原理图");

        clickable(leftX, cardY, cardW, cardH, () -> {
            playClick();
            minecraft.setScreen(new MWMAUIScreen());
        });
        clickable(rightX, cardY, cardW, cardH, () -> {
            playClick();
            minecraft.setScreen(new MAUIScreen());
        });

        textCentered(g, "M + P  打开此界面", width / 2, height - 18, MUTED);
        button(g, width - 8 - 54, 8, 54, 22, "设置", true,
                () -> minecraft.setScreen(new SettingsScreen(this)));
    }

    private void drawWelcomeCard(GuiGraphics g, int x, int y, int w, int h, boolean word,
                                 String pixelText, String title, String subtitle) {
        boolean hovered = hover(x, y, w, h);
        g.fill(x, y, x + w, y + h, hovered ? 0xFF2C343D : PANEL);
        outline(g, x, y, w, h, hovered ? ACCENT : BORDER);
        if (hovered) {
            g.fill(x, y + h - 3, x + w, y + h, ACCENT);
        }

        int iconTop = y + 22;
        int iconBottom = y + h - 58;
        int centerX = x + w / 2;
        int centerY = (iconTop + iconBottom) / 2;

        if (word) {
            int scale = Math.max(2, Math.min(6, (w - 40) / Math.max(18, font.width(pixelText))));
            int textW = font.width(pixelText) * scale;
            int textH = 9 * scale;
            g.pose().pushMatrix();
            g.pose().translate(centerX - textW / 2, centerY - textH / 2);
            g.pose().scale(scale, scale);
            g.drawString(font, pixelText, 0, 0, hovered ? 0xFFFFFFFF : 0xFFE9FFF7, false);
            g.pose().popMatrix();
        } else {
            int scale = Math.max(3, Math.min(6, (w - 56) / 16));
            g.pose().pushMatrix();
            g.pose().translate(centerX - 8 * scale, centerY - 8 * scale);
            g.pose().scale(scale, scale);
            g.renderItem(new ItemStack(Blocks.GRASS_BLOCK), 0, 0);
            g.pose().popMatrix();
        }

        textCentered(g, title, centerX, y + h - 42, hovered ? ACCENT : TEXT);
        textCentered(g, trim(subtitle, w - 20), centerX, y + h - 24, MUTED);
    }
}
