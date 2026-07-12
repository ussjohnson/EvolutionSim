package evosim.model;

/**
 * Generates a stable, pronounceable species name from a genome's normalized
 * gene vector. Crucially, the name is a pure function of the *same* vector the
 * speciation system uses for genetic distance — so genetically similar species
 * get similar-looking names, and identical genomes always get the same name.
 *
 * The 15 gene values are folded into a fixed 8-character name (alternating
 * consonant/vowel for pronounceability), independent of how many genes exist.
 */
public final class NameGenerator {
  private NameGenerator() {}

  private static final String CONS = "bcdfghjklmnprstvwz";
  private static final String VOW = "aeiou";
  private static final int LENGTH = 8;

  public static String nameFor(double[] norm) {
    StringBuilder sb = new StringBuilder(LENGTH);
    for (int i = 0; i < LENGTH; i++) {
      // Blend two adjacent genes so every character reflects the genome and
      // small genetic changes produce small spelling changes.
      double a = norm[(i * 2) % norm.length];
      double b = norm[(i * 2 + 1) % norm.length];
      double mix = a * 0.6 + b * 0.4;
      if (mix < 0.0) mix = 0.0;
      if (mix >= 1.0) mix = 0.999999;
      String pool = (i % 2 == 0) ? CONS : VOW;
      sb.append(pool.charAt((int) (mix * pool.length())));
    }
    sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
    return sb.toString();
  }
}
