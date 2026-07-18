package evosim.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

import evosim.engine.StatsCollector;
import evosim.engine.World;
import evosim.model.BiomeType;
import evosim.model.Species;

/**
 * The "Ecology" results tab: the whole-ecosystem story over the run — total
 * population, how the diet guilds and biome occupancy shifted, soil fertility,
 * the birth/death pulse, and a tally of what actually killed everything.
 * Everything is read from the finished run's {@link StatsCollector} and species
 * records, so there is no concurrency to worry about.
 */
public final class EcologyPanel extends JPanel {
  public EcologyPanel(World world) {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    StatsCollector stats = world.stats();
    int[] xAxis = toIntArray(stats.sampleTicks());

    add(titled("Total population over time",
        new MultiLineChart(xAxis, "animals")
            .add(new MultiLineChart.Series("Population", new Color(0x3B7DBF),
                toDoubleArray(stats.globalPopulation())))));

    add(titled("Diet-guild composition over time",
        new StackedAreaChart(xAxis,
            new String[] { "Herbivore", "Omnivore", "Carnivore" },
            new Color[] { new Color(0x5AC85A), new Color(0xDCC850), new Color(0xDC5A50) },
            toIntMatrix(stats.dietCounts(), 3))));

    add(titled("Biome occupancy over time",
        new StackedAreaChart(xAxis, StatsCollector.biomeLabels(), biomeColors(),
            toIntMatrix(stats.biomeCounts(), BiomeType.ALL.length))));

    add(titled("Average soil fertility over time",
        new MultiLineChart(xAxis, "fertility (0–1)").withMax(1.0)
            .add(new MultiLineChart.Series("Fertility", new Color(0x8A6D3B),
                toDoubleArray(stats.averageFertility())))));

    add(titled("Births & deaths per sample interval",
        new MultiLineChart(xAxis, "count / interval")
            .add(new MultiLineChart.Series("Births", new Color(0x3B7DBF),
                toDoubleArray(stats.births())))
            .add(new MultiLineChart.Series("Deaths", new Color(0xC0504D),
                toDoubleArray(stats.deaths())))));

    add(titled("What killed them (all species)", new CauseOfDeathBars(world)));
  }

  private JComponent titled(String title, JComponent chart) {
    JPanel p = new JPanel(new java.awt.BorderLayout());
    p.setBorder(BorderFactory.createTitledBorder(title));
    p.setAlignmentX(Component.LEFT_ALIGNMENT);
    p.setMaximumSize(new Dimension(Integer.MAX_VALUE, chart.getPreferredSize().height + 30));
    p.add(chart);
    return p;
  }

  /** A horizontal bar chart of cumulative deaths by cause across every species. */
  private static final class CauseOfDeathBars extends JComponent {
    private final long starvation, predation, oldAge, disaster;

    CauseOfDeathBars(World world) {
      long s = 0, p = 0, o = 0, d = 0;
      for (Species sp : world.species().all()) {
        s += sp.starvationDeaths();
        p += sp.predationDeaths();
        o += sp.oldAgeDeaths();
        d += sp.disasterDeaths();
      }
      starvation = s; predation = p; oldAge = o; disaster = d;
      setPreferredSize(new Dimension(700, 130));
      setFont(new Font("SansSerif", Font.PLAIN, 12));
    }

    @Override
    protected void paintComponent(Graphics g0) {
      Graphics2D g = (Graphics2D) g0.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, getWidth(), getHeight());
      long max = Math.max(1, Math.max(Math.max(starvation, predation), Math.max(oldAge, disaster)));
      String[] labels = { "Starvation", "Predation", "Old age", "Disaster" };
      long[] vals = { starvation, predation, oldAge, disaster };
      Color[] colors = { new Color(0xC0894D), new Color(0xC0504D),
          new Color(0x7B68A6), new Color(0xE07B39) };
      int barH = 22, gap = 8, left = 90, top = 12;
      int barMaxW = getWidth() - left - 80;
      for (int i = 0; i < labels.length; i++) {
        int y = top + i * (barH + gap);
        g.setColor(new Color(0x333333));
        g.drawString(labels[i], 8, y + 16);
        g.setColor(new Color(0xEEEEEE));
        g.fillRect(left, y, barMaxW, barH);
        g.setColor(colors[i]);
        int w = (int) ((long) barMaxW * vals[i] / max);
        g.fillRect(left, y, w, barH);
        g.setColor(new Color(0x222222));
        g.drawString(String.valueOf(vals[i]), left + w + 6, y + 16);
      }
      g.dispose();
    }
  }

  private static Color[] biomeColors() {
    Color[] c = new Color[BiomeType.ALL.length];
    for (BiomeType b : BiomeType.ALL) c[b.ordinal()] = new Color(b.tint());
    return c;
  }

  private static int[] toIntArray(List<Integer> list) {
    int[] a = new int[list.size()];
    for (int i = 0; i < a.length; i++) a[i] = list.get(i);
    return a;
  }

  private static double[] toDoubleArray(List<? extends Number> list) {
    double[] a = new double[list.size()];
    for (int i = 0; i < a.length; i++) a[i] = list.get(i).doubleValue();
    return a;
  }

  private static int[][] toIntMatrix(List<int[]> rows, int cols) {
    int[][] m = new int[rows.size()][cols];
    for (int i = 0; i < rows.size(); i++) {
      int[] r = rows.get(i);
      System.arraycopy(r, 0, m[i], 0, Math.min(cols, r.length));
    }
    return m;
  }
}
