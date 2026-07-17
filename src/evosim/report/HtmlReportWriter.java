package evosim.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import evosim.engine.SimTime;
import evosim.engine.StatsCollector;
import evosim.engine.World;
import evosim.model.BiomeType;
import evosim.model.GeneType;
import evosim.model.Species;
import evosim.model.SpeciesLineage;
import evosim.model.SpeciesRegistry;

/**
 * Writes a single, self-contained HTML report of a finished (or stopped) run:
 * a run summary, an inline SVG population-over-time chart, a species field
 * guide, and a few "superlatives". No external assets — the SVG and CSS are
 * embedded so the file opens anywhere.
 */
public final class HtmlReportWriter {
  private HtmlReportWriter() {}

  /** Chart/table only include species whose population ever reached this. */
  private static final int SIGNIFICANT_PEAK = 2;

  public static void write(World w, Path out) throws IOException {
    StatsCollector stats = w.stats();
    int endTick = w.currentTick();

    List<Species> all = new ArrayList<>(w.species().all());
    List<Species> significant = new ArrayList<>();
    int ephemeralCount = 0;
    for (Species s : all) {
      if (s.peakPopulation() >= SIGNIFICANT_PEAK) significant.add(s);
      else ephemeralCount++;
    }
    significant.sort(Comparator.comparingInt(Species::peakPopulation).reversed());

    int[] xTicks = toIntArray(stats.sampleTicks());
    int samples = xTicks.length;

    // Every significant species gets a row, in lineage (depth-first) order,
    // so a child's row sits directly beside its parent's and branches off
    // visibly at its founding tick — no line-count cap, so species founded
    // late in a long run always appear.
    List<Species> lineageOrder = SpeciesLineage.dfsOrder(significant);
    java.util.Map<Integer, Integer> rowOf = new java.util.HashMap<>();
    for (int i = 0; i < lineageOrder.size(); i++) rowOf.put(lineageOrder.get(i).id(), i);
    List<SvgChart.LineageRow> rows = new ArrayList<>();
    for (Species s : lineageOrder) {
      Integer parentRow = rowOf.get(s.parentSpeciesId());
      String range = s.isExtant()
          ? ("born tick " + s.birthTick() + ", still alive")
          : ("tick " + s.birthTick() + "–" + s.extinctionTick());
      String tooltip = s.name() + (s.isExtant() ? "" : " (extinct)") + " — "
          + describe(s.centroid()) + " · " + range + " · peak " + s.peakPopulation();
      rows.add(new SvgChart.LineageRow(s.name(), tooltip, toHex(s.color()),
          SpeciesLineage.leftPad(s.populationHistory(), samples), s.birthTick(),
          (parentRow != null) ? parentRow : -1));
    }
    String chart = SvgChart.lineageTree("Species family tree & population over time", xTicks, rows, 900);

    List<Integer> fertilityPct = new ArrayList<>(samples);
    for (double f : stats.averageFertility()) fertilityPct.add((int) Math.round(f * 100));
    String fertilityChart = SvgChart.lineChart("Average plant fertility over time (%)", xTicks,
        List.of(new SvgChart.Series("fertility", "#4a9c4a", toIntArray(fertilityPct))), 860, 260);

    StringBuilder html = new StringBuilder();
    html.append("<!doctype html>\n<html lang='en'><head><meta charset='utf-8'>\n");
    html.append("<title>EvolutionSim Report</title>\n");
    html.append("<style>")
        .append("body{font-family:system-ui,sans-serif;margin:24px;color:#1a1a1a;background:#fafafa;}")
        .append("h1{margin:0 0 4px;} h2{margin-top:32px;border-bottom:2px solid #ddd;padding-bottom:4px;}")
        .append(".sub{color:#666;margin-top:0;} .cards{display:flex;gap:16px;flex-wrap:wrap;margin:12px 0;}")
        .append(".card{background:#fff;border:1px solid #e2e2e2;border-radius:8px;padding:12px 16px;min-width:150px;}")
        .append(".card .n{font-size:24px;font-weight:bold;} .card .l{color:#777;font-size:12px;}")
        .append("table{border-collapse:collapse;width:100%;background:#fff;}")
        .append("th,td{border:1px solid #e2e2e2;padding:6px 10px;text-align:left;font-size:13px;}")
        .append("th{background:#f0f0f0;} .sw{display:inline-block;width:12px;height:12px;border:1px solid #999;vertical-align:middle;margin-right:6px;}")
        .append(".chart{background:#fff;border:1px solid #e2e2e2;border-radius:8px;padding:8px;display:inline-block;}")
        .append("details{font-size:12px;} details summary{cursor:pointer;color:#3366aa;}")
        .append(".genes{margin:4px 0 0;padding-left:16px;color:#444;}")
        .append(".taxonomy{list-style:none;padding-left:18px;}")
        .append(".taxonomy>li{margin:4px 0;} .taxonomy .row{font-size:13px;}")
        .append(".taxonomy .stat{color:#777;font-size:11px;margin-left:4px;}")
        .append("</style>\n</head>\n<body>\n");

    html.append("<h1>EvolutionSim Report</h1>\n");
    html.append("<p class='sub'>seed ").append(w.seed())
        .append(" &middot; map ").append(w.width()).append("×").append(w.height())
        .append(" &middot; ").append(esc(SimTime.format(endTick)))
        .append(" (").append(endTick).append(" ticks) elapsed</p>\n");

    html.append("<div class='cards'>\n");
    card(html, w.population(), "final population");
    card(html, w.species().extantCount(), "species alive");
    card(html, w.species().totalCount(), "species ever");
    card(html, ephemeralCount, "ephemeral variants (peak &lt;" + SIGNIFICANT_PEAK + ")");
    html.append("</div>\n");

    html.append("<h2>Family tree</h2>\n<div class='chart'>").append(chart).append("</div>\n");

    html.append("<h2>Environment</h2>\n<div class='chart'>").append(fertilityChart).append("</div>\n");

    appendSuperlatives(html, significant, endTick);

    html.append("<h2>Taxonomy</h2>\n");
    appendTaxonomyOutline(html, significant);

    html.append("<h2>Species field guide</h2>\n");
    html.append("<table>\n<tr><th>Species</th><th>Description</th><th>Evolved From</th>"
        + "<th>Lifespan</th><th>Avg Age at Death</th><th>Peak</th><th>Births</th><th>Deaths</th>"
        + "<th>Cause of Death</th><th>Kills</th></tr>\n");
    SpeciesRegistry registry = w.species();
    for (Species s : significant) {
      String parent = registry.parentNameOf(s);
      html.append("<tr>")
          .append("<td><span class='sw' style='background:").append(toHex(s.color())).append("'></span>")
          .append(esc(s.name())).append(s.isExtant() ? "" : " <em>(extinct)</em>")
          .append(detailsGenes(s.centroid())).append("</td>")
          .append("<td>").append(esc(describe(s.centroid()))).append("</td>")
          .append("<td>").append(parent != null ? esc(parent) : "&mdash;").append("</td>")
          .append("<td>").append(s.lifespan(endTick)).append(" t</td>")
          .append("<td>").append(s.totalDeaths() > 0
              ? Math.round(s.averageMemberLifespan()) + " t" : "&mdash;").append("</td>")
          .append("<td>").append(s.peakPopulation()).append("</td>")
          .append("<td>").append(s.totalBirths()).append("</td>")
          .append("<td>").append(s.totalDeaths()).append("</td>")
          .append("<td>").append(esc(s.primaryCauseOfDeath())).append("</td>")
          .append("<td>").append(s.kills()).append("</td>")
          .append("</tr>\n");
    }
    html.append("</table>\n");
    if (ephemeralCount > 0) {
      html.append("<p class='sub'>").append(ephemeralCount)
          .append(" ephemeral variant(s) that never exceeded a population of ")
          .append(SIGNIFICANT_PEAK).append(" are omitted from the guide.</p>\n");
    }

    html.append("</body></html>\n");
    Files.write(out, html.toString().getBytes(StandardCharsets.UTF_8));
  }

