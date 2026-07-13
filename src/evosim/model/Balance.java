package evosim.model;

/**
 * Every tunable constant in the simulation, gathered in one place so the
 * ecosystem can be balanced without hunting through the code. Fields are
 * mutable statics so a tuning harness can sweep them.
 *
 * The defaults below were empirically tuned (see the survival-band checks in
 * {@code SmokeTest} and an ad-hoc multi-seed/multi-checkpoint harness) to
 * counter runaway trends the untuned model fell into: a "shrink to nothing"
 * spiral (SIZE had real upkeep/growth costs but no offsetting benefit, so
 * populations converged on minimum viable size within a few hundred ticks)
 * and non-viable carnivory (hunting's energy payoff didn't cover the
 * risk/cost of a multi-tick chase-and-kill cycle, so carnivorous lineages
 * went extinct within ~30 ticks). Countermeasures: foliage intake and whole-
 * body metabolism both now scale with an animal's actual current mass
 * (see {@link Animal#grazeCapacity()}, {@link Animal#metabolism()}) rather
 * than a flat per-tick rate, so a bigger mouth/gut and Kleiber's-law
 * metabolic efficiency give SIZE genuine upside; HP_PER_MASS/GROWTH_ENERGY
 * were tuned so bigger bodies are survivable and reachable; MEAT_ENERGY/DMG_K
 * were raised so a successful hunt pays for itself, and prey preference
 * (PREY_DISTANCE_WEIGHT) makes big bodies a partial predation refuge without
 * a hard cutoff. Note this only slows the underlying dynamic — the
 * environment has a single undifferentiated food resource (foliage), so
 * competitive exclusion still drives most single-run populations toward one
 * dominant herbivore lineage over long timescales; durable multi-niche
 * coexistence likely needs actual niche differentiation (multiple food
 * types, spatial biomes) rather than more constant-tuning.
 *
 * Lives in {@code model} (rather than {@code engine}) because both the model
 * layer (derived attributes on {@link Animal}, speciation in
 * {@link SpeciesRegistry}) and the engine layer read these values.
 */
public final class Balance {
  private Balance() {}

  // --- Energy & metabolism ---
  public static double BASE_META = 0.05;          // baseline upkeep per tick (flat, all sizes)
  // Economy of scale, in the spirit of Kleiber's law: active-trait upkeep
  // (strength, speed, awareness, etc.) costs proportionally less at max SIZE
  // than at min SIZE. Bounded and linear in normalized SIZE (like the old
  // SIZE_META_SCALE it replaces) rather than a raw mass exponent — an
  // exponent applied to the *whole* budget created a cost cliff just above
  // minimum size and destabilized the population; this stays gentle. Energy
  // reserves (below) still scale linearly with mass, so a large animal's
  // reserve-to-upkeep ratio grows with size: it can coast through longer
  // stretches without food than a small animal can.
  public static double SIZE_METABOLIC_DISCOUNT = 0.35;
  public static double MOVE_K = 0.02;             // energy per tile stepped, per unit mass
  public static double RESERVE_PER_MASS = 10.0;   // max energy reserves per unit mass

  // --- Growth ---
  public static double START_MASS_FRACTION = 0.6; // newborn/adult starting mass vs maxMass
  public static double START_ENERGY_FRACTION = 0.6;
  public static double GROWTH_RATE = 0.06;        // fraction of remaining mass gained per tick
  public static double GROWTH_ENERGY = 0.6;       // energy cost per unit mass grown
  public static double GROWTH_MARGIN = 1.5;       // only grow when energy > metabolism*this

  // --- Feeding ---
  public static double APPETITE = 0.35;           // base foliage grazed per tick
  public static double APPETITE_MASS_SCALE = 0.25; // extra graze capacity per unit body mass
  public static double PLANT_ENERGY = 3.0;        // energy per unit foliage (× herbivory)
  public static double MEAT_ENERGY = 7.0;         // energy per unit prey mass (× carnivory)
  public static double PREY_DISTANCE_WEIGHT = 0.15; // how much distance offsets mass-ratio in prey preference
  public static double MAX_PREY_MASS_RATIO = 3.0; // prey this much bigger than the predator isn't worth attempting

