package com.schemapart.core;

import java.awt.image.BufferedImage;

/** Result of scaling an input picture to one block per output pixel. */
public final class PreparedImage {
    public final int width;
    public final int height;
    public final int[] argb;

    public PreparedImage(int width, int height, int[] argb) {
        this.width = width;
        this.height = height;
        this.argb = argb;
    }

    public int pixel(int x, int y) {
        return argb[y * width + x];
    }

    public BufferedImage toBufferedImage() {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        return image;
    }
}
