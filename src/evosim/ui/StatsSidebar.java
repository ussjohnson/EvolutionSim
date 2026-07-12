package evosim.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

import evosim.engine.Snapshot;

/**
 * A live "field guide" listing every extant species with a colour swatch, its
 * name, and its current population, sorted most-abundant first. Repainted each
 * frame from the latest {@link Snapshot}.
 */
public final class StatsSidebar extends JComponent {
  private volatile Snapshot snap;

  public StatsSidebar() {
    setPreferredSize(new Dimension(220, 600));
    setBackground(new Color(25, 25, 30));
    setFont(new Font("SansSerif", Font.PLAIN, 12));
  }

  public void setSnapshot(Snapshot s) {
    this.snap = s;
  }

  @Override
  protected void paintComponent(Graphics g0) {
    Graphics2D g = (Graphics2D) g0.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(new Color(25, 25, 30));
    g.fillRect(0, 0, getWidth(), getHeight());

    Snapshot s = snap;
    g.setColor(Color.WHITE);
    g.setFont(getFont().deriveFont(Font.BOLD, 13f));
    g.drawString("Species", 12, 22);

    if (s == null) {
      g.dispose();
      return;
    }

    g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
    int y = 46;
    int rowH = 20;
    int max = Math.max(0, (getHeight() - y) / rowH);
    int shown = Math.min(max, s.species.size());
    for (int i = 0; i < shown; i++) {
      Snapshot.SpeciesCount sc = s.species.get(i);
      g.setColor(new Color(sc.rgb));
      g.fillRect(12, y - 11, 12, 12);
      g.setColor(new Color(120, 120, 130));
      g.drawRect(12, y - 11, 12, 12);
      g.setColor(Color.WHITE);
      g.drawString(sc.name + "  ×" + sc.count, 32, y);
      y += rowH;
    }
    if (s.species.size() > shown) {
      g.setColor(new Color(150, 150, 160));
      g.drawString("+" + (s.species.size() - shown) + " more…", 32, y);
    }
    g.dispose();
  }
}
