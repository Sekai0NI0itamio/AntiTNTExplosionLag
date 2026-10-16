# Configuration

File: `config/antitntexplosionlag.json`. Reload live with `/antitnt reload`.
Invalid numbers are clamped into range; corrupt files keep current defaults.

| Key | Type | Default | Range | Behavior |
|---|---|---|---|---|
| `masterEnabled` | bool | `true` | — | Kill switch. `false` = all mixins idle, baseline behavior. |
| `dedupEnabled` | bool | `true` | — | Order-preserving duplicate removal on the affected-block list after vanilla collection. No gameplay delta. |
| `effectThrottleEnabled` | bool | `true` | — | Same-tick cosmetic merging. Throttled explosions use vanilla `affectWorld(false)`: blocks break, damage applies, only the extra burst is skipped. Sync packets never suppressed. |
| `maxEffectsPerTick` | int | `4` | 1–64 | Cosmetic bursts allowed per tick per throttle cell before merging. |
| `throttleRadius` | double | `32.0` | 4–256 | Cell size (blocks) for grouping same-tick explosions. |
| `stagedProcessingEnabled` | bool | `false` | — | **Opt-in trade-off.** Over-budget explosions are split in vanilla order: the first chunk is destroyed immediately with full effects, further chunks drain one per server tick (up to 4 stages per tick) with the cosmetic burst off. Entity damage still applies once up front; per-block destruction uses vanilla logic per chunk, so drops match per position but the crater finishes late. |
| `stagedBlocksPerTick` | int | `2048` | 64–65536 | Chunk budget for staged mode; also the takeover threshold. |
| `diagnosticsEnabled` | bool | `false` | — | Extra debug logging (dedup counts). `/antitnt stats` works regardless. |

| `clusterDetonationEnabled` | bool | `true` | — | Merge transitively connected primed TNT into one blast (combined power `4*cbrt(n)`, capped). One big crater at once instead of a sequential chain. Set `false` for vanilla chaining. |
| `clusterRadius` | double | `7.0` | 2–16 | Connection distance for cluster membership (transitive). |
| `clusterMinSize` | int | `3` | 2–64 | Minimum connected TNT to trigger a merge; pairs/singles explode vanilla-style. |
| `clusterMaxPower` | float | `48.0` | 8–128 | Power cap for merged blasts. |
| `mergeFieldRadius` | double | `64.0` | 8–128 | Half-edge of the cube scanned for primed TNT around each ignition. |
| `exactClusterCap` | int | `20000` | 1024–65536 | Fields above this size skip transitive precision (one dense cluster). |
| `mergeWindowTicks` | int | `10` | 0–100 | Ticks a merged crater absorbs chain primers (`0` = no absorption). |
| `mergeWindowMargin` | double | `8.0` | 0–64 | Extra radius beyond blast reach covered by the window. |
| `stormEffectCap` | int | `40` | 1–256 | Max cosmetic bursts per explosion storm (sound/particle ceiling). |
| `stormGapTicks` | int | `10` | 2–100 | Quiet gap that ends a storm and resets the ceiling. |
| `autoStageThreshold` | int | `16384` | 0–1048576 | Affected lists above this auto-stage (`0` = off). |
| `autoStageBudget` | int | `8192` | 64–65536 | Per-tick block budget for automatic staging. |

## Which side installs the mod

Server install gives the full effect (cluster merging, dedup, staged mode,
profiler). Client install adds the client-side cosmetic throttle: duplicate
explosion bursts/sounds during storms are skipped locally, which is the main
FPS saver. Either side works alone; both together is best.

## Staged mode warning

Enabling `stagedProcessingEnabled` means a 20,000-block crater finishes over
~10 ticks at the default budget instead of one. Redstone contraptions keyed
to instant demolition will behave differently. That is the documented price
of spreading inherently expensive work, not a transparent optimization.
