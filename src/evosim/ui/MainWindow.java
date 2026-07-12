package evosim.ui;

import java.awt.CardLayout;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import evosim.config.SimSettings;
import evosim.engine.SimulationEngine;
import evosim.engine.World;

/**
 * The top-level application window. Uses a {@link CardLayout} to move between
 * screens: settings → simulation (→ results, in a later phase). The user
 * configures a run on the settings card; pressing Start builds the world and
 * engine and switches to the live simulation card.
 */
public final class MainWindow extends JFrame {
  private final CardLayout cards = new CardLayout();
  private final SettingsPanel settings = new SettingsPanel(this::startSimulation);
  private SimView simView;
  private ResultsPanel resultsPanel;

  public MainWindow() {
    super("EvolutionSim");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLayout(cards);
    setSize(1040, 720);
    setLocationRelativeTo(null);

    add(settings, "settings");
    cards.show(getContentPane(), "settings");
  }

  private void startSimulation(SimSettings config) {
    World world = World.fromSettings(config);
    SimulationEngine engine = new SimulationEngine(world, config.durationTicks);
    // finish fires on the engine thread; hop to the EDT to swap in the results.
    engine.setOnFinished(() -> SwingUtilities.invokeLater(() -> showResults(world)));

    if (simView != null) {
      simView.stopRendering();
      remove(simView);
    }
    simView = new SimView(engine);
    add(simView, "sim");
    cards.show(getContentPane(), "sim");
    revalidate();
    repaint();

    engine.startThread();
    engine.play();
    simView.begin();
  }

  private void showResults(World world) {
    if (simView != null) simView.stopRendering();
    if (resultsPanel != null) remove(resultsPanel);
    resultsPanel = new ResultsPanel(world, this::showSettings);
    add(resultsPanel, "results");
    cards.show(getContentPane(), "results");
    revalidate();
    repaint();
  }

  private void showSettings() {
    cards.show(getContentPane(), "settings");
  }
}
