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
  public final List<Dot> dots;              // one per living animal
  public final List<SpeciesCount> species;  // extant species, for the sidebar

  public Snapshot(int width, int height, int tick, int population,
                  float[] foliage, List<Dot> dots, List<SpeciesCount> species) {
    this.width = width;
    this.height = height;
    this.tick = tick;
    this.population = population;
    this.foliage = foliage;
    this.dots = dots;
    this.species = species;
  }

  /** A single animal to draw: grid position, colour, and mass (for icon size). */
  public static final class Dot {
    public final int x;
    public final int y;
    public final int rgb;
    public final float mass;
    public final float maxMass;

    public Dot(int x, int y, int rgb, float mass, float maxMass) {
      this.x = x;
      this.y = y;
      this.rgb = rgb;
      this.mass = mass;
      this.maxMass = maxMass;
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
