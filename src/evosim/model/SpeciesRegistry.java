package evosim.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Assigns and tracks taxonomic species using online "leader" clustering.
 *
 * On each birth, {@link #classify} compares the genome to the centroid of every
 * extant species. If the nearest is within {@link Balance#SPECIATION_THRESHOLD}
 * it joins that species (and nudges its centroid); otherwise a brand-new species
 * is founded, named from the founder's normalized genome — the same vector the
 * distance metric uses, so related species get related names.
 *
 * Comparing against a running-mean centroid (rather than the fixed founder)
 * keeps a slowly drifting lineage as one species instead of spawning a new one
 * every few generations.
 */
public final class SpeciesRegistry {
  private final List<Species> all = new ArrayList<>();
  private int nextId = 0;

  /** Classify a genome, joining or founding a species, and return its id. */
  public int classify(Genome genome, int tick) {
    double[] norm = genome.normVector();
    Species best = null;
    double bestDist = Double.MAX_VALUE;
    for (Species s : all) {
      if (!s.isExtant()) continue;
      double d = s.distanceTo(norm);
      if (d < bestDist) {
        bestDist = d;
        best = s;
      }
    }
    if (best != null && bestDist < Balance.SPECIATION_THRESHOLD) {
      best.absorb(norm);
      return best.id();
    }
    Species founded = new Species(nextId++, NameGenerator.nameFor(norm),
        colorFromNorm(norm), norm, tick);
    founded.absorb(norm);
    all.add(founded);
    return founded.id();
  }

  public void recordDeath(int speciesId, int tick) {
    Species s = byId(speciesId);
    if (s != null) s.recordDeath(tick);
  }

  public void recordKill(int speciesId) {
    Species s = byId(speciesId);
    if (s != null) s.recordKill();
  }

  /** Append the current population of every species to its history. */
  public void sample() {
    for (Species s : all) {
      s.sample();
    }
  }

  public Species byId(int speciesId) {
    if (speciesId < 0 || speciesId >= all.size()) return null;
    Species s = all.get(speciesId);
    // ids are assigned sequentially from 0, so index == id
    return (s.id() == speciesId) ? s : lookup(speciesId);
  }

  private Species lookup(int speciesId) {
    for (Species s : all) {
      if (s.id() == speciesId) return s;
    }
    return null;
  }

  public List<Species> all() {
    return all;
  }

  public int extantCount() {
    int c = 0;
    for (Species s : all) {
      if (s.isExtant()) c++;
    }
    return c;
  }

  public int totalCount() {
    return all.size();
  }

  /** Same colour derivation as Animal (red=diet, green=fecundity, blue=speed). */
  private static int colorFromNorm(double[] norm) {
    int r = clamp255(norm[GeneType.DIET.ordinal()]);
    int g = clamp255(norm[GeneType.FECUNDITY.ordinal()]);
    int b = clamp255(norm[GeneType.LAND_SPEED.ordinal()]);
    return (r << 16) | (g << 8) | b;
  }

  private static int clamp255(double norm) {
    int v = (int) Math.round(norm * 255.0);
    if (v < 0) v = 0;
    if (v > 255) v = 255;
    return v;
  }
}
