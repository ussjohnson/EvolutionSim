package evosim.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.function.BiConsumer;
import java.util.function.DoubleConsumer;

import javax.swing.JComponent;

import evosim.engine.Snapshot;
import evosim.model.BiomeType;
import evosim.model.GeneType;

/**
 * Paints the terrarium: each tile is tinted by its {@link BiomeType} and
 * shaded by its foliage level (darker toward bare ground, full tint at lush
 * foliage). Each animal is filled in its species' colour (species identity,
 * not the individual's own genome — see {@code World#snapshot()}), sized by
 * its current mass, shaped by its DIET (circle/diamond/triangle for
 * herbivore/omnivore/carnivore) with a DIET-coloured outline (green through
 * red) as a second, redundant reading of diet, and faded toward transparent
 * by its effective stealth on the current tile (capped at
 * {@link #MIN_OPACITY} so even the stealthiest animal stays visible) — so
 * the dot hints at what the animal is like on top of colour identifying its
 * species. Any attack that happened this tick ({@link Snapshot.Fight}) is
 * drawn as a red line between attacker and target with a small "clash" spark
 * at the midpoint. Reads only the latest immutable {@link Snapshot}, never
 * the live model.
 *
 * Tile size is a fixed {@link #BASE_CELL} pixels scaled by {@link #getZoom},
 * not "fit to container" — so the panel's preferred size grows with zoom and
 * a surrounding {@link javax.swing.JScrollPane} handles panning once the
 * rendered map exceeds the viewport. Clicking a tile reports its coordinates
 * via the listener registered with {@link #onTileClick}; the currently
 * selected tile (set with {@link #setSelectedTile}) is drawn with a
 * highlight outline. The mouse wheel zooms in/out (see {@link #onZoomChanged}
 * for keeping an external zoom control in sync).
 */
public final class MapPanel extends JComponent {
  private static final double BASE_CELL = 20.0; // pixels per tile at zoom = 1.0
  private static final double MIN_ZOOM = 0.25;
  private static final double MAX_ZOOM = 4.0;
  /** Even a maximally stealthy animal renders at least this opaque. */
  private static final double MIN_OPACITY = 0.5;

  private volatile Snapshot snap;
  private BiConsumer<Integer, Integer> tileClickListener;
  private BiConsumer<Integer, Integer> pointerListener; // press + drag, for god-tool brushes
  private DoubleConsumer zoomListener;
  private double zoom = 1.0;
  private int selectedX = -1;
  private int selectedY = -1;
  private int brushRadius = 0;      // >0 draws a brush cursor for the active tool
  private int hoverX = -1, hoverY = -1; // pixel position of the cursor, for the brush ring

  public MapPanel() {
    setBackground(Color.BLACK);
    MouseAdapter mouse = new MouseAdapter() {
      @Override public void mouseClicked(MouseEvent e) { handleClick(e.getX(), e.getY()); }
      @Override public void mousePressed(MouseEvent e) { handlePointer(e.getX(), e.getY()); }
      @Override public void mouseDragged(MouseEvent e) {
        hoverX = e.getX(); hoverY = e.getY();
        handlePointer(e.getX(), e.getY());
      }
      @Override public void mouseMoved(MouseEvent e) {
        hoverX = e.getX(); hoverY = e.getY();
        if (brushRadius > 0) repaint();
      }
      @Override public void mouseExited(MouseEvent e) { hoverX = hoverY = -1; repaint(); }
    };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
    addMouseWheelListener(e -> {
      double factor = Math.pow(1.1, -e.getWheelRotation());
      setZoom(zoom * factor);
    });
  }

  public void setSnapshot(Snapshot s) {
    boolean sizeChanged = (snap == null) || (s != null && (s.width != snap.width || s.height != snap.height));
    this.snap = s;
    if (sizeChanged) {
      revalidate();
    }
  }

  /** Registers a listener invoked with (tileX, tileY) when the user clicks a tile. */
  public void onTileClick(BiConsumer<Integer, Integer> listener) {
    this.tileClickListener = listener;
  }

  /** Registers a listener invoked with (tileX, tileY) on mouse press and while dragging — used to paint god-tool brushes. */
  public void onTilePointer(BiConsumer<Integer, Integer> listener) {
    this.pointerListener = listener;
  }

  /** Sets the brush cursor radius in tiles (0 hides it, e.g. for the Inspect tool). */
  public void setBrushRadius(int r) {
    this.brushRadius = Math.max(0, r);
    repaint();
  }

