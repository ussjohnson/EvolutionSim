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

  /**
   * A stacked-area ("stream") chart: each series is a filled band whose
   * thickness is its population, stacked baseline-0 in the given order. Pass
   * series in {@link evosim.model.SpeciesLineage#dfsOrder} so a species'
   * band sits directly adjacent to its parent's, visually showing the split
   * at the tick its population first becomes non-zero.
   */
  public static String streamChart(String title, int[] xTicks, List<Series> seriesStackOrder,
                                    int width, int height) {
    int padL = 60, padR = 16, padT = 40, padB = 42;
    int plotW = width - padL - padR;
    int plotH = height - padT - padB;

    int n = xTicks.length;
    int maxX = (n > 0) ? xTicks[n - 1] : 1;
    if (maxX <= 0) maxX = 1;

    int[] cum = new int[n]; // running stack total at each sample, filled in as we go
    int maxTotal = 1;
    for (Series s : seriesStackOrder) {
      for (int i = 0; i < n; i++) maxTotal = Math.max(maxTotal, cum[i] + s.values[i]);
    }
    int niceMaxY = niceCeil(maxTotal);

    StringBuilder svg = new StringBuilder();
    svg.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ")
       .append(width).append(' ').append(height).append("' width='").append(width)
       .append("' height='").append(height).append("' font-family='sans-serif'>\n");
    svg.append("<rect width='").append(width).append("' height='").append(height)
       .append("' fill='#ffffff'/>\n");
    svg.append("<text x='").append(width / 2).append("' y='24' text-anchor='middle' "
        + "font-size='16' font-weight='bold' fill='#222'>").append(esc(title)).append("</text>\n");

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

    svg.append("<text x='").append(padL).append("' y='").append(height - 22)
       .append("' text-anchor='start' font-size='11' fill='#666'>tick 0</text>\n");
    svg.append("<text x='").append(padL + plotW).append("' y='").append(height - 22)
       .append("' text-anchor='end' font-size='11' fill='#666'>tick ").append(maxX).append("</text>\n");
    svg.append("<text x='").append(padL + plotW / 2).append("' y='").append(height - 8)
       .append("' text-anchor='middle' font-size='11' fill='#888'>time →</text>\n");

    int[] base = new int[n]; // stack floor for the series currently being drawn
    for (Series s : seriesStackOrder) {
      String[] topPts = new String[n];
      String[] bottomPts = new String[n];
      for (int i = 0; i < n; i++) {
        double x = padL + (n == 1 ? 0 : (double) xTicks[i] / maxX * plotW);
        double yTop = padT + plotH - (double) (base[i] + s.values[i]) / niceMaxY * plotH;
        double yBase = padT + plotH - (double) base[i] / niceMaxY * plotH;
        topPts[i] = fmt(x) + "," + fmt(yTop);
        bottomPts[i] = fmt(x) + "," + fmt(yBase);
      }
      // Top edge left-to-right, then bottom edge right-to-left, to close a
      // non-self-intersecting polygon.
      StringBuilder points = new StringBuilder(String.join(" ", topPts));
      for (int i = n - 1; i >= 0; i--) {
        points.append(' ').append(bottomPts[i]);
      }
      svg.append("<polygon fill='").append(s.colorHex).append("' fill-opacity='0.85' stroke='")
         .append(s.colorHex).append("' stroke-width='0.5' points='")
         .append(points).append("'/>\n");
      for (int i = 0; i < n; i++) base[i] += s.values[i];
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
