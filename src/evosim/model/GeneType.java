package evosim.model;

import java.util.Random;

/**
 * The catalog of hereditary genes. Replaces the old magic int indices
 * (size=0, diet=1, ...) with a typed enum. Each constant carries its own
 * value bounds, default mutation variation, and how much it contributes to an
 * animal's resting metabolism (its energetic "upkeep" cost).
 *
 * The declaration order here defines the canonical genome vector order used by
 * {@link Genome#normVector()}, genetic-distance comparisons, speciation, and
 * species name generation. Adding a gene is as simple as adding a constant.
 */
public enum GeneType {
  //          min,  max,  variation, metabolicWeight
  SIZE       ( 1.0, 10.0, 0.8, 0.00), // cost comes from Kleiber-law mass scaling, not a flat weight
  LAND_SPEED ( 0.0,  1.0, 0.1, 0.15),
  DIET       ( 0.0,  1.0, 0.1, 0.00), // 0 = pure herbivore, 1 = pure carnivore
  TOLERANCE  ( 0.0,  1.0, 0.1, 0.00), // how carnivorous another must be to read as a threat
  SKITTISH   ( 0.0,  1.0, 0.1, 0.00),
  STRENGTH   ( 0.0,  1.0, 0.1, 0.15),
  DURABILITY ( 0.0,  1.0, 0.1, 0.15),
  INCLUSIVITY( 0.0,  1.0, 0.1, 0.00), // genetic tolerance for "same species" (kin)
  STEALTH    ( 0.0,  1.0, 0.1, 0.10),
  WANDERLUST ( 0.0,  1.0, 0.1, 0.00),
  SENSITIVITY( 0.0,  1.0, 0.1, 0.10),
  AWARENESS  ( 1.0,  6.0, 0.5, 0.05), // detection radius in tiles
  DEFENSIVE  ( 0.0,  1.0, 0.1, 0.00), // fight (1) vs flight (0) disposition
  BRAVERY    ( 0.0,  1.0, 0.1, 0.00), // how much the mass ratio sways a fight/flee choice
  FECUNDITY  ( 0.0,  1.0, 0.1, 0.00), // higher = reproduces at a lower energy reserve
  // Adapted climate: an animal's effective stealth (Animal#effectiveStealth)
  // peaks when a tile's actual temperature/moisture matches these, and
  // fades with mismatch — camouflage is biome-specific, not universal.
  CAMO_TEMP    ( 0.0,  1.0, 0.1, 0.00),
  CAMO_MOISTURE( 0.0,  1.0, 0.1, 0.00),
  // Thermoregulation (fur/blubber): relieves cold stress but adds heat stress,
  // plus a flat upkeep cost — great in tundra/winter, a liability in desert/
  // summer, so cold and hot regions favor genuinely different body plans.
  INSULATION   ( 0.0,  1.0, 0.1, 0.06),
  // Disease resistance: lowers infection chance, virulence, and speeds
  // recovery — but a strong immune system is metabolically expensive.
  IMMUNITY     ( 0.0,  1.0, 0.1, 0.06),
  // Herding: high sociability dilutes predation risk among same-species
  // neighbours (safety in numbers) but crowds accelerate disease and deplete
  // shared forage faster — a real grouping tradeoff, no direct upkeep cost.
  SOCIABILITY  ( 0.0,  1.0, 0.1, 0.00),
  // Life-history strategy (r vs K): high care means fewer, better-provisioned
  // offspring born further apart; low care means many cheap offspring in quick
  // succession. Neither dominates — they win in different conditions.
  PARENTAL_CARE( 0.0,  1.0, 0.1, 0.00),
  // Sexual display (a peacock's tail): attracts mates under sexual
  // reproduction but is metabolically costly and conspicuous (cuts stealth),
  // the raw material for Fisherian runaway selection.
  ORNAMENT     ( 0.0,  1.0, 0.1, 0.05);

  public static final GeneType[] ALL = values();
  public static final int COUNT = ALL.length;

  private final double min;
  private final double max;
  private final double variation;
  private final double metabolicWeight;

  GeneType(double min, double max, double variation, double metabolicWeight) {
    this.min = min;
    this.max = max;
    this.variation = variation;
    this.metabolicWeight = metabolicWeight;
  }

  public double min() { return min; }
  public double max() { return max; }
  public double variation() { return variation; }
  public double metabolicWeight() { return metabolicWeight; }

  /** A gene of this type holding the given raw value. */
  public Gene of(double value) {
    return new Gene(name(), max, min, variation, value);
  }

  /** A gene of this type with a value drawn uniformly from [min, max]. */
  public Gene random(Random rng) {
    return of(min + rng.nextDouble() * (max - min));
  }

  /** A gene of this type at the midpoint of its range. */
  public Gene midpoint() {
    return of((min + max) / 2.0);
  }
}
