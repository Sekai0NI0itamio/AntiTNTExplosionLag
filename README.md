# AntiTNTExplosionLag

Server-side Fabric mod for Minecraft Java Edition 1.21.1 that makes even
million-TNT detonations resolve near-instantly: connected primed TNT merges
into single blasts, chain echoes are absorbed, cosmetics compress to a storm
ceiling (default 40 bursts), and mega-craters stage automatically.

## What it does

| Optimization | Default | Gameplay effect |
|---|---|---|
| Affected-block dedup after vanilla collection | ON | None — destroying one block twice equals destroying it once; only shrinks the apply loop |
| Per-tick cosmetic effect throttle via vanilla's own `affectWorld(particles=false)` path | ON | Cosmetic only — blocks still break, entities still take damage; only the duplicate particle/sound burst is skipped during same-tick storms |
| Explosion profiler (collect/affect timings, chain counters) + `/antitnt stats` | ON | None — observation only |
| Cluster detonation: connected primed TNT explodes as ONE merged blast | ON | Intended trade-off — one big crater immediately instead of N sequential vanilla chain blasts; disable for vanilla chaining |
| Merge windows: chain primers inside a fresh crater are absorbed, not chained | ON | Same trade-off — a fill goes off at once instead of echoing for seconds |
| Storm ceiling: at most 40 cosmetic bursts per explosion storm | ON | Cosmetic only — gameplay still applies; sounds/particles compressed |
| Auto-staging for mega-craters (>16k blocks) | ON | Safety net — streams block breaking so one tick can't freeze |
| Staged application of huge affected-block lists across ticks | OFF (opt-in) | **Changes timing** — delays block destruction and chain reactions |
| Primed-TNT chain monitoring | ON | None — counting only |

What it deliberately does **not** do: cap affected blocks, reduce explosion
power/radius, skip block updates, skip entity damage, run world mutations on
background threads, or add a second network compression layer. An inherently
huge explosion still has to destroy its blocks; the mod only removes
redundant work around that.

## Requirements

- Minecraft Java Edition **1.21.1**
- Fabric Loader **>= 0.16.14**
- Fabric API (any recent 1.21.1 build; tested with `0.102.0+1.21.1`)
- Java **21**
- Install on the **server** for the full effect (cluster merging, dedup,
  staged mode). Installing on the **client too** enables the client-side
  cosmetic throttle, which skips duplicate explosion bursts/sounds during
  storms — the main FPS saver on weak machines. Server-only install still
  fixes the server tick; client-only install still smooths rendering.

## Installation

1. Install Fabric Loader + Fabric API for 1.21.1 on your server.
2. Drop `antitntexplosionlag-<version>.jar` into `mods/`.
3. Start the server once to generate `config/antitntexplosionlag.json`.
4. Tune options (see `docs/CONFIG.md`), then `/antitnt reload`.

## Configuration

`config/antitntexplosionlag.json` (all options wired to real behavior):

```json
{
  "masterEnabled": true,
  "dedupEnabled": true,
  "effectThrottleEnabled": true,
  "maxEffectsPerTick": 4,
  "throttleRadius": 32.0,
  "stagedProcessingEnabled": false,
    "stagedBlocksPerTick": 2048,
    "stormEffectCap": 40,
    "stormGapTicks": 10,
    "autoStageThreshold": 16384,
    "autoStageBudget": 8192,
    "clusterDetonationEnabled": true,
    "clusterRadius": 7.0,
    "clusterMinSize": 3,
    "clusterMaxPower": 48.0,
    "mergeFieldRadius": 64.0,
    "exactClusterCap": 20000,
    "mergeWindowTicks": 10,
  "diagnosticsEnabled": false
}
```

Full semantics in [`docs/CONFIG.md`](docs/CONFIG.md).

## Commands (op level 2)

- `/antitnt status` — current configuration summary
- `/antitnt stats` — live chain + profiler numbers
- `/antitnt reload` — reload config from disk

## Performance measurement

- Helper-level microbenchmark (CI, reproducible):
  `gradle benchmark` → `build/reports/antitnt-benchmark/BENCHMARK-REPORT.md`
- Live profiling on your server: `/antitnt stats` (collect avg/p95, affect
  avg, TNT/explosions per tick, throttled effects).
- Methodology, scenarios, and the honest limits of headless-CI measurement:
  [`BENCHMARKS.md`](BENCHMARKS.md).

## Compatibility & limitations

- Mixin targets verified against Yarn `1.21.1+build.3`
  (`Explosion.collectBlocksAndDamageEntities/affectWorld/getAffectedBlocks/getPosition`,
  `TntEntity.tick`, `Entity.getWorld`, `World.isClient`).
- Staged mode is untested against every redstone contraption; enable it only
  if you accept delayed demolition.
- Full-server benchmarks cannot run reliably in headless CI; CI runs the
  helper benchmark + unit tests, and the report says so instead of inventing
  server numbers.

## Building

CI builds with Gradle 8.10.2 + Java 21 (`gradle build`, `gradle test`,
`gradle benchmark`). See `.github/workflows/`.

## License

MIT — see [LICENSE](LICENSE).
