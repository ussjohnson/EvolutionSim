package evosim.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

import javax.swing.JComponent;

/**
 * A minimal Java2D line chart for the in-app results screen: axes, a couple of
 * gridlines, and one polyline per series. (The HTML report uses {@link
 * evosim.report.SvgChart} for the shareable version; this is the live-window
 * equivalent.)
 */
public final class ChartPanel extends JComponent {
  public static final class Line {
    public final Color color;
    public final int[] values;
    public Line(Color color, int[] values) {
      this.color = color;
      this.values = values;
    }
  }

  private final int[] xTicks;
  private final List<Line> lines;

  public ChartPanel(int[] xTicks, List<Line> lines) {
    this.xTicks = xTicks;
    this.lines = lines;
    setPreferredSize(new Dimension(600, 320));
    setBackground(Color.WHITE);
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
    int maxY = 1;
    for (Line l : lines) {
      for (int v : l.values) maxY = Math.max(maxY, v);
    }

    g.setColor(new Color(0xE6E6E6));
    for (int i = 0; i <= 4; i++) {
      int y = padT + plotH - i * plotH / 4;
      g.drawLine(padL, y, padL + plotW, y);
    }
    g.setColor(new Color(0x666666));
    g.drawString("0", padL - 20, padT + plotH + 4);
    g.drawString(String.valueOf(maxY), padL - 34, padT + 8);
    g.drawString("tick " + maxX, padL + plotW - 46, h - 10);

    for (Line l : lines) {
      g.setColor(l.color);
      int prevX = -1, prevY = -1;
      for (int i = 0; i < n; i++) {
        int x = padL + (n == 1 ? 0 : xTicks[i] * plotW / maxX);
        int y = padT + plotH - l.values[i] * plotH / maxY;
        if (prevX >= 0) g.drawLine(prevX, prevY, x, y);
        prevX = x;
        prevY = y;
      }
    }
    g.dispose();
  }
}
