package evosim.model;

/**
 * Every tunable constant in the simulation, gathered in one place so the
 * ecosystem can be balanced without hunting through the code. Fields are
 * mutable statics so a tuning harness can sweep them; the defaults below are
 * the starting point tuned for a stable-ish mixed population.
 *
 * Lives in {@code model} (rather than {@code engine}) because both the model
 * layer (derived attributes on {@link Animal}, speciation in
 * {@link SpeciesRegistry}) and the engine layer read these values.
 */
public final class Balance {
  private Balance() {}

  // --- Energy & metabolism ---
  public static double BASE_META = 0.05;          // baseline upkeep per tick
  public static double SIZE_META_SCALE = 0.5;     // how much size amplifies upkeep
  public static double MOVE_K = 0.02;             // energy per tile stepped, per unit mass
  public static double RESERVE_PER_MASS = 10.0;   // max energy reserves per unit mass

  // --- Growth ---
  public static double START_MASS_FRACTION = 0.6; // newborn/adult starting mass vs maxMass
  public static double START_ENERGY_FRACTION = 0.6;
  public static double GROWTH_RATE = 0.06;        // fraction of remaining mass gained per tick
  public static double GROWTH_ENERGY = 1.0;       // energy cost per unit mass grown
  public static double GROWTH_MARGIN = 1.5;       // only grow when energy > metabolism*this

  // --- Feeding ---
  public static double APPETITE = 0.35;           // max foliage grazed per tick
  public static double PLANT_ENERGY = 3.0;        // energy per unit foliage (× herbivory)
  public static double MEAT_ENERGY = 2.5;         // energy per unit prey mass (× carnivory)

  // --- Combat ---
  public static double BASE_HP = 5.0;
  public static double HP_PER_DURABILITY = 10.0;
  public static double HP_PER_MASS = 1.0;
  public static double DMG_K = 5.0;               // strength → damage
  public static double ARMOR_K = 3.0;             // durability → damage reduction
  public static double FIGHT_TH = 0.5;            // fight when (defensive + bravery*(massRatio-1)) exceeds this

  // --- Reproduction ---
  public static double OFFSPRING_ENERGY_FRACTION = 0.4; // share of parent energy passed to child

  // --- Perception ---
  public static double PERCEPTION_NOISE = 0.15;   // random slop added to detection contests

  // --- Speciation ---
  public static double SPECIATION_THRESHOLD = 0.18; // genetic distance to found a new species
  public static double MAX_KIN_DIST = 0.4;          // Inclusivity=1 treats everything within this as kin

  // --- Sampling / reporting ---
  public static int SAMPLE_INTERVAL = 5;          // ticks between population samples
}
