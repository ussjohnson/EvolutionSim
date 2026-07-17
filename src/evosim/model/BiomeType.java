package evosim.model;

/**
 * Climate/terrain classification for a tile, assigned once at world
 * generation from two blended value-noise fields (temperature, moisture) —
 * see {@code World}'s constructor. Each constant owns an "ideal" point in
 * that same (temperature, moisture) space, plus a fertility multiplier,
 * foliage-regrowth rate, and a "climate stress" fraction (extra resting
 * upkeep an animal pays just for living somewhere hostile, independent of
 * its genes — see {@link Animal#metabolism(double)}). This is the spatial
 * niche differentiation {@link Balance}'s class doc notes the ecosystem
 * lacked when it only had one undifferentiated food resource. The same
 * ideal point also anchors {@link Animal#effectiveStealth(double, double)}'s
 * camouflage mismatch, so specializing for one biome's cover doesn't
 * transfer fully to another.
 *
 * {@link #classify} assigns a tile to whichever biome's ideal point is
 * nearest (a Voronoi partition of climate space) rather than a hand-coded
 * threshold tree, so adding a biome later is just adding a constant here —
 * no classification logic to touch, and no risk of breaking existing
 * boundaries.
 */
public enum BiomeType {
  //           tint      idealTemp idealMoisture fertilityMultiplier regenMultiplier climateStress
  TUNDRA    (0x9FB4B8, 0.10, 0.40, 0.35, 0.60, 0.35),
  DESERT    (0xD8C27A, 0.80, 0.10, 0.30, 0.50, 0.30),
  GRASSLAND (0x8FBF5A, 0.55, 0.45, 1.00, 1.00, 0.00),
  FOREST    (0x3F7D3F, 0.55, 0.80, 1.35, 1.15, 0.05),
  RAINFOREST(0x1F6B45, 0.85, 0.85, 1.60, 1.40, 0.12);

  public static final BiomeType[] ALL = values();

  private final int tint;
  private final double idealTemperature;
  private final double idealMoisture;
  private final double fertilityMultiplier;
  private final double regenMultiplier;
  private final double climateStress;

  BiomeType(int tint, double idealTemperature, double idealMoisture,
            double fertilityMultiplier, double regenMultiplier, double climateStress) {
    this.tint = tint;
    this.idealTemperature = idealTemperature;
    this.idealMoisture = idealMoisture;
    this.fertilityMultiplier = fertilityMultiplier;
    this.regenMultiplier = regenMultiplier;
    this.climateStress = climateStress;
  }

  /** Packed 0xRRGGBB used to tint this biome's tiles on the map. */
  public int tint() { return tint; }

  /** This biome's anchor point in normalized [0,1] temperature space. */
  public double idealTemperature() { return idealTemperature; }

  /** This biome's anchor point in normalized [0,1] moisture space. */
  public double idealMoisture() { return idealMoisture; }

  /** Scales the world's global baseFertility setting for tiles of this biome. */
  public double fertilityMultiplier() { return fertilityMultiplier; }

  /** Scales how fast foliage regrows on tiles of this biome. */
  public double regenMultiplier() { return regenMultiplier; }

  /** Fractional extra resting metabolism paid by any animal standing here. */
  public double climateStress() { return climateStress; }

  /** Friendly display name, e.g. "Rainforest". */
  public String label() {
    String n = name().toLowerCase();
    return Character.toUpperCase(n.charAt(0)) + n.substring(1);
  }

  /**
   * Classify a tile from its normalized temperature and moisture in [0,1]:
   * whichever biome's ideal point is closest (a Voronoi partition of
   * climate space).
   */
  public static BiomeType classify(double temperature, double moisture) {
    BiomeType best = null;
    double bestDist = Double.MAX_VALUE;
    for (BiomeType b : ALL) {
      double dt = temperature - b.idealTemperature;
      double dm = moisture - b.idealMoisture;
      double dist = dt * dt + dm * dm;
      if (dist < bestDist) {
        bestDist = dist;
        best = b;
      }
    }
    return best;
  }
}
