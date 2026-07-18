package evosim.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import evosim.model.Animal;
import evosim.model.Balance;
import evosim.model.GeneType;
import evosim.model.Genome;
import evosim.model.Species;
import evosim.model.Tile;

/**
 * The per-animal decision and action logic for a single tick: perceive,
 * classify what was seen (threat / prey / kin), pick one action by priority,
 * resolve it, then pay upkeep and check for death.
 *
 * Decision priority: respond to a nearby threat (fight or flee) → scavenge
 * carrion underfoot → hunt when hungry and carnivorous → graze when hungry and
 * herbivorous → reproduce → seek a better-matching biome → wander from depleted
 * ground → rest.
 */
final class Behavior {

  void step(World w, Animal a, List<Animal> newborns) {
    Random rng = w.rng().get();
    List<Animal> seen = Perception.detect(w, a);

    Animal threat = null, prey = null;
    int threatDist = Integer.MAX_VALUE, preyDist = Integer.MAX_VALUE;
    double bestPreyScore = Double.MAX_VALUE;
    Genome ag = a.getGenome();
    double kinRange = a.n(GeneType.INCLUSIVITY) * Balance.MAX_KIN_DIST;
    boolean canHunt = a.n(GeneType.DIET) > 0.3;

    for (Animal b : seen) {
      // Same species is never a threat or prey — a hard biological boundary
      // against cannibalism, on top of the fuzzy genetic kin-distance check
      // below (which only protects close relatives within one species).
      if (b.getSpeciesId() == a.getSpeciesId()) continue;
      boolean kin = ag.distance(b.getGenome()) < kinRange;
      if (kin) continue;
      int d = cheb(w, a, b);
      // Threat: b is carnivorous enough to worry this animal.
      if (b.n(GeneType.DIET) >= a.n(GeneType.TOLERANCE) && d < threatDist) {
        threat = b;
        threatDist = d;
      }
      // Prey: a preference, not a hard cutoff — predators favor prey well
      // under their own mass (easy, safe kills) but will still go after
      // something bigger, and riskier, if nothing smaller is around. Still
      // capped at MAX_PREY_MASS_RATIO: beyond that the attack formula does
      // essentially no damage, so it's not a "risky fallback", it's a
      // guaranteed waste of every tick spent hunting instead of grazing.
      if (canHunt && b.getMass() < a.getMass() * Balance.MAX_PREY_MASS_RATIO) {
        double massRatio = b.getMass() / a.getMass();
        double score = massRatio + Balance.PREY_DISTANCE_WEIGHT * d;
        if (score < bestPreyScore) {
          bestPreyScore = score;
          prey = b;
          preyDist = d;
        }
      }
    }

    boolean acted = false;

    // 1. Threat response.
    if (threat != null) {
      double reactRange = a.n(GeneType.SKITTISH) * a.awarenessRadius() + 1;
      if (threatDist <= reactRange) {
        double massRatio = a.getMass() / threat.getMass();
        double fightScore = a.n(GeneType.DEFENSIVE) + a.n(GeneType.BRAVERY) * (massRatio - 1.0);
        if (fightScore > Balance.FIGHT_TH) {
          if (threatDist <= 1) attack(w, a, threat);
          else moveToward(w, a, threat.getX(), threat.getY());
        } else {
          moveAway(w, a, threat.getX(), threat.getY());
        }
        acted = true;
      }
    }

    // 2. Scavenge carrion underfoot — a safe, fightless meat meal for anything
    // carnivorous enough to stomach it. Recycles the biomass of animals that
    // died of starvation/old age/disaster back into the food web.
    if (!acted && a.isHungry() && a.n(GeneType.DIET) >= Balance.SCAVENGE_MIN_DIET) {
      Tile here = w.tileAt(a.getX(), a.getY());
      if (here.getCarrion() > 0) {
        double got = here.scavenge(a.grazeCapacity());
        a.addEnergy(got * Balance.CARRION_ENERGY * a.n(GeneType.DIET));
        acted = true;
      }
    }

    // 3. Hunt.
    if (!acted && a.isHungry() && prey != null && a.n(GeneType.DIET) > 0.3) {
      if (preyDist <= 1) attack(w, a, prey);
      else moveToward(w, a, prey.getX(), prey.getY());
      acted = true;
    }

    // 4. Graze.
    if (!acted && a.isHungry() && a.n(GeneType.DIET) < 0.7) {
      acted = graze(w, a);
    }

    // 5. Reproduce.
    if (!acted && a.canReproduce()) {
      acted = reproduce(w, a, newborns);
    }

    // 6. Seek a better-matching biome when idle somewhere climatically poor
    // for this animal — reuses CAMO_TEMP/CAMO_MOISTURE (the climate an
    // animal is adapted to blend into, see Animal#effectiveStealth) as its
    // habitat preference too, rather than a separate gene: the climate an
    // animal hides best in is also the one it gravitates toward.
    if (!acted) {
      Tile home = w.tileAt(a.getX(), a.getY());
      double homeMismatch = climateMismatch(a, home);
      if (homeMismatch > Balance.BIOME_SEEK_MISMATCH_TH && rng.nextDouble() < a.n(GeneType.WANDERLUST)) {
        int[] better = betterBiomeAdjacent(w, a, homeMismatch);
        if (better != null) {
          w.moveAnimal(a, better[0], better[1]);
          a.addEnergy(-a.moveCost());
          acted = true;
        }
      }
    }

    // 7. Wander from depleted ground.
    if (!acted && w.tileAt(a.getX(), a.getY()).getFoliage() < 0.05
        && rng.nextDouble() < a.n(GeneType.WANDERLUST)) {
      wander(w, a);
      acted = true;
    }

    // 8. Upkeep: metabolism, growth, death check (always runs).
    upkeep(w, a);
  }

