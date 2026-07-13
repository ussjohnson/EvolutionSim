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
  private final int color;

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
    this.color = calcColor();
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
    double sum = 0.0;
    for (GeneType t : GeneType.ALL) {
      sum += t.metabolicWeight() * n(t);
    }
    double economyOfScale = 1.0 - Balance.SIZE_METABOLIC_DISCOUNT * n(GeneType.SIZE);
    return Balance.BASE_META + sum * economyOfScale;
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

  /** Stealth after the size penalty — big animals simply can't hide well. */
  public double effectiveStealth() {
    return n(GeneType.STEALTH) * (1.0 - 0.5 * n(GeneType.SIZE));
  }

  /**
   * Display colour (see {@link Coloring}). Size deliberately does not affect
   * colour (it sizes the on-screen icon instead).
   */
  public final int calcColor() {
    return Coloring.pack(n(GeneType.DIET), n(GeneType.FECUNDITY), n(GeneType.LAND_SPEED));
  }

  public String getColorHex() {
    return String.format("#%06X", color & 0xFFFFFF);
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

  public void kill() {
    alive = false;
  }

  // --- Getters / setters ---

  public Genome getGenome() { return genome; }
  public int getId() { return id; }
  public int getColorInt() { return color; }
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
