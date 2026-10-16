package com.antitntexplosionlag.mixin;

import com.antitntexplosionlag.AntiTntExplosionLagMod;
import com.antitntexplosionlag.config.AntiTntConfig;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hooks the two vanilla explosion phases (verified against Yarn 1.21.1
 * {@code Explosion}: {@code collectBlocksAndDamageEntities()V},
 * {@code affectWorld(Z)V}, {@code getAffectedBlocks()},
 * {@code getPosition()}).
 *
 * <ul>
 *   <li>Profiles both phases into lock-free-read ring buffers.</li>
 *   <li>Dedups the affected-block list after collection (same end state:
 *   destroying one block twice equals destroying it once).</li>
 *   <li>Reroutes cosmetic-throttled explosions through vanilla's own
 *   {@code affectWorld(false)} path: blocks still break, entities still take
 *   damage, only the particle/sound burst is skipped. Sync packets untouched.</li>
 * </ul>
 * No world mutation ever leaves the server thread; no background threads.
 */
@Mixin(Explosion.class)
public abstract class ExplosionCollectMixin {
    @Shadow
    public abstract List<BlockPos> getAffectedBlocks();

    @Shadow
    public abstract Vec3d getPosition();

    @Shadow
    public abstract void affectWorld(boolean particles);

    @Unique
    private long antiTnt$phaseStartNanos;

    @Inject(method = "collectBlocksAndDamageEntities", at = @At("HEAD"))
    private void antiTnt$beforeCollect(CallbackInfo ci) {
        AntiTntConfig cfg = AntiTntConfig.get();
        if (!cfg.masterEnabled) {
            antiTnt$phaseStartNanos = 0L;
            return;
        }
        AntiTntExplosionLagMod.CHAINS.onExplosion(AntiTntExplosionLagMod.serverTick());
        antiTnt$phaseStartNanos = System.nanoTime();
    }

    @Inject(method = "collectBlocksAndDamageEntities", at = @At("TAIL"))
    private void antiTnt$afterCollect(CallbackInfo ci) {
        AntiTntConfig cfg = AntiTntConfig.get();
        if (!cfg.masterEnabled || antiTnt$phaseStartNanos == 0L) {
            return;
        }
        AntiTntExplosionLagMod.COLLECT_PROFILER.record(System.nanoTime() - antiTnt$phaseStartNanos);
        antiTnt$phaseStartNanos = 0L;
        if (!cfg.dedupEnabled) {
            return;
        }
        List<BlockPos> blocks = getAffectedBlocks();
        if (blocks.size() < 2) {
            return;
        }
        // Order-preserving in-place dedup. BlockPos is value-compared, so
        // two entries for one coordinate collapse with zero gameplay delta.
        Set<BlockPos> seen = new HashSet<>(blocks.size() * 2);
        int w = 0;
        for (int r = 0; r < blocks.size(); r++) {
            BlockPos pos = blocks.get(r);
            if (seen.add(pos)) {
                blocks.set(w++, pos);
            }
        }
        if (w < blocks.size()) {
            int removed = blocks.size() - w;
            blocks.subList(w, blocks.size()).clear();
            if (cfg.diagnosticsEnabled) {
                AntiTntExplosionLagMod.LOG.debug("[AntiTNT] dedup removed {} duplicate blocks", removed);
            }
        }
    }

    @Inject(method = "affectWorld", at = @At("HEAD"), cancellable = true)
    private void antiTnt$beforeAffect(boolean particles, CallbackInfo ci) {
        AntiTntConfig cfg = AntiTntConfig.get();
        if (!cfg.masterEnabled) {
            antiTnt$phaseStartNanos = 0L;
            return;
        }
        antiTnt$phaseStartNanos = System.nanoTime();
        int size = getAffectedBlocks().size();
        // Opt-in staged mode takes over over-budget explosions, and the
        // automatic safety net catches mega-craters even when staged mode
        // is off: the first chunk is destroyed immediately with full vanilla
        // semantics, the rest drains one chunk per server tick (see drain).
        if (cfg.stagedProcessingEnabled && size > cfg.stagedBlocksPerTick) {
            ci.cancel();
            AntiTntExplosionLagMod.beginStaged((Explosion) (Object) this, cfg.stagedBlocksPerTick);
            return;
        }
        if (cfg.autoStageThreshold > 0 && size > cfg.autoStageThreshold) {
            ci.cancel();
            AntiTntExplosionLagMod.beginStaged((Explosion) (Object) this, cfg.autoStageBudget);
            return;
        }
        if (!particles || !cfg.effectThrottleEnabled) {
            return;
        }
        Vec3d center = getPosition();
        boolean emit = AntiTntExplosionLagMod.throttler().admit(
                AntiTntExplosionLagMod.serverTick(), center.x, center.y, center.z);
        if (!emit) {
            // Vanilla-supported cosmetic skip: same blocks, same damage, no burst.
            // Re-entrant call carries particles=false, so this hook returns early.
            ci.cancel();
            affectWorld(false);
        }
    }

    @Inject(method = "affectWorld", at = @At("TAIL"))
    private void antiTnt$afterAffect(CallbackInfo ci) {
        if (!AntiTntConfig.get().masterEnabled || antiTnt$phaseStartNanos == 0L) {
            return;
        }
        AntiTntExplosionLagMod.AFFECT_PROFILER.record(System.nanoTime() - antiTnt$phaseStartNanos);
        antiTnt$phaseStartNanos = 0L;
    }
}
