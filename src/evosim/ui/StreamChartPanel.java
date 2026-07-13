package evosim.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.List;

import javax.swing.JComponent;

/**
 * A stacked-area ("stream") chart for the live-window results screen: each
 * species is a filled band whose thickness is its population, stacked in the
 * order given (pass lineage/depth-first order so a species' band sits beside
 * its parent's). The HTML report's equivalent is {@link
 * evosim.report.SvgChart#streamChart}.
 */
public final class StreamChartPanel extends JComponent {
  public static final class Band {
    public final Color color;
    public final int[] values;
    public Band(Color color, int[] values) {
      this.color = color;
      this.values = values;
    }
  }

  private final int[] xTicks;
  private final List<Band> bands;

  public StreamChartPanel(int[] xTicks, List<Band> bands) {
    this.xTicks = xTicks;
    this.bands = bands;
    setPreferredSize(new Dimension(600, 320));
    setBackground(Color.WHITE);
    setFont(new Font("SansSerif", Font.PLAIN, 12));
  }

  @Override
  protected void paintComponent(Graphics g0) {
    Graphics2D g = (Graphics2D) g0.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int w = getWidth(), h = getHeight();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, w, h);

    int padL = 48, padR = 12, padT = 12, padB = 28;
    int plotW = w - padL - padR;
    int plotH = h - padT - padB;

    int n = xTicks.length;
    int maxX = (n > 0) ? Math.max(1, xTicks[n - 1]) : 1;

    int[] cumForMax = new int[n];
    int maxTotal = 1;
    for (Band b : bands) {
      for (int i = 0; i < n; i++) {
        cumForMax[i] += b.values[i];
        maxTotal = Math.max(maxTotal, cumForMax[i]);
      }
    }

    g.setColor(new Color(0xE6E6E6));
    for (int i = 0; i <= 4; i++) {
      int y = padT + plotH - i * plotH / 4;
      g.drawLine(padL, y, padL + plotW, y);
    }
    g.setColor(new Color(0x666666));
    g.drawString("0", padL - 20, padT + plotH + 4);
    g.drawString(String.valueOf(maxTotal), padL - 40, padT + 8);
    g.drawString("tick " + maxX, padL + plotW - 46, h - 10);

    int[] base = new int[n];
    for (Band b : bands) {
      int[] xs = new int[2 * n];
      int[] ys = new int[2 * n];
      for (int i = 0; i < n; i++) {
        int x = padL + (n == 1 ? 0 : xTicks[i] * plotW / maxX);
        xs[i] = x;
        ys[i] = padT + plotH - (base[i] + b.values[i]) * plotH / maxTotal;
        xs[2 * n - 1 - i] = x;
        ys[2 * n - 1 - i] = padT + plotH - base[i] * plotH / maxTotal;
      }
      g.setColor(b.color);
      g.fillPolygon(new Polygon(xs, ys, 2 * n));
      g.setColor(b.color.darker());
      g.drawPolygon(new Polygon(xs, ys, 2 * n));
      for (int i = 0; i < n; i++) base[i] += b.values[i];
    }
    g.dispose();
  }
}
