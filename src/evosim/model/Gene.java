package evosim.model;

import java.util.Random;

/**
 * A single evolvable trait: a value bounded by [min, max], with a per-gene
 * {@code variation} controlling how far a mutation can shift it. The scaling
 * helpers map the value onto arbitrary output ranges (used by name generation
 * and colour derivation); {@link #norm()} is the common [0,1] accessor.
 */
public class Gene {
  private double value;
  private double max;
  private double min;
  private double variation;
  private String name;

  public Gene(double max) {
    this(max, 0.0);
  }
  public Gene(double max, double min) {
    this(max, min, (max - min) / 10.0);
  }
  public Gene(double max, double min, double variation) {
    this(max, min, variation, (max - min) / 2.0);
  }
  public Gene(double max, double min, double variation, double value) {
    this("unknown", max, min, variation, value);
  }
  public Gene(String name, double max, double min, double variation, double value) {
    this.name = name;
    this.max = max;
    this.min = min;
    this.variation = variation;
    this.value = value;
  }

  /** A deep copy — independent of this gene, safe to mutate. */
  public Gene copy() {
    return new Gene(name, max, min, variation, value);
  }

  /** Shift the value by a random amount in [-variation/2, +variation/2], clamped to bounds. */
  public void mutate(Random rng) {
    double change = rng.nextDouble() * variation - variation / 2.0;
    setValue(value + change);
  }

  /** This gene's value expressed as a fraction of its range, in [0,1]. */
  public double norm() {
    if (max == min) return 0.0;
    return (value - min) / (max - min);
  }

  public int intScaleOffset(int upper, int lower) {
    int original = convToIntScale(upper, lower);
    int range = Math.abs(upper - lower);
    if (range == 0) return lower;
    int base = Math.min(upper, lower);
    int offset = range / 2;
    return base + ((original - base + offset) % range);
  }
  public int convToIntScale(int upper, int lower) {
    return (int) convToDoubleScale(upper, lower);
  }
  public double convToDoubleScale(double upper, double lower) {
    double percentile = (max == min) ? 0.0 : (value - min) / (max - min);
    return percentile * (upper - lower) + lower;
  }

  @Override
  public String toString() {
    return name + ": " + value + " (" + min + "-" + max + ")";
  }

  // Getters and setters
  public double value() {
    return value;
  }
  public void setValue(double value) {
    if (value > this.max) value = this.max;
    if (value < this.min) value = this.min;
    this.value = value;
  }
  public double getMax() {
    return max;
  }
  public void setMax(double max) {
    this.max = max;
  }
  public double getMin() {
    return min;
  }
  public void setMin(double min) {
    this.min = min;
  }
  public double getVariation() {
    return variation;
  }
  public void setVariation(double variation) {
    this.variation = variation;
  }
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }
}