  // --- Combat ---
  public static double BASE_HP = 5.0;
  public static double HP_PER_DURABILITY = 10.0;
  public static double HP_PER_MASS = 1.5;
  public static double DMG_K = 6.0;               // strength → damage
  public static double ARMOR_K = 3.0;             // durability → damage reduction
  public static double FIGHT_TH = 0.5;            // fight when (defensive + bravery*(massRatio-1)) exceeds this

  // --- Reproduction ---
  public static double OFFSPRING_ENERGY_FRACTION = 0.4; // share of parent energy passed to child

  // --- Perception ---
  public static double PERCEPTION_NOISE = 0.15;   // random slop added to detection contests

  // --- Speciation ---
  // Empirically calibrated (see below) so that, once the founder-diversity and
  // centroid-staleness bugs described in FOUNDER_GENE_BAND/CENTROID_HORIZON
  // were fixed, genuine drift-based speciation reliably occurs within a
  // multi-thousand-tick run without fragmenting into noisy, ephemeral
  // one-generation species. Swept 0.09-0.18 across 8 seeds at 3000-8000
  // ticks: 0.18 never split in 8000 ticks (steady-state drift plateaus around
  // 0.17 under stabilizing selection to a single dominant strategy — see the
  // class doc); 0.10 and below started producing species that never exceeded
  // a population of 1 (mutation noise, not real divergence); 0.11 was the
  // sweet spot — every seed speciated by tick 4000-8000, and 20+ of 24 new
  // species across the sweep reached a real, sustained population.
  public static double SPECIATION_THRESHOLD = 0.11; // genetic distance to found a new species
  public static double MAX_KIN_DIST = 0.4;          // Inclusivity=1 treats everything within this as kin
  // Bounds how many absorbed members a species' centroid "remembers" (see
  // Species#absorb). Below this many total members it behaves as a true
  // running mean; beyond it, older members are progressively discounted so
  // the centroid keeps tracking the current population instead of hardening
  // into an immovable lifetime average — without this, no species could ever
  // speciate again once it had accumulated more than a few hundred births,
  // no matter how far the population had actually drifted.
  public static int CENTROID_HORIZON = 300;

  // --- Founding population ---
  // Default width (as a fraction of each gene's full range, centered on the
  // midpoint) that the starting population is drawn from when the user hasn't
  // narrowed a gene's range in Settings. Two independently-random founders
  // drawn from the *full* range sit ~0.41 apart on average — more than twice
  // SPECIATION_THRESHOLD — so a fully-random founding population instantly
  // fragments into one species per animal before any real drift happens. A
  // narrow shared band starts the population as a small handful of species
  // (or one) and lets speciation genuinely emerge from mutation over the run,
  // which is both more realistic and what the per-gene range sliders in
  // Settings were already documented to do.
  public static double FOUNDER_GENE_BAND = 0.15;

  // --- Aging ---
  // A Gompertz hazard (the standard actuarial aging model: risk of death from
  // senescence rises exponentially with age) rather than a hard lifespan cap,
  // evaluated against each animal's own "pace of life" (see
  // Animal#agingPace) instead of raw calendar age — so genetics shift *when*
  // the curve bites without anyone being exempt from it. AGING_H0/AGING_K
  // shape the baseline curve for an animal at reference pace (pace = 1.0):
  // hazard is negligible for ~1000 ticks, then climbs sharply, reaching ~5%
  // per tick by age 2000 and >45% per tick by age 2500 — well beyond the
  // ~150-900 tick lifespans starvation/predation already produce in most
  // runs, so old age is a real cause of death only for lineages that are
  // otherwise safe and well-fed long enough to reach it, not a dominant one.
  public static double AGING_H0 = 5e-6;
  public static double AGING_K = 0.0046;
  // "Typical" metabolism (empirically, the average across random genomes),
  // used so an animal at exactly this rate has aging-pace multiplier 1.0.
  public static double AGING_REFERENCE_METABOLISM = 0.34;
  // Fraction by which max SIZE slows the aging clock — the same Kleiber's-law
  // economy of scale that already discounts SIZE's raw metabolic upkeep, so
  // a long life is bought with the SIZE tradeoffs that already exist
  // (slower growth, more food needed, worse stealth), not a free perk.
  public static double AGING_SIZE_PROTECTION = 0.5;

  // --- Sampling / reporting ---
  public static int SAMPLE_INTERVAL = 5;          // ticks between population samples
}
