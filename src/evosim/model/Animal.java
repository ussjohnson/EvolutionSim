package evosim.model;

import java.util.Random;

/**
 * A single organism. Its hereditary traits live in a {@link Genome}; everything
 * else here is mutable per-individual state (position, energy, mass, health) or
 * a *derived* attribute computed from the genes rather than inherited directly
 * (metabolism, growth, reserves, display colour).
 *
 * Each derived attribute encodes a tradeoff so no gene is strictly beneficial —
 * e.g. costly traits raise metabolism, and size suppresses stealth.
 */
public class Animal {
  private final Genome genome;
  private final int id;

  private int x, y;
  private double mass;
  private double energy;
  private double hp;
  private int speciesId = -1;
  private int birthTick = 0;
  private boolean alive = true;

  public Animal(Genome genome, int id) {
    this.genome = genome;
    this.id = id;
    this.mass = maxMass() * Balance.START_MASS_FRACTION;
    this.hp = maxHp();
    this.energy = maxEnergy() * Balance.START_ENERGY_FRACTION;
  }

  /** Shorthand for the normalized [0,1] value of a gene. */
  public double n(GeneType t) {
    return genome.norm(t);
  }

  // --- Derived attributes (computed from genes, not inherited) ---

  /** Maximum attainable mass, i.e. the SIZE gene's raw value (1..10). */
  public double maxMass() {
    return genome.value(GeneType.SIZE);
  }

  /**
   * Resting energy cost per tick. Costly traits (strength, speed, awareness,
   * etc.) add upkeep at a rate that *shrinks* with SIZE — a bounded economy
   * of scale (in the spirit of Kleiber's law: larger bodies run their active
   * machinery more efficiently per unit of capability). The flat baseline
   * (BASE_META) is unaffected — every animal pays some fixed upkeep
   * regardless of size.
   */
  public double metabolism() {
    return metabolism(0.0);
  }

  /**
   * As {@link #metabolism()}, plus extra upkeep from environmental climate
   * stress (see {@link BiomeType#climateStress()}) — living somewhere
   * hostile costs more just to stay alive. SIZE discounts this specific
   * cost (Bergmann's rule: larger bodies retain heat more efficiently), so a
   * big animal shrugs off a harsh climate that would burden a small one,
   * while still paying SIZE's usual costs (growth time, food, stealth)
   * everywhere else — never a free win.
   */
  public double metabolism(double climateStress) {
    double sum = 0.0;
    for (GeneType t : GeneType.ALL) {
      sum += t.metabolicWeight() * n(t);
    }
    double economyOfScale = 1.0 - Balance.SIZE_METABOLIC_DISCOUNT * n(GeneType.SIZE);
    double climateProtection = 1.0 - Balance.SIZE_CLIMATE_PROTECTION * n(GeneType.SIZE);
    return Balance.BASE_META * (1.0 + climateStress * climateProtection) + sum * economyOfScale;
  }

  /** Energy spent per tile of movement. */
  public double moveCost() {
    return Balance.MOVE_K * mass * (0.5 + n(GeneType.LAND_SPEED));
  }

  /**
   * Foliage this animal can consume from a tile in one graze. Scales with body
   * mass (a bigger mouth/gut), so large herbivores aren't out-foraged by tiny
   * ones whose flat intake vastly exceeds their needs. Big grazers tend to
   * strip a tile in one bite and must roam — paying movement energy — which
   * keeps size a genuine tradeoff rather than a free win.
   */
  public double grazeCapacity() {
    return Balance.APPETITE * (0.5 + Balance.APPETITE_MASS_SCALE * mass);
  }

  /** Energy reserve capacity — larger animals bank more. */
  public double maxEnergy() {
    return mass * Balance.RESERVE_PER_MASS;
  }

  public double maxHp() {
    return Balance.BASE_HP
        + n(GeneType.DURABILITY) * Balance.HP_PER_DURABILITY
        + mass * Balance.HP_PER_MASS;
  }

  /** Detection radius in tiles. */
  public int awarenessRadius() {
    return (int) Math.round(genome.value(GeneType.AWARENESS));
  }

  /**
   * How fast this animal's biological clock runs relative to a "typical"
   * animal (rate-of-living theory): burning energy faster than average
   * (higher {@link #metabolism()}) speeds it up, and SIZE slows it down via
   * the same Kleiber's-law economy of scale that already discounts SIZE's
   * raw upkeep. Never a free win either way — whatever shortens this pace
   * already costs energy, growth time, or stealth elsewhere.
   */
  public double agingPace() {
    double metabolicFactor = metabolism() / Balance.AGING_REFERENCE_METABOLISM;
    double sizeProtection = 1.0 - Balance.AGING_SIZE_PROTECTION * n(GeneType.SIZE);
    return metabolicFactor * sizeProtection;
  }