  /** Registers a listener invoked whenever the zoom level changes, e.g. via the mouse wheel. */
  public void onZoomChanged(DoubleConsumer listener) {
    this.zoomListener = listener;
  }

  public double getZoom() {
    return zoom;
  }

  public void setZoom(double z) {
    double clamped = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    if (clamped == zoom) return;
    zoom = clamped;
    revalidate();
    repaint();
    if (zoomListener != null) zoomListener.accept(zoom);
  }

  public void setSelectedTile(int x, int y) {
    this.selectedX = x;
    this.selectedY = y;
  }

  @Override
  public Dimension getPreferredSize() {
    Snapshot s = snap;
    double cell = BASE_CELL * zoom;
    if (s == null) return new Dimension((int) (40 * cell), (int) (30 * cell));
    return new Dimension(Math.max(1, (int) Math.round(s.width * cell)),
        Math.max(1, (int) Math.round(s.height * cell)));
  }

  private void handleClick(int px, int py) {
    Snapshot s = snap;
    if (s == null || tileClickListener == null) return;
    double cell = BASE_CELL * zoom;
    int tx = (int) (px / cell);
    int ty = (int) (py / cell);
    if (tx < 0 || tx >= s.width || ty < 0 || ty >= s.height) return;
    tileClickListener.accept(tx, ty);
  }

  private void handlePointer(int px, int py) {
    Snapshot s = snap;
    if (s == null || pointerListener == null) return;
    double cell = BASE_CELL * zoom;
    int tx = (int) (px / cell);
    int ty = (int) (py / cell);
    if (tx < 0 || tx >= s.width || ty < 0 || ty >= s.height) return;
    pointerListener.accept(tx, ty);
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

    double cell = BASE_CELL * zoom;

    // Tiles.
    for (int y = 0; y < s.height; y++) {
      for (int x = 0; x < s.width; x++) {
        float f = s.foliage[y * s.width + x];
        if (f < 0) f = 0;
        if (f > 1) f = 1;
        int tint = BiomeType.ALL[s.biome[y * s.width + x]].tint();
        double lightness = 0.5 + 0.5 * f; // bare ground is a darker shade of the biome tint
        int r = clampByte((int) (((tint >> 16) & 0xFF) * lightness));
        int gr = clampByte((int) (((tint >> 8) & 0xFF) * lightness));
        int b = clampByte((int) ((tint & 0xFF) * lightness));
        g.setColor(new Color(r, gr, b));
        int px = (int) (x * cell);
        int py = (int) (y * cell);
        int w = (int) ((x + 1) * cell) - px;
        int h = (int) ((y + 1) * cell) - py;
        g.fillRect(px, py, w, h);
      }
    }

    // Carrion: a small dark-red carcass mark on tiles holding decaying meat.
    if (s.carrion != null) {
      for (int y = 0; y < s.height; y++) {
        for (int x = 0; x < s.width; x++) {
          float c = s.carrion[y * s.width + x];
          if (c <= 0) continue;
          double px = x * cell, py = y * cell;
          int a = clampByte(60 + (int) Math.min(150, c * 40));
          g.setColor(new Color(120, 30, 30, a));
          double m = cell * 0.28;
          g.fillOval((int) (px + cell / 2 - m / 2), (int) (py + cell / 2 - m / 2), (int) m, (int) m);
        }
      }
    }

    // Animals.
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    for (Snapshot.Dot d : s.dots) {
      double sizeFrac = 0.35 + 0.55 * Math.min(1.0, d.mass / 10.0); // SIZE gene maxes at 10
      double r = cell * sizeFrac;
      double cx = d.x * cell + cell / 2.0;
      double cy = d.y * cell + cell / 2.0;
      double stealthNorm = Math.max(0.0, Math.min(1.0, d.stealth));
      double opacity = 1.0 - (1.0 - MIN_OPACITY) * stealthNorm;
      int alpha = (int) Math.round(opacity * 255);
      double diet = d.genomeNorm[GeneType.DIET.ordinal()];
      Shape shape = animalShape(cx, cy, r, diet);

      Color base = new Color(d.rgb);
      g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
      g.fill(shape);

      Color outline = dietOutlineColor(diet);
      g.setColor(new Color(outline.getRed(), outline.getGreen(), outline.getBlue(), alpha));
      g.setStroke(new BasicStroke(1.5f));
      g.draw(shape);
    }

    // Fight indicator: a line between combatants plus a "clash" mark at the midpoint.
    g.setStroke(new BasicStroke(2f));
    for (Snapshot.Fight f : s.fights) {
      double ax = f.ax * cell + cell / 2.0, ay = f.ay * cell + cell / 2.0;
      double tx = f.tx * cell + cell / 2.0, ty = f.ty * cell + cell / 2.0;
      g.setColor(new Color(255, 60, 40, 200));
      g.draw(new java.awt.geom.Line2D.Double(ax, ay, tx, ty));
      double mx = (ax + tx) / 2.0, my = (ay + ty) / 2.0;
      double spark = cell * 0.22;
      g.setColor(new Color(255, 210, 40, 230));
      g.draw(new java.awt.geom.Line2D.Double(mx - spark, my - spark, mx + spark, my + spark));
      g.draw(new java.awt.geom.Line2D.Double(mx - spark, my + spark, mx + spark, my - spark));
    }

    // Wildfire: a flickering orange overlay over burning tiles (drawn over
    // animals since fire is the dominant event where it's happening).
    if (s.fire != null) {
      boolean flick = (s.tick & 1) == 0;
      for (int y = 0; y < s.height; y++) {
        for (int x = 0; x < s.width; x++) {
          if (!s.fire[y * s.width + x]) continue;
          int px = (int) (x * cell), py = (int) (y * cell);
          int w = (int) ((x + 1) * cell) - px, h = (int) ((y + 1) * cell) - py;
          g.setColor(new Color(255, flick ? 140 : 90, 20, 190));
          g.fillRect(px, py, w, h);
          g.setColor(new Color(255, 230, 120, 220));
          g.fillOval(px + w / 4, py + h / 4, Math.max(2, w / 2), Math.max(2, h / 2));
        }
      }
    }

    // Brush cursor: a ring showing the reach of the active god-tool.
    if (brushRadius > 0 && hoverX >= 0 && hoverY >= 0) {
      int tx = (int) (hoverX / cell), ty = (int) (hoverY / cell);
      double ccx = tx * cell + cell / 2.0, ccy = ty * cell + cell / 2.0;
      double rr = (brushRadius + 0.5) * cell;
      g.setStroke(new BasicStroke(2f));
      g.setColor(new Color(255, 255, 255, 180));
      g.draw(new Ellipse2D.Double(ccx - rr, ccy - rr, rr * 2, rr * 2));
    }

    // Selection highlight.
    if (selectedX >= 0 && selectedY >= 0 && selectedX < s.width && selectedY < s.height) {
      int px = (int) (selectedX * cell);
      int py = (int) (selectedY * cell);
      int w = (int) ((selectedX + 1) * cell) - px;
      int h = (int) ((selectedY + 1) * cell) - py;
      g.setColor(Color.WHITE);
      g.setStroke(new BasicStroke(2f));
      g.drawRect(px, py, Math.max(1, w - 1), Math.max(1, h - 1));
    }

    g.dispose();
  }

