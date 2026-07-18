package evosim.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.Timer;

import evosim.engine.Interactions;
import evosim.engine.SimTime;
import evosim.engine.SimulationEngine;
import evosim.engine.Snapshot;

/**
 * The live-simulation screen: the map, a species/trends tabbed sidebar, an
 * inspector for a clicked tile/animal, a god-tools palette for reshaping the
 * world as it runs, and a control bar (play/pause, stop, speed, zoom, and a
 * tick/season/population readout). A ~30 fps Swing {@link Timer} on the EDT
 * polls the engine's latest snapshot, repaints, and feeds the live charts.
 */
public final class SimView extends JPanel {
  /** The interactive tools. Brush tools paint on press/drag; events fire once per click. */
  private enum Tool {
    INSPECT("Inspect", 0), RAIN("Rain", 3), DROUGHT("Drought", 3),
    SPAWN("Spawn life", 2), SMITE("Smite", 3), FIRE("Wildfire", 0), METEOR("Meteor", 4);

    final String label;
    final int defaultRadius;
    Tool(String label, int defaultRadius) { this.label = label; this.defaultRadius = defaultRadius; }
  }

  private final SimulationEngine engine;
  private final MapPanel map = new MapPanel();
  private final StatsSidebar sidebar = new StatsSidebar();
  private final LiveChartsPanel charts = new LiveChartsPanel();
  private final InspectorPanel inspector = new InspectorPanel();
  private final JButton playPause = new JButton("Play");
  private final JLabel status = new JLabel("tick 0   pop 0");
  private final JSlider zoomSlider = new JSlider(25, 400, 100);
  private final JSlider brushSlider = new JSlider(1, 12, 3);
  private final Timer repaintTimer;

  private Tool tool = Tool.INSPECT;

  public SimView(SimulationEngine engine) {
    super(new BorderLayout());
    this.engine = engine;

    JScrollPane mapScroll = new JScrollPane(map);
    mapScroll.getVerticalScrollBar().setUnitIncrement(20);
    mapScroll.getHorizontalScrollBar().setUnitIncrement(20);
    add(mapScroll, BorderLayout.CENTER);

    add(buildToolPalette(), BorderLayout.WEST);
    add(buildEast(), BorderLayout.EAST);
    add(buildControls(), BorderLayout.SOUTH);

    map.onTileClick((x, y) -> {
      if (tool == Tool.INSPECT) {
        map.setSelectedTile(x, y);
        inspector.setSelectedTile(x, y);
        inspector.refresh();
      }
    });
    map.onTilePointer(this::applyTool);
    map.onZoomChanged(z -> zoomSlider.setValue((int) Math.round(z * 100)));

    repaintTimer = new Timer(33, e -> refresh());
  }

