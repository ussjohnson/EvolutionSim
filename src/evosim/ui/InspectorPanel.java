package evosim.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import evosim.engine.Snapshot;
import evosim.model.GeneType;
import evosim.report.HtmlReportWriter;

/**
 * Shows details for a clicked tile during a live run: its foliage level and,
 * if occupied, the resident animal's species, vitals, and full genome. Reads
 * only the latest immutable {@link Snapshot}, like {@link MapPanel} and
 * {@link StatsSidebar}; the selected tile is set via {@link #setSelectedTile}
 * and the panel is redrawn by calling {@link #refresh()} each frame.
 */
public final class InspectorPanel extends JPanel {
  private final JTextArea text = new JTextArea();
  private volatile Snapshot snap;
  private int selX = -1;
  private int selY = -1;

  public InspectorPanel() {
    super(new BorderLayout());
    setPreferredSize(new Dimension(240, 280));
    setBorder(BorderFactory.createTitledBorder("Inspector"));
    text.setEditable(false);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    text.setFont(new Font("Monospaced", Font.PLAIN, 12));
    text.setText("Click a tile in the map to inspect it.");
    add(new JScrollPane(text), BorderLayout.CENTER);
  }

  public void setSnapshot(Snapshot s) {
    this.snap = s;
  }

  public void setSelectedTile(int x, int y) {
    this.selX = x;
    this.selY = y;
  }

  /** Recomputes the displayed text from the latest snapshot and selection. Call once per frame. */
  public void refresh() {
    Snapshot s = snap;
    if (s == null || selX < 0 || selY < 0 || selX >= s.width || selY >= s.height) return;

    StringBuilder sb = new StringBuilder();
    sb.append("Tile (").append(selX).append(", ").append(selY).append(")\n");
    float foliage = s.foliage[selY * s.width + selX];
    sb.append(String.format("Foliage: %.0f%%%n%n", foliage * 100));

    Snapshot.Dot animal = null;
    for (Snapshot.Dot d : s.dots) {
      if (d.x == selX && d.y == selY) {
        animal = d;
        break;
      }
    }
    if (animal == null) {
      sb.append("(no animal here)");
      text.setText(sb.toString());
      text.setCaretPosition(0);
      return;
    }

    String speciesName = "unknown";
    for (Snapshot.SpeciesCount sc : s.species) {
      if (sc.id == animal.speciesId) {
        speciesName = sc.name;
        break;
      }
    }

    sb.append(speciesName).append('\n');
    sb.append(HtmlReportWriter.describe(animal.genomeNorm)).append("\n\n");
    sb.append("Age: ").append(s.tick - animal.birthTick).append(" ticks\n");
    sb.append(String.format("Mass:   %.1f / %.1f%n", animal.mass, animal.maxMass));
    sb.append(String.format("Energy: %.0f%%%n", 100.0 * animal.energy / Math.max(1e-6f, animal.maxEnergy)));
    sb.append(String.format("HP:     %.0f%%%n%n", 100.0 * animal.hp / Math.max(1e-6f, animal.maxHp)));

    sb.append("Genome:\n");
    for (GeneType t : GeneType.ALL) {
      sb.append(String.format("  %-12s %3.0f%%%n", t.name(), animal.genomeNorm[t.ordinal()] * 100));
    }

    text.setText(sb.toString());
    text.setCaretPosition(0);
  }
}
