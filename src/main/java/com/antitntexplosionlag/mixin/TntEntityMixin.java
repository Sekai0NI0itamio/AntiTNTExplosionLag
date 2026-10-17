package com.antitntexplosionlag.mixin;

import com.antitntexplosionlag.AntiTntExplosionLagMod;
import com.antitntexplosionlag.config.AntiTntConfig;
import com.antitntexplosionlag.explosion.ClusterMerge;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Intercepts primed-TNT explosions so an entire field detonates as ONE blast
 * instead of a fuse-by-fuse chain.
 *
 * <p>Mixing into {@code TntEntity.explode()} (Yarn 1.21.1: {@code method_6971
 * explode ()V}). At HEAD (cancellable): if clustering is on, every primed TNT
 * inside {@code mergeFieldRadius} is discarded and replaced by a single
 * vanilla explosion with combined power at the first TNT's position.
 *
 * <p>All world access happens on the server thread. No async work, no
 * background threads.
 */
@Mixin(TntEntity.class)
public abstract class TntEntityMixin {
    private static final Logger LOG = LoggerFactory.getLogger("AntiTNTExplosionLag");

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
        if (!cfg.masterEnabled || !cfg.clusterDetonationEnabled) {
            return;
        }
        TntEntity self = (TntEntity) (Object) this;
        World world = self.getWorld();
        if (world.isClient) {
            return;
        }

        long tick = AntiTntExplosionLagMod.serverTick();

        // Chain echo inside a fresh merged crater: discard, don't re-explode.
        if (AntiTntExplosionLagMod.WINDOWS.isAbsorbed(tick, self.getX(), self.getY(), self.getZ())) {
            ci.cancel();
            self.remove(Entity.RemovalReason.DISCARDED);
            AntiTntExplosionLagMod.CHAINS.onAbsorbed(1);
            AntiTntExplosionLagMod.LOG.debug("[AntiTNTExplosionLag] absorbed chain echo at ({},{},{})",
                    self.getX(), self.getY(), self.getZ());
            return;
        }

        // One bulk query: every primed TNT in range merges into this blast.
        double r = cfg.mergeFieldRadius;
        Box box = new Box(self.getX() - r, self.getY() - r, self.getZ() - r,
                self.getX() + r, self.getY() + r, self.getZ() + r);
        List<TntEntity> all = new ArrayList<>(
                world.getEntitiesByClass(TntEntity.class, box, Entity::isAlive));

        if (all.size() < cfg.clusterMinSize) {
            return; // vanilla chaining for small groups
        }

        double cx = self.getX();
        double cy = self.getY();
        double cz = self.getZ();
        float power = ClusterMerge.combinedPower(all.size(), 4.0f, cfg.clusterMaxPower);

        // Discard every clustered TNT so none of them can explode again later.
        for (TntEntity t : all) {
            t.remove(Entity.RemovalReason.DISCARDED);
        }

        AntiTntExplosionLagMod.CHAINS.onMerged(all.size());
        world.createExplosion(self, cx, cy, cz, power, World.ExplosionSourceType.TNT);

        if (cfg.mergeWindowTicks > 0) {
            AntiTntExplosionLagMod.WINDOWS.add(cx, cy, cz,
                    power * 2.0 + cfg.mergeWindowMargin, tick + cfg.mergeWindowTicks);
        }

        AntiTntExplosionLagMod.LOG.info("[AntiTNTExplosionLag] MERGED {} TNT into one power-{} "
                        + "blast at ({},{},{}) — no chain, one tick", all.size(),
                String.format("%.1f", power), cx, cy, cz);

        ci.cancel();
    }
}
