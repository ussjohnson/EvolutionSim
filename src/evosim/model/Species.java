package evosim.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A taxonomic species tracked for statistics. Identity is assigned by
 * {@link SpeciesRegistry} via online clustering of genomes. Each species keeps
 * a running-mean {@code centroid} of its members' normalized gene vectors (so
 * classification tracks the lineage as it drifts), plus lifetime bookkeeping
 * and a sampled population history for the end-of-run charts.
 */
public final class Species {
  /** What killed a member: starvation (ran out of energy) or a predator's kill. */
  public enum DeathCause { STARVATION, PREDATION }

  private final int id;
  private final String name;
  private final int color;
  private final double[] founder;   // normalized genome of the first member
  private final double[] centroid;  // running mean of member norm vectors
  private final int birthTick;
  private final int parentSpeciesId; // -1 if founded by the initial population (no ancestor)

  private int members = 0;          // living members right now
  private int memberSamples = 0;    // total classified into this species (for the running mean)
  private int extinctionTick = -1;
  private int peakPopulation = 0;
  private int totalBirths = 0;
  private int totalDeaths = 0;
  private int kills = 0;
  private int starvationDeaths = 0;
  private int predationDeaths = 0;
  private long totalLifetimeTicks = 0; // accumulated age-at-death, for the average

  // Sampled population over time (parallel to SpeciesRegistry sample ticks).
  private final List<Integer> populationHistory = new ArrayList<>();

  Species(int id, String name, int color, double[] founderNorm, int birthTick, int parentSpeciesId) {
    this.id = id;
    this.name = name;
    this.color = color;
    this.founder = founderNorm.clone();
    this.centroid = founderNorm.clone();
    this.birthTick = birthTick;
    this.parentSpeciesId = parentSpeciesId;
  }

  /**
   * Fold a newly classified genome into the centroid and counts. The centroid
   * is a moving average bounded by {@link Balance#CENTROID_HORIZON}: early on
   * it behaves like a true running mean (converging quickly to represent the
   * founding members), but once a species has absorbed more than the horizon,
   * older members are progressively forgotten so the centroid keeps tracking
   * where the *current* population actually sits. Without this bound, a
   * long-lived species' centroid would harden into a lifetime average and
   * stop moving at all, making later drift undetectable no matter how far the
   * population had actually shifted.
   */
  void absorb(double[] norm) {
    memberSamples++;
    double alpha = 1.0 / Math.min(memberSamples, Balance.CENTROID_HORIZON);
    for (int i = 0; i < centroid.length; i++) {
      centroid[i] += alpha * (norm[i] - centroid[i]);
    }
    members++;
    totalBirths++;
    if (members > peakPopulation) peakPopulation = members;
  }

  void recordDeath(int tick, DeathCause cause, int bornAtTick) {
    members--;
    totalDeaths++;
    totalLifetimeTicks += Math.max(0, tick - bornAtTick);
    if (cause == DeathCause.STARVATION) starvationDeaths++;
    else predationDeaths++;
    if (members <= 0) {
      members = 0;
      if (extinctionTick < 0) extinctionTick = tick;
    }
  }

  void recordKill() {
    kills++;
  }

  void sample() {
    populationHistory.add(members);
    if (members > peakPopulation) peakPopulation = members;
  }

  public boolean isExtant() {
    return members > 0;
  }

  public double distanceTo(double[] norm) {
    double sum = 0.0;
    for (int i = 0; i < centroid.length; i++) {
      double d = centroid[i] - norm[i];
      sum += d * d;
    }
    return Math.sqrt(sum / centroid.length);
  }

  /** "starvation", "predation", or "none yet" if nobody has died. */
  public String primaryCauseOfDeath() {
    if (starvationDeaths == 0 && predationDeaths == 0) return "none yet";
    return (starvationDeaths >= predationDeaths) ? "starvation" : "predation";
  }

  /** Mean age-at-death of members that have died, in ticks; 0 if nobody has died. */
  public double averageMemberLifespan() {
    return (totalDeaths == 0) ? 0.0 : (double) totalLifetimeTicks / totalDeaths;
  }

  // Getters
  public int id() { return id; }
  public String name() { return name; }
  public int color() { return color; }
  public double[] founder() { return founder; }
  public double[] centroid() { return centroid; }
  public int birthTick() { return birthTick; }
  public int parentSpeciesId() { return parentSpeciesId; }
  public int extinctionTick() { return extinctionTick; }
  public int members() { return members; }
  public int peakPopulation() { return peakPopulation; }
  public int totalBirths() { return totalBirths; }
  public int totalDeaths() { return totalDeaths; }
  public int kills() { return kills; }
  public int starvationDeaths() { return starvationDeaths; }
  public int predationDeaths() { return predationDeaths; }
  public List<Integer> populationHistory() { return populationHistory; }

  public int lifespan(int currentTick) {
    int end = (extinctionTick >= 0) ? extinctionTick : currentTick;
    return end - birthTick;
  }
}
