package evosim.engine;

import java.util.Random;

/**
 * The single seeded random source threaded through the entire simulation.
 * Everything stochastic — mutation, tile regrowth, perception noise, movement
 * tie-breaks, iteration shuffling — must draw from this one instance so that a
 * given seed reproduces a run exactly. No {@code Math.random()} anywhere in the
 * engine or model during a run.
 */
public final class RandomSource {
  private final Random random;
  private final long seed;

  public RandomSource(long seed) {
    this.seed = seed;
    this.random = new Random(seed);
  }

  public Random get() {
    return random;
  }

  public long seed() {
    return seed;
  }

  public double nextDouble() {
    return random.nextDouble();
  }

  public int nextInt(int bound) {
    return random.nextInt(bound);
  }
}
