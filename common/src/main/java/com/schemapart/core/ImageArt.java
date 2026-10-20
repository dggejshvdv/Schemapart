package com.schemapart.core;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/** Loads and scales pictures without depending on Minecraft classes. */
public final class ImageArt {
    public static final int MAX_DIMENSION = 2048;

    private ImageArt() {
    }

    public static BufferedImage read(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Unsupported or corrupt image: " + path.getFileName());
        }
        return image;
    }

    public static PreparedImage prepare(BufferedImage source, ArtSettings settings) {
        double pixelsPerBlock = settings.pixelsPerBlock();
        int outW = Math.max(1, (int) Math.round(source.getWidth() / pixelsPerBlock));
        int outH = Math.max(1, (int) Math.round(source.getHeight() / pixelsPerBlock));
        if (outW > MAX_DIMENSION || outH > MAX_DIMENSION) {
            throw new IllegalArgumentException("Result is too large: " + outW + "x" + outH
                    + " blocks. Maximum is " + MAX_DIMENSION + " per side.");
        }
        BufferedImage scaled = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    pixelsPerBlock < 1.0 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                            : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(java.awt.Color.BLACK);
            g.fillRect(0, 0, outW, outH);
            g.drawImage(source, 0, 0, outW, outH, null);
        } finally {
            g.dispose();
        }
        int[] argb = new int[outW * outH];
        scaled.getRGB(0, 0, outW, outH, argb, 0, outW);
        for (int i = 0; i < argb.length; i++) {
            argb[i] |= 0xFF000000;
        }
        return new PreparedImage(outW, outH, argb);
    }
}
