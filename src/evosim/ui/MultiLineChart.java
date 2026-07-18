package evosim.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

/**
 * A lightweight multi-series line chart for the results screen: shared x-axis
 * (sample ticks), any number of named/coloured y-series, auto-scaled to the
 * largest value across all visible series, with axis labels and an inline
 * legend. Pure Swing, no dependencies — the on-screen twin of the SVG charts
 * in the HTML report.
 */
public final class MultiLineChart extends JComponent {
  /** One plotted series: a label, a colour, and a y-value per x sample. */
  public static final class Series {
    final String name;
    final Color color;
    final double[] y;
    boolean visible = true;

    public Series(String name, Color color, double[] y) {
      this.name = name;
      this.color = color;
      this.y = y;
    }
  }

  private final int[] xAxis;
  private final List<Series> series = new ArrayList<>();
  private final String yLabel;
  private double fixedMax = Double.NaN; // NaN => auto-scale

  public MultiLineChart(int[] xAxis, String yLabel) {
    this.xAxis = xAxis;
    this.yLabel = yLabel;
    setPreferredSize(new Dimension(700, 260));
    setBackground(Color.WHITE);
    setFont(new Font("SansSerif", Font.PLAIN, 11));
  }

  public MultiLineChart add(Series s) {
    series.add(s);
    return this;
  }

  /** Pin the y-axis maximum (e.g. 1.0 for normalized traits) instead of auto-scaling. */
  public MultiLineChart withMax(double max) {
    this.fixedMax = max;
    return this;
  }

  @Override
  protected void paintComponent(Graphics g0) {
    Graphics2D g = (Graphics2D) g0.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int w = getWidth(), h = getHeight();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, w, h);

    int left = 46, right = 12, top = 12, bottom = 40;
    int plotW = w - left - right, plotH = h - top - bottom;
    if (plotW <= 0 || plotH <= 0 || xAxis.length == 0) { g.dispose(); return; }

    double max = fixedMax;
    if (Double.isNaN(max)) {
      max = 1e-9;
      for (Series s : series) {
        if (!s.visible) continue;
        for (double v : s.y) max = Math.max(max, v);
      }
    }

    // Axes + gridlines.
    g.setColor(new Color(0xE0E0E0));
    g.setStroke(new BasicStroke(1f));
    for (int i = 0; i <= 4; i++) {
      int gy = top + plotH - (int) ((long) i * plotH / 4);
      g.drawLine(left, gy, left + plotW, gy);
      g.setColor(new Color(0x888888));
      g.drawString(fmt(max * i / 4), 6, gy + 4);
      g.setColor(new Color(0xE0E0E0));
    }
    g.setColor(new Color(0x555555));
    g.drawLine(left, top, left, top + plotH);
    g.drawLine(left, top + plotH, left + plotW, top + plotH);
    g.drawString(yLabel, left, top - 1 + 0);

    int n = xAxis.length;
    int xMax = xAxis[n - 1];
    // x tick labels (start / mid / end tick).
    g.setColor(new Color(0x888888));
    g.drawString("tick 0", left, top + plotH + 16);
    g.drawString("tick " + xMax, left + plotW - 54, top + plotH + 16);

    // Series lines.
    g.setStroke(new BasicStroke(1.8f));
    for (Series s : series) {
      if (!s.visible) continue;
      g.setColor(s.color);
      int prevX = -1, prevY = -1;
      int len = Math.min(n, s.y.length);
      for (int i = 0; i < len; i++) {
        int px = left + (xMax == 0 ? 0 : (int) ((long) xAxis[i] * plotW / xMax));
        int py = top + plotH - (int) (s.y[i] / max * plotH);
        if (prevX >= 0) g.drawLine(prevX, prevY, px, py);
        prevX = px;
        prevY = py;
      }
    }

    // Legend across the bottom.
    int lx = left, ly = top + plotH + 32;
    g.setFont(getFont());
    for (Series s : series) {
      if (!s.visible) continue;
      g.setColor(s.color);
      g.fillRect(lx, ly - 9, 10, 10);
      g.setColor(new Color(0x333333));
      g.drawString(s.name, lx + 14, ly);
      lx += 26 + g.getFontMetrics().stringWidth(s.name);
      if (lx > w - 80) { lx = left; ly += 15; }
    }

    g.dispose();
  }

  private static String fmt(double v) {
    if (v >= 100) return String.format("%.0f", v);
    if (v >= 1) return String.format("%.1f", v);
    return String.format("%.2f", v);
  }
}
