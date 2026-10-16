# Benchmarks

## What CI measures (reproducible)

`gradle benchmark` (also run by `.github/workflows/benchmark.yml`) executes
`BenchmarkRunner`, a pure-Java microbenchmark with no Minecraft classes:

1. **Exposure calculation** — naive (per-sample allocation) vs optimized
   (precomputed direction table, primitives) over the 16-resolution shell.
   Asserts identical verdicts; reports both averages and speedup.
2. **Affected-list dedup** — 20,000 unique + 35% duplicates synthetic
   overlapping-blast workload; reports removed entries and avg pass time.
3. **Cluster grouping** — 9,261-point solid fill resolves into exactly 1
   component; 100k-member centroid pass for the coarse (millions) path.
   Storm-cap and merge-window logic are covered by unit tests
   (long-storm suppression, gap reset, absorb/expire/prune).
4. **Scenario workload specs** — the five representative scenarios
   (small-isolated, large-detonation, chain-reaction, overlapping-confined,
   repeated-stress) as deterministic workload descriptors.

Report: `build/reports/antitnt-benchmark/BENCHMARK-REPORT.md` (uploaded as
the `antitntexplosionlag-benchmark` workflow artifact).

## What CI does NOT measure (limitation)

A full Minecraft dedicated-server benchmark (world load, TNT ignition,
tick-time percentiles, packet counts) cannot run reliably in headless CI
runners: no GPU, throttled CPU, no stable baseline. We report this
limitation instead of presenting microbenchmarks as server proof.

## How to measure on a real server

1. Install the mod on a test server (Paper/Vanilla/Fabric 1.21.1, Java 21).
2. Build a flat test world; run each scenario 5+ times with and without the
   mod (same seed, same TNT layout, same `maxEffectsPerTick`).
3. Record with `/antitnt stats` after each run: `collectAvg/collectP95`,
   `affectAvg`, explosions/tick, throttled effects; plus external tick-time
   sampling (`/debug` or Spark profiler).
4. Compare medians, not single runs.

## Anti-cheating rules for numbers

- Never compare different seeds, layouts, or hardware and call it a mod win.
- The helper speedup is real but small per call; its server impact scales
  with entity counts and overlap, which only live measurement can confirm.
