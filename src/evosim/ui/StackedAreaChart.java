package evosim.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;

import javax.swing.JComponent;

/**
 * A normalized stacked-area chart: at each x sample the bands sum to the full
 * height, showing how a whole (e.g. the population) is composed of parts (diet
 * guilds, biomes) over time. Used on the results Ecology tab.
 */
public final class StackedAreaChart extends JComponent {
  private final int[] xAxis;
  private final String[] labels;
  private final Color[] colors;
  private final int[][] values; // [sample][band]

  public StackedAreaChart(int[] xAxis, String[] labels, Color[] colors, int[][] values) {
    this.xAxis = xAxis;
    this.labels = labels;
    this.colors = colors;
    this.values = values;
    setPreferredSize(new Dimension(700, 220));
    setBackground(Color.WHITE);
    setFont(new Font("SansSerif", Font.PLAIN, 11));
  }

  @Override
  protected void paintComponent(Graphics g0) {
    Graphics2D g = (Graphics2D) g0.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int w = getWidth(), h = getHeight();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, w, h);

    int left = 12, right = 12, top = 12, bottom = 34;
    int plotW = w - left - right, plotH = h - top - bottom;
    int n = xAxis.length;
    if (plotW <= 0 || plotH <= 0 || n == 0) { g.dispose(); return; }
    int xMax = Math.max(1, xAxis[n - 1]);
    int bands = labels.length;

    // Precompute cumulative fractions per sample (bottom→top of each band).
    // Draw each band as a polygon between its lower and upper cumulative edges.
    double[][] upper = new double[n][bands];
    for (int i = 0; i < n; i++) {
      int total = 0;
      for (int b = 0; b < bands; b++) total += values[i][b];
      double cum = 0;
      for (int b = 0; b < bands; b++) {
        cum += (total == 0) ? 0 : (double) values[i][b] / total;
        upper[i][b] = cum;
      }
    }

    for (int b = 0; b < bands; b++) {
      Polygon poly = new Polygon();
      for (int i = 0; i < n; i++) {
        int px = left + (int) ((long) xAxis[i] * plotW / xMax);
        int py = top + plotH - (int) (upper[i][b] * plotH);
        poly.addPoint(px, py);
      }
      for (int i = n - 1; i >= 0; i--) {
        int px = left + (int) ((long) xAxis[i] * plotW / xMax);
        double lowerEdge = (b == 0) ? 0 : upper[i][b - 1];
        int py = top + plotH - (int) (lowerEdge * plotH);
        poly.addPoint(px, py);
      }
      g.setColor(colors[b]);
      g.fillPolygon(poly);
    }

    // Legend.
    int lx = left, ly = top + plotH + 24;
    for (int b = 0; b < bands; b++) {
      g.setColor(colors[b]);
      g.fillRect(lx, ly - 9, 10, 10);
      g.setColor(new Color(0x333333));
      g.drawString(labels[b], lx + 14, ly);
      lx += 26 + g.getFontMetrics().stringWidth(labels[b]);
    }
    g.dispose();
  }
}
