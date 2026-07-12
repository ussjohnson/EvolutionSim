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
    String reportPath = null;

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--headless": headless = true; break;
        case "--seed":     seed = Long.parseLong(args[++i]); break;
        case "--ticks":    ticks = Integer.parseInt(args[++i]); break;
        case "--width":    width = Integer.parseInt(args[++i]); break;
        case "--height":   height = Integer.parseInt(args[++i]); break;
        case "--pop":      pop = Integer.parseInt(args[++i]); break;
        case "--report":   reportPath = args[++i]; headless = true; break;
        default:
          System.err.println("Unknown argument: " + args[i]);
          return;
      }
    }

    if (headless) {
      runHeadless(seed, ticks, width, height, pop, reportPath);
    } else {
      SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
  }

  private static void runHeadless(long seed, int ticks, int width, int height, int pop, String reportPath) {
    System.out.printf("Seed=%d  map=%dx%d  startPop=%d  ticks=%d%n", seed, width, height, pop, ticks);
    World w = World.create(width, height, pop, seed, 0.2);
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
