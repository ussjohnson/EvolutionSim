package evosim.model;

/**
 * Generates a stable, pronounceable species name from a genome's normalized
 * gene vector. Crucially, the name is a pure function of the *same* vector the
 * speciation system uses for genetic distance — so genetically similar species
 * get similar-looking names, and identical genomes always get the same name.
 *
 * Built from 2-4 syllables (count and shape both genome-derived, not fixed),
 * each a consonant (occasionally a digraph, for phonetic texture) + vowel,
 * sometimes closed with a trailing consonant — so names vary in length and
 * rhythm instead of always being the same eight-character CVCVCVCV shape.
 */
public final class NameGenerator {
  private NameGenerator() {}

  private static final String CONS = "bcdfghjklmnprstvwz";
  private static final String[] DIGRAPHS = { "th", "sh", "ch", "ng", "kr", "dr", "bl", "st", "gr", "fl" };
  private static final String VOW = "aeiou";
  private static final int MIN_SYLLABLES = 2;
  private static final int MAX_SYLLABLES = 4;

  public static String nameFor(double[] norm) {
    int len = norm.length;
    int syllables = MIN_SYLLABLES
        + (int) (clamp01(norm[(len - 1) % len]) * (MAX_SYLLABLES - MIN_SYLLABLES + 1));
    if (syllables > MAX_SYLLABLES) syllables = MAX_SYLLABLES;

    StringBuilder sb = new StringBuilder();
    int idx = 0;
    for (int syl = 0; syl < syllables; syl++) {
      double onsetSeed = clamp01(norm[idx++ % len]);
      double vowelSeed = clamp01(norm[idx++ % len]);
      double codaSeed = clamp01(norm[idx++ % len]);

      if (onsetSeed > 0.82) {
        int d = (int) ((onsetSeed - 0.82) / 0.18 * DIGRAPHS.length);
        sb.append(DIGRAPHS[clampIndex(d, DIGRAPHS.length)]);
      } else {
        sb.append(CONS.charAt(clampIndex((int) (onsetSeed / 0.82 * CONS.length()), CONS.length())));
      }
      sb.append(VOW.charAt(clampIndex((int) (vowelSeed * VOW.length()), VOW.length())));
      if (codaSeed > 0.62 && syl < syllables - 1) {
        sb.append(CONS.charAt(clampIndex((int) (codaSeed * CONS.length()), CONS.length())));
      }
    }
    sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
    return sb.toString();
  }

  private static double clamp01(double v) {
    if (v < 0.0) return 0.0;
    if (v >= 1.0) return 0.999999;
    return v;
  }

  private static int clampIndex(int i, int length) {
    if (i < 0) return 0;
    if (i >= length) return length - 1;
    return i;
  }
}
