package evosim.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

/**
 * Live at-a-glance charts for the running simulation, shown as the "Trends" tab
 * beside the map: a population-over-time line and a current diet-guild mix with
 * a births/deaths readout. Fed an immutable {@link Frame} each UI frame by
 * {@link SimView} (built with a defensive copy off the engine's stats series),
 * so it never touches the mutating model directly.
 */
public final class LiveChartsPanel extends JComponent {
  /** An immutable slice of the stats needed to draw one frame. */
  public static final class Frame {
    final int[] population;   // population at each sample point
    final int lastBirths, lastDeaths;
    final int herb, omni, carn;
    final double meanMass;

    public Frame(int[] population, int lastBirths, int lastDeaths,
                 int herb, int omni, int carn, double meanMass) {
      this.population = population;
      this.lastBirths = lastBirths;
      this.lastDeaths = lastDeaths;
      this.herb = herb;
      this.omni = omni;
      this.carn = carn;
      this.meanMass = meanMass;
    }
  }

  private volatile Frame frame;

  public LiveChartsPanel() {
    setPreferredSize(new Dimension(220, 300));
    setBackground(new Color(25, 25, 30));
    setFont(new Font("SansSerif", Font.PLAIN, 12));
  }

  public void update(Frame f) {
    this.frame = f;
  }

  @Override
  protected void paintComponent(Graphics g0) {
    Graphics2D g = (Graphics2D) g0.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(new Color(25, 25, 30));
    g.fillRect(0, 0, getWidth(), getHeight());

    Frame f = frame;
    int w = getWidth();
    g.setColor(Color.WHITE);
    g.setFont(getFont().deriveFont(Font.BOLD, 13f));
    g.drawString("Population", 12, 20);
    if (f == null || f.population.length == 0) {
      g.dispose();
      return;
    }

    // Population line chart.
    int chartX = 12, chartY = 30, chartW = w - 24, chartH = 120;
    int max = 1;
    for (int p : f.population) max = Math.max(max, p);
    g.setColor(new Color(45, 45, 55));
    g.drawRect(chartX, chartY, chartW, chartH);
    g.setColor(new Color(120, 200, 120));
    int n = f.population.length;
    int prevX = -1, prevY = -1;
    for (int i = 0; i < n; i++) {
      int px = chartX + (n == 1 ? chartW / 2 : (int) ((long) i * chartW / (n - 1)));
      int py = chartY + chartH - (int) ((long) f.population[i] * chartH / max);
      if (prevX >= 0) g.drawLine(prevX, prevY, px, py);
      prevX = px;
      prevY = py;
    }
    g.setColor(new Color(150, 150, 160));
    g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
    g.drawString("peak " + max, chartX + 4, chartY + 13);
    g.setColor(Color.WHITE);
    g.drawString("now " + f.population[n - 1], chartX + 4, chartY + chartH - 5);

    // Births / deaths since the last sample.
    int by = chartY + chartH + 24;
    g.setFont(getFont().deriveFont(Font.BOLD, 12f));
    g.setColor(new Color(120, 180, 240));
    g.drawString("+" + f.lastBirths + " births", 12, by);
    g.setColor(new Color(240, 120, 120));
    g.drawString("-" + f.lastDeaths + " deaths", 110, by);
    g.setColor(new Color(180, 180, 190));
    g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
    g.drawString(String.format("avg mass %.1f", f.meanMass), 12, by + 16);

    // Diet-guild mix as three horizontal bars.
    int total = Math.max(1, f.herb + f.omni + f.carn);
    int barY = by + 36;
    g.setColor(Color.WHITE);
    g.setFont(getFont().deriveFont(Font.BOLD, 12f));
    g.drawString("Diet mix", 12, barY);
    barY += 8;
    barY = dietBar(g, "Herbivore", f.herb, total, barY, new Color(90, 200, 90));
    barY = dietBar(g, "Omnivore", f.omni, total, barY, new Color(220, 200, 80));
    dietBar(g, "Carnivore", f.carn, total, barY, new Color(220, 90, 80));

    g.dispose();
  }

  private int dietBar(Graphics2D g, String label, int count, int total, int y, Color c) {
    int barW = getWidth() - 24;
    int h = 16;
    g.setColor(new Color(40, 40, 48));
    g.fillRect(12, y, barW, h);
    int fill = (int) ((long) count * barW / total);
    g.setColor(c);
    g.fillRect(12, y, fill, h);
    g.setColor(Color.WHITE);
    g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
    g.drawString(label + "  " + count, 16, y + 12);
    return y + h + 6;
  }
}