  /** A nested, indented outline of the species tree (root founders down to their descendants). */
  private static void appendTaxonomyOutline(StringBuilder html, List<Species> significant) {
    if (significant.isEmpty()) return;
    java.util.Map<Integer, List<Species>> children = SpeciesLineage.childrenByParent(significant);
    html.append("<ul class='taxonomy'>\n");
    for (Species root : SpeciesLineage.roots(significant)) {
      appendTaxonomyNode(html, root, children);
    }
    html.append("</ul>\n");
  }

  private static void appendTaxonomyNode(StringBuilder html, Species s, java.util.Map<Integer, List<Species>> children) {
    html.append("<li><div class='row'><span class='sw' style='background:").append(toHex(s.color())).append("'></span>")
        .append("<b>").append(esc(s.name())).append("</b>").append(s.isExtant() ? "" : " <em>(extinct)</em>")
        .append("<span class='stat'>").append(esc(describe(s.centroid())))
        .append(" &middot; peak ").append(s.peakPopulation()).append("</span></div>\n");
    List<Species> kids = children.get(s.id());
    if (kids != null && !kids.isEmpty()) {
      html.append("<ul class='taxonomy'>\n");
      for (Species child : kids) {
        appendTaxonomyNode(html, child, children);
      }
      html.append("</ul>\n");
    }
    html.append("</li>\n");
  }

