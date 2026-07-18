package evosim.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.EnumSet;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import evosim.engine.StatsCollector;
import evosim.model.GeneType;

/**
 * The "Trait Evolution" results tab: the population-mean of every normalized
 * gene plotted over the whole run, with a checkbox selector so the user can
 * focus on the traits they care about. This is where the actual evolution
 * shows up — you watch, say, mean DIET climb as a lineage turns carnivorous or
 * mean SIZE fall under a shrink-to-efficiency pressure.
 */
public final class TraitChartPanel extends JPanel {
  // A sensible default view: the handful of traits with the clearest stories.
  private static final EnumSet<GeneType> DEFAULT_ON = EnumSet.of(
      GeneType.SIZE, GeneType.DIET, GeneType.LAND_SPEED, GeneType.STRENGTH,
      GeneType.STEALTH, GeneType.AWARENESS);

  private final MultiLineChart chart;
  private final MultiLineChart.Series[] byGene = new MultiLineChart.Series[GeneType.COUNT];

  public TraitChartPanel(StatsCollector stats) {
    super(new BorderLayout(8, 0));
    int[] xAxis = toIntArray(stats.sampleTicks());
    chart = new MultiLineChart(xAxis, "mean gene value (0–1)").withMax(1.0);

    for (GeneType t : GeneType.ALL) {
      double[] y = toDoubleArray(stats.geneMean(t));
      MultiLineChart.Series s = new MultiLineChart.Series(t.name(), colorFor(t), y);
      s.visible = DEFAULT_ON.contains(t);
      byGene[t.ordinal()] = s;
      chart.add(s);
    }

    add(chart, BorderLayout.CENTER);
    add(buildSelector(), BorderLayout.EAST);
  }

  private JScrollPane buildSelector() {
    JPanel list = new JPanel();
    list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
    list.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    for (GeneType t : GeneType.ALL) {
      MultiLineChart.Series s = byGene[t.ordinal()];
      JCheckBox cb = new JCheckBox(t.name(), s.visible);
      cb.setForeground(colorFor(t).darker());
      cb.addActionListener(e -> {
        s.visible = cb.isSelected();
        chart.repaint();
      });
      list.add(cb);
    }
    JScrollPane scroll = new JScrollPane(list);
    scroll.setPreferredSize(new Dimension(150, 260));
    scroll.setBorder(BorderFactory.createTitledBorder("Traits"));
    return scroll;
  }

  /** A distinct hue per gene, matching the chart line and the checkbox label. */
  static Color colorFor(GeneType t) {
    float hue = (float) t.ordinal() / GeneType.COUNT;
    return Color.getHSBColor(hue, 0.72f, 0.80f);
  }

  private static int[] toIntArray(java.util.List<Integer> list) {
    int[] a = new int[list.size()];
    for (int i = 0; i < a.length; i++) a[i] = list.get(i);
    return a;
  }

  private static double[] toDoubleArray(java.util.List<Double> list) {
    double[] a = new double[list.size()];
    for (int i = 0; i < a.length; i++) a[i] = list.get(i);
    return a;
  }
}
