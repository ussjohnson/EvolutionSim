package evosim.engine;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Drives the {@link World} tick loop on its own background thread, decoupled
 * from the Swing EDT. State (running/paused/stopped) and speed are volatile so
 * the UI can change them from the EDT; after every tick the engine publishes an
 * immutable {@link Snapshot} that the UI polls. The model is only ever touched
 * from this one thread, preserving determinism.
 */
public final class SimulationEngine implements Runnable {
  public enum State { PAUSED, RUNNING, STOPPED }

  private final World world;
  private final AtomicReference<Snapshot> latest = new AtomicReference<>();

  private volatile State state = State.PAUSED;
  private volatile int ticksPerSecond = 20; // 0 = uncapped
  private volatile int maxTicks;            // 0 = run until stopped
  private volatile Runnable onFinished;

  private Thread thread;

  public SimulationEngine(World world, int maxTicks) {
    this.world = world;
    this.maxTicks = maxTicks;
    latest.set(world.snapshot());
  }

  public void startThread() {
    if (thread != null) return;
    thread = new Thread(this, "evosim-engine");
    thread.setDaemon(true);
    thread.start();
  }

  @Override
  public void run() {
    while (state != State.STOPPED) {
      if (state == State.PAUSED) {
        sleep(20);
        continue;
      }
      world.tick();
      latest.set(world.snapshot());

      if ((maxTicks > 0 && world.currentTick() >= maxTicks) || world.population() == 0) {
        finish();
        break;
      }
      int tps = ticksPerSecond;
      if (tps > 0) sleep(1000 / tps);
    }
  }

  private void finish() {
    state = State.STOPPED;
    Runnable cb = onFinished;
    if (cb != null) cb.run();
  }

  private static void sleep(long ms) {
    if (ms <= 0) return;
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // --- Controls (called from the EDT) ---
  public void play() { if (state != State.STOPPED) state = State.RUNNING; }
  public void pause() { if (state != State.STOPPED) state = State.PAUSED; }
  public void stop() { if (state != State.STOPPED) finish(); }

  public void setTicksPerSecond(int tps) { this.ticksPerSecond = Math.max(0, tps); }
  public void setOnFinished(Runnable cb) { this.onFinished = cb; }

  public State state() { return state; }
  public Snapshot latest() { return latest.get(); }
  public World world() { return world; }
}
