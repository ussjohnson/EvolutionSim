package evosim.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import evosim.engine.World;
import evosim.model.Species;
import evosim.report.HtmlReportWriter;

/**
 * The end-of-run summary screen: headline stats, an embedded population chart,
 * a species table, and buttons to save the full HTML report or start a new run.
 */
public final class ResultsPanel extends JPanel {
  private static final int SIGNIFICANT_PEAK = 2;
  private static final int MAX_CHART_LINES = 12;

  private final World world;

  public ResultsPanel(World world, Runnable onNewSimulation) {
    super(new BorderLayout(10, 10));
    this.world = world;
    setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

    add(buildHeader(), BorderLayout.NORTH);
    add(buildCenter(), BorderLayout.CENTER);
    add(buildButtons(onNewSimulation), BorderLayout.SOUTH);
  }

  private JPanel buildHeader() {
    JPanel p = new JPanel(new BorderLayout());
    JLabel title = new JLabel("Simulation Complete");
    title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
    JLabel sub = new JLabel(String.format(
        "seed %d   ·   %d ticks   ·   final pop %d   ·   %d species alive of %d ever",
        world.seed(), world.currentTick(), world.population(),
        world.species().extantCount(), world.species().totalCount()));
    sub.setForeground(new Color(0x555555));
    p.add(title, BorderLayout.NORTH);
    p.add(sub, BorderLayout.SOUTH);
    return p;
  }

  private JPanel buildCenter() {
    List<Species> significant = significantSpecies();

    int[] xTicks = toIntArray(world.stats().sampleTicks());
    int samples = xTicks.length;
    List<ChartPanel.Line> lines = new ArrayList<>();
    for (int i = 0; i < Math.min(MAX_CHART_LINES, significant.size()); i++) {
      Species s = significant.get(i);
      lines.add(new ChartPanel.Line(new Color(s.color()), leftPad(s.populationHistory(), samples)));
    }
    ChartPanel chart = new ChartPanel(xTicks, lines);
    chart.setBorder(BorderFactory.createTitledBorder("Species populations over time"));

    DefaultTableModel model = new DefaultTableModel(
        new Object[] { "Species", "Description", "Lifespan", "Peak", "Births", "Deaths", "Kills" }, 0) {
      @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    int end = world.currentTick();
    for (Species s : significant) {
      model.addRow(new Object[] {
          s.name() + (s.isExtant() ? "" : " (extinct)"),
          HtmlReportWriter.describe(s.founder()),
          s.lifespan(end) + " t", s.peakPopulation(),
          s.totalBirths(), s.totalDeaths(), s.kills() });
    }
    JTable table = new JTable(model);
    table.setRowHeight(22);

    JPanel center = new JPanel(new BorderLayout(0, 10));
    center.add(chart, BorderLayout.NORTH);
    center.add(new JScrollPane(table), BorderLayout.CENTER);
    return center;
  }

  private JPanel buildButtons(Runnable onNewSimulation) {
    JButton save = new JButton("Save HTML report…");
    save.addActionListener(e -> saveReport());
    JButton restart = new JButton("New Simulation");
    restart.addActionListener(e -> onNewSimulation.run());
    JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
    p.add(save);
    p.add(restart);
    return p;
  }

  private void saveReport() {
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new File("evolution-report.html"));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
    Path out = chooser.getSelectedFile().toPath();
    try {
      HtmlReportWriter.write(world, out);
      JOptionPane.showMessageDialog(this, "Report saved to:\n" + out);
    } catch (IOException ex) {
      JOptionPane.showMessageDialog(this, "Failed to save report:\n" + ex.getMessage(),
          "Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  private List<Species> significantSpecies() {
    List<Species> out = new ArrayList<>();
    for (Species s : world.species().all()) {
      if (s.peakPopulation() >= SIGNIFICANT_PEAK) out.add(s);
    }
    out.sort(Comparator.comparingInt(Species::peakPopulation).reversed());
    return out;
  }

  private static int[] leftPad(List<Integer> history, int target) {
    int[] out = new int[target];
    int offset = target - history.size();
    for (int i = 0; i < history.size(); i++) {
      int idx = offset + i;
      if (idx >= 0 && idx < target) out[idx] = history.get(i);
    }
    return out;
  }

  private static int[] toIntArray(List<Integer> list) {
    int[] a = new int[list.size()];
    for (int i = 0; i < a.length; i++) a[i] = list.get(i);
    return a;
  }
}
