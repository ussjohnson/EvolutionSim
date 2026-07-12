package evosim.model;

import java.util.Random;

/**
 * An animal's full set of hereditary genes, indexed by {@link GeneType}.
 * Provides normalized access, mutation (for asexual reproduction), and the
 * genetic-distance metric that drives both speciation and kin perception.
 */
public final class Genome {
  private final Gene[] genes; // indexed by GeneType.ordinal()

  public Genome(Gene[] genes) {
    if (genes.length != GeneType.COUNT) {
      throw new IllegalArgumentException("expected " + GeneType.COUNT + " genes, got " + genes.length);
    }
    this.genes = genes;
  }

  /** A genome with every gene drawn uniformly at random from its full range. */
  public static Genome random(Random rng) {
    Gene[] g = new Gene[GeneType.COUNT];
    for (GeneType t : GeneType.ALL) {
      g[t.ordinal()] = t.random(rng);
    }
    return new Genome(g);
  }

  /**
   * A genome with each gene drawn uniformly from a caller-supplied start range
   * (indexed by {@link GeneType#ordinal()}), used to seed a biased founding
   * population. Ranges must lie within each gene's allowed bounds.
   */
  public static Genome random(Random rng, double[] startMin, double[] startMax) {
    Gene[] g = new Gene[GeneType.COUNT];
    for (GeneType t : GeneType.ALL) {
      int i = t.ordinal();
      double v = startMin[i] + rng.nextDouble() * (startMax[i] - startMin[i]);
      g[i] = t.of(v);
    }
    return new Genome(g);
  }

  public Gene gene(GeneType t) {
    return genes[t.ordinal()];
  }
  public double value(GeneType t) {
    return genes[t.ordinal()].value();
  }
  public double norm(GeneType t) {
    return genes[t.ordinal()].norm();
  }

  /** A mutated deep copy: each gene is copied then perturbed. The original is untouched. */
  public Genome mutatedCopy(Random rng) {
    Gene[] c = new Gene[genes.length];
    for (int i = 0; i < genes.length; i++) {
      c[i] = genes[i].copy();
      c[i].mutate(rng);
    }
    return new Genome(c);
  }

  /** The normalized [0,1] value of every gene, in GeneType order. */
  public double[] normVector() {
    double[] v = new double[genes.length];
    for (int i = 0; i < genes.length; i++) {
      v[i] = genes[i].norm();
    }
    return v;
  }

  /** Normalized Euclidean distance to another genome, scaled to roughly [0,1]. */
  public double distance(Genome other) {
    return distanceTo(other.normVector());
  }

  /** Normalized Euclidean distance from this genome's norm vector to the given one. */
  public double distanceTo(double[] otherNorm) {
    double sum = 0.0;
    for (int i = 0; i < genes.length; i++) {
      double d = genes[i].norm() - otherNorm[i];
      sum += d * d;
    }
    return Math.sqrt(sum / genes.length);
  }
}
