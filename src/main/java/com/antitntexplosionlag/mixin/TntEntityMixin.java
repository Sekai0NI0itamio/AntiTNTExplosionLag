package com.antitntexplosionlag.mixin;

import com.antitntexplosionlag.AntiTntExplosionLagMod;
import com.antitntexplosionlag.config.AntiTntConfig;
import net.minecraft.entity.TntEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts primed-TNT ticks for chain-reaction diagnostics.
 * Observation only: fuse timing, explosion power, and chaining are untouched.
 * Server side only (client ticks are ignored) so stats reflect real load.
 */
@Mixin(TntEntity.class)
public abstract class TntEntityMixin {
    @Shadow
    public abstract World getWorld();

    @Inject(method = "tick", at = @At("HEAD"))
    private void antiTnt$countPrimed(CallbackInfo ci) {
        if (!AntiTntConfig.get().masterEnabled) {
            return;
        }
        if (getWorld().isClient) {
            return;
        }
        AntiTntExplosionLagMod.CHAINS.onTntTick(AntiTntExplosionLagMod.serverTick());
    }
}
