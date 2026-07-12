package evosim;

import java.util.Arrays;

import evosim.model.Gene;
import evosim.model.GeneType;
import evosim.model.Genome;
import evosim.engine.World;

/**
 * A dependency-free smoke/regression test (plain asserts via {@code check}).
 * Run: {@code java -cp bin evosim.SmokeTest}
 *
 * Covers: determinism (same seed → identical run), model invariants (mutation
 * stays in bounds, reproduction leaves the parent untouched, distance metric
 * behaves), and an ecosystem survival band (population neither dies out nor
 * explodes over 500 ticks).
 */
public class SmokeTest {
  private static int failures = 0;

  public static void main(String[] args) {
    testMutationBounds();
    testMutatedCopyIsIndependent();
    testDistanceMetric();
    testDeterminism();
    testSurvivalBand();

    if (failures == 0) {
      System.out.println("\nALL SMOKE TESTS PASSED");
    } else {
      System.out.println("\n" + failures + " CHECK(S) FAILED");
      System.exit(1);
    }
  }

  private static void testMutationBounds() {
    java.util.Random rng = new java.util.Random(1);
    Gene g = GeneType.DIET.of(0.5);
    for (int i = 0; i < 10000; i++) {
      g.mutate(rng);
      check("mutation stays >= min", g.value() >= GeneType.DIET.min() - 1e-9);
      check("mutation stays <= max", g.value() <= GeneType.DIET.max() + 1e-9);
    }
  }

  private static void testMutatedCopyIsIndependent() {
    java.util.Random rng = new java.util.Random(2);
    Genome parent = Genome.random(rng);
    double[] before = parent.normVector();
    Genome child = parent.mutatedCopy(rng);
    double[] after = parent.normVector();
    check("reproduce does not mutate the parent", Arrays.equals(before, after));
    check("child is a distinct object", child != parent);
  }

  private static void testDistanceMetric() {
    java.util.Random rng = new java.util.Random(3);
    Genome a = Genome.random(rng);
    Genome b = Genome.random(rng);
    check("distance to self is zero", a.distance(a) < 1e-9);
    check("distance is symmetric", Math.abs(a.distance(b) - b.distance(a)) < 1e-9);
    check("distance is non-negative", a.distance(b) >= 0);
  }

  private static void testDeterminism() {
    int[] runA = runPopulations(1234, 300);
    int[] runB = runPopulations(1234, 300);
    check("same seed yields identical population history", Arrays.equals(runA, runB));
  }

  private static int[] runPopulations(long seed, int ticks) {
    World w = World.create(30, 30, 100, seed, 0.2);
    int[] pops = new int[ticks];
    for (int t = 0; t < ticks; t++) {
      w.tick();
      pops[t] = w.population();
    }
    return pops;
  }

  private static void testSurvivalBand() {
    World w = World.create(30, 30, 100, 777, 0.2);
    int cap = w.capacity();
    int[] checkpoints = { 50, 150, 300, 500 };
    int ci = 0;
    for (int t = 1; t <= 500; t++) {
      w.tick();
      if (ci < checkpoints.length && t == checkpoints[ci]) {
        int pop = w.population();
        System.out.printf("  survival checkpoint tick=%d pop=%d species=%d%n",
            t, pop, w.species().extantCount());
        check("population alive at tick " + t, pop > 0);
        check("population within capacity at tick " + t, pop <= cap);
        ci++;
      }
    }
    check("species count stays bounded", w.species().totalCount() < 5000);
  }

  private static void check(String label, boolean condition) {
    if (!condition) {
      failures++;
      System.out.println("FAIL: " + label);
    }
  }
}
