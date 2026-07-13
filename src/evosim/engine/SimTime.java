package evosim.engine;

/**
 * Formats a raw tick count as an in-world calendar date: 1 tick = 1 day,
 * 30 days = 1 month, 12 months = 1 year. Display-only — the engine and model
 * work exclusively in ticks; nothing simulation-relevant depends on this.
 */
public final class SimTime {
  public static final int DAYS_PER_MONTH = 30;
  public static final int MONTHS_PER_YEAR = 12;
  public static final int DAYS_PER_YEAR = DAYS_PER_MONTH * MONTHS_PER_YEAR;

  private SimTime() {}

  /** e.g. tick 400 -> "Year 2, Month 2, Day 11" (1-indexed for display). */
  public static String format(int tick) {
    int year = tick / DAYS_PER_YEAR + 1;
    int dayInYear = tick % DAYS_PER_YEAR;
    int month = dayInYear / DAYS_PER_MONTH + 1;
    int day = dayInYear % DAYS_PER_MONTH + 1;
    return "Year " + year + ", Month " + month + ", Day " + day;
  }
}