  private static void appendSuperlatives(StringBuilder html, List<Species> sp, int endTick) {
    if (sp.isEmpty()) return;
    Species longest = sp.get(0), abundant = sp.get(0), carnivore = sp.get(0);
    for (Species s : sp) {
      if (s.lifespan(endTick) > longest.lifespan(endTick)) longest = s;
      if (s.peakPopulation() > abundant.peakPopulation()) abundant = s;
      if (s.centroid()[GeneType.DIET.ordinal()] > carnivore.centroid()[GeneType.DIET.ordinal()]) carnivore = s;
    }
    html.append("<h2>Notable</h2>\n<ul>\n");
    html.append("<li><b>Longest-lived:</b> ").append(esc(longest.name()))
        .append(" (").append(longest.lifespan(endTick)).append(" ticks)</li>\n");
    html.append("<li><b>Most abundant:</b> ").append(esc(abundant.name()))
        .append(" (peak ").append(abundant.peakPopulation()).append(")</li>\n");
    html.append("<li><b>Most carnivorous lineage:</b> ").append(esc(carnivore.name()))
        .append(" (diet ").append(pct(carnivore.centroid()[GeneType.DIET.ordinal()])).append(")</li>\n");
    html.append("</ul>\n");
  }

  /** A collapsible &lt;details&gt; block listing every gene's normalized value. */
  private static String detailsGenes(double[] n) {
    StringBuilder sb = new StringBuilder("<details><summary>full genome</summary><ul class='genes'>");
    for (GeneType t : GeneType.ALL) {
      sb.append("<li>").append(esc(t.name())).append(": ")
          .append(pct(n[t.ordinal()])).append("</li>");
    }
    sb.append("</ul></details>");
    return sb.toString();
  }

  /**
   * A short natural-language description of a genome — a species' current
   * centroid (tracks live drift, not frozen at founding) or an individual's
   * own genome.
   */
  public static String describe(double[] n) {
    double diet = n[GeneType.DIET.ordinal()];
    String feeding = diet < 0.33 ? "herbivore" : (diet < 0.66 ? "omnivore" : "carnivore");
    double size = n[GeneType.SIZE.ordinal()];
    String build = size < 0.33 ? "small" : (size < 0.66 ? "mid-sized" : "large");
    double speed = n[GeneType.LAND_SPEED.ordinal()];
    String pace = speed < 0.33 ? "slow" : (speed < 0.66 ? "moderate" : "fast");
    BiomeType camoBiome = BiomeType.classify(
        n[GeneType.CAMO_TEMP.ordinal()], n[GeneType.CAMO_MOISTURE.ordinal()]);
    String camo = camoBiome.label().toLowerCase() + "-camouflaged";
    return build + ", " + pace + ", " + camo + " " + feeding;
  }

  private static void card(StringBuilder html, int n, String label) {
    html.append("<div class='card'><div class='n'>").append(n)
        .append("</div><div class='l'>").append(label).append("</div></div>\n");
  }

  private static int[] toIntArray(List<Integer> list) {
    int[] a = new int[list.size()];
    for (int i = 0; i < a.length; i++) a[i] = list.get(i);
    return a;
  }

  private static String toHex(int rgb) {
    return String.format("#%06X", rgb & 0xFFFFFF);
  }

  private static String pct(double norm) {
    return Math.round(norm * 100) + "%";
  }

  private static String esc(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
