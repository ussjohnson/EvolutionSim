package evosim.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
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
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import evosim.engine.SimTime;
import evosim.engine.World;
import evosim.model.GeneType;
import evosim.model.Species;
import evosim.model.SpeciesLineage;
import evosim.report.HtmlReportWriter;

/**
 * The end-of-run summary screen: headline stats, a branching species
 * family-tree diagram ({@link LineageTreePanel}), a browsable taxonomy tree
 * and a detailed species table (as tabs), and buttons to save the full HTML
 * report or start a new run.
 */
public final class ResultsPanel extends JPanel {
  private static final int SIGNIFICANT_PEAK = 2;

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
        "seed %d   ·   %s (%d ticks)   ·   final pop %d   ·   %d species alive of %d ever",
        world.seed(), SimTime.format(world.currentTick()), world.currentTick(), world.population(),
        world.species().extantCount(), world.species().totalCount()));
    sub.setForeground(new Color(0x555555));
    p.add(title, BorderLayout.NORTH);
    p.add(sub, BorderLayout.SOUTH);
    return p;
  }

  private JPanel buildCenter() {
    List<Species> significant = significantSpecies();

    int[] xTicks = toIntArray(world.stats().sampleTicks());
    List<Species> lineageOrder = SpeciesLineage.dfsOrder(significant);
    LineageTreePanel chart = new LineageTreePanel(xTicks, lineageOrder, world.currentTick());
    JScrollPane chartScroll = new JScrollPane(chart);
    chartScroll.setBorder(BorderFactory.createTitledBorder("Species family tree & population over time"));
    chartScroll.setPreferredSize(new Dimension(760, 340));
    chartScroll.getVerticalScrollBar().setUnitIncrement(24);

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Trait Evolution", new TraitChartPanel(world.stats()));
    JScrollPane ecoScroll = new JScrollPane(new EcologyPanel(world));
    ecoScroll.getVerticalScrollBar().setUnitIncrement(24);
    tabs.addTab("Ecology", ecoScroll);
    tabs.addTab("Taxonomy", new SpeciesTreePanel(significant));
    tabs.addTab("Species Table", buildSpeciesTableTab(significant));

    JPanel center = new JPanel(new BorderLayout(0, 10));
    center.add(chartScroll, BorderLayout.NORTH);
    center.add(tabs, BorderLayout.CENTER);
    return center;
  }

  /**
   * The species table: sortable (click a header) and filterable (type in the
   * search box, matched against every column), with a details panel below
   * that shows a clicked species' full stats and current genome.
   */
  private JComponent buildSpeciesTableTab(List<Species> significant) {
    DefaultTableModel model = new DefaultTableModel(
        new Object[] { "Species", "Description", "Evolved From", "Lifespan", "Avg Age at Death",
            "Peak", "Births", "Deaths", "Cause of Death", "Kills" }, 0) {
      @Override public boolean isCellEditable(int r, int c) { return false; }
      @Override public Class<?> getColumnClass(int c) {
        return (c == 5 || c == 6 || c == 7 || c == 9) ? Integer.class : String.class;
      }
    };
    int end = world.currentTick();
    for (Species s : significant) {
      String parent = world.species().parentNameOf(s);
      model.addRow(new Object[] {
          s.name() + (s.isExtant() ? "" : " (extinct)"),
          HtmlReportWriter.describe(s.centroid()),
          (parent != null) ? parent : "—",
          s.lifespan(end) + " t",
          (s.totalDeaths() > 0) ? String.format("%.0f t", s.averageMemberLifespan()) : "—",
          s.peakPopulation(),
          s.totalBirths(), s.totalDeaths(), s.primaryCauseOfDeath(), s.kills() });
    }
    JTable table = new JTable(model);
    table.setRowHeight(22);
    TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    table.setRowSorter(sorter);

    JTextField search = new JTextField();
    search.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent e) { apply(); }
      @Override public void removeUpdate(DocumentEvent e) { apply(); }
      @Override public void changedUpdate(DocumentEvent e) { apply(); }
      private void apply() {
        String text = search.getText();
        sorter.setRowFilter(text.isBlank() ? null
            : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
      }
    });
    JPanel filterBar = new JPanel(new BorderLayout(6, 0));
    filterBar.add(new JLabel("Filter:"), BorderLayout.WEST);
    filterBar.add(search, BorderLayout.CENTER);
    filterBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

    JTextArea detail = new JTextArea("Click a species row to see its full details.");
    detail.setEditable(false);
    detail.setLineWrap(true);
    detail.setWrapStyleWord(true);
    detail.setFont(new Font("Monospaced", Font.PLAIN, 12));
    table.getSelectionModel().addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) return;
      int viewRow = table.getSelectedRow();
      if (viewRow < 0) return;
      Species s = significant.get(table.convertRowIndexToModel(viewRow));
      detail.setText(speciesDetailText(s));
      detail.setCaretPosition(0);
    });

    JPanel tablePanel = new JPanel(new BorderLayout());
    tablePanel.add(filterBar, BorderLayout.NORTH);
    tablePanel.add(new JScrollPane(table), BorderLayout.CENTER);

    JScrollPane detailScroll = new JScrollPane(detail);
    detailScroll.setBorder(BorderFactory.createTitledBorder("Species details"));
    detailScroll.setPreferredSize(new Dimension(200, 180));

    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tablePanel, detailScroll);
    split.setResizeWeight(0.75);
    return split;
  }

  private String speciesDetailText(Species s) {
    StringBuilder sb = new StringBuilder();
    sb.append(s.name()).append(s.isExtant() ? "" : " (extinct)").append('\n');
    sb.append(HtmlReportWriter.describe(s.centroid())).append("\n\n");
    String parent = world.species().parentNameOf(s);
    sb.append("Evolved from: ").append(parent != null ? parent : "— (founding population)").append('\n');
    sb.append("Born: tick ").append(s.birthTick());
    if (!s.isExtant()) sb.append("   Extinct: tick ").append(s.extinctionTick());
    sb.append('\n');
    sb.append(String.format("Peak population: %d%n", s.peakPopulation()));
    sb.append(String.format("Births: %d   Deaths: %d   Kills: %d%n",
        s.totalBirths(), s.totalDeaths(), s.kills()));
    if (s.totalDeaths() > 0) {
      sb.append(String.format("Avg age at death: %.0f ticks   Primary cause: %s%n",
          s.averageMemberLifespan(), s.primaryCauseOfDeath()));
    }
    sb.append("\nGenome (current centroid):\n");
    double[] c = s.centroid();
    for (GeneType t : GeneType.ALL) {
      sb.append(String.format("  %-14s %3.0f%%%n", t.name(), c[t.ordinal()] * 100));
    }
    return sb.toString();
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

  private static int[] toIntArray(List<Integer> list) {
    int[] a = new int[list.size()];
    for (int i = 0; i < a.length; i++) a[i] = list.get(i);
    return a;
  }
}
