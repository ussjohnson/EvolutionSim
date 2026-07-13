package evosim.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;

import javax.swing.JComponent;

import evosim.engine.Snapshot;

/**
 * Paints the terrarium: each tile as a green cell whose brightness tracks its
 * foliage, and each animal as a filled circle in its species colour, sized by
 * the animal's current mass. Reads only the latest immutable {@link Snapshot},
 * never the live model. Clicking a tile reports its coordinates via the
 * listener registered with {@link #onTileClick}; the currently selected tile
 * (set with {@link #setSelectedTile}) is drawn with a highlight outline.
 */
public final class MapPanel extends JComponent {
  private volatile Snapshot snap;
  private BiConsumer<Integer, Integer> tileClickListener;
  private int selectedX = -1;
  private int selectedY = -1;

  public MapPanel() {
    setPreferredSize(new Dimension(800, 600));
    setBackground(Color.BLACK);
    addMouseListener(new MouseAdapter() {
      @Override public void mouseClicked(MouseEvent e) {
        handleClick(e.getX(), e.getY());
      }
    });
  }

  public void setSnapshot(Snapshot s) {
    this.snap = s;
  }

  /** Registers a listener invoked with (tileX, tileY) when the user clicks a tile. */
  public void onTileClick(BiConsumer<Integer, Integer> listener) {
    this.tileClickListener = listener;
  }

  public void setSelectedTile(int x, int y) {
    this.selectedX = x;
    this.selectedY = y;
  }

  private void handleClick(int px, int py) {
    Snapshot s = snap;
    if (s == null || tileClickListener == null) return;
    double cell = Math.min((double) getWidth() / s.width, (double) getHeight() / s.height);
    if (cell <= 0) return;
    int tx = (int) (px / cell);
    int ty = (int) (py / cell);
    if (tx < 0 || tx >= s.width || ty < 0 || ty >= s.height) return;
    tileClickListener.accept(tx, ty);
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

    // Selection highlight.
    if (selectedX >= 0 && selectedY >= 0 && selectedX < s.width && selectedY < s.height) {
      int px = (int) (selectedX * cell);
      int py = (int) (selectedY * cell);
      int w = (int) ((selectedX + 1) * cell) - px;
      int h = (int) ((selectedY + 1) * cell) - py;
      g.setColor(Color.WHITE);
      g.setStroke(new java.awt.BasicStroke(2f));
      g.drawRect(px, py, Math.max(1, w - 1), Math.max(1, h - 1));
    }

    g.dispose();
  }
}
