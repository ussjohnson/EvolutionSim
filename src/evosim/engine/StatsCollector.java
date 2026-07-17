package evosim.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the time series used for the end-of-run report. Per-species population
 * history lives on each {@link evosim.model.Species}; this collector owns the
 * shared sample-tick axis and the global population curve, and triggers the
 * per-species sampling so everything stays aligned to the same sample points.
 */
public final class StatsCollector {
  private final List<Integer> sampleTicks = new ArrayList<>();
  private final List<Integer> globalPopulation = new ArrayList<>();
  private final List<Double> averageFertility = new ArrayList<>();

  /** Take one aligned sample of the world's current state. */
  public void sample(World w) {
    sampleTicks.add(w.currentTick());
    globalPopulation.add(w.population());
    averageFertility.add(w.averageFertility());
    w.species().sample();
  }

  public List<Integer> sampleTicks() { return sampleTicks; }
  public List<Integer> globalPopulation() { return globalPopulation; }
  public List<Double> averageFertility() { return averageFertility; }
  public int sampleCount() { return sampleTicks.size(); }
}
