package evosim.engine;

import java.util.List;
import java.util.Random;

import evosim.model.Animal;
import evosim.model.Balance;
import evosim.model.GeneType;
import evosim.model.Genome;

/**
 * The per-animal decision and action logic for a single tick: perceive,
 * classify what was seen (threat / prey / kin), pick one action by priority,
 * resolve it, then pay upkeep and check for death.
 *
 * Decision priority: respond to a nearby threat (fight or flee) → hunt when
 * hungry and carnivorous → graze when hungry and herbivorous → reproduce →
 * wander from depleted ground → rest.
 */
final class Behavior {

  void step(World w, Animal a, List<Animal> newborns) {
    Random rng = w.rng().get();
    List<Animal> seen = Perception.detect(w, a);

    Animal threat = null, prey = null;
    int threatDist = Integer.MAX_VALUE, preyDist = Integer.MAX_VALUE;
    Genome ag = a.getGenome();
    double kinRange = a.n(GeneType.INCLUSIVITY) * Balance.MAX_KIN_DIST;

    for (Animal b : seen) {
      boolean kin = ag.distance(b.getGenome()) < kinRange;
      if (kin) continue;
      int d = cheb(a, b);
      // Threat: b is carnivorous enough to worry this animal.
      if (b.n(GeneType.DIET) >= a.n(GeneType.TOLERANCE) && d < threatDist) {
        threat = b;
        threatDist = d;
      }
      // Prey: this animal is carnivorous, and b is a plausible catch.
      if (a.n(GeneType.DIET) > 0.3 && b.getMass() < a.getMass() * 1.5 && d < preyDist) {
        prey = b;
        preyDist = d;
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

    // 2. Hunt.
    if (!acted && a.isHungry() && prey != null && a.n(GeneType.DIET) > 0.3) {
      if (preyDist <= 1) attack(w, a, prey);
      else moveToward(w, a, prey.getX(), prey.getY());
      acted = true;
    }

    // 3. Graze.
    if (!acted && a.isHungry() && a.n(GeneType.DIET) < 0.7) {
      acted = graze(w, a);
    }

    // 4. Reproduce.
    if (!acted && a.canReproduce()) {
      acted = reproduce(w, a, newborns);
    }

    // 5. Wander from depleted ground.
    if (!acted && w.tileAt(a.getX(), a.getY()).getFoliage() < 0.05
        && rng.nextDouble() < a.n(GeneType.WANDERLUST)) {
      wander(w, a);
      acted = true;
    }

    // 6. Upkeep: metabolism, growth, death check (always runs).
    upkeep(w, a);
  }

  // --- Actions ---

  private boolean graze(World w, Animal a) {
    double got = w.tileAt(a.getX(), a.getY()).graze(Balance.APPETITE);
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
    double damage = a.n(GeneType.STRENGTH) * Balance.DMG_K * (a.getMass() / b.getMass())
        - b.n(GeneType.DURABILITY) * Balance.ARMOR_K;
    b.damage(Math.max(0, damage));
    if (b.getHp() <= 0) {
      b.kill();
      w.removeFromGrid(b);
      w.species().recordDeath(b.getSpeciesId(), w.currentTick());
      w.species().recordKill(a.getSpeciesId());
      a.addEnergy(b.getMass() * Balance.MEAT_ENERGY * a.n(GeneType.DIET));
    }
  }

  private boolean reproduce(World w, Animal a, List<Animal> newborns) {
    int[] spot = freeAdjacent(w, a);
    if (spot == null) return false;
    Genome childGenome = a.getGenome().mutatedCopy(w.rng().get());
    Animal child = new Animal(childGenome, w.nextId());
    double invest = a.getEnergy() * Balance.OFFSPRING_ENERGY_FRACTION;
    a.setEnergy(a.getEnergy() - invest);
    child.setEnergy(Math.min(invest, child.maxEnergy()));
    child.setSpeciesId(w.species().classify(childGenome, w.currentTick()));
    w.placeNewborn(child, spot[0], spot[1]);
    newborns.add(child);
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
    a.addEnergy(-a.metabolism());
    a.grow();
    if (a.getEnergy() <= 0) {
      a.kill();
      w.removeFromGrid(a);
      w.species().recordDeath(a.getSpeciesId(), w.currentTick());
    }
  }

  // --- Movement helpers (one step at a time into empty tiles) ---

  private void moveToward(World w, Animal a, int tx, int ty) {
    int steps = 1 + (int) Math.round(a.n(GeneType.LAND_SPEED) * 2);
    for (int i = 0; i < steps; i++) {
      int nx = a.getX() + Integer.signum(tx - a.getX());
      int ny = a.getY() + Integer.signum(ty - a.getY());
      if (!tryStep(w, a, nx, ny)) break;
      if (a.getX() == tx && a.getY() == ty) break;
    }
  }

  private void moveAway(World w, Animal a, int tx, int ty) {
    int steps = 1 + (int) Math.round(a.n(GeneType.LAND_SPEED) * 2);
    for (int i = 0; i < steps; i++) {
      int nx = a.getX() - Integer.signum(tx - a.getX());
      int ny = a.getY() - Integer.signum(ty - a.getY());
      if (!tryStep(w, a, nx, ny)) break;
    }
  }

  private boolean tryStep(World w, Animal a, int nx, int ny) {
    if (nx == a.getX() && ny == a.getY()) return false;
    if (!w.inBounds(nx, ny)) return false;
    if (w.residentAt(nx, ny) != null) return false;
    w.moveAnimal(a, nx, ny);
    a.addEnergy(-a.moveCost());
    return true;
  }

  private int[] freeAdjacent(World w, Animal a) {
    for (int dy = -1; dy <= 1; dy++) {
      for (int dx = -1; dx <= 1; dx++) {
        if (dx == 0 && dy == 0) continue;
        int nx = a.getX() + dx;
        int ny = a.getY() + dy;
        if (w.inBounds(nx, ny) && w.residentAt(nx, ny) == null) {
          return new int[] { nx, ny };
        }
      }
    }
    return null;
  }

  private int[] richestAdjacent(World w, Animal a) {
    int[] best = null;
    double bestFoliage = 0.05;
    for (int dy = -1; dy <= 1; dy++) {
      for (int dx = -1; dx <= 1; dx++) {
        if (dx == 0 && dy == 0) continue;
        int nx = a.getX() + dx;
        int ny = a.getY() + dy;
        if (w.inBounds(nx, ny) && w.residentAt(nx, ny) == null) {
          double f = w.tileAt(nx, ny).getFoliage();
          if (f > bestFoliage) {
            bestFoliage = f;
            best = new int[] { nx, ny };
          }
        }
      }
    }
    return best;
  }

  private static int cheb(Animal a, Animal b) {
    return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
  }
}