  private JComponent buildEast() {
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Species", sidebar);
    tabs.addTab("Trends", charts);
    JSplitPane east = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, inspector);
    east.setResizeWeight(0.55);
    east.setDividerLocation(340);
    east.setPreferredSize(new Dimension(250, 600));
    return east;
  }

  private JComponent buildToolPalette() {
    JPanel panel = new JPanel();
    panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createTitledBorder("God tools"));
    panel.setPreferredSize(new Dimension(150, 600));

    ButtonGroup group = new ButtonGroup();
    for (Tool t : Tool.values()) {
      JToggleButton b = new JToggleButton(t.label);
      b.setAlignmentX(LEFT_ALIGNMENT);
      b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
      b.setToolTipText(tooltipFor(t));
      b.addActionListener(e -> selectTool(t));
      if (t == Tool.INSPECT) b.setSelected(true);
      group.add(b);
      panel.add(b);
      panel.add(javax.swing.Box.createVerticalStrut(4));
    }

    panel.add(javax.swing.Box.createVerticalStrut(10));
    JLabel brushLabel = new JLabel("Brush size");
    brushLabel.setAlignmentX(LEFT_ALIGNMENT);
    panel.add(brushLabel);
    brushSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, brushSlider.getPreferredSize().height));
    brushSlider.setAlignmentX(LEFT_ALIGNMENT);
    brushSlider.addChangeListener(e -> updateBrushCursor());
    panel.add(brushSlider);

    panel.add(javax.swing.Box.createVerticalStrut(14));
    JLabel eventLabel = new JLabel("Global events");
    eventLabel.setAlignmentX(LEFT_ALIGNMENT);
    panel.add(eventLabel);
    panel.add(javax.swing.Box.createVerticalStrut(4));
    JButton bloom = new JButton("Bloom 🌱");
    bloom.setAlignmentX(LEFT_ALIGNMENT);
    bloom.setMaximumSize(new Dimension(Integer.MAX_VALUE, bloom.getPreferredSize().height));
    bloom.setToolTipText("A global surge of plant growth across the whole map.");
    bloom.addActionListener(e -> engine.world().enqueue(Interactions.bloom(0.6)));
    panel.add(bloom);

    JLabel hint = new JLabel("<html><i>Pick a brush,<br>then paint on<br>the map.</i></html>");
    hint.setForeground(new Color(120, 120, 130));
    hint.setAlignmentX(LEFT_ALIGNMENT);
    panel.add(javax.swing.Box.createVerticalStrut(12));
    panel.add(hint);
    return panel;
  }

  private static String tooltipFor(Tool t) {
    return switch (t) {
      case INSPECT -> "Click a tile to inspect its biome, foliage, and resident animal.";
      case RAIN -> "Paint rain: raise foliage and soil fertility.";
      case DROUGHT -> "Paint drought: strip foliage and degrade fertility.";
      case SPAWN -> "Drop in fresh, randomly-generated animals.";
      case SMITE -> "Kill every animal under the brush.";
      case FIRE -> "Start a wildfire that spreads to nearby foliage.";
      case METEOR -> "A scorching impact: kills, bares the ground, and sets the rim alight.";
    };
  }

  private void selectTool(Tool t) {
    this.tool = t;
    updateBrushCursor();
  }

  private void updateBrushCursor() {
    // Fire is a single ignition point; other tools show their brush disc.
    int r = (tool == Tool.INSPECT) ? 0 : (tool == Tool.FIRE ? 0 : brushRadius());
    map.setBrushRadius(r);
  }

  private int brushRadius() {
    return brushSlider.getValue();
  }

  /** Route a pointer (press/drag) on tile (x, y) to the active tool's world command. */
  private void applyTool(int x, int y) {
    int r = brushRadius();
    switch (tool) {
      case INSPECT -> { /* handled via onTileClick */ }
      case RAIN -> engine.world().enqueue(Interactions.rain(x, y, r, 0.5));
      case DROUGHT -> engine.world().enqueue(Interactions.drought(x, y, r, 0.6));
      case SPAWN -> engine.world().enqueue(Interactions.spawn(x, y, r, 1 + r));
      case SMITE -> engine.world().enqueue(Interactions.smite(x, y, r));
      case FIRE -> engine.world().enqueue(Interactions.fire(x, y));
      case METEOR -> engine.world().enqueue(Interactions.meteor(x, y, Math.max(2, r)));
    }
  }

  private JPanel buildControls() {
    JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
    bar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

    playPause.addActionListener(e -> togglePlay());

    JButton stop = new JButton("Stop & view results");
    stop.addActionListener(e -> engine.stop());

    JLabel speedLabel = new JLabel("Speed");
    JSlider speed = new JSlider(1, 120, 20);
    speed.setPreferredSize(new Dimension(160, speed.getPreferredSize().height));
    speed.addChangeListener(e -> engine.setTicksPerSecond(speed.getValue()));

    JLabel zoomLabel = new JLabel("Zoom");
    zoomSlider.setPreferredSize(new Dimension(120, zoomSlider.getPreferredSize().height));
    zoomSlider.addChangeListener(e -> map.setZoom(zoomSlider.getValue() / 100.0));

    bar.add(playPause);
    bar.add(stop);
    bar.add(speedLabel);
    bar.add(speed);
    bar.add(zoomLabel);
    bar.add(zoomSlider);
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
    inspector.setSnapshot(s);
    inspector.refresh();
    LiveChartsPanel.Frame frame = buildLiveFrame(s);
    if (frame != null) charts.update(frame);
    if (s != null) {
      String season = s.seasonsEnabled ? "   " + seasonGlyph(s.seasonName) + " " + s.seasonName : "";
      status.setText(SimTime.format(s.tick) + " (tick " + s.tick + ")" + season
          + "   pop " + s.population + "   species " + s.species.size());
    }
    if (engine.state() == SimulationEngine.State.STOPPED) {
      playPause.setText("Play");
      playPause.setEnabled(false);
    }
    map.repaint();
    sidebar.repaint();
    charts.repaint();
  }

  private static String seasonGlyph(String season) {
    return switch (season) {
      case "Spring" -> "🌱";
      case "Summer" -> "☀";
      case "Autumn" -> "🍂";
      case "Winter" -> "❄";
      default -> "";
    };
  }

  /**
   * Build a chart frame off the engine's stats series with a defensive copy —
   * the series is appended on the engine thread, so we copy under a guard and
   * simply skip the frame if a concurrent append trips us up.
   */
  private LiveChartsPanel.Frame buildLiveFrame(Snapshot s) {
    try {
      var stats = engine.world().stats();
      List<Integer> pop = new ArrayList<>(stats.globalPopulation());
      List<Integer> births = new ArrayList<>(stats.births());
      List<Integer> deaths = new ArrayList<>(stats.deaths());
      List<int[]> diets = new ArrayList<>(stats.dietCounts());
      List<Double> masses = new ArrayList<>(stats.meanMass());
      int[] popArr = new int[pop.size()];
      for (int i = 0; i < popArr.length; i++) popArr[i] = pop.get(i);
      int lastBirths = births.isEmpty() ? 0 : births.get(births.size() - 1);
      int lastDeaths = deaths.isEmpty() ? 0 : deaths.get(deaths.size() - 1);
      int[] diet = diets.isEmpty() ? new int[3] : diets.get(diets.size() - 1);
      double mass = masses.isEmpty() ? 0 : masses.get(masses.size() - 1);
      return new LiveChartsPanel.Frame(popArr, lastBirths, lastDeaths,
          diet[0], diet[1], diet[2], mass);
    } catch (RuntimeException race) {
      return null;
    }
  }
}
