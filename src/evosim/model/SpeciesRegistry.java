package evosim.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Assigns and tracks taxonomic species using online "leader" clustering.
 *
 * On each birth, {@link #classify} compares the genome to the centroid of every
 * extant species. If the nearest is within {@link Balance#SPECIATION_THRESHOLD}
 * it joins that species (and nudges its centroid); otherwise a brand-new species
 * is founded, named from the founder's normalized genome — the same vector the
 * distance metric uses, so related species get related names — and recorded as
 * descending from whichever species its parent animal belonged to.
 *
 * Comparing against a running-mean centroid (rather than the fixed founder)
 * keeps a slowly drifting lineage as one species instead of spawning a new one
 * every few generations.
 *
 * A founded species' colour follows the same lineage: a child inherits its
 * parent's hue plus a small random nudge (see {@link Balance#SPECIES_HUE_JITTER}),
 * so related species look like variations on a theme when viewed on the map or
 * in the taxonomy tree; a root species (no in-sim ancestor) instead gets a hue
 * spread via the golden angle from every other root, for maximal separation
 * between unrelated lineages.
 */
public final class SpeciesRegistry {
  /** No ancestor: used when founding species from the initial random population. */
  public static final int NO_PARENT = -1;

  /** Irrational angle (degrees) that spreads successive hues around the wheel with minimal repeats. */
  private static final double GOLDEN_ANGLE = 137.50776;

  private final List<Species> all = new ArrayList<>();
  private int nextId = 0;
  private int rootHueIndex = 0;

  /**
   * Classify a genome, joining or founding a species, and return its id.
   * @param parentSpeciesId the species of the animal that produced this genome
   *        (its parent, for a birth), or {@link #NO_PARENT} for the initial
   *        random population, which has no in-sim ancestor.
   * @param rng the world's seeded RNG, used only for a founded species' colour jitter.
   */
  public int classify(Genome genome, int tick, int parentSpeciesId, Random rng) {
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
    int color = colorFor(parentSpeciesId, rng);
    Species founded = new Species(nextId++, NameGenerator.nameFor(norm),
        color, norm, tick, parentSpeciesId);
    founded.absorb(norm);
    all.add(founded);
    return founded.id();
  }

  /** A new root hue (golden-angle spread) or a jittered nudge from the parent's hue. */
  private int colorFor(int parentSpeciesId, Random rng) {
    Species parent = (parentSpeciesId == NO_PARENT) ? null : byId(parentSpeciesId);
    if (parent == null) {
      double hue = (rootHueIndex++ * GOLDEN_ANGLE) % 360.0;
      return Coloring.fromHue(hue);
    }
    double parentHue = Coloring.hueOf(parent.color());
    double jitter = (rng.nextDouble() - 0.5) * 2.0 * Balance.SPECIES_HUE_JITTER;
    return Coloring.fromHue(parentHue + jitter);
  }

  public void recordDeath(int speciesId, int tick, Species.DeathCause cause, int bornAtTick) {
    Species s = byId(speciesId);
    if (s != null) s.recordDeath(tick, cause, bornAtTick);
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

  /** The parent species' name, or null if this species has no in-sim ancestor. */
  public String parentNameOf(Species s) {
    if (s.parentSpeciesId() == NO_PARENT) return null;
    Species parent = byId(s.parentSpeciesId());
    return (parent != null) ? parent.name() : null;
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
}
