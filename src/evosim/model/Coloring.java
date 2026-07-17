package evosim.model;

/**
 * Display-colour derivation for {@link Species}. A species' colour is its
 * identity (every member renders in its species' colour, not its own
 * genome — see {@code World#snapshot()}), assigned once at founding via
 * {@link SpeciesRegistry#classify} using lineage inheritance: a child
 * species' hue is a small random nudge from its parent's hue, so related
 * species look like variations on a theme, while founder species (no
 * parent) spread their hues via the golden angle for maximal separation.
 * Saturation and value are fixed and high so every species stays vivid
 * against the muted, "natural" tones of every {@link BiomeType} tile tint.
 */
final class Coloring {
  private Coloring() {}

  private static final double SATURATION = 0.75;
  private static final double VALUE = 0.92;

  /** A packed 0xRRGGBB colour at this hue (degrees, any range) and the fixed saturation/value. */
  static int fromHue(double hueDegrees) {
    double h = ((hueDegrees % 360.0) + 360.0) % 360.0;
    double c = VALUE * SATURATION;
    double x = c * (1 - Math.abs((h / 60.0) % 2 - 1));
    double m = VALUE - c;
    double r1, g1, b1;
    if (h < 60)       { r1 = c; g1 = x; b1 = 0; }
    else if (h < 120) { r1 = x; g1 = c; b1 = 0; }
    else if (h < 180) { r1 = 0; g1 = c; b1 = x; }
    else if (h < 240) { r1 = 0; g1 = x; b1 = c; }
    else if (h < 300) { r1 = x; g1 = 0; b1 = c; }
    else              { r1 = c; g1 = 0; b1 = x; }
    int r = clamp255(r1 + m);
    int g = clamp255(g1 + m);
    int b = clamp255(b1 + m);
    return (r << 16) | (g << 8) | b;
  }

  /** The hue (degrees, [0,360)) of a packed 0xRRGGBB colour. */
  static double hueOf(int rgb) {
    double rf = ((rgb >> 16) & 0xFF) / 255.0;
    double gf = ((rgb >> 8) & 0xFF) / 255.0;
    double bf = (rgb & 0xFF) / 255.0;
    double max = Math.max(rf, Math.max(gf, bf));
    double min = Math.min(rf, Math.min(gf, bf));
    double d = max - min;
    if (d < 1e-9) return 0.0;
    double h;
    if (max == rf) h = 60 * (((gf - bf) / d) % 6);
    else if (max == gf) h = 60 * ((bf - rf) / d + 2);
    else h = 60 * ((rf - gf) / d + 4);
    if (h < 0) h += 360;
    return h;
  }

  private static int clamp255(double norm) {
    int v = (int) Math.round(norm * 255.0);
    if (v < 0) v = 0;
    if (v > 255) v = 255;
    return v;
  }
}
