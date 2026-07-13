package evosim.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import evosim.config.SimSettings;
import evosim.model.GeneType;

/**
 * The setup screen shown before a run. Collects the global world parameters and
 * a per-gene initial value range, assembles a {@link SimSettings}, and hands it
 * to the supplied callback when "Start Simulation" is pressed.
 */
public final class SettingsPanel extends JPanel {
  private final JSpinner width = intSpinner(40, 4, 400);
  private final JSpinner height = intSpinner(30, 4, 400);
  private final JSpinner population = intSpinner(150, 1, 100000);
  private final JSpinner fertility = doubleSpinner(0.2, 0.0, 1.0, 0.05);
  private final JSpinner duration = intSpinner(0, 0, 1000000);
  private final JSpinner seed = intSpinner(42, 0, Integer.MAX_VALUE);
  private final DefaultTableModel geneModel;

  public SettingsPanel(Consumer<SimSettings> onStart) {
    super(new BorderLayout(10, 10));
    setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

    JLabel title = new JLabel("EvolutionSim — Starting Conditions");
    title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 18f));
    add(title, BorderLayout.NORTH);

    add(buildForm(), BorderLayout.WEST);

    geneModel = buildGeneModel();
    JTable geneTable = new JTable(geneModel);
    geneTable.setRowHeight(22);
    JScrollPane scroll = new JScrollPane(geneTable);
    scroll.setBorder(BorderFactory.createTitledBorder(
        "Initial gene ranges (starting population is drawn from these)"));
    add(scroll, BorderLayout.CENTER);

    JButton start = new JButton("Start Simulation");
    start.setFont(start.getFont().deriveFont(java.awt.Font.BOLD, 14f));
    start.addActionListener(e -> onStart.accept(collect()));

    JButton reset = new JButton("Reset gene ranges");
    reset.addActionListener(e -> resetGeneRanges());

    JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
    south.add(reset);
    south.add(start);
    add(south, BorderLayout.SOUTH);
  }

  private JPanel buildForm() {
    JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
    form.setBorder(BorderFactory.createTitledBorder("World"));
    form.add(new JLabel("Map width (tiles)"));   form.add(width);
    form.add(new JLabel("Map height (tiles)"));  form.add(height);
    form.add(new JLabel("Starting population")); form.add(population);
    form.add(new JLabel("Plant fertility (0-1)")); form.add(fertility);
    form.add(new JLabel("Duration (ticks, 0 = until Stop)")); form.add(duration);
    form.add(new JLabel("Random seed"));         form.add(seed);
    JPanel wrap = new JPanel(new BorderLayout());
    wrap.add(form, BorderLayout.NORTH);
    wrap.setPreferredSize(new Dimension(300, 400));
    return wrap;
  }

  private DefaultTableModel buildGeneModel() {
    DefaultTableModel model = new DefaultTableModel(
        new Object[] { "Gene", "Start min", "Start max" }, 0) {
      @Override public boolean isCellEditable(int row, int col) { return col > 0; }
      @Override public Class<?> getColumnClass(int col) { return col == 0 ? String.class : Double.class; }
    };
    SimSettings defaults = new SimSettings();
    for (GeneType t : GeneType.ALL) {
      model.addRow(new Object[] { t.name(), defaults.startMin[t.ordinal()], defaults.startMax[t.ordinal()] });
    }
    return model;
  }

  private void resetGeneRanges() {
    SimSettings defaults = new SimSettings();
    for (GeneType t : GeneType.ALL) {
      geneModel.setValueAt(defaults.startMin[t.ordinal()], t.ordinal(), 1);
      geneModel.setValueAt(defaults.startMax[t.ordinal()], t.ordinal(), 2);
    }
  }

  private SimSettings collect() {
    SimSettings s = new SimSettings();
    s.width = (Integer) width.getValue();
    s.height = (Integer) height.getValue();
    s.population = (Integer) population.getValue();
    s.baseFertility = ((Number) fertility.getValue()).doubleValue();
    s.durationTicks = (Integer) duration.getValue();
    s.seed = ((Number) seed.getValue()).longValue();
    for (GeneType t : GeneType.ALL) {
      int i = t.ordinal();
      s.startMin[i] = ((Number) geneModel.getValueAt(i, 1)).doubleValue();
      s.startMax[i] = ((Number) geneModel.getValueAt(i, 2)).doubleValue();
    }
    s.sanitize();
    return s;
  }

  private static JSpinner intSpinner(int value, int min, int max) {
    return new JSpinner(new SpinnerNumberModel(value, min, max, 1));
  }

  private static JSpinner doubleSpinner(double value, double min, double max, double step) {
    return new JSpinner(new SpinnerNumberModel(value, min, max, step));
  }
}
