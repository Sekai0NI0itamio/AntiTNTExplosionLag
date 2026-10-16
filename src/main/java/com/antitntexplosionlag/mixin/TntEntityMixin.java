package com.antitntexplosionlag.mixin;

import com.antitntexplosionlag.AntiTntExplosionLagMod;
import com.antitntexplosionlag.config.AntiTntConfig;
import net.minecraft.entity.TntEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts primed-TNT ticks for chain-reaction diagnostics.
 * Observation only: fuse timing, explosion power, and chaining are untouched.
 *
 * <p>No world/client check is performed on purpose: on dedicated servers
 * (the deployment target) there is no client world, so every counted tick is
 * a real server tick. On integrated client+server sessions the counters are
 * approximate diagnostics, never gameplay logic.
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
}
