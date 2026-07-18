package evosim;

import java.io.IOException;
import java.nio.file.Path;

import javax.swing.SwingUtilities;

import evosim.model.Balance;
import evosim.engine.World;
import evosim.report.HtmlReportWriter;
import evosim.ui.MainWindow;

/**
 * Entry point. Launches the Swing GUI by default, or runs headless for
 * development and testing:
 *
 *   java -cp bin evosim.Main --headless [--seed N] [--ticks N]
 *                            [--width N] [--height N] [--pop N]
 *                            [--report path.html]
 */
public class Main {
  public static void main(String[] args) {
    boolean headless = false;
    long seed = 42;
    int ticks = 500;
    int width = 40;
    int height = 30;
    int pop = 150;
    boolean seasons = false;
    boolean wraparound = false;
    boolean sexual = false;
    boolean disease = false;
    String reportPath = null;

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--headless": headless = true; break;
        case "--seed":     seed = Long.parseLong(args[++i]); break;
        case "--ticks":    ticks = Integer.parseInt(args[++i]); break;
        case "--width":    width = Integer.parseInt(args[++i]); break;
        case "--height":   height = Integer.parseInt(args[++i]); break;
        case "--pop":      pop = Integer.parseInt(args[++i]); break;
        case "--seasons":  seasons = true; break;
        case "--wraparound": wraparound = true; break;
        case "--sexual":   sexual = true; break;
        case "--disease":  disease = true; break;
        case "--report":   reportPath = args[++i]; headless = true; break;
        default:
          System.err.println("Unknown argument: " + args[i]);
          return;
      }
    }

    if (headless) {
      runHeadless(seed, ticks, width, height, pop, seasons, wraparound, sexual, disease, reportPath);
    } else {
      SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
  }

  private static void runHeadless(long seed, int ticks, int width, int height, int pop,
                                  boolean seasons, boolean wraparound, boolean sexual,
                                  boolean disease, String reportPath) {
    System.out.printf("Seed=%d  map=%dx%d  startPop=%d  ticks=%d  seasons=%b  wraparound=%b  sexual=%b  disease=%b%n",
        seed, width, height, pop, ticks, seasons, wraparound, sexual, disease);
    evosim.config.SimSettings s = new evosim.config.SimSettings();
    s.seed = seed;
    s.width = width;
    s.height = height;
    s.population = pop;
    s.baseFertility = 0.2;
    s.seasonsEnabled = seasons;
    s.wraparound = wraparound;
    s.sexualReproduction = sexual;
    s.diseaseEnabled = disease;
    World w = World.fromSettings(s);
    System.out.printf("tick=%-5d pop=%-5d species=%d%n", 0, w.population(), w.species().extantCount());
    for (int t = 1; t <= ticks; t++) {
      w.tick();
      if (t % (Balance.SAMPLE_INTERVAL * 4) == 0 || w.population() == 0) {
        System.out.printf("tick=%-5d pop=%-5d species=%d%n", t, w.population(), w.species().extantCount());
      }
      if (w.population() == 0) {
        System.out.println("Population extinct at tick " + t);
        break;
      }
    }
    System.out.printf("Done. Final pop=%d, extant species=%d, total species ever=%d%n",
        w.population(), w.species().extantCount(), w.species().totalCount());

    if (reportPath != null) {
      try {
        HtmlReportWriter.write(w, Path.of(reportPath));
        System.out.println("Report written to " + reportPath);
      } catch (IOException e) {
        System.err.println("Failed to write report: " + e.getMessage());
      }
    }
  }
}
