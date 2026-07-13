# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Plain `javac`/`java` project — no build tool, no external dependencies. Sources live under `src/evosim/` in subpackages, so the compile command must gather them recursively:

```sh
# Compile everything into bin/ (Git Bash / POSIX)
javac -d bin $(find src -name '*.java')

# PowerShell equivalent
javac -d bin $(Get-ChildItem -Recurse src -Filter *.java | ForEach-Object FullName)

# Launch the GUI (default): settings screen → live sim → results
java -cp bin evosim.Main

# Run headless (dev/test harness); --report also writes an HTML report
java -cp bin evosim.Main --headless --seed 42 --ticks 500 --width 40 --height 30 --pop 150
java -cp bin evosim.Main --report out.html --seed 42 --ticks 500

# Run the smoke/regression test (determinism + survival band)
java -cp bin evosim.SmokeTest
```

## Architecture

An evolution "terrarium": animals defined by a genome of evolvable traits live on a 2D tile grid, eat (plants and/or each other), and reproduce asexually with mutation. Genomes that drift far enough apart are tracked as distinct species. All four build phases are complete (see `.claude/plans/keen-riding-hare.md`): core model, headless engine, Swing GUI (settings → live map → results), and the stats/HTML report.

Packages under `src/evosim/`:

- **`model`** — the domain, with no engine/UI dependencies:
  - `GeneType` (enum) is the catalog of the 15 hereditary genes; its declaration order defines the canonical genome vector. Each constant carries bounds, mutation variation, and a metabolic-cost weight. **Add a gene by adding a constant here.**
  - `Gene` is one evolvable trait (value + bounds + variation); `Genome` is a `Gene[]` indexed by `GeneType.ordinal()` and provides `norm()`, `mutatedCopy(rng)`, and the `distance()` metric used by both speciation and kin perception.
  - `Animal` holds a `Genome` plus per-individual state (position, energy, mass, hp). Its *derived* attributes (metabolism, growth, reserves, effective stealth, display colour) are computed from genes, not inherited — each encodes a tradeoff. Colour: R=DIET, G=FECUNDITY, B=LAND_SPEED; SIZE sizes the on-screen icon, not the colour.
  - `Tile` is a grid cell with foliage/fertility; over-grazing degrades fertility (the environment-overuse mechanic).
  - `Species` + `SpeciesRegistry` do online centroid clustering: each birth joins the nearest species within `Balance.SPECIATION_THRESHOLD` or founds a new one and records its parent species. A species' centroid is a bounded moving average (`Balance.CENTROID_HORIZON`), not a lifetime mean, so it keeps tracking drift instead of freezing once well-established. `NameGenerator` makes a stable pronounceable name from the founder's normalized genome (same vector the distance metric uses). `SpeciesLineage` turns the flat, parent-id-linked species list into a tree (roots/children/depth-first order) for the family-tree chart and taxonomy views.
  - `Balance` holds every tunable constant (mutable statics) — the one place to adjust ecosystem balance.
- **`engine`** — depends on `model`:
  - `RandomSource` is the single seeded RNG threaded everywhere; **no `Math.random()` in a run**, so a seed reproduces exactly.
  - `World` owns the tile grid, the animal list, the registry, and the deterministic `tick()` loop. One animal per tile (`Tile.resident` is the occupancy index; `Animal` x/y is the source of truth).
  - `Perception` scans the awareness-radius window (bounded, not O(n²)); `Behavior` is the per-animal decide/act logic (threat response → hunt → graze → reproduce → wander → rest, then upkeep).
  - `SimulationEngine` runs `tick()` on a daemon thread with play/pause/stop + a ticks-per-second throttle, publishing an immutable `Snapshot` per frame via `AtomicReference` for the UI. `StatsCollector` owns the sample-tick axis + global-population series (per-species history lives on `Species`).
- **`config`** — `SimSettings` (world params + per-gene start ranges, consumed by `World.fromSettings`).
- **`ui`** — Swing, EDT-only. `MainWindow` (CardLayout: settings → sim → results). `SettingsPanel` builds a `SimSettings` (starting population is drawn from a narrow per-gene band by default — see `Balance.FOUNDER_GENE_BAND` — not the gene's full range, so speciation is something that emerges during a run rather than being pre-baked into 150 unrelated founders); `SimView` renders the live run (`MapPanel` + `StatsSidebar` + `InspectorPanel`) polling the engine snapshot via a ~30fps `Timer`. Clicking a tile in `MapPanel` selects it (highlighted, tracked by tile coordinates not animal id) and `InspectorPanel` shows its foliage plus the resident animal's species/vitals/full genome, re-read from the snapshot every frame — this is why `Snapshot.Dot` carries per-animal id/species/vitals/genome, not just position and color. `ResultsPanel` shows the end-of-run summary — a `StreamChartPanel` (Java2D stacked-area family tree/population chart, species ordered via `SpeciesLineage.dfsOrder` so a child's band sits beside its parent's) plus tabs for a `SpeciesTreePanel` (taxonomy `JTree`) and the detailed species `JTable` — with a "Save HTML report" button.
- **`report`** — `SvgChart` (hand-rolled SVG line + stacked-area/stream charts) and `HtmlReportWriter` (self-contained HTML: summary, family-tree stream chart, taxonomy outline, species field guide, superlatives). Shared by the GUI Save button and the `--report` flag.

### Invariants to preserve
- **Determinism:** all randomness flows through `World.rng()`; iteration is id-ordered before any seeded shuffle. Don't introduce `Math.random()`, `HashMap` iteration over model objects in the tick path, or wall-clock/threading-dependent behavior in the engine.
- **Layering:** `model` must not import `engine`/`ui`. `Balance` lives in `model` because both layers read it.
