package com.antitntexplosionlag;

import com.antitntexplosionlag.command.AntiTntCommands;
import com.antitntexplosionlag.config.AntiTntConfig;
import com.antitntexplosionlag.explosion.ChainMonitor;
import com.antitntexplosionlag.explosion.ClusterMerge;
import com.antitntexplosionlag.explosion.EffectThrottler;
import com.antitntexplosionlag.explosion.ExplosionProfiler;
import com.antitntexplosionlag.explosion.StagedProcessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-side entrypoint. Registers config, tick bookkeeping, and commands.
 * All world mutation stays on the server thread; this class never spawns
 * worker threads and never touches the world off-thread.
 */
public class AntiTntExplosionLagMod implements ModInitializer {
    public static final String MOD_ID = "antitntexplosionlag";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static final ExplosionProfiler COLLECT_PROFILER = new ExplosionProfiler(512);
    public static final ExplosionProfiler AFFECT_PROFILER = new ExplosionProfiler(512);
    public static final ChainMonitor CHAINS = new ChainMonitor();

    private static volatile EffectThrottler throttler;
    private static final AtomicLong EXPLOSION_SEQ = new AtomicLong();
    private static volatile long serverTick;
    private static volatile long clientTick;

    /** Pending staged remainders; appended and drained on the server thread. */
    private static final Queue<PendingStage> STAGES = new ArrayDeque<>();

    private record PendingStage(Explosion explosion, StagedProcessor<BlockPos> rest) {
    }

    public static EffectThrottler throttler() {
        EffectThrottler t = throttler;
        if (t == null) {
            synchronized (AntiTntExplosionLagMod.class) {
                t = throttler;
                if (t == null) {
                    t = rebuildThrottler();
                }
            }
        }
        return t;
    }

    /** Rebuilds the throttle policy from current config (also resets counters). */
    public static synchronized EffectThrottler rebuildThrottler() {
        AntiTntConfig cfg = AntiTntConfig.get();
        throttler = new EffectThrottler(cfg.maxEffectsPerTick, cfg.throttleRadius);
        return throttler;
    }

    public static long nextExplosionSeq() {
        return EXPLOSION_SEQ.incrementAndGet();
    }

    public static long serverTick() {
        return serverTick;
    }

    /** Advanced by the client entrypoint; drives client-side effect windows. */
    public static void onClientTick() {
        clientTick++;
    }

    /**
     * Tick basis for cosmetic windows. The dedicated server uses the server
     * tick; a pure client (no integrated server) uses the client tick so
     * throttle budgets still roll over instead of wedging shut.
     */
    public static long effectiveTick() {
        return Math.max(serverTick, clientTick);
    }

    public static int stagedPending() {
        synchronized (STAGES) {
            return STAGES.size();
        }
    }

    /**
     * Takes over an over-budget explosion: destroys the first chunk right now
     * with full vanilla semantics and queues the rest, one chunk per tick.
     * Called on the server thread from the mixin with the vanilla call
     * already cancelled. Re-entrant {@code affectWorld} calls for the first
     * chunk carry a within-budget list, so they proceed normally.
     */
    public static void beginStaged(Explosion explosion) {
        AntiTntConfig cfg = AntiTntConfig.get();
        List<BlockPos> all = new ArrayList<>(explosion.getAffectedBlocks());
        StagedProcessor<BlockPos> plan = new StagedProcessor<>(all, cfg.stagedBlocksPerTick);
        List<BlockPos> first = plan.nextChunk();
        explosion.getAffectedBlocks().clear();
        explosion.getAffectedBlocks().addAll(first);
        if (!plan.isComplete()) {
            synchronized (STAGES) {
                STAGES.add(new PendingStage(explosion, plan));
            }
            if (cfg.diagnosticsEnabled) {
                LOG.info("[AntiTNTExplosionLag] staged {} blocks over ~{} ticks",
                        plan.total(), plan.ticksRequired());
            }
        }
        explosion.affectWorld(true);
    }

