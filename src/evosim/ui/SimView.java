package evosim.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.Timer;

import evosim.engine.SimTime;
import evosim.engine.SimulationEngine;
import evosim.engine.Snapshot;

/**
 * The live-simulation screen: the map, the species sidebar, and a control bar
 * (play/pause, stop, speed slider, tick/population readout). A ~30 fps Swing
 * {@link Timer} on the EDT polls the engine's latest snapshot and repaints —
 * the only bridge between the UI and the background engine thread.
 */
public final class SimView extends JPanel {
  private final SimulationEngine engine;
  private final MapPanel map = new MapPanel();
  private final StatsSidebar sidebar = new StatsSidebar();
  private final JButton playPause = new JButton("Play");
  private final JLabel status = new JLabel("tick 0   pop 0");
  private final Timer repaintTimer;

  public SimView(SimulationEngine engine) {
    super(new BorderLayout());
    this.engine = engine;

    add(map, BorderLayout.CENTER);
    add(sidebar, BorderLayout.EAST);
    add(buildControls(), BorderLayout.SOUTH);

    repaintTimer = new Timer(33, e -> refresh());
  }

  private JPanel buildControls() {
    JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
    bar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

    playPause.addActionListener(e -> togglePlay());

    JButton stop = new JButton("Stop");
    stop.addActionListener(e -> engine.stop());

    JLabel speedLabel = new JLabel("Speed");
    JSlider speed = new JSlider(1, 120, 20);
    speed.setPreferredSize(new Dimension(180, speed.getPreferredSize().height));
    speed.addChangeListener(e -> engine.setTicksPerSecond(speed.getValue()));

    bar.add(playPause);
    bar.add(stop);
    bar.add(speedLabel);
    bar.add(speed);
    bar.add(status);
    return bar;
  }

  private void togglePlay() {
    if (engine.state() == SimulationEngine.State.RUNNING) {
      engine.pause();
      playPause.setText("Play");
    } else {
      engine.play();
      playPause.setText("Pause");
    }
  }

  /** Begin polling + rendering. Call once the view is shown. */
  public void begin() {
    repaintTimer.start();
    refresh();
  }

  public void stopRendering() {
    repaintTimer.stop();
  }

  private void refresh() {
    Snapshot s = engine.latest();
    map.setSnapshot(s);
    sidebar.setSnapshot(s);
    if (s != null) {
      status.setText(SimTime.format(s.tick) + " (tick " + s.tick + ")   pop " + s.population
          + "   species " + s.species.size());
    }
    if (engine.state() == SimulationEngine.State.STOPPED) {
      playPause.setText("Play");
      playPause.setEnabled(false);
    }
    map.repaint();
    sidebar.repaint();
  }
}
