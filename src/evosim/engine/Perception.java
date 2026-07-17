package evosim.engine;

import java.util.ArrayList;
import java.util.List;

import evosim.model.Animal;
import evosim.model.Balance;
import evosim.model.GeneType;
import evosim.model.Tile;

/**
 * Sensing: which other animals a given animal can currently detect. Only the
 * tiles within the perceiver's awareness radius are scanned (a bounded
 * Chebyshev window), so this is O(n · r²) rather than an O(n²) all-pairs scan —
 * with one resident per tile the window is cheap to walk.
 *
 * Whether a target is actually noticed is a contest between the perceiver's
 * SENSITIVITY (plus random slop) and the target's effective stealth, degraded
 * by distance.
 */
final class Perception {
  private Perception() {}

  static List<Animal> detect(World w, Animal a) {
    int r = a.awarenessRadius();
    double sensitivity = a.n(GeneType.SENSITIVITY);
    List<Animal> found = new ArrayList<>();
    int ax = a.getX();
    int ay = a.getY();
    for (int dy = -r; dy <= r; dy++) {
      for (int dx = -r; dx <= r; dx++) {
        if (dx == 0 && dy == 0) continue;
        int nx = ax + dx;
        int ny = ay + dy;
        if (!w.canReach(nx, ny)) continue;
        nx = w.wrapX(nx);
        ny = w.wrapY(ny);
        Tile tile = w.tileAt(nx, ny);
        Animal b = tile.getResident();
        if (b == null || !b.isAlive()) continue;

        int cheb = Math.max(Math.abs(dx), Math.abs(dy));
        double falloff = 1.0 + (double) cheb / (r + 1); // farther = harder to see
        double noise = (w.rng().nextDouble() - 0.5) * Balance.PERCEPTION_NOISE;
        double stealth = b.effectiveStealth(tile.getTemperature(), tile.getMoisture());
        double detectScore = sensitivity + noise - stealth * falloff;
        if (detectScore > 0) {
          found.add(b);
        }
      }
    }
    return found;
  }
}
