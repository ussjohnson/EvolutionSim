package evosim.engine;

import java.util.Random;

/**
 * Smooth 2D value noise for world generation: a coarse lattice of random
 * values spaced {@code cellSize} tiles apart, bilinearly interpolated with
 * smoothstep easing so nearby tiles vary gradually instead of independently
 * — the basis for {@code BiomeType} regions (see {@link World}'s
 * constructor). Package-private, single-purpose helper, like
 * {@link Behavior}/{@link Perception}.
 */
final class NoiseField {
  private final double[][] lattice;
  private final double cellSize;

  NoiseField(int width, int height, double cellSize, Random rng) {
    this.cellSize = cellSize;
    int cols = (int) Math.ceil(width / cellSize) + 2;
    int rows = (int) Math.ceil(height / cellSize) + 2;
    lattice = new double[rows][cols];
    for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
        lattice[y][x] = rng.nextDouble();
      }
    }
  }

  /** Sample the noise field at a tile coordinate; result is in [0,1]. */
  double sample(double x, double y) {
    double gx = x / cellSize;
    double gy = y / cellSize;
    int x0 = (int) Math.floor(gx);
    int y0 = (int) Math.floor(gy);
    double tx = smooth(gx - x0);
    double ty = smooth(gy - y0);
    double v00 = lattice[y0][x0];
    double v10 = lattice[y0][x0 + 1];
    double v01 = lattice[y0 + 1][x0];
    double v11 = lattice[y0 + 1][x0 + 1];
    double a = lerp(v00, v10, tx);
    double b = lerp(v01, v11, tx);
    return lerp(a, b, ty);
  }

  private static double smooth(double t) {
    return t * t * (3 - 2 * t);
  }

  private static double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }
}
