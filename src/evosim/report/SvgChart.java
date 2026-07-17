package evosim.report;

import java.util.List;

/**
 * Builds a standalone SVG line chart as a string — no charting libraries. Used
 * for the population-over-time graph in the HTML report. Each series is a
 * y-value array already aligned to the shared x-axis (left-padded with zeros for
 * species that appeared partway through the run).
 */
public final class SvgChart {
  private SvgChart() {}

  public static final class Series {
    public final String label;
    public final String colorHex; // e.g. "#3ab0ff"
    public final int[] values;

    public Series(String label, String colorHex, int[] values) {
      this.label = label;
      this.colorHex = colorHex;
      this.values = values;
    }
  }

  /**
   * @param xTicks tick number at each sample point (x-axis)
   * @param series one line per series, each values.length == xTicks.length
   */
  public static String lineChart(String title, int[] xTicks, List<Series> series,
                                 int width, int height) {
    int padL = 60, padR = 16, padT = 40, padB = 42;
    int plotW = width - padL - padR;
    int plotH = height - padT - padB;

    int n = xTicks.length;
    int maxX = (n > 0) ? xTicks[n - 1] : 1;
    if (maxX <= 0) maxX = 1;
    int maxY = 1;
    for (Series s : series) {
      for (int v : s.values) maxY = Math.max(maxY, v);
    }
    int niceMaxY = niceCeil(maxY);

    StringBuilder svg = new StringBuilder();
    svg.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ")
       .append(width).append(' ').append(height).append("' width='").append(width)
       .append("' height='").append(height).append("' font-family='sans-serif'>\n");
    svg.append("<rect width='").append(width).append("' height='").append(height)
       .append("' fill='#ffffff'/>\n");
    svg.append("<text x='").append(width / 2).append("' y='24' text-anchor='middle' "
        + "font-size='16' font-weight='bold' fill='#222'>").append(esc(title)).append("</text>\n");

    // Y gridlines + labels.
    int ySteps = 4;
    for (int i = 0; i <= ySteps; i++) {
      int yVal = niceMaxY * i / ySteps;
      double y = padT + plotH - (double) i / ySteps * plotH;
      svg.append("<line x1='").append(padL).append("' y1='").append(fmt(y))
         .append("' x2='").append(padL + plotW).append("' y2='").append(fmt(y))
         .append("' stroke='#e6e6e6'/>\n");
      svg.append("<text x='").append(padL - 8).append("' y='").append(fmt(y + 4))
         .append("' text-anchor='end' font-size='11' fill='#666'>").append(yVal).append("</text>\n");
    }

    // X axis end labels.
    svg.append("<text x='").append(padL).append("' y='").append(height - 22)
       .append("' text-anchor='start' font-size='11' fill='#666'>tick 0</text>\n");
    svg.append("<text x='").append(padL + plotW).append("' y='").append(height - 22)
       .append("' text-anchor='end' font-size='11' fill='#666'>tick ").append(maxX).append("</text>\n");
    svg.append("<text x='").append(padL + plotW / 2).append("' y='").append(height - 8)
       .append("' text-anchor='middle' font-size='11' fill='#888'>time →</text>\n");

    // Series polylines.
    for (Series s : series) {
      StringBuilder pts = new StringBuilder();
      for (int i = 0; i < n; i++) {
        double x = padL + (n == 1 ? 0 : (double) xTicks[i] / maxX * plotW);
        double y = padT + plotH - (double) s.values[i] / niceMaxY * plotH;
        if (i > 0) pts.append(' ');
        pts.append(fmt(x)).append(',').append(fmt(y));
      }
      svg.append("<polyline fill='none' stroke='").append(s.colorHex)
         .append("' stroke-width='1.8' points='").append(pts).append("'/>\n");
    }

    svg.append("</svg>");
    return svg.toString();
  }

  /** One row of {@link #lineageTree}: a species' population history plus its tree position. */
  public static final class LineageRow {
    public final String name;       // short label drawn beside the row
    public final String tooltip;    // full hover text (name, description, stats)
    public final String colorHex;
    public final int[] population;  // aligned to the shared xTicks axis
    public final int birthTick;
    public final int parentRow;     // index into the rows list, or -1 if root/parent not shown

    public LineageRow(String name, String tooltip, String colorHex, int[] population,
                       int birthTick, int parentRow) {
      this.name = name;
      this.tooltip = tooltip;
      this.colorHex = colorHex;
      this.population = population;
      this.birthTick = birthTick;
      this.parentRow = parentRow;
    }
  }

