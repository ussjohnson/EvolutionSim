package evosim.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import evosim.model.Animal;
import evosim.model.Balance;
import evosim.model.BiomeType;
import evosim.model.GeneType;
import evosim.model.Genome;
import evosim.model.Species;
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
  private final boolean wraparound;
  private final Tile[][] tiles; // [y][x]
  private final List<Animal> animals = new ArrayList<>();
  private final RandomSource rng;
  private final SpeciesRegistry species = new SpeciesRegistry();
  private final Behavior behavior = new Behavior();
  private final StatsCollector stats = new StatsCollector();
  private final List<int[]> fights = new ArrayList<>(); // this tick's attacks: {ax, ay, tx, ty}

  // Interactive user commands (god-tools) queued from the UI thread and drained
  // on the engine thread at the start of each tick, so all model mutation still
  // happens on one thread. Empty on the headless/deterministic path.
  private final ConcurrentLinkedQueue<Consumer<World>> commands = new ConcurrentLinkedQueue<>();
  // Actively burning tiles: tileIndex (y*width+x) -> remaining burn ticks. A
  // TreeMap for ordered iteration. Only ever populated by user-triggered fire.
  private Map<Integer, Integer> burning = new TreeMap<>();

  // Seasons: a global yearly climate cycle (see #seasonalPhase). Off by default
  // (headless/dev path); enabled per-run by fromSettings.
  private boolean seasonsEnabled = false;
  private int seasonLength = Balance.SEASON_LENGTH;

  // Per-sample flow counters (reset by StatsCollector each sample).
  private int birthsSinceSample = 0;
  private int deathsSinceSample = 0;

  private int tick = 0;
  private int nextId = 0;

  public World(int width, int height, long seed, double baseFertility, boolean wraparound) {
    this.width = width;
    this.height = height;
    this.wraparound = wraparound;
    this.rng = new RandomSource(seed);
    this.tiles = new Tile[height][width];

    // Biomes: two blended value-noise fields (coarse + fine octave) per axis,
    // classified via BiomeType.classify so regions are organic and
    // contiguous rather than per-tile independent randomness. Blended
    // octaves have a central tendency (rarely reach the true 0/1 extremes),
    // so raw samples are contrast-stretched to fill [0,1] before
    // classification — otherwise biomes whose ideal point sits near an
    // extreme would barely appear. Stretching (rather than hand-tuning
    // ideal points to this specific noise shape) keeps BiomeType's anchor
    // points meaningful regardless of how BIOME_NOISE_SCALE or the octave
    // blend is tuned later.
    Random r = rng.get();
    double scale = Balance.BIOME_NOISE_SCALE;
    NoiseField tempCoarse = new NoiseField(width, height, scale, r);
    NoiseField tempFine = new NoiseField(width, height, scale * 0.4, r);
    NoiseField moistCoarse = new NoiseField(width, height, scale, r);
    NoiseField moistFine = new NoiseField(width, height, scale * 0.4, r);

    double[][] rawTemp = new double[height][width];
    double[][] rawMoist = new double[height][width];
    double tMin = Double.MAX_VALUE, tMax = -Double.MAX_VALUE;
    double mMin = Double.MAX_VALUE, mMax = -Double.MAX_VALUE;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        double t = 0.7 * tempCoarse.sample(x, y) + 0.3 * tempFine.sample(x, y);
        double m = 0.7 * moistCoarse.sample(x, y) + 0.3 * moistFine.sample(x, y);
        rawTemp[y][x] = t;
        rawMoist[y][x] = m;
        if (t < tMin) tMin = t;
        if (t > tMax) tMax = t;
        if (m < mMin) mMin = m;
        if (m > mMax) mMax = m;
      }
    }
    double tRange = Math.max(1e-9, tMax - tMin);
    double mRange = Math.max(1e-9, mMax - mMin);

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        double temp = (rawTemp[y][x] - tMin) / tRange;
        double moist = (rawMoist[y][x] - mMin) / mRange;
        BiomeType biome = BiomeType.classify(temp, moist);
        double fertility = baseFertility * biome.fertilityMultiplier() * (0.85 + 0.3 * r.nextDouble());
        double foliage = Math.min(1.0, r.nextDouble() * biome.fertilityMultiplier());
        tiles[y][x] = new Tile(foliage, fertility, biome, temp, moist);
      }
    }
  }

  /**
   * Build a world and seed it with {@code population} randomly placed animals
   * drawn from the default founding band (see {@link evosim.config.SimSettings}).
   * Bounded edges (no wraparound) — used by the headless/dev entry points
   * where a settings-driven {@link #fromSettings} isn't in play.
   */
  public static World create(int width, int height, int population, long seed, double baseFertility) {
    World w = new World(width, height, seed, baseFertility, false);
    evosim.config.SimSettings defaults = new evosim.config.SimSettings();
    w.seedPopulation(population, defaults.startMin, defaults.startMax, 1);
    w.stats.sample(w);
    return w;
  }

  /** Build a world from user-configured settings, honouring per-gene start ranges and wraparound. */
  public static World fromSettings(evosim.config.SimSettings s) {
    s.sanitize();
    World w = new World(s.width, s.height, s.seed, s.baseFertility, s.wraparound);
    w.seasonsEnabled = s.seasonsEnabled;
    w.seasonLength = Math.max(1, s.seasonLength);
    w.seedPopulation(s.population, s.startMin, s.startMax, s.founderSpeciesCount);
    w.stats.sample(w);
    return w;
  }

  /**
   * Seeds the population from one founding band, or (when {@code founderSpeciesCount}
   * > 1) from that many bands instead: each cluster's center is drawn uniformly
   * from the gene's full allowed range (independently-random genomes sit
   * ~0.41 apart on average, comfortably past {@link Balance#SPECIATION_THRESHOLD}),
   * with a normal {@link Balance#FOUNDER_GENE_BAND}-width band around it — so
   * the population starts as {@code founderSpeciesCount} distinct, internally
   * cohesive species rather than needing in-run drift to diversify.
   */
  private void seedPopulation(int population, double[] startMin, double[] startMax, int founderSpeciesCount) {
    Random r = rng.get();
    int clusters = Math.max(1, founderSpeciesCount);
    double[][] clusterMin = new double[clusters][];
    double[][] clusterMax = new double[clusters][];
    if (clusters == 1) {
      clusterMin[0] = startMin;
      clusterMax[0] = startMax;
    } else {
      for (int c = 0; c < clusters; c++) {
        double[] cMin = new double[GeneType.COUNT];
        double[] cMax = new double[GeneType.COUNT];
        for (GeneType t : GeneType.ALL) {
          double center = t.min() + r.nextDouble() * (t.max() - t.min());
          double half = Balance.FOUNDER_GENE_BAND * (t.max() - t.min()) / 2.0;
          cMin[t.ordinal()] = Math.max(t.min(), center - half);
          cMax[t.ordinal()] = Math.min(t.max(), center + half);
        }
        clusterMin[c] = cMin;
        clusterMax[c] = cMax;
      }
    }

    int attempts = 0;
    int placed = 0;
    while (placed < population && attempts < population * 20) {
      attempts++;
      int x = r.nextInt(width);
      int y = r.nextInt(height);
      if (tiles[y][x].getResident() != null) continue;
      int cluster = placed % clusters;
      Genome genome = Genome.random(r, clusterMin[cluster], clusterMax[cluster]);
      Animal a = new Animal(genome, nextId++);
      a.setPosition(x, y);
      a.setSpeciesId(species.classify(a.getGenome(), 0, evosim.model.SpeciesRegistry.NO_PARENT, r));
      tiles[y][x].setResident(a);
      animals.add(a);
      placed++;
    }
  }

  /** Advance the simulation by one tick. */
  public void tick() {
    fights.clear();

    // Apply any interactive user commands, then advance active wildfires —
    // both before the animals act, so a freshly-triggered event takes effect
    // this tick.
    drainCommands();
    spreadFire();

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

    double growth = seasonalGrowthFactor();
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        tiles[y][x].recover(rng.get(), growth);
      }
    }

    animals.removeIf(a -> !a.isAlive());
    tick++;
    if (tick % Balance.SAMPLE_INTERVAL == 0) {
      stats.sample(this);
    }
  }

  // --- Interactive commands (god-tools) ---

  /** Queue a mutation to run on the engine thread at the start of the next tick. */
  public void enqueue(Consumer<World> command) {
    commands.add(command);
  }

  private void drainCommands() {
    Consumer<World> c;
    while ((c = commands.poll()) != null) {
      c.accept(this);
    }
  }

  // --- Seasons ---

  public boolean seasonsEnabled() { return seasonsEnabled; }
  public int seasonLength() { return seasonLength; }

  /** Position within the current year in [0,1): 0=spring start, .25=midsummer, .5=autumn, .75=midwinter. */
  public double seasonalPhase() {
    if (!seasonsEnabled) return 0.0;
    return (double) (((tick % seasonLength) + seasonLength) % seasonLength) / seasonLength;
  }

  /** The seasonal swing s = sin(2πφ): +1 at midsummer, -1 at midwinter, 0 at the equinoxes. */
  private double seasonalSwing() {
    if (!seasonsEnabled) return 0.0;
    return Math.sin(2.0 * Math.PI * seasonalPhase());
  }

  /** Multiplier on foliage regrowth: lush in summer, sparse in winter (never below 0.15). */
  public double seasonalGrowthFactor() {
    return Math.max(0.15, 1.0 + Balance.SEASON_GROWTH_SWING * seasonalSwing());
  }

  /** Extra climate-stress fraction every animal pays in the cold half of the year (0 in summer). */
  public double seasonalColdStress() {
    return Balance.SEASON_COLD_STRESS * Math.max(0.0, -seasonalSwing());
  }

  /** Human-readable season for the current tick, or "—" when seasons are off. */
  public String seasonName() {
    if (!seasonsEnabled) return "—";
    double p = seasonalPhase();
    if (p < 0.25) return "Spring";
    if (p < 0.50) return "Summer";
    if (p < 0.75) return "Autumn";
    return "Winter";
  }

  // --- Wildfire ---

  /** Ignite a wildfire at (x, y) if it has fuel; spreads on subsequent ticks. */
  public void ignite(int x, int y) {
    if (!inBounds(x, y)) return;
    if (tiles[y][x].getFoliage() >= Balance.FIRE_MIN_FOLIAGE) {
      burning.put(y * width + x, Balance.FIRE_DURATION);
    }
  }

  public boolean isBurning(int x, int y) {
    return burning.containsKey(y * width + x);
  }

  private void spreadFire() {
    if (burning.isEmpty()) return;
    Map<Integer, Integer> next = new TreeMap<>();
    List<int[]> ignitions = new ArrayList<>();
    Random r = rng.get();
    for (Map.Entry<Integer, Integer> e : burning.entrySet()) {
      int idx = e.getKey();
      int x = idx % width, y = idx / width;
      Tile t = tiles[y][x];
      t.setFoliage(t.getFoliage() * 0.25); // fire strips most of the foliage
      Animal resident = t.getResident();
      if (resident != null && resident.isAlive()) {
        animalDied(resident, Species.DeathCause.DISASTER, false); // burned, no carrion
      }
      for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
          if (dx == 0 && dy == 0) continue;
          int nx = x + dx, ny = y + dy;
          if (!inBounds(nx, ny)) continue;
          int nIdx = ny * width + nx;
          if (burning.containsKey(nIdx)) continue;
          if (tiles[ny][nx].getFoliage() >= Balance.FIRE_MIN_FOLIAGE
              && r.nextDouble() < Balance.FIRE_SPREAD_CHANCE) {
            ignitions.add(new int[] { nIdx, Balance.FIRE_DURATION });
          }
        }
      }
      int rem = e.getValue() - 1;
      if (rem > 0) next.put(idx, rem);
    }
    for (int[] ig : ignitions) next.putIfAbsent(ig[0], ig[1]);
    burning = next;
  }

  // --- Death bookkeeping (centralized so carrion + counters stay consistent) ---

  /**
   * Record an animal's death: remove it from the grid, tally it against its
   * species, and (unless it was eaten by a predator) leave its body as carrion
   * on its tile. Called for every death so births/deaths flow counters and the
   * carrion economy stay consistent.
   */
  public void animalDied(Animal a, Species.DeathCause cause, boolean leaveCarrion) {
    if (!a.isAlive()) return;
    if (leaveCarrion) {
      tiles[a.getY()][a.getX()].addCarrion(a.getMass() * Balance.CARRION_FROM_MASS);
    }
    a.kill();
    removeFromGrid(a);
    species.recordDeath(a.getSpeciesId(), tick, cause, a.getBirthTick());
    deathsSinceSample++;
  }

  void recordBirth() {
    birthsSinceSample++;
  }

  /** Births since the last stats sample, then reset — called by StatsCollector. */
  public int takeBirthsSinceSample() {
    int b = birthsSinceSample;
    birthsSinceSample = 0;
    return b;
  }

  /** Deaths since the last stats sample, then reset — called by StatsCollector. */
  public int takeDeathsSinceSample() {
    int d = deathsSinceSample;
    deathsSinceSample = 0;
    return d;
  }

  // --- Grid helpers ---

  public boolean wraparound() {
    return wraparound;
  }

  public boolean inBounds(int x, int y) {
    return x >= 0 && x < width && y >= 0 && y < height;
  }

  /** Like {@link #inBounds}, but always true under wraparound (any coordinate resolves to a tile). */
  public boolean canReach(int x, int y) {
    return wraparound || inBounds(x, y);
  }

  /** Normalizes x into [0, width) when wraparound is on; a no-op otherwise (caller must bounds-check separately). */
  public int wrapX(int x) {
    return wraparound ? Math.floorMod(x, width) : x;
  }

  /** Normalizes y into [0, height) when wraparound is on; a no-op otherwise. */
  public int wrapY(int y) {
    return wraparound ? Math.floorMod(y, height) : y;
  }

  /**
   * Signed x-distance from {@code from} to {@code to}, taking the shorter way
   * around the edge when wraparound is on. {@code Integer.signum(...)} of the
   * result gives the correct step direction either way.
   */
  public int wrappedDeltaX(int from, int to) {
    int d = to - from;
    if (!wraparound) return d;
    if (d > width / 2) d -= width;
    else if (d < -width / 2) d += width;
    return d;
  }

  /** Signed y-distance from {@code from} to {@code to}; see {@link #wrappedDeltaX}. */
  public int wrappedDeltaY(int from, int to) {
    int d = to - from;
    if (!wraparound) return d;
    if (d > height / 2) d -= height;
    else if (d < -height / 2) d += height;
    return d;
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

  /**
   * Interactively spawn an animal with the given genome onto an empty tile at
   * (x, y), classifying it into a species with no in-sim ancestor. Returns the
   * new animal, or null if the tile is out of bounds or occupied. Used by the
   * god-tools "spawn life" brush.
   */
  public Animal spawnAnimal(Genome g, int x, int y) {
    if (!inBounds(x, y) || tiles[y][x].getResident() != null) return null;
    Animal a = new Animal(g, nextId++);
    a.setPosition(x, y);
    a.setBirthTick(tick);
    a.setSpeciesId(species.classify(g, tick, SpeciesRegistry.NO_PARENT, rng.get()));
    tiles[y][x].setResident(a);
    animals.add(a);
    recordBirth();
    return a;
  }

  /** Kill whatever animal stands at (x, y), if any, as a disaster casualty (leaves carrion). */
  public void killAt(int x, int y) {
    if (!inBounds(x, y)) return;
    Animal a = tiles[y][x].getResident();
    if (a != null && a.isAlive()) {
      animalDied(a, Species.DeathCause.DISASTER, true);
    }
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
    byte[] biome = new byte[width * height];
    float[] carrion = new float[width * height];
    boolean[] fire = new boolean[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int i = y * width + x;
        fol[i] = (float) tiles[y][x].getFoliage();
        biome[i] = (byte) tiles[y][x].getBiome().ordinal();
        carrion[i] = (float) tiles[y][x].getCarrion();
      }
    }
    for (int idx : burning.keySet()) fire[idx] = true;
    java.util.List<Snapshot.Dot> dots = new ArrayList<>(animals.size());
    for (Animal a : animals) {
      Tile tile = tiles[a.getY()][a.getX()];
      evosim.model.Species sp = species.byId(a.getSpeciesId());
      int rgb = (sp != null) ? sp.color() : 0xFFFFFF;
      float stealth = (float) a.effectiveStealth(tile.getTemperature(), tile.getMoisture());
      dots.add(new Snapshot.Dot(a.getId(), a.getX(), a.getY(), rgb,
          (float) a.getMass(), (float) a.maxMass(), a.getSpeciesId(), a.getBirthTick(),
          (float) a.getEnergy(), (float) a.maxEnergy(), (float) a.getHp(), (float) a.maxHp(),
          stealth, a.getGenome().normVector()));
    }
    java.util.List<Snapshot.SpeciesCount> sc = new ArrayList<>();
    for (evosim.model.Species s : species.all()) {
      if (s.isExtant()) {
        sc.add(new Snapshot.SpeciesCount(s.id(), s.name(), s.color(), s.members()));
      }
    }
    sc.sort((p, q) -> Integer.compare(q.count, p.count));
    java.util.List<Snapshot.Fight> fs = new ArrayList<>(fights.size());
    for (int[] f : fights) {
      fs.add(new Snapshot.Fight(f[0], f[1], f[2], f[3]));
    }
    return new Snapshot(width, height, tick, animals.size(), fol, biome, carrion, fire, dots, sc, fs,
        seasonsEnabled, seasonName(), seasonalPhase());
  }

  /** Records an attack this tick (attacker at ax,ay against a target at tx,ty), for the map's fight indicator. */
  void recordFight(int ax, int ay, int tx, int ty) {
    fights.add(new int[] { ax, ay, tx, ty });
  }

  // --- Accessors ---

  public int width() { return width; }
  public int height() { return height; }
  public int currentTick() { return tick; }
  public RandomSource rng() { return rng; }
  public SpeciesRegistry species() { return species; }
  public StatsCollector stats() { return stats; }
  public long seed() { return rng.seed(); }
  public List<Animal> animals() { return animals; }
  public int population() { return animals.size(); }
  public int capacity() { return width * height; }

  /** Mean tile fertility across the whole map, in [0,1] — tracks environment health/over-grazing pressure. */
  public double averageFertility() {
    double sum = 0.0;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        sum += tiles[y][x].getFertility();
      }
    }
    return sum / (width * height);
  }

  /** Population-mean of every normalized gene (GeneType order), or all-zero if extinct — the evolution signal. */
  public double[] meanGenome() {
    double[] mean = new double[GeneType.COUNT];
    if (animals.isEmpty()) return mean;
    for (Animal a : animals) {
      double[] g = a.getGenome().normVector();
      for (int i = 0; i < mean.length; i++) mean[i] += g[i];
    }
    for (int i = 0; i < mean.length; i++) mean[i] /= animals.size();
    return mean;
  }

  /** Mean body mass across the living population, or 0 if extinct. */
  public double meanMass() {
    if (animals.isEmpty()) return 0.0;
    double sum = 0.0;
    for (Animal a : animals) sum += a.getMass();
    return sum / animals.size();
  }

  /** Head counts by diet guild: [herbivores(<0.33), omnivores(<0.66), carnivores]. */
  public int[] dietCounts() {
    int[] c = new int[3];
    for (Animal a : animals) {
      double d = a.n(GeneType.DIET);
      c[d < 0.33 ? 0 : d < 0.66 ? 1 : 2]++;
    }
    return c;
  }

  /** Head counts per {@link BiomeType} (ordinal-indexed) of where animals currently stand. */
  public int[] biomeCounts() {
    int[] c = new int[BiomeType.ALL.length];
    for (Animal a : animals) {
      c[tiles[a.getY()][a.getX()].getBiome().ordinal()]++;
    }
    return c;
  }
}