  // --- Actions ---

  private boolean graze(World w, Animal a) {
    double got = w.tileAt(a.getX(), a.getY()).graze(a.grazeCapacity());
    if (got <= 0) {
      // Nothing here — step toward the richest adjacent tile.
      int[] best = richestAdjacent(w, a);
      if (best != null) {
        w.moveAnimal(a, best[0], best[1]);
        a.addEnergy(-a.moveCost());
      }
      return true;
    }
    a.addEnergy(got * Balance.PLANT_ENERGY * (1.0 - a.n(GeneType.DIET)));
    return true;
  }

  private void attack(World w, Animal a, Animal b) {
    w.recordFight(a.getX(), a.getY(), b.getX(), b.getY());
    double damage = a.n(GeneType.STRENGTH) * Balance.DMG_K * (a.getMass() / b.getMass())
        - b.n(GeneType.DURABILITY) * Balance.ARMOR_K;
    b.damage(Math.max(0, damage));
    if (b.getHp() <= 0) {
      // Eaten by the predator, so no carrion is left behind.
      w.animalDied(b, Species.DeathCause.PREDATION, false);
      w.species().recordKill(a.getSpeciesId());
      // Intra-guild predation (eating another carnivore) yields much less
      // usable energy than eating an herbivore — see Balance.CARNIVORE_MEAT_PENALTY.
      double trophicFactor = 1.0 - Balance.CARNIVORE_MEAT_PENALTY * b.n(GeneType.DIET);
      a.addEnergy(b.getMass() * Balance.MEAT_ENERGY * a.n(GeneType.DIET) * trophicFactor);
    }
  }

  private boolean reproduce(World w, Animal a, List<Animal> newborns) {
    int[] spot = freeAdjacent(w, a);
    if (spot == null) return false;
    Genome childGenome = a.getGenome().mutatedCopy(w.rng().get());
    Animal child = new Animal(childGenome, w.nextId());
    child.setBirthTick(w.currentTick());
    double invest = a.getEnergy() * Balance.OFFSPRING_ENERGY_FRACTION;
    a.setEnergy(a.getEnergy() - invest);
    child.setEnergy(Math.min(invest, child.maxEnergy()));
    child.setSpeciesId(w.species().classify(childGenome, w.currentTick(), a.getSpeciesId(), w.rng().get()));
    w.placeNewborn(child, spot[0], spot[1]);
    newborns.add(child);
    w.recordBirth();
    return true;
  }

  private void wander(World w, Animal a) {
    int[] spot = freeAdjacent(w, a);
    if (spot != null) {
      w.moveAnimal(a, spot[0], spot[1]);
      a.addEnergy(-a.moveCost());
    }
  }

  private void upkeep(World w, Animal a) {
    Tile t = w.tileAt(a.getX(), a.getY());
    // Climate stress is the tile's biome cost plus any seasonal winter cold.
    a.addEnergy(-a.metabolism(t.getBiome().climateStress() + w.seasonalColdStress()));
    a.grow();
    a.regen();
    if (a.getEnergy() <= 0) {
      w.animalDied(a, Species.DeathCause.STARVATION, true);
      return;
    }
    if (w.rng().get().nextDouble() < a.agingDeathChance(w.currentTick())) {
      w.animalDied(a, Species.DeathCause.OLD_AGE, true);
    }
  }

  // --- Movement helpers (one step at a time into empty tiles) ---

