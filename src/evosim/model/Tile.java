package evosim.model;

import java.util.Random;

/**
 * One cell of the world grid. Holds a plant food supply ({@code foliage}) that
 * regrows according to {@code fertility}. Over-grazing permanently-ish reduces
 * fertility — this is the environment-overuse mechanic that lets an herbivore
 * boom degrade its own habitat. Fertility slowly recovers toward its baseline.
 */
public class Tile {
  private double fertility;
  private double baseFertility;
  private double foliage;
  private double carrion; // meat energy from carcasses left here; decays each tick (see recover)
  private final BiomeType biome;
  private final double temperature; // normalized [0,1], from world-gen noise; see BiomeType
  private final double moisture;    // normalized [0,1], from world-gen noise; see BiomeType
  private Animal resident;

  public static final double MAX_FOLIAGE = 1.0;

  public Tile(double foliage, double fertility, BiomeType biome, double temperature, double moisture) {
    this.foliage = foliage;
    this.fertility = fertility;
    this.baseFertility = fertility;
    this.biome = biome;
    this.temperature = temperature;
    this.moisture = moisture;
  }
  /** Falls back to the biome's own ideal climate point when no tile-specific noise sample is given. */
  public Tile(double foliage, double fertility, BiomeType biome) {
    this(foliage, fertility, biome, biome.idealTemperature(), biome.idealMoisture());
  }
  public Tile(double foliage, double fertility) {
    this(foliage, fertility, BiomeType.GRASSLAND);
  }
  public Tile(double foliage) {
    this(foliage, 0.2);
  }
  public Tile() {
    this(0.5);
  }

  /**
   * Regrow foliage (scaled by the tile's biome and a global seasonal growth
   * factor — see {@link evosim.engine.World#seasonalGrowthFactor()}), let
   * degraded fertility creep back toward its baseline, and decay any carrion:
   * a decaying carcass slowly returns its nutrients to the soil as fertility
   * ({@link Balance#CARRION_TO_FERTILITY}) before it disappears.
   */
  public void recover(Random rng, double seasonGrowthFactor) {
    foliage += fertility * biome.regenMultiplier() * seasonGrowthFactor * rng.nextDouble();
    if (foliage > MAX_FOLIAGE) foliage = MAX_FOLIAGE;
    if (foliage < 0) foliage = 0;
    if (fertility < baseFertility) fertility += 0.01 * rng.nextDouble();
    if (carrion > 0) {
      double after = carrion * Balance.CARRION_DECAY;
      fertilize((carrion - after) * Balance.CARRION_TO_FERTILITY);
      carrion = (after < 1e-4) ? 0 : after;
    }
  }

  /** Add carcass energy to this tile (deposited when an animal dies here, minus predation). */
  public void addCarrion(double amount) {
    if (amount > 0) carrion += amount;
  }

  /** Consume up to {@code want} carrion units from this tile; returns the amount actually taken. */
  public double scavenge(double want) {
    double taken = Math.min(want, carrion);
    carrion -= taken;
    if (carrion < 1e-4) carrion = 0;
    return taken;
  }

  public double getCarrion() {
    return carrion;
  }

  /** Eat up to {@code eaten} foliage; grazing bare ground degrades fertility. Returns amount actually eaten. */
  public double graze(double eaten) {
    if (foliage < eaten) {
      eaten = foliage;
      fertility -= 0.1;
      if (fertility < 0.0) fertility = 0.0;
    }
    foliage -= eaten;
    return eaten;
  }

  public void fertilize(double fertilizer) {
    fertility += fertilizer;
    if (fertility > 1.0) fertility = 1.0;
  }

  // Getters and setters
  public void setFertility(double fertility) {
    this.fertility = fertility;
  }
  public double getFertility() {
    return fertility;
  }
  public double getBaseFertility() {
    return baseFertility;
  }
  public void setFoliage(double foliage) {
    this.foliage = foliage;
  }
  public double getFoliage() {
    return foliage;
  }
  public void setResident(Animal resident) {
    this.resident = resident;
  }
  public Animal getResident() {
    return resident;
  }
  public BiomeType getBiome() {
    return biome;
  }
  public double getTemperature() {
    return temperature;
  }
  public double getMoisture() {
    return moisture;
  }
}