  /**
   * Per-tick probability of dying of old age: a Gompertz hazard (risk rises
   * exponentially with age, the standard actuarial aging model) evaluated at
   * this animal's own {@link #agingPace()} rather than raw calendar age.
   */
  public double agingDeathChance(int currentTick) {
    double effectiveAge = Math.max(0, currentTick - birthTick) * agingPace();
    double hazard = Balance.AGING_H0 * Math.exp(Balance.AGING_K * effectiveAge);
    return Math.min(1.0, hazard);
  }

  /**
   * Stealth assuming a perfect climate match (see
   * {@link #effectiveStealth(double, double)}) — used where no tile context
   * is available.
   */
  public double effectiveStealth() {
    return effectiveStealth(n(GeneType.CAMO_TEMP), n(GeneType.CAMO_MOISTURE));
  }

  /**
   * Stealth after the size penalty (big animals simply can't hide well) and
   * a camouflage mismatch penalty: {@link GeneType#CAMO_TEMP}/
   * {@link GeneType#CAMO_MOISTURE} are the climate this animal is adapted to
   * blend into, so its stealth bonus is strongest on a tile matching that
   * climate and fades the further the tile's actual temperature/moisture
   * (see {@link Tile#getTemperature()}/{@link Tile#getMoisture()}) drifts
   * from it — camouflage specialized for one biome doesn't fully transfer to
   * a climatically different one.
   */
  public double effectiveStealth(double tileTemperature, double tileMoisture) {
    double base = n(GeneType.STEALTH) * (1.0 - 0.5 * n(GeneType.SIZE));
    double dt = n(GeneType.CAMO_TEMP) - tileTemperature;
    double dm = n(GeneType.CAMO_MOISTURE) - tileMoisture;
    double mismatch = Math.sqrt(dt * dt + dm * dm); // 0 (perfect match) .. ~1.41 (opposite corners)
    double camoFactor = Math.max(0.0, 1.0 - Balance.CAMO_MISMATCH_K * mismatch);
    return base * camoFactor;
  }

  // --- State mutation ---

  public void addEnergy(double delta) {
    energy += delta;
    double cap = maxEnergy();
    if (energy > cap) energy = cap;
  }

  public void grow() {
    if (energy <= metabolism() * Balance.GROWTH_MARGIN) return;
    double target = maxMass();
    if (mass >= target) return;
    double gained = Balance.GROWTH_RATE * (target - mass);
    mass += gained;
    energy -= gained * Balance.GROWTH_ENERGY;
  }

  public void damage(double amount) {
    if (amount > 0) hp -= amount;
  }

  /**
   * Slowly heals HP each tick out of spare energy reserves — never free:
   * healing competes with growth/reproduction for the same energy pool, and
   * an animal too hungry to spare the cost simply doesn't heal that tick.
   */
  public void regen() {
    if (hp >= maxHp()) {
      hp = maxHp();
      return;
    }
    double amount = Balance.HP_REGEN_RATE * maxHp();
    double cost = amount * Balance.HP_REGEN_ENERGY_COST;
    if (energy <= cost) return;
    hp = Math.min(maxHp(), hp + amount);
    energy -= cost;
  }

  public void kill() {
    alive = false;
  }

  // --- Getters / setters ---

  public Genome getGenome() { return genome; }
  public int getId() { return id; }
  public int getX() { return x; }
  public int getY() { return y; }
  public void setPosition(int x, int y) { this.x = x; this.y = y; }
  public double getMass() { return mass; }
  public double getEnergy() { return energy; }
  public void setEnergy(double energy) { this.energy = energy; }
  public double getHp() { return hp; }
  public int getSpeciesId() { return speciesId; }
  public void setSpeciesId(int speciesId) { this.speciesId = speciesId; }
  public int getBirthTick() { return birthTick; }
  public void setBirthTick(int birthTick) { this.birthTick = birthTick; }
  public boolean isAlive() { return alive; }

  /** True once reserves are low enough that the animal wants to feed. */
  public boolean isHungry() {
    return energy < 0.5 * maxEnergy();
  }

  /** True when reserves are full enough to reproduce, given the FECUNDITY gene. */
  public boolean canReproduce() {
    return energy >= (1.0 - n(GeneType.FECUNDITY)) * maxEnergy();
  }
}