  private void moveToward(World w, Animal a, int tx, int ty) {
    int steps = 1 + (int) Math.round(a.n(GeneType.LAND_SPEED) * 2);
    for (int i = 0; i < steps; i++) {
      int dx = w.wrappedDeltaX(a.getX(), tx);
      int dy = w.wrappedDeltaY(a.getY(), ty);
      int nx = a.getX() + Integer.signum(dx);
      int ny = a.getY() + Integer.signum(dy);
      if (!tryStep(w, a, nx, ny)) break;
      if (a.getX() == tx && a.getY() == ty) break;
    }
  }

  private void moveAway(World w, Animal a, int tx, int ty) {
    int steps = 1 + (int) Math.round(a.n(GeneType.LAND_SPEED) * 2);
    for (int i = 0; i < steps; i++) {
      int dx = w.wrappedDeltaX(a.getX(), tx);
      int dy = w.wrappedDeltaY(a.getY(), ty);
      int nx = a.getX() - Integer.signum(dx);
      int ny = a.getY() - Integer.signum(dy);
      if (!tryStep(w, a, nx, ny)) break;
    }
  }

  /** Attempts one step to (nx, ny), wrapping around the edge first if wraparound is on. */
  private boolean tryStep(World w, Animal a, int nx, int ny) {
    if (!w.canReach(nx, ny)) return false;
    nx = w.wrapX(nx);
    ny = w.wrapY(ny);
    if (nx == a.getX() && ny == a.getY()) return false;
    if (w.residentAt(nx, ny) != null) return false;
    w.moveAnimal(a, nx, ny);
    a.addEnergy(-a.moveCost());
    return true;
  }

  /** The 8 neighbor offsets in a random order, so tie-breaks aren't always northwest. */
  private static List<int[]> shuffledNeighbors(World w) {
    List<int[]> offsets = new ArrayList<>(8);
    for (int dy = -1; dy <= 1; dy++) {
      for (int dx = -1; dx <= 1; dx++) {
        if (dx == 0 && dy == 0) continue;
        offsets.add(new int[] { dx, dy });
      }
    }
    Collections.shuffle(offsets, w.rng().get());
    return offsets;
  }

  private int[] freeAdjacent(World w, Animal a) {
    for (int[] off : shuffledNeighbors(w)) {
      int nx = a.getX() + off[0];
      int ny = a.getY() + off[1];
      if (!w.canReach(nx, ny)) continue;
      nx = w.wrapX(nx);
      ny = w.wrapY(ny);
      if (w.residentAt(nx, ny) == null) {
        return new int[] { nx, ny };
      }
    }
    return null;
  }

  /** Euclidean mismatch between an animal's adapted climate and a tile's actual climate. */
  private static double climateMismatch(Animal a, Tile t) {
    double dt = a.n(GeneType.CAMO_TEMP) - t.getTemperature();
    double dm = a.n(GeneType.CAMO_MOISTURE) - t.getMoisture();
    return Math.sqrt(dt * dt + dm * dm);
  }

  /** The empty adjacent tile with the lowest climate mismatch, if any beats the current tile's. */
  private int[] betterBiomeAdjacent(World w, Animal a, double homeMismatch) {
    int[] best = null;
    double bestMismatch = homeMismatch;
    for (int[] off : shuffledNeighbors(w)) {
      int nx = a.getX() + off[0];
      int ny = a.getY() + off[1];
      if (!w.canReach(nx, ny)) continue;
      nx = w.wrapX(nx);
      ny = w.wrapY(ny);
      if (w.residentAt(nx, ny) == null) {
        double m = climateMismatch(a, w.tileAt(nx, ny));
        if (m < bestMismatch) {
          bestMismatch = m;
          best = new int[] { nx, ny };
        }
      }
    }
    return best;
  }

  private int[] richestAdjacent(World w, Animal a) {
    int[] best = null;
    double bestFoliage = 0.05;
    for (int[] off : shuffledNeighbors(w)) {
      int nx = a.getX() + off[0];
      int ny = a.getY() + off[1];
      if (!w.canReach(nx, ny)) continue;
      nx = w.wrapX(nx);
      ny = w.wrapY(ny);
      if (w.residentAt(nx, ny) == null) {
        double f = w.tileAt(nx, ny).getFoliage();
        if (f > bestFoliage) {
          bestFoliage = f;
          best = new int[] { nx, ny };
        }
      }
    }
    return best;
  }

  private static int cheb(World w, Animal a, Animal b) {
    int dx = Math.abs(w.wrappedDeltaX(a.getX(), b.getX()));
    int dy = Math.abs(w.wrappedDeltaY(a.getY(), b.getY()));
    return Math.max(dx, dy);
  }
}
