package com.schemapart.core;

/** Small, dependency-free colour helpers used by the quantizer. */
public final class ColorMath {
    private ColorMath() {
    }

    public static int red(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    public static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int lerp(int a, int b, double t) {
        int ar = red(a), ag = green(a), ab = blue(a);
        int br = red(b), bg = green(b), bb = blue(b);
        return rgb(
                (int) Math.round(ar + (br - ar) * t),
                (int) Math.round(ag + (bg - ag) * t),
                (int) Math.round(ab + (bb - ab) * t)
        );
    }

    public static int multiply(int argb, double factor) {
        return rgb(
                clamp((int) Math.round(red(argb) * factor)),
                clamp((int) Math.round(green(argb) * factor)),
                clamp((int) Math.round(blue(argb) * factor))
        );
    }

    public static int mix(int a, int b, double amount) {
        return lerp(a, b, Math.max(0, Math.min(1, amount)));
    }

    public static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static double[] toLab(int argb) {
        double r = srgbToLinear(red(argb) / 255.0);
        double g = srgbToLinear(green(argb) / 255.0);
        double b = srgbToLinear(blue(argb) / 255.0);
        double x = r * 0.4124564 + g * 0.3575761 + b * 0.1804375;
        double y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750;
        double z = r * 0.0193339 + g * 0.1191920 + b * 0.9503041;
        double xn = 0.95047, yn = 1.00000, zn = 1.08883;
        double fx = labF(x / xn);
        double fy = labF(y / yn);
        double fz = labF(z / zn);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double labF(double t) {
        return t > 0.008856451679 ? Math.cbrt(t) : (903.296296296 * t + 16) / 116;
    }

    private static double srgbToLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static double labDistanceSq(double[] a, double b0, double b1, double b2) {
        double d0 = a[0] - b0;
        double d1 = a[1] - b1;
        double d2 = a[2] - b2;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }
}
