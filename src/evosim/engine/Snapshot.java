package evosim.engine;

import java.util.List;

/**
 * An immutable, self-contained render frame handed from the engine thread to
 * the Swing EDT. It holds only plain data (no references to live model objects)
 * so the UI can read it freely without ever touching the mutating simulation —
 * which is what keeps the engine deterministic and thread-safe.
 */
public final class Snapshot {
  public final int width;
  public final int height;
  public final int tick;
  public final int population;
  public final float[] foliage;             // length width*height, row-major
  public final byte[] biome;                // length width*height, row-major; BiomeType ordinal
  public final float[] carrion;             // length width*height, row-major; carcass energy per tile
  public final boolean[] fire;              // length width*height, row-major; true where a tile is burning
  public final List<Dot> dots;              // one per living animal
  public final List<SpeciesCount> species;  // extant species, for the sidebar
  public final List<Fight> fights;          // this tick's attacks, for the map's fight indicator
  public final boolean seasonsEnabled;
  public final String seasonName;           // "Spring".."Winter", or "—" when seasons are off
  public final double seasonPhase;          // [0,1) position within the year

  public Snapshot(int width, int height, int tick, int population, float[] foliage, byte[] biome,
                  float[] carrion, boolean[] fire, List<Dot> dots, List<SpeciesCount> species,
                  List<Fight> fights, boolean seasonsEnabled, String seasonName, double seasonPhase) {
    this.width = width;
    this.height = height;
    this.tick = tick;
    this.population = population;
    this.foliage = foliage;
    this.biome = biome;
    this.carrion = carrion;
    this.fire = fire;
    this.dots = dots;
    this.species = species;
    this.fights = fights;
    this.seasonsEnabled = seasonsEnabled;
    this.seasonName = seasonName;
    this.seasonPhase = seasonPhase;
  }

  /**
   * A single animal: grid position and colour/mass for drawing, plus enough
   * per-individual state (id, species, vitals, full genome) for the results
   * screen's click-to-inspect tile/animal panel.
   */
  public static final class Dot {
    public final int id;
    public final int x;
    public final int y;
    public final int rgb;
    public final float mass;
    public final float maxMass;
    public final int speciesId;
    public final int birthTick;
    public final float energy;
    public final float maxEnergy;
    public final float hp;
    public final float maxHp;
    public final float stealth;       // effective stealth on the animal's current tile; drives map opacity
    public final boolean infected;    // currently carrying a disease
    public final double[] genomeNorm; // GeneType order, for the inspector's full-genome view

    public Dot(int id, int x, int y, int rgb, float mass, float maxMass, int speciesId,
               int birthTick, float energy, float maxEnergy, float hp, float maxHp,
               float stealth, boolean infected, double[] genomeNorm) {
      this.id = id;
      this.x = x;
      this.y = y;
      this.rgb = rgb;
      this.mass = mass;
      this.maxMass = maxMass;
      this.speciesId = speciesId;
      this.birthTick = birthTick;
      this.energy = energy;
      this.maxEnergy = maxEnergy;
      this.hp = hp;
      this.maxHp = maxHp;
      this.stealth = stealth;
      this.infected = infected;
      this.genomeNorm = genomeNorm;
    }
  }

  /** One attack this tick: an attacker at (ax, ay) against a target at (tx, ty). */
  public static final class Fight {
    public final int ax, ay, tx, ty;

    public Fight(int ax, int ay, int tx, int ty) {
      this.ax = ax;
      this.ay = ay;
      this.tx = tx;
      this.ty = ty;
    }
  }

  /** A live species and its current headcount, for the population sidebar. */
  public static final class SpeciesCount {
    public final int id;
    public final String name;
    public final int rgb;
    public final int count;

    public SpeciesCount(int id, String name, int rgb, int count) {
      this.id = id;
      this.name = name;
      this.rgb = rgb;
      this.count = count;
    }
  }
}
