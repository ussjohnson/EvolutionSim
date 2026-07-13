package evosim.model;

/**
 * Shared display-colour derivation for both individual {@link Animal}s and
 * founded {@link Species} (they must agree, since a species' swatch is its
 * founder's colour). Packed 0xRRGGBB: red = carnivory (DIET), green =
 * FECUNDITY, blue = LAND_SPEED. SIZE deliberately does not affect colour.
 *
 * Green is capped at 50% of its range so no animal's dot reads as the same
 * bright green as ungrazed terrain in {@code MapPanel}.
 */
final class Coloring {
  private Coloring() {}

  private static final double GREEN_CAP = 0.5;

  static int fromNorm(double[] norm) {
    return pack(norm[GeneType.DIET.ordinal()], norm[GeneType.FECUNDITY.ordinal()],
        norm[GeneType.LAND_SPEED.ordinal()]);
  }

  static int pack(double dietNorm, double fecundityNorm, double speedNorm) {
    int r = clamp255(dietNorm);
    int g = clamp255(fecundityNorm * GREEN_CAP);
    int b = clamp255(speedNorm);
    return (r << 16) | (g << 8) | b;
  }

  private static int clamp255(double norm) {
    int v = (int) Math.round(norm * 255.0);
    if (v < 0) v = 0;
    if (v > 255) v = 255;
    return v;
  }
}
