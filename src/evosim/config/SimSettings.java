package evosim.config;

import evosim.model.Balance;
import evosim.model.GeneType;

/**
 * User-configurable starting conditions for a run. Holds the world dimensions,
 * starting population, plant productivity, run length, RNG seed, and — per gene
 * — the value range from which the initial population is drawn. Narrowing a
 * gene's start range lets the user seed a biased founding population (e.g. all
 * small herbivores) and watch it diversify from there.
 */
public final class SimSettings {
  public int width = 40;
  public int height = 30;
  public int population = 150;
  public double baseFertility = 0.2;
  public int durationTicks = 0; // 0 = run until the user stops it
  public long seed = 42;

  // Per-gene initial value range, indexed by GeneType.ordinal().
  public final double[] startMin = new double[GeneType.COUNT];
  public final double[] startMax = new double[GeneType.COUNT];

  public SimSettings() {
    resetGeneRangesToDefault();
  }

  /**
   * Reset every gene's start range to the default founding band: a narrow
   * range centered on the gene's midpoint (see {@link Balance#FOUNDER_GENE_BAND})
   * rather than its full allowed range, so the starting population begins as
   * a handful of related founders and speciates from there over the run.
   */
  public void resetGeneRangesToDefault() {
    for (GeneType t : GeneType.ALL) {
      double mid = (t.min() + t.max()) / 2.0;
      double half = Balance.FOUNDER_GENE_BAND * (t.max() - t.min()) / 2.0;
      startMin[t.ordinal()] = mid - half;
      startMax[t.ordinal()] = mid + half;
    }
  }

  /** Clamp all fields into valid ranges so a malformed form can't break a run. */
  public void sanitize() {
    if (width < 4) width = 4;
    if (height < 4) height = 4;
    if (population < 1) population = 1;
    if (population > width * height) population = width * height;
    if (baseFertility < 0) baseFertility = 0;
    if (baseFertility > 1) baseFertility = 1;
    if (durationTicks < 0) durationTicks = 0;
    for (GeneType t : GeneType.ALL) {
      int i = t.ordinal();
      startMin[i] = clamp(startMin[i], t.min(), t.max());
      startMax[i] = clamp(startMax[i], t.min(), t.max());
      if (startMin[i] > startMax[i]) {
        double tmp = startMin[i];
        startMin[i] = startMax[i];
        startMax[i] = tmp;
      }
    }
  }

  private static double clamp(double v, double lo, double hi) {
    if (v < lo) return lo;
    if (v > hi) return hi;
    return v;
  }
}
