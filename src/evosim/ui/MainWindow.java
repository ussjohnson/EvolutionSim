package evosim.ui;

import java.awt.CardLayout;

import javax.swing.JFrame;

import evosim.engine.SimulationEngine;
import evosim.engine.World;

/**
 * The top-level application window. Uses a {@link CardLayout} to move between
 * screens: settings → simulation → results. Phase B wires up the simulation
 * card and launches a default run; the settings and results cards arrive in
 * later phases.
 */
public final class MainWindow extends JFrame {
  private final CardLayout cards = new CardLayout();
  private SimView simView;

  public MainWindow() {
    super("EvolutionSim");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLayout(cards);
    setSize(1040, 700);
    setLocationRelativeTo(null);
  }

  /** Build a world/engine and show the live simulation. (Temporary Phase-B entry.) */
  public void startDefaultRun() {
    World world = World.create(40, 30, 150, System.nanoTime(), 0.2);
    SimulationEngine engine = new SimulationEngine(world, 0);
    simView = new SimView(engine);
    add(simView, "sim");
    cards.show(getContentPane(), "sim");

    engine.startThread();
    engine.play();
    simView.begin();
  }
}
