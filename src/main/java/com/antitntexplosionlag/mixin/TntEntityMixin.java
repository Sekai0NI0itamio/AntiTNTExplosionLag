package com.antitntexplosionlag.mixin;

import com.antitntexplosionlag.AntiTntExplosionLagMod;
import com.antitntexplosionlag.config.AntiTntConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Two interception points on primed TNT (server side only):
 *
 * <ul>
 *   <li>{@code tick} HEAD — counts primed-TNT ticks for chain diagnostics.
 *   Observation only.</li>
 *   <li>{@code explode} HEAD (cancellable) — instant field detonation:
 *     <ol>
 *       <li>Inside a live merge window (fresh merged crater): the primer is
 *       a chain echo — discard it, count it absorbed, no new blast.</li>
 *       <li>Otherwise, when clustering is enabled: one bulk query groups the
 *       whole primed field, every qualifying group detonates as ONE merged
 *       blast immediately. Millions resolve in a single pass (dense fields
 *       skip transitive precision by design).</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <p>Member access uses plain casts (no {@code @Shadow} on inherited
 * members): calls like {@code getWorld()} resolve against
 * {@code net.minecraft.entity.Entity} at compile time.
 */
@Mixin(TntEntity.class)
public abstract class TntEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void antiTnt$countPrimed(CallbackInfo ci) {
        if (!AntiTntConfig.get().masterEnabled) {
            return;
        }
        AntiTntExplosionLagMod.CHAINS.onTntTick(AntiTntExplosionLagMod.serverTick());
    }

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void antiTnt$fieldDetonation(CallbackInfo ci) {
        AntiTntConfig cfg = AntiTntConfig.get();
        if (!cfg.masterEnabled) {
            return;
        }
        TntEntity self = (TntEntity) (Object) this;
        World world = self.getWorld();
        if (world.isClient) {
            return;
        }
        long tick = AntiTntExplosionLagMod.serverTick();
        if (AntiTntExplosionLagMod.WINDOWS.isAbsorbed(tick, self.getX(), self.getY(), self.getZ())) {
            ci.cancel();
            self.remove(Entity.RemovalReason.DISCARDED);
            AntiTntExplosionLagMod.CHAINS.onAbsorbed(1);
            return;
        }
        if (!cfg.clusterDetonationEnabled) {
            return;
        }
        boolean rootMerged = false;
        for (List<TntEntity> group : AntiTntExplosionLagMod.groupField(world, self)) {
            if (group.size() < cfg.clusterMinSize || group.isEmpty()) {
                continue;
            }
            if (group.contains(self)) {
                rootMerged = true;
            }
            AntiTntExplosionLagMod.mergeAndDetonate(world, group, self);
        }
        if (rootMerged) {
            ci.cancel();
        }
    }
}