    private static void drainStaged() {
        if (!AntiTntConfig.get().masterEnabled || !AntiTntConfig.get().stagedProcessingEnabled) {
            return;
        }
        // Bound the catch-up work per tick so the drain itself can't spike.
        int stagesThisTick = 0;
        while (stagesThisTick < 4) {
            PendingStage head;
            synchronized (STAGES) {
                head = STAGES.peek();
            }
            if (head == null) {
                return;
            }
            List<BlockPos> chunk = head.rest.nextChunk();
            if (chunk.isEmpty()) {
                synchronized (STAGES) {
                    STAGES.poll();
                }
                continue;
            }
            try {
                head.explosion.getAffectedBlocks().clear();
                head.explosion.getAffectedBlocks().addAll(chunk);
                // Damage was already applied once during collection; only the
                // block destruction (with vanilla per-block semantics) repeats.
                head.explosion.affectWorld(false);
            } catch (Exception e) {
                LOG.warn("[AntiTNTExplosionLag] dropping staged remainder: {}", e.toString());
                synchronized (STAGES) {
                    STAGES.remove(head);
                }
                return;
            }
            if (head.rest.isComplete()) {
                synchronized (STAGES) {
                    STAGES.remove(head);
                }
            }
            stagesThisTick++;
        }
    }

    /**
     * Finds the transitively connected cluster of primed TNT around
     * {@code root}: every member is within {@code radius} of at least one
     * other member. Bounded by {@code maxScan} visited entities so a huge
     * field can't stall the tick during the scan itself. Server thread only.
     */
    public static List<TntEntity> collectCluster(World world, TntEntity root, double radius, int maxScan) {
        List<TntEntity> found = new ArrayList<>();
        Set<TntEntity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<TntEntity> queue = new ArrayDeque<>();
        seen.add(root);
        queue.add(root);
        found.add(root);
        double r2 = radius * radius;
        while (!queue.isEmpty() && found.size() < maxScan) {
            TntEntity cur = queue.poll();
            double cx = cur.getX();
            double cy = cur.getY();
            double cz = cur.getZ();
            Box box = new Box(cx - radius, cy - radius, cz - radius,
                    cx + radius, cy + radius, cz + radius);
            for (TntEntity t : world.getEntitiesByClass(TntEntity.class, box, Entity::isAlive)) {
                if (!seen.add(t)) {
                    continue;
                }
                double dx = t.getX() - cx;
                double dy = t.getY() - cy;
                double dz = t.getZ() - cz;
                if (dx * dx + dy * dy + dz * dz > r2) {
                    continue;
                }
                found.add(t);
                queue.add(t);
                if (found.size() >= maxScan) {
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Replaces a connected TNT cluster with a single merged blast at its
     * centroid. All members are discarded first, then one vanilla explosion
     * with combined power runs the normal collect/affect path (so dedup,
     * throttle, profiler, and staged logic all still apply to it).
     *
     * @return number of TNT merged
     */
    public static int mergeAndDetonate(World world, List<TntEntity> cluster, TntEntity cause) {
        AntiTntConfig cfg = AntiTntConfig.get();
        List<double[]> points = new ArrayList<>(cluster.size());
        for (TntEntity t : cluster) {
            points.add(new double[]{t.getX(), t.getY(), t.getZ()});
        }
        double[] center = ClusterMerge.centroid(points);
        float power = ClusterMerge.combinedPower(cluster.size(), 4.0f, cfg.clusterMaxPower);
        for (TntEntity t : cluster) {
            t.remove(Entity.RemovalReason.DISCARDED);
        }
        CHAINS.onMerged(cluster.size());
        if (cfg.diagnosticsEnabled) {
            LOG.info("[AntiTNTExplosionLag] merged {} connected TNT into one power-{} blast",
                    cluster.size(), String.format("%.1f", power));
        }
        world.createExplosion(cause, center[0], center[1], center[2], power,
                World.ExplosionSourceType.TNT);
        return cluster.size();
    }

    @Override
    public void onInitialize() {
        AntiTntConfig.load();
        rebuildThrottler();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                AntiTntCommands.register(dispatcher));
        ServerTickEvents.START_SERVER_TICK.register(server -> serverTick = server.getTicks());
        ServerTickEvents.END_SERVER_TICK.register(server -> drainStaged());
        AntiTntConfig cfg = AntiTntConfig.get();
        LOG.info("[AntiTNTExplosionLag] initialized: master={} dedup={} throttle={} (maxFx={} radius={}) "
                        + "staged={} (budget={}) cluster={} (radius={} min={} maxPower={}) diag={}",
                cfg.masterEnabled, cfg.dedupEnabled, cfg.effectThrottleEnabled,
                cfg.maxEffectsPerTick, cfg.throttleRadius,
                cfg.stagedProcessingEnabled, cfg.stagedBlocksPerTick,
                cfg.clusterDetonationEnabled, cfg.clusterRadius, cfg.clusterMinSize,
                cfg.clusterMaxPower, cfg.diagnosticsEnabled);
    }
}
