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
  public boolean wraparound = false; // off = bounded edges (today's default behavior)
  // Number of distinct founding gene-clusters to seed. 1 (default) is today's
  // behavior: one narrow band, speciation emerges from in-run drift. >1
  // spreads that many cluster centers across each gene's full range (see
  // World#seedPopulation) so the run starts with that many already-distinct
  // species instead of waiting for drift to produce them.
  public int founderSpeciesCount = 1;
  // Seasons: a global yearly climate cycle that swings foliage growth and adds
  // winter cold-stress upkeep (see World#seasonalPhase). On by default for GUI
  // runs; seasonLength is the number of ticks in one full year.
  public boolean seasonsEnabled = true;
  public int seasonLength = Balance.SEASON_LENGTH;
  // Sexual reproduction (recombination + mate choice on ORNAMENT) and a
  // density/frequency-dependent disease system. On by default for GUI runs;
  // both add realism and help maintain long-term diversity.
  public boolean sexualReproduction = Balance.SEXUAL_DEFAULT;
  public boolean diseaseEnabled = true;

  // Per-gene initial value range, indexed by GeneType.ordinal().
  public final double[] startMin = new double[GeneType.COUNT];
  public final double[] startMax = new double[GeneType.COUNT];

  public SimSettings() {
    resetGeneRangesToDefault();
  }

  /**
   * Reset every gene's start range to the default founding band: a narrow
   * range centered on the gene's default center (see
   * {@link Balance#FOUNDER_GENE_BAND} and {@link #defaultCenter}) rather than
   * its full allowed range, so the starting population begins as a handful of
   * related founders and speciates from there over the run.
   */
  public void resetGeneRangesToDefault() {
    for (GeneType t : GeneType.ALL) {
      double center = defaultCenter(t);
      double half = Balance.FOUNDER_GENE_BAND * (t.max() - t.min()) / 2.0;
      startMin[t.ordinal()] = center - half;
      startMax[t.ordinal()] = center + half;
    }
  }

  /**
   * The raw value a gene's founding band is centered on. Most genes start at
   * their midpoint, but the costly specialization traits — thermal insulation,
   * a sexual-display ornament, and (to a lesser degree) immune investment —
   * start low: a founding population isn't pre-adapted to fur, plumage, or a
   * particular pathogen, so those traits should *evolve up* only where and when
   * selection (a cold biome, sexual competition, an epidemic) actually favours
   * them, rather than burdening every founder with their upkeep from tick 0.
   */
  private static double defaultCenter(GeneType t) {
    double mid = (t.min() + t.max()) / 2.0;
    return switch (t) {
      case INSULATION, ORNAMENT -> t.min() + 0.15 * (t.max() - t.min());
      case IMMUNITY -> t.min() + 0.30 * (t.max() - t.min());
      default -> mid;
    };
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
    if (founderSpeciesCount < 1) founderSpeciesCount = 1;
    if (founderSpeciesCount > population) founderSpeciesCount = population;
    if (seasonLength < 1) seasonLength = 1;
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
