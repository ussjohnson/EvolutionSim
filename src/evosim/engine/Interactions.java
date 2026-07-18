package evosim.engine;

import java.util.function.Consumer;

import evosim.model.Genome;
import evosim.model.Tile;

/**
 * Factories for the interactive "god-tools" a user can apply to a running
 * world: localized brushes (rain, drought, spawn, smite, fire) and one-shot
 * global events (meteor, bloom). Each returns a {@link Consumer}&lt;World&gt;
 * that {@link World#enqueue} runs on the engine thread at the top of a tick, so
 * user edits mutate the model on the same single thread as the simulation.
 */
public final class Interactions {
  private Interactions() {}

  /** Rain: raise foliage and soil fertility in a disc, greening the land. */
  public static Consumer<World> rain(int cx, int cy, int radius, double strength) {
    return w -> forEachInDisc(w, cx, cy, radius, (t, falloff) -> {
      t.setFoliage(Math.min(Tile.MAX_FOLIAGE, t.getFoliage() + strength * falloff));
      t.fertilize(0.15 * strength * falloff);
    });
  }

  /** Drought: strip foliage and degrade fertility in a disc. */
  public static Consumer<World> drought(int cx, int cy, int radius, double strength) {
    return w -> forEachInDisc(w, cx, cy, radius, (t, falloff) -> {
      t.setFoliage(Math.max(0, t.getFoliage() - strength * falloff));
      t.setFertility(Math.max(0, t.getFertility() - 0.2 * strength * falloff));
    });
  }

  /** Smite: kill every animal in a disc (a plague/meteor casualty). */
  public static Consumer<World> smite(int cx, int cy, int radius) {
    return w -> {
      for (int y = cy - radius; y <= cy + radius; y++) {
        for (int x = cx - radius; x <= cx + radius; x++) {
          if (!w.inBounds(x, y)) continue;
          if (dist2(x, y, cx, cy) <= radius * radius) w.killAt(x, y);
        }
      }
    };
  }

  /**
   * Spawn life: seed up to {@code count} fresh, fully-random animals onto empty
   * tiles near the click. Genomes are drawn from the world's own seeded RNG on
   * the engine thread, so no randomness crosses the UI/engine boundary.
   */
  public static Consumer<World> spawn(int cx, int cy, int radius, int count) {
    return w -> {
      int placed = 0;
      for (int y = cy - radius; y <= cy + radius && placed < count; y++) {
        for (int x = cx - radius; x <= cx + radius && placed < count; x++) {
          if (!w.inBounds(x, y)) continue;
          if (dist2(x, y, cx, cy) <= radius * radius
              && w.spawnAnimal(Genome.random(w.rng().get()), x, y) != null) {
            placed++;
          }
        }
      }
    };
  }

  /** Fire: ignite a wildfire at the click that then spreads on its own. */
  public static Consumer<World> fire(int cx, int cy) {
    return w -> w.ignite(cx, cy);
  }

  /** Meteor: a scorching impact — kills, strips the ground bare, and sets the rim alight. */
  public static Consumer<World> meteor(int cx, int cy, int radius) {
    return w -> {
      for (int y = cy - radius; y <= cy + radius; y++) {
        for (int x = cx - radius; x <= cx + radius; x++) {
          if (!w.inBounds(x, y)) continue;
          double d2 = dist2(x, y, cx, cy);
          if (d2 > radius * radius) continue;
          w.killAt(x, y);
          Tile t = w.tileAt(x, y);
          t.setFoliage(0);
          t.setFertility(Math.max(0, t.getFertility() - 0.4));
          if (d2 > (radius - 1) * (radius - 1)) w.ignite(x, y); // ring of fire at the rim
        }
      }
    };
  }

  /** Bloom: a global surge of plant growth across the whole map. */
  public static Consumer<World> bloom(double strength) {
    return w -> {
      for (int y = 0; y < w.height(); y++) {
        for (int x = 0; x < w.width(); x++) {
          Tile t = w.tileAt(x, y);
          t.setFoliage(Math.min(Tile.MAX_FOLIAGE, t.getFoliage() + strength));
          t.fertilize(0.1 * strength);
        }
      }
    };
  }

  // --- helpers ---

  private interface TileOp { void apply(Tile t, double falloff); }

  private static void forEachInDisc(World w, int cx, int cy, int radius, TileOp op) {
    int r2 = radius * radius;
    for (int y = cy - radius; y <= cy + radius; y++) {
      for (int x = cx - radius; x <= cx + radius; x++) {
        if (!w.inBounds(x, y)) continue;
        double d2 = dist2(x, y, cx, cy);
        if (d2 > r2) continue;
        double falloff = 1.0 - Math.sqrt(d2) / (radius + 1); // 1 at center, tapering to the rim
        op.apply(w.tileAt(x, y), falloff);
      }
    }
  }

  private static double dist2(int x, int y, int cx, int cy) {
    double dx = x - cx, dy = y - cy;
    return dx * dx + dy * dy;
  }
}
