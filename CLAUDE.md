# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Plain `javac`/`java` project — no build tool, no external dependencies. Sources live under `src/evosim/` in subpackages, so the compile command must gather them recursively:

```sh
# Compile everything into bin/ (Git Bash / POSIX)
javac -d bin $(find src -name '*.java')

# PowerShell equivalent
javac -d bin $(Get-ChildItem -Recurse src -Filter *.java | ForEach-Object FullName)

# Run headless (Phase A dev/test harness)
java -cp bin evosim.Main --headless --seed 42 --ticks 500 --width 40 --height 30 --pop 150

# Run the smoke/regression test (determinism + survival band)
java -cp bin evosim.SmokeTest
```

## Architecture

An evolution "terrarium": animals defined by a genome of evolvable traits live on a 2D tile grid, eat (plants and/or each other), and reproduce asexually with mutation. Genomes that drift far enough apart are tracked as distinct species. The build proceeds in phases (see `.claude/plans/keen-riding-hare.md`): **A** = core model + headless engine (done), **B** = Swing map view, **C** = settings screen, **D** = stats + HTML report.

Packages under `src/evosim/`:

- **`model`** — the domain, with no engine/UI dependencies:
  - `GeneType` (enum) is the catalog of the 15 hereditary genes; its declaration order defines the canonical genome vector. Each constant carries bounds, mutation variation, and a metabolic-cost weight. **Add a gene by adding a constant here.**
  - `Gene` is one evolvable trait (value + bounds + variation); `Genome` is a `Gene[]` indexed by `GeneType.ordinal()` and provides `norm()`, `mutatedCopy(rng)`, and the `distance()` metric used by both speciation and kin perception.
  - `Animal` holds a `Genome` plus per-individual state (position, energy, mass, hp). Its *derived* attributes (metabolism, growth, reserves, effective stealth, display colour) are computed from genes, not inherited — each encodes a tradeoff. Colour: R=DIET, G=FECUNDITY, B=LAND_SPEED; SIZE sizes the on-screen icon, not the colour.
  - `Tile` is a grid cell with foliage/fertility; over-grazing degrades fertility (the environment-overuse mechanic).
  - `Species` + `SpeciesRegistry` do online centroid clustering: each birth joins the nearest species within `Balance.SPECIATION_THRESHOLD` or founds a new one. `NameGenerator` makes a stable pronounceable name from the founder's normalized genome (same vector the distance metric uses).
  - `Balance` holds every tunable constant (mutable statics) — the one place to adjust ecosystem balance.
- **`engine`** — depends on `model`:
  - `RandomSource` is the single seeded RNG threaded everywhere; **no `Math.random()` in a run**, so a seed reproduces exactly.
  - `World` owns the tile grid, the animal list, the registry, and the deterministic `tick()` loop. One animal per tile (`Tile.resident` is the occupancy index; `Animal` x/y is the source of truth).
  - `Perception` scans the awareness-radius window (bounded, not O(n²)); `Behavior` is the per-animal decide/act logic (threat response → hunt → graze → reproduce → wander → rest, then upkeep).
- **`config`** (`SimSettings`), **`ui`**, **`report`** — populated in later phases.

### Invariants to preserve
- **Determinism:** all randomness flows through `World.rng()`; iteration is id-ordered before any seeded shuffle. Don't introduce `Math.random()`, `HashMap` iteration over model objects in the tick path, or wall-clock/threading-dependent behavior in the engine.
- **Layering:** `model` must not import `engine`/`ui`. `Balance` lives in `model` because both layers read it.
