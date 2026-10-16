package com.antitntexplosionlag.mixin;

import com.antitntexplosionlag.AntiTntExplosionLagMod;
import com.antitntexplosionlag.config.AntiTntConfig;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side cosmetic throttle. Runs for explosions the client renders
 * from server packets: when more than the per-tick budget lands in one
 * cell, the extras render through vanilla's own
 * {@code affectWorld(false)} path (no burst, no sound). Block breaking and
 * damage are server-authoritative and untouched here — this mixin only ever
 * removes duplicate eye-candy that freezes weak GPUs during TNT storms.
 *
 * <p>Never dedups, never stages, never cancels destruction: destruction on
 * the client is a visual echo of server packets, and this mixin does not
 * decide what breaks.
 */
@Mixin(Explosion.class)
public abstract class ClientExplosionMixin {
    @Shadow
    public abstract Vec3d getPosition();

    @Shadow
    public abstract void affectWorld(boolean particles);

    @Inject(method = "affectWorld", at = @At("HEAD"), cancellable = true)
    private void antiTnt$clientThrottle(boolean particles, CallbackInfo ci) {
        AntiTntConfig cfg = AntiTntConfig.get();
        if (!cfg.masterEnabled || !cfg.effectThrottleEnabled || !particles) {
            return;
        }
        Vec3d center = getPosition();
        boolean emit = AntiTntExplosionLagMod.throttler().admit(
                AntiTntExplosionLagMod.effectiveTick(), center.x, center.y, center.z);
        if (!emit) {
            ci.cancel();
            affectWorld(false);
        }
    }
}
