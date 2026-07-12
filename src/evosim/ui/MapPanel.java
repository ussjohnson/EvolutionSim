package evosim.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

import evosim.engine.Snapshot;

/**
 * Paints the terrarium: each tile as a green cell whose brightness tracks its
 * foliage, and each animal as a filled circle in its species colour, sized by
 * the animal's current mass. Reads only the latest immutable {@link Snapshot},
 * never the live model.
 */
public final class MapPanel extends JComponent {
  private volatile Snapshot snap;

  public MapPanel() {
    setPreferredSize(new Dimension(800, 600));
    setBackground(Color.BLACK);
  }

  public void setSnapshot(Snapshot s) {
    this.snap = s;
  }

  @Override
  protected void paintComponent(Graphics g0) {
    Graphics2D g = (Graphics2D) g0.create();
    g.setColor(new Color(15, 15, 20));
    g.fillRect(0, 0, getWidth(), getHeight());

    Snapshot s = snap;
    if (s == null) {
      g.dispose();
      return;
    }

    double cell = Math.min((double) getWidth() / s.width, (double) getHeight() / s.height);
    if (cell <= 0) {
      g.dispose();
      return;
    }

    // Tiles.
    for (int y = 0; y < s.height; y++) {
      for (int x = 0; x < s.width; x++) {
        float f = s.foliage[y * s.width + x];
        if (f < 0) f = 0;
        if (f > 1) f = 1;
        int green = (int) (35 + f * 170);
        g.setColor(new Color(20, green, 30));
        int px = (int) (x * cell);
        int py = (int) (y * cell);
        int w = (int) ((x + 1) * cell) - px;
        int h = (int) ((y + 1) * cell) - py;
        g.fillRect(px, py, w, h);
      }
    }

    // Animals.
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    for (Snapshot.Dot d : s.dots) {
      double sizeFrac = 0.35 + 0.55 * Math.min(1.0, d.mass / 10.0); // SIZE gene maxes at 10
      double r = cell * sizeFrac;
      double cx = d.x * cell + cell / 2.0;
      double cy = d.y * cell + cell / 2.0;
      g.setColor(new Color(d.rgb));
      g.fillOval((int) (cx - r / 2), (int) (cy - r / 2), (int) r, (int) r);
    }
    g.dispose();
  }
}