  /**
   * A branching family-tree diagram: each species is a horizontal ribbon on
   * its own row (row index order should be {@link evosim.model.SpeciesLineage#dfsOrder},
   * so a child's row sits near its parent's), thickness tracking population
   * at each sample (tapering to nothing outside its lifespan), with a
   * vertical connector back to the parent's row at the child's birth tick —
   * a branching tree over time, not a stacked-area population chart. Each
   * ribbon carries an SVG {@code <title>} so hovering shows the species'
   * info natively in-browser.
   */
  public static String lineageTree(String title, int[] xTicks, List<LineageRow> rows, int width) {
    int padL = 130, padR = 16, padT = 40, padB = 32;
    int rowHeight = 24;
    int plotW = width - padL - padR;
    int plotH = Math.max(1, rows.size()) * rowHeight;
    int height = padT + plotH + padB;

    int n = xTicks.length;
    int maxX = (n > 0) ? Math.max(1, xTicks[n - 1]) : 1;

    int maxPop = 1;
    for (LineageRow r : rows) {
      for (int v : r.population) maxPop = Math.max(maxPop, v);
    }
    double maxHalf = rowHeight / 2.0 - 2.0;
    double minHalf = 1.5;
    double thicknessScale = maxHalf / Math.sqrt(maxPop);

    StringBuilder svg = new StringBuilder();
    svg.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ")
       .append(width).append(' ').append(height).append("' width='").append(width)
       .append("' height='").append(height).append("' font-family='sans-serif'>\n");
    svg.append("<rect width='").append(width).append("' height='").append(height)
       .append("' fill='#ffffff'/>\n");
    svg.append("<text x='").append(width / 2).append("' y='24' text-anchor='middle' "
        + "font-size='16' font-weight='bold' fill='#222'>").append(esc(title)).append("</text>\n");
    svg.append("<text x='").append(padL).append("' y='").append(height - 10)
       .append("' text-anchor='start' font-size='11' fill='#666'>tick 0</text>\n");
    svg.append("<text x='").append(padL + plotW).append("' y='").append(height - 10)
       .append("' text-anchor='end' font-size='11' fill='#666'>tick ").append(maxX).append("</text>\n");

    for (int row = 0; row < rows.size(); row++) {
      LineageRow r = rows.get(row);
      double centerY = padT + row * rowHeight + rowHeight / 2.0;

      if (r.parentRow >= 0) {
        double bx = padL + (double) r.birthTick / maxX * plotW;
        double parentCenterY = padT + r.parentRow * rowHeight + rowHeight / 2.0;
        svg.append("<line x1='").append(fmt(bx)).append("' y1='").append(fmt(parentCenterY))
           .append("' x2='").append(fmt(bx)).append("' y2='").append(fmt(centerY))
           .append("' stroke='").append(r.colorHex).append("' stroke-width='1.5'/>\n");
      }

      String[] topPts = new String[n];
      String[] bottomPts = new String[n];
      for (int i = 0; i < n; i++) {
        double x = padL + (n == 1 ? 0 : (double) xTicks[i] / maxX * plotW);
        int pop = r.population[i];
        double half = (pop <= 0) ? 0.0 : Math.max(minHalf, thicknessScale * Math.sqrt(pop));
        topPts[i] = fmt(x) + "," + fmt(centerY - half);
        bottomPts[i] = fmt(x) + "," + fmt(centerY + half);
      }
      StringBuilder points = new StringBuilder(String.join(" ", topPts));
      for (int i = n - 1; i >= 0; i--) {
        points.append(' ').append(bottomPts[i]);
      }
      svg.append("<g><title>").append(esc(r.tooltip)).append("</title>")
         .append("<polygon fill='").append(r.colorHex).append("' points='").append(points).append("'/>")
         .append("</g>\n");
      svg.append("<text x='6' y='").append(fmt(centerY + 4))
         .append("' font-size='11' fill='#333'>").append(esc(r.name)).append("</text>\n");
    }

    svg.append("</svg>");
    return svg.toString();
  }

  private static int niceCeil(int v) {
    if (v <= 5) return 5;
    int mag = (int) Math.pow(10, (int) Math.log10(v));
    int lead = v / mag + 1;
    return lead * mag;
  }

  private static String fmt(double d) {
    return String.format(java.util.Locale.US, "%.1f", d);
  }

  private static String esc(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
