# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Plain `javac`/`java` project — no build tool (Maven/Gradle were removed; nothing in the code depends on external libraries).

```sh
# Compile all sources into bin/
javac -d bin src/*.java

# Run the entry point
java -cp bin Main
```

There is no test suite yet.

## Architecture

All source lives flat in `src/` (default package, no subpackages). The simulation models animals with evolvable genetic traits on a tile-based map:

- **`Gene`** (`src/Gene.java`) — a single evolvable trait: holds `value`, `min`/`max` bounds, and a `variation` amount used by `mutate()` to randomly perturb the value each generation. Also provides scaling helpers (`convToDoubleScale`, `convToIntScale`, `intScaleOffset`) that map a gene's value onto an arbitrary output range — used elsewhere to turn genetic values into discrete choices (e.g. name generation).
- **`Animal`** (`src/Animal.java`) — holds a `Gene[]` array. Fixed integer indices (`size=0`, `diet=1`, `speed=2`, `tolerance=3`) are used to pull specific genes out of that array, so gene order matters and is currently assumed rather than enforced. `reproduce()` mutates all genes in place. `calcName()` procedurally generates a name from gene values via `Gene.convToIntScale`/`intScaleOffset`.
- **`Tile`** (`src/Tile.java`) — a map cell with `foliage`/`fertility` that animals graze (`graze()`) and that regenerates over time (`recover()`). Holds a reference to its current `resident` `Animal`.
- **`SimSettings`** (`src/SimSettings.java`) and **`Tools`** (`src/Tools.java`) — stubs, not yet implemented.
- **`Main`** (`src/Main.java`) — entry point; currently just builds a random gene set and prints one `Animal`.

### Known issue

`Animal.calcName()` (`src/Animal.java`) loops `i` up to `numGenes * 2` while indexing directly into `genes[i]`, which only has `numGenes` elements — this throws `ArrayIndexOutOfBoundsException` once `i >= numGenes`. This is a pre-existing bug from the original implementation, not a build/config issue.
