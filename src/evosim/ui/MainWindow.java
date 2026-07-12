package evosim.ui;

import java.awt.CardLayout;

import javax.swing.JFrame;

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
  private SimView simView;

  public MainWindow() {
    super("EvolutionSim");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLayout(cards);
    setSize(1040, 720);
    setLocationRelativeTo(null);

    SettingsPanel settings = new SettingsPanel(this::startSimulation);
    add(settings, "settings");
    cards.show(getContentPane(), "settings");
  }

  private void startSimulation(SimSettings settings) {
    World world = World.fromSettings(settings);
    SimulationEngine engine = new SimulationEngine(world, settings.durationTicks);

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
}
