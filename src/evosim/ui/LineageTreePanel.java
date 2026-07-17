package evosim.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;

import evosim.model.Species;
import evosim.model.SpeciesLineage;
import evosim.report.HtmlReportWriter;

/**
 * A branching family-tree diagram: each species is a horizontal ribbon on its
 * own row (row order is {@link SpeciesLineage#dfsOrder}, so a child's row
 * always sits near its parent's), running from its birth tick to its
 * extinction tick (or the run's current tick if still alive). Ribbon
 * thickness tracks population at each sampled tick (thin at birth, swelling
 * with population, tapering to nothing at extinction), and a vertical
 * connector links a child's row back to its parent's row at the child's
 * birth tick — so the whole picture reads as a branching tree over time
 * (each row a "cactus arm"), not a stacked-area population chart. Hover a
 * row for the species' name, description, and stats.
 */
public final class LineageTreePanel extends JComponent {
  private static final int ROW_HEIGHT = 24;
  private static final int PAD_L = 130, PAD_R = 16, PAD_T = 16, PAD_B = 28;
  private static final double MAX_HALF_THICKNESS = ROW_HEIGHT / 2.0 - 2.0;
  private static final double MIN_HALF_THICKNESS = 1.5;

  private final int[] xTicks;
  private final List<Species> rows; // dfs order; list index == row
  private final Map<Integer, Integer> rowById = new HashMap<>();
  private final int currentTick;

  public LineageTreePanel(int[] xTicks, List<Species> lineageOrder, int currentTick) {
    this.xTicks = xTicks;
    this.rows = lineageOrder;
    this.currentTick = currentTick;
    for (int i = 0; i < rows.size(); i++) rowById.put(rows.get(i).id(), i);
    int plotH = Math.max(1, rows.size()) * ROW_HEIGHT;
    setPreferredSize(new Dimension(760, PAD_T + plotH + PAD_B));
    setBackground(Color.WHITE);
    setFont(new Font("SansSerif", Font.PLAIN, 12));
    setToolTipText(""); // registers with ToolTipManager; text supplied per-event below
  }

  @Override
  public String getToolTipText(MouseEvent e) {
    int row = (e.getY() - PAD_T) / ROW_HEIGHT;
    if (row < 0 || row >= rows.size()) return null;
    Species s = rows.get(row);
    String range = s.isExtant()
        ? ("born tick " + s.birthTick() + ", still alive")
        : ("tick " + s.birthTick() + "–" + s.extinctionTick());
    return "<html><b>" + esc(s.name()) + "</b>" + (s.isExtant() ? "" : " (extinct)") + "<br>"
        + esc(HtmlReportWriter.describe(s.centroid())) + "<br>"
        + range + " &middot; peak " + s.peakPopulation() + " &middot; " + s.totalBirths() + " births</html>";
  }

  @Override
  protected void paintComponent(Graphics g0) {
    Graphics2D g = (Graphics2D) g0.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int w = getWidth(), h = getHeight();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, w, h);

    int plotW = Math.max(1, w - PAD_L - PAD_R);
    int n = xTicks.length;
    int maxX = (n > 0) ? Math.max(1, xTicks[n - 1]) : 1;

    if (rows.isEmpty()) {
      g.dispose();
      return;
    }

    int[][] pops = new int[rows.size()][];
    int maxPop = 1;
    for (int r = 0; r < rows.size(); r++) {
      pops[r] = SpeciesLineage.leftPad(rows.get(r).populationHistory(), n);
      for (int v : pops[r]) maxPop = Math.max(maxPop, v);
    }
    double thicknessScale = MAX_HALF_THICKNESS / Math.sqrt(maxPop);

    g.setColor(new Color(0x999999));
    g.drawString("tick 0", PAD_L, h - 8);
    g.drawString("tick " + maxX, PAD_L + plotW - 46, h - 8);

    for (int r = 0; r < rows.size(); r++) {
      Species s = rows.get(r);
      double centerY = PAD_T + r * ROW_HEIGHT + ROW_HEIGHT / 2.0;

      // Vertical connector back to the parent's row, at this species' birth tick.
      Integer parentRow = rowById.get(s.parentSpeciesId());
      if (parentRow != null) {
        double bx = xToPixel(s.birthTick(), maxX, plotW);
        double parentCenterY = PAD_T + parentRow * ROW_HEIGHT + ROW_HEIGHT / 2.0;
        g.setColor(new Color(s.color()).darker());
        g.drawLine((int) bx, (int) parentCenterY, (int) bx, (int) centerY);
      }

      // Ribbon: thickness at each sample tracks population, tapering to 0 outside its lifespan.
      int[] pop = pops[r];
      double[] topY = new double[n];
      double[] botY = new double[n];
      double[] px = new double[n];
      for (int i = 0; i < n; i++) {
        px[i] = xToPixel(xTicks[i], maxX, plotW);
        double half = (pop[i] <= 0) ? 0.0 : Math.max(MIN_HALF_THICKNESS, thicknessScale * Math.sqrt(pop[i]));
        topY[i] = centerY - half;
        botY[i] = centerY + half;
      }
      Polygon poly = new Polygon();
      for (int i = 0; i < n; i++) poly.addPoint((int) px[i], (int) topY[i]);
      for (int i = n - 1; i >= 0; i--) poly.addPoint((int) px[i], (int) botY[i]);
      g.setColor(new Color(s.color()));
      g.fillPolygon(poly);

      g.setColor(new Color(0x333333));
      String label = s.name() + (s.isExtant() ? "" : " ✝");
      g.drawString(label, 6, (int) centerY + 4);
    }

    g.dispose();
  }

  private double xToPixel(int tick, int maxX, int plotW) {
    return PAD_L + (double) tick / maxX * plotW;
  }

  private static String esc(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
