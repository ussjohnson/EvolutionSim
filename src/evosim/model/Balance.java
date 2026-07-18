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
  public static double PLANT_ENERGY = 3.6;        // energy per unit foliage (× herbivory)
  public static double MEAT_ENERGY = 10.0;        // energy per unit prey mass (× carnivory)
  public static double PREY_DISTANCE_WEIGHT = 0.15; // how much distance offsets mass-ratio in prey preference
  public static double MAX_PREY_MASS_RATIO = 3.0; // prey this much bigger than the predator isn't worth attempting

  // --- Combat ---
  public static double BASE_HP = 5.0;
  public static double HP_PER_DURABILITY = 10.0;
  public static double HP_PER_MASS = 1.5;
  public static double DMG_K = 6.0;               // strength → damage
  public static double ARMOR_K = 3.0;             // durability → damage reduction
  public static double FIGHT_TH = 0.5;            // fight when (defensive + bravery*(massRatio-1)) exceeds this
  // Predators eating other predators ("intra-guild predation") transfer much
  // less usable energy than eating an herbivore, scaled by the victim's own
  // DIET (its trophic level). This closes a real energy-conservation
  // loophole: MEAT_ENERGY per unit mass (9.0) vastly exceeds GROWTH_ENERGY
  // per unit mass invested (0.6), so without this penalty a closed loop of
  // carnivores repeatedly killing each other could manufacture net energy
  // from nothing and sustain a population indefinitely with no herbivores or
  // plants anywhere in the food chain. 0.88 keeps a full-carnivore kill's
  // yield (9.0*0.12=1.08 per unit mass) below what it cost to grow that mass
  // in the first place, so the loop is a net loss even before accounting for
  // the metabolism/movement/reproduction overhead a real hunt also pays —
  // scaling the yield down by the victim's DIET keeps genuine herbivore-
  // hunting at full value (DIET=0 prey is unaffected) while making
  // predator-on-predator predation unsustainable on its own, so an
  // all-carnivore population still collapses once its actual prey base is
  // gone. Verified against seed 42 / fertility 0.1 / 80x60 / wraparound (the
  // original runaway-carnivore repro case, which now purges to zero
  // carnivores by ~tick 6500) and a controlled mixed-cohort probe seeding a
  // deliberately well-built predator (high STRENGTH/SIZE/LAND_SPEED)
  // alongside herbivores, which hunted successfully but still could not
  // out-earn its own baseline metabolism over a full lifetime — a deeper,
  // pre-existing carnivory-economics problem (see MEAT_ENERGY) independent
  // of this penalty.
  public static double CARNIVORE_MEAT_PENALTY = 0.88;
  // Passive healing: a small fraction of max HP per tick, paid for out of
  // energy reserves so it competes with growth/reproduction rather than
  // being free — an animal too hungry to spare the cost doesn't heal.
  public static double HP_REGEN_RATE = 0.01;         // fraction of maxHp healed per tick
  public static double HP_REGEN_ENERGY_COST = 0.5;   // energy spent per HP point regenerated
  // How poorly a tile's actual climate must mismatch an animal's adapted
  // climate (CAMO_TEMP/CAMO_MOISTURE) before it's worth a tick of movement
  // to seek a better one (see Behavior's biome-seeking priority). Mismatch
  // ranges 0 (perfect match) to sqrt(2)~=1.41 (opposite corners); 0.35 is
  // meaningfully poor without triggering on every minor gradient, so
  // well-matched animals mostly settle rather than churning tile to tile.
  public static double BIOME_SEEK_MISMATCH_TH = 0.35;

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
  // A founded species' display colour inherits its parent's hue plus a
  // random nudge of up to this many degrees either way (see
  // SpeciesRegistry#colorFor) — small enough that a family of related
  // species reads as visibly related (a color gradient across the taxonomy
  // tree/family-tree chart), large enough that siblings founded from the
  // same parent are still distinguishable from each other and from the
  // parent itself.
  public static double SPECIES_HUE_JITTER = 35.0;

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
  // Recalibrated to ~0.41 after the gene set grew (INSULATION/IMMUNITY/ORNAMENT
  // add metabolic weight); see the Measure harness.
  public static double AGING_REFERENCE_METABOLISM = 0.41;
  // Fraction by which max SIZE slows the aging clock — the same Kleiber's-law
  // economy of scale that already discounts SIZE's raw metabolic upkeep, so
  // a long life is bought with the SIZE tradeoffs that already exist
  // (slower growth, more food needed, worse stealth), not a free perk.
  public static double AGING_SIZE_PROTECTION = 0.5;

  // --- Biomes ---
  // World generation classifies each tile into a BiomeType from two blended
  // value-noise fields (temperature, moisture) — see the NoiseField/World
  // constructor and BiomeType#classify. This is the spatial niche
  // differentiation this class's doc comment above notes the ecosystem
  // lacked when it only had one undifferentiated food resource: each biome
  // now has its own fertility, foliage-regrowth rate, and climate-stress
  // upkeep cost, so different regions favor different survival strategies
  // instead of one dominant lineage winning everywhere. BIOME_NOISE_SCALE is
  // the coarse lattice spacing in tiles: larger values produce a handful of
  // large, contiguous regions; smaller values fragment the map into many
  // small patches. Chosen so a default 40x30 map yields roughly 4-8 distinct
  // regions, closer to real-world biome scale than tile-by-tile noise.
  public static double BIOME_NOISE_SCALE = 9.0;
  // Bergmann's rule: larger animals retain heat more efficiently, so SIZE
  // discounts the *climate* portion of metabolism specifically (see
  // Animal#metabolism(double)) — the same "SIZE buys a discount on an
  // upkeep cost" pattern already used for raw metabolic upkeep
  // (SIZE_METABOLIC_DISCOUNT) and aging pace (AGING_SIZE_PROTECTION). Never
  // a free win: SIZE still costs slower growth, more food needed, and worse
  // stealth, so a large cold-hardy lineage is a genuine tradeoff, not a
  // strictly dominant strategy — it just makes harsh biomes survivable for
  // a body plan that already pays for the privilege elsewhere.
  public static double SIZE_CLIMATE_PROTECTION = 0.6;
  // Camouflage is biome-specific: an animal's effective stealth is scaled
  // down by the mismatch between its adapted climate (CAMO_TEMP/
  // CAMO_MOISTURE genes) and the tile it's standing on (see
  // Animal#effectiveStealth(double,double)). Mismatch ranges 0 (perfect
  // match) to sqrt(2)~=1.41 (climatically opposite corners of the map's
  // temperature/moisture space); CAMO_MISMATCH_K=0.7 means a fully
  // mismatched animal's stealth bonus is wiped out entirely (factor 0)
  // while a well-matched one keeps its full STEALTH value — so
  // specializing to hide in dense rainforest cover buys little if that
  // lineage later spreads into open tundra.
  public static double CAMO_MISMATCH_K = 0.7;

  // --- Seasons / dynamic climate ---
  // A run can carry a global seasonal cycle (see World#seasonalPhase): a sine
  // wave over SEASON_LENGTH ticks that swings foliage regrowth up in summer
  // and down in winter (SEASON_GROWTH_SWING) and layers extra climate-stress
  // upkeep on every animal at the coldest point of the year
  // (SEASON_COLD_STRESS). Off by default on the headless/dev path (so the
  // deterministic SmokeTest survival band is unaffected); on by default for
  // GUI runs via SimSettings. Deterministic — a pure function of the tick.
  public static int SEASON_LENGTH = 480;          // ticks in one full year
  public static double SEASON_GROWTH_SWING = 0.55; // ± fraction on foliage regrowth across the year
  public static double SEASON_COLD_STRESS = 0.45;  // extra climate-stress fraction at deepest winter

  // --- Carrion & scavenging ---
  // When an animal dies of starvation, old age, or a disaster (but not
  // predation — the predator already ate it), its body is left as carrion on
  // its tile: a pool of meat energy that decays each tick (CARRION_DECAY),
  // returning a little of itself to the soil as fertility (CARRION_TO_FERTILITY,
  // nutrient cycling) while it lasts. Any animal carnivorous enough to eat meat
  // (DIET >= SCAVENGE_MIN_DIET) standing on a carrion tile can scavenge it — a
  // safe, fightless food source that recycles biomass and gives omnivores a
  // real secondary strategy. CARRION_ENERGY is the energy yield per carrion
  // unit eaten (scaled by the scavenger's carnivory, like fresh meat).
  public static double CARRION_FROM_MASS = 0.7;   // carrion units left per unit body mass at death
  public static double CARRION_DECAY = 0.88;      // fraction of a carcass remaining after one tick
  public static double CARRION_TO_FERTILITY = 0.015; // fertility returned to the tile per carrion unit lost to decay
  public static double SCAVENGE_MIN_DIET = 0.2;   // minimum DIET to be able to scavenge carrion
  public static double CARRION_ENERGY = 8.0;      // energy per carrion unit eaten (× carnivory)

  // --- Disasters (user-triggered; never fire on the deterministic test path) ---
  // A wildfire started with the interactive Fire tool/event spreads to
  // adjacent foliage-bearing tiles each tick (FIRE_SPREAD_CHANCE) as long as
  // the tile has at least FIRE_MIN_FOLIAGE to burn, lasts FIRE_DURATION ticks
  // per tile, strips foliage, and kills whatever stands in it.
  public static double FIRE_SPREAD_CHANCE = 0.33;
  public static double FIRE_MIN_FOLIAGE = 0.12;
  public static int FIRE_DURATION = 3;

  // --- Thermoregulation ---
  // Climate stress is modelled from the tile's actual temperature (plus the
  // seasonal shift) against a comfortable band, rather than a flat per-biome
  // number: below COLD_COMFORT an animal pays cold stress, above HEAT_COMFORT
  // it pays heat stress. INSULATION removes most cold stress but *adds* heat
  // stress (fur is deadly in the desert), so cold biomes/winters favour
  // insulated bodies and hot biomes/summers favour bare ones — real spatial
  // niche differentiation that keeps one body plan from winning everywhere.
  public static double COLD_COMFORT = 0.42;       // effective temp below this → cold stress
  public static double HEAT_COMFORT = 0.72;       // effective temp above this → heat stress
  public static double COLD_STRESS_K = 1.1;       // upkeep multiplier on cold deficit
  public static double HEAT_STRESS_K = 1.1;       // upkeep multiplier on heat excess
  public static double INSULATION_COLD_RELIEF = 0.85; // fraction of cold stress insulation removes
  public static double INSULATION_HEAT_PENALTY = 0.7; // heat stress insulation adds, scaled by temp
  public static double SEASON_TEMP_AMPLITUDE = 0.28;  // ± shift to effective tile temp across the year
  public static double THERMAL_COST = 0.5;            // energy/tick per unit of thermal stress (before SIZE discount)

  // --- Sociability / herding ---
  // Being among same-species neighbours dilutes predation (a predator can only
  // take one of many, and more eyes spot it sooner): an attack on a highly
  // social animal with company lands for less. The offsetting costs — faster
  // disease spread in crowds and quicker local forage depletion — are charged
  // by the disease system and ordinary grazing, so grouping is a real tradeoff.
  public static double SOCIAL_DILUTION = 0.55;    // max predation-damage reduction in a full herd
  public static int SOCIAL_HERD_FULL = 4;         // same-species neighbours for the full dilution effect

  // --- Sexual selection ---
  public static double ORNAMENT_STEALTH_PENALTY = 0.6; // conspicuous display cuts effective stealth

  // --- Life history (r/K) ---
  // PARENTAL_CARE trades offspring quantity for quality: high care invests more
  // parental energy per child (a bigger head start) but imposes a long
  // refractory period between births; low care reproduces fast and cheap. The
  // base offspring investment (OFFSPRING_ENERGY_FRACTION) is scaled up by care.
  public static double PARENTAL_CARE_INVEST = 0.6; // extra investment fraction at max care
  public static int REPRO_COOLDOWN_BASE = 2;       // min ticks between births (r-strategist)
  public static int REPRO_COOLDOWN_CARE = 55;      // additional cooldown ticks at max care (K-strategist)

  // --- Sexual reproduction ---
  // When enabled, reproduction needs a ready same-species mate within
  // perception; the mate is chosen by ORNAMENT (sexual selection) and the child
  // is a per-gene recombination of both parents plus mutation. Sparse
  // populations struggle to find mates — a realistic Allee effect.
  public static boolean SEXUAL_DEFAULT = true;

  // --- Disease / epidemics ---
  // A susceptible-infected-recovered contagion. Outbreaks emerge more readily
  // in crowds and, crucially, in proportion to a lineage's share of the whole
  // population: the more an ecosystem is dominated by one abundant species, the
  // harder epidemics hit it (density- and frequency-dependent transmission —
  // the mechanism by which pathogens keep any single lineage from taking over,
  // maintaining diversity). IMMUNITY lowers infection odds and virulence and
  // speeds recovery. Off on the headless/dev path; on by default for GUI runs.
  public static double DISEASE_EMERGENCE = 0.00004; // base per-animal spontaneous outbreak chance
  public static double DISEASE_CROWD_FACTOR = 8.0;  // emergence scaling with local same-species crowding
  public static double DISEASE_DOMINANCE_FACTOR = 6.0; // emergence scaling with the lineage's population share
  public static double DISEASE_TRANSMISSION = 0.22; // base chance an infected neighbour infects a susceptible
  public static double DISEASE_VIRULENCE = 0.02;    // base per-tick mortality while infected
  public static double DISEASE_ENERGY_DRAIN = 0.4;  // fraction of metabolism added as upkeep while sick
  public static double DISEASE_RECOVERY = 0.04;     // base per-tick recovery chance
  public static int DISEASE_MIN_DURATION = 12;      // ticks before recovery can begin
  public static double DISEASE_IMMUNITY_RELIEF = 0.85; // how much IMMUNITY cuts infection/virulence

  // --- Sampling / reporting ---
  public static int SAMPLE_INTERVAL = 5;          // ticks between population samples
}