  /**
   * A shape centered at (cx, cy) with bounding-box side {@code r}, chosen by
   * DIET: circle (herbivore), diamond (omnivore), or triangle (carnivore) —
   * the same silhouette convention as a predator/prey field guide, so diet
   * is readable without decoding a colour channel. Returned as a
   * {@link Shape} so the caller can both fill and stroke the same outline
   * (see {@link #dietOutlineColor}).
   */
  private static Shape animalShape(double cx, double cy, double r, double diet) {
    double half = r / 2.0;
    double left = cx - half, top = cy - half, right = cx + half, bottom = cy + half;
    if (diet < 0.33) {
      return new Ellipse2D.Double(left, top, r, r);
    }
    Polygon p = new Polygon();
    if (diet < 0.66) {
      p.addPoint((int) cx, (int) top);
      p.addPoint((int) right, (int) cy);
      p.addPoint((int) cx, (int) bottom);
      p.addPoint((int) left, (int) cy);
    } else {
      p.addPoint((int) cx, (int) top);
      p.addPoint((int) right, (int) bottom);
      p.addPoint((int) left, (int) bottom);
    }
    return p;
  }

  /**
   * Diet as a colour, independent of species identity: green (herbivore)
   * through to red (carnivore), drawn as a stroke around the shape's fill —
   * a second, redundant encoding of diet (alongside shape) that reads at a
   * glance without shrinking the map to make out silhouettes.
   */
  private static Color dietOutlineColor(double diet) {
    float hue = (float) ((1.0 - diet) / 3.0); // 0 = red (carnivore) .. 1/3 = green (herbivore)
    return Color.getHSBColor(hue, 0.85f, 0.9f);
  }

  private static int clampByte(int v) {
    return Math.max(0, Math.min(255, v));
  }
}
