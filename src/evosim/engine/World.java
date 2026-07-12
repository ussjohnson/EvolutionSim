package evosim.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import evosim.model.Animal;
import evosim.model.Balance;
import evosim.model.Genome;
import evosim.model.SpeciesRegistry;
import evosim.model.Tile;

/**
 * The simulation state and the master tick loop. Holds the tile grid, the live
 * animals, the seeded RNG, and the species registry. One animal occupies at
 * most one tile ({@link Tile#getResident()} is the occupancy index); an
 * animal's own x/y is the source of truth and is kept in sync with the tiles.
 *
 * {@link #tick()} advances the whole world by one step. It is deterministic
 * given the seed: animals act in a seed-shuffled order, and every random draw
 * comes from {@link #rng()}.
 */
public final class World {
  private final int width;
  private final int height;
  private final Tile[][] tiles; // [y][x]
  private final List<Animal> animals = new ArrayList<>();
  private final RandomSource rng;
  private final SpeciesRegistry species = new SpeciesRegistry();
  private final Behavior behavior = new Behavior();

  private int tick = 0;
  private int nextId = 0;

  public World(int width, int height, long seed, double baseFertility) {
    this.width = width;
    this.height = height;
    this.rng = new RandomSource(seed);
    this.tiles = new Tile[height][width];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        double fertility = baseFertility * (0.5 + rng.nextDouble());
        tiles[y][x] = new Tile(rng.nextDouble(), fertility);
      }
    }
  }

  /** Build a world and seed it with {@code population} randomly placed random-genome animals. */
  public static World create(int width, int height, int population, long seed, double baseFertility) {
    World w = new World(width, height, seed, baseFertility);
    w.seedPopulation(population, null, null);
    return w;
  }

  /** Build a world from user-configured settings, honouring per-gene start ranges. */
  public static World fromSettings(evosim.config.SimSettings s) {
    s.sanitize();
    World w = new World(s.width, s.height, s.seed, s.baseFertility);
    w.seedPopulation(s.population, s.startMin, s.startMax);
    return w;
  }

  private void seedPopulation(int population, double[] startMin, double[] startMax) {
    Random r = rng.get();
    int attempts = 0;
    int placed = 0;
    while (placed < population && attempts < population * 20) {
      attempts++;
      int x = r.nextInt(width);
      int y = r.nextInt(height);
      if (tiles[y][x].getResident() != null) continue;
      Genome genome = (startMin == null)
          ? Genome.random(r)
          : Genome.random(r, startMin, startMax);
      Animal a = new Animal(genome, nextId++);
      a.setPosition(x, y);
      a.setSpeciesId(species.classify(a.getGenome(), 0));
      tiles[y][x].setResident(a);
      animals.add(a);
      placed++;
    }
  }

  /** Advance the simulation by one tick. */
  public void tick() {
    // Deterministic base order (by id) then a seeded shuffle for turn fairness.
    List<Animal> order = new ArrayList<>(animals);
    order.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
    Collections.shuffle(order, rng.get());

    List<Animal> newborns = new ArrayList<>();
    for (Animal a : order) {
      if (!a.isAlive()) continue;
      behavior.step(this, a, newborns);
    }
    animals.addAll(newborns);

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        tiles[y][x].recover(rng.get());
      }
    }

    animals.removeIf(a -> !a.isAlive());
    tick++;
    if (tick % Balance.SAMPLE_INTERVAL == 0) {
      species.sample();
    }
  }

  // --- Grid helpers ---

  public boolean inBounds(int x, int y) {
    return x >= 0 && x < width && y >= 0 && y < height;
  }

  public Tile tileAt(int x, int y) {
    return tiles[y][x];
  }

  public Animal residentAt(int x, int y) {
    return tiles[y][x].getResident();
  }

  /** Move an animal to a new empty tile, keeping tile occupancy in sync. */
  public void moveAnimal(Animal a, int nx, int ny) {
    tiles[a.getY()][a.getX()].setResident(null);
    a.setPosition(nx, ny);
    tiles[ny][nx].setResident(a);
  }

  public void placeNewborn(Animal child, int x, int y) {
    child.setPosition(x, y);
    tiles[y][x].setResident(child);
  }

  public void removeFromGrid(Animal a) {
    Tile t = tiles[a.getY()][a.getX()];
    if (t.getResident() == a) t.setResident(null);
  }

  public int nextId() {
    return nextId++;
  }

  /** Build an immutable render frame of the current state for the UI thread. */
  public Snapshot snapshot() {
    float[] fol = new float[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        fol[y * width + x] = (float) tiles[y][x].getFoliage();
      }
    }
    java.util.List<Snapshot.Dot> dots = new ArrayList<>(animals.size());
    for (Animal a : animals) {
      dots.add(new Snapshot.Dot(a.getX(), a.getY(), a.getColorInt(),
          (float) a.getMass(), (float) a.maxMass()));
    }
    java.util.List<Snapshot.SpeciesCount> sc = new ArrayList<>();
    for (evosim.model.Species s : species.all()) {
      if (s.isExtant()) {
        sc.add(new Snapshot.SpeciesCount(s.id(), s.name(), s.color(), s.members()));
      }
    }
    sc.sort((p, q) -> Integer.compare(q.count, p.count));
    return new Snapshot(width, height, tick, animals.size(), fol, dots, sc);
  }

  // --- Accessors ---

  public int width() { return width; }
  public int height() { return height; }
  public int currentTick() { return tick; }
  public RandomSource rng() { return rng; }
  public SpeciesRegistry species() { return species; }
  public List<Animal> animals() { return animals; }
  public int population() { return animals.size(); }
  public int capacity() { return width * height; }
}
