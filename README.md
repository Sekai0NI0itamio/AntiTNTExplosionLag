# AntiTNTExplosionLag

Server-side Fabric mod for Minecraft Java Edition 1.21.1 that reduces server
tick lag from large TNT detonations, chained explosions, and overlapping
explosive workloads. Defaults preserve vanilla gameplay.

## What it does

| Optimization | Default | Gameplay effect |
|---|---|---|
| Affected-block dedup after vanilla collection | ON | None — destroying one block twice equals destroying it once; only shrinks the apply loop |
| Per-tick cosmetic effect throttle via vanilla's own `affectWorld(particles=false)` path | ON | Cosmetic only — blocks still break, entities still take damage; only the duplicate particle/sound burst is skipped during same-tick storms |
| Explosion profiler (collect/affect timings, chain counters) + `/antitnt stats` | ON | None — observation only |
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
- Install on the **server only**. Clients do not need the mod.

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
