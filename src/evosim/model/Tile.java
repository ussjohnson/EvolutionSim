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

  /** Regrow foliage (scaled by the tile's biome) and let degraded fertility creep back toward its baseline. */
  public void recover(Random rng) {
    foliage += fertility * biome.regenMultiplier() * rng.nextDouble();
    if (foliage > MAX_FOLIAGE) foliage = MAX_FOLIAGE;
    if (fertility < baseFertility) fertility += 0.01 * rng.nextDouble();
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
