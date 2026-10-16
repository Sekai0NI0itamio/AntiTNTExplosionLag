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

## Which side installs the mod

Server only. All hooks are server hooks (`server` mixin set); nothing
requires clients to install anything, and no feature reads client state.

## Staged mode warning

Enabling `stagedProcessingEnabled` means a 20,000-block crater finishes over
~10 ticks at the default budget instead of one. Redstone contraptions keyed
to instant demolition will behave differently. That is the documented price
of spreading inherently expensive work, not a transparent optimization.
