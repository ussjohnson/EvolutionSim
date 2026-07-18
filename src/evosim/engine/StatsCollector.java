package evosim.engine;

import java.util.ArrayList;
import java.util.List;

import evosim.model.BiomeType;
import evosim.model.GeneType;

/**
 * Records the time series used for the live charts and the end-of-run report.
 * Per-species population history lives on each {@link evosim.model.Species};
 * this collector owns the shared sample-tick axis and the whole-ecosystem
 * series: global population, average fertility, the population-mean of every
 * gene (the evolution signal), mean body mass, the diet-guild and biome
 * breakdowns, and per-interval birth/death flow.
 */
public final class StatsCollector {
  private final List<Integer> sampleTicks = new ArrayList<>();
  private final List<Integer> globalPopulation = new ArrayList<>();
  private final List<Double> averageFertility = new ArrayList<>();
  private final List<Double> meanMass = new ArrayList<>();
  // One list per gene (GeneType order); each holds the population-mean of that
  // normalized gene at each sample tick — this is what makes evolution visible.
  private final List<List<Double>> geneMeans = new ArrayList<>();
  private final List<int[]> dietCounts = new ArrayList<>();   // [herb, omni, carn] per sample
  private final List<int[]> biomeCounts = new ArrayList<>();  // per BiomeType per sample
  private final List<Integer> births = new ArrayList<>();     // births since previous sample
  private final List<Integer> deaths = new ArrayList<>();     // deaths since previous sample

  public StatsCollector() {
    for (int i = 0; i < GeneType.COUNT; i++) geneMeans.add(new ArrayList<>());
  }

  /** Take one aligned sample of the world's current state. */
  public void sample(World w) {
    sampleTicks.add(w.currentTick());
    globalPopulation.add(w.population());
    averageFertility.add(w.averageFertility());
    meanMass.add(w.meanMass());
    double[] mg = w.meanGenome();
    for (GeneType t : GeneType.ALL) {
      geneMeans.get(t.ordinal()).add(mg[t.ordinal()]);
    }
    dietCounts.add(w.dietCounts());
    biomeCounts.add(w.biomeCounts());
    births.add(w.takeBirthsSinceSample());
    deaths.add(w.takeDeathsSinceSample());
    w.species().sample();
  }

  public List<Integer> sampleTicks() { return sampleTicks; }
  public List<Integer> globalPopulation() { return globalPopulation; }
  public List<Double> averageFertility() { return averageFertility; }
  public List<Double> meanMass() { return meanMass; }
  public List<Double> geneMean(GeneType t) { return geneMeans.get(t.ordinal()); }
  public List<int[]> dietCounts() { return dietCounts; }
  public List<int[]> biomeCounts() { return biomeCounts; }
  public List<Integer> births() { return births; }
  public List<Integer> deaths() { return deaths; }
  public int sampleCount() { return sampleTicks.size(); }

  /** BiomeType labels in the order {@link #biomeCounts()} columns appear. */
  public static String[] biomeLabels() {
    String[] labels = new String[BiomeType.ALL.length];
    for (BiomeType b : BiomeType.ALL) labels[b.ordinal()] = b.label();
    return labels;
  }
}
