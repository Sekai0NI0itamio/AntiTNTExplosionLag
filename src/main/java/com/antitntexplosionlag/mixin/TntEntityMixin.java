package com.antitntexplosionlag.mixin;

import com.antitntexplosionlag.AntiTntExplosionLagMod;
import com.antitntexplosionlag.config.AntiTntConfig;
import net.minecraft.entity.TntEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Two observation/interception points on primed TNT (server side only):
 *
 * <ul>
 *   <li>{@code tick} HEAD — counts primed-TNT ticks for chain diagnostics.
 *   Observation only: fuse timing and chaining are untouched.</li>
 *   <li>{@code explode} HEAD (cancellable) — cluster takeover. When enabled
 *   and at least {@code clusterMinSize} primed TNT are transitively
 *   connected, they detonate together as ONE vanilla explosion with combined
 *   power instead of a sequential second-by-second chain. Smaller groups fall
 *   through to vanilla chaining. No world/client checks beyond the
 *   dedicated-server reality: on servers every tick counted is real.</li>
 * </ul>
 *
 * <p>Member access uses plain casts (no {@code @Shadow} on inherited
 * members): calls like {@code getWorld()} resolve against
 * {@code net.minecraft.entity.Entity} at compile time and need no Mixin
 * validation.
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
    private void antiTnt$clusterTakeover(CallbackInfo ci) {
        AntiTntConfig cfg = AntiTntConfig.get();
        if (!cfg.masterEnabled || !cfg.clusterDetonationEnabled) {
            return;
        }
        TntEntity self = (TntEntity) (Object) this;
        World world = self.getWorld();
        if (world.isClient) {
            return;
        }
        List<TntEntity> cluster = AntiTntExplosionLagMod.collectCluster(
                world, self, cfg.clusterRadius, cfg.clusterMaxScan);
        if (cluster.size() < cfg.clusterMinSize) {
            return;
        }
        ci.cancel();
        AntiTntExplosionLagMod.mergeAndDetonate(world, cluster, self);
    }
}
