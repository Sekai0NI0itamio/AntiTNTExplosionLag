package com.antitntexplosionlag;

import com.antitntexplosionlag.command.AntiTntCommands;
import com.antitntexplosionlag.config.AntiTntConfig;
import com.antitntexplosionlag.explosion.ChainMonitor;
import com.antitntexplosionlag.explosion.ClusterMerge;
import com.antitntexplosionlag.explosion.EffectThrottler;
import com.antitntexplosionlag.explosion.ExplosionProfiler;
import com.antitntexplosionlag.explosion.MergeWindows;import com.antitntexplosionlag.explosion.StagedProcessor;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Queue;
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

    /** Absorption left behind by merged blasts (kills block-fill chaining). */
    public static final MergeWindows WINDOWS = new MergeWindows();

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
        throttler = new EffectThrottler(cfg.maxEffectsPerTick, cfg.throttleRadius,
                cfg.stormEffectCap, cfg.stormGapTicks);
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
    public static void beginStaged(Explosion explosion, int budgetPerTick) {
        AntiTntConfig cfg = AntiTntConfig.get();
        List<BlockPos> all = new ArrayList<>(explosion.getAffectedBlocks());
        StagedProcessor<BlockPos> plan = new StagedProcessor<>(all, budgetPerTick);
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
        if (!AntiTntConfig.get().masterEnabled) {
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
     * Groups the primed-TNT field around {@code root} with ONE bulk entity
     * query (no per-entity BFS, so even huge fields resolve in one pass).
     * Small fields get exact transitive components; fields above the exact
     * cap become a single dense cluster. Server thread only.
     */
    public static List<List<TntEntity>> groupField(World world, TntEntity root) {
        AntiTntConfig cfg = AntiTntConfig.get();
        double r = cfg.mergeFieldRadius;
        Box box = new Box(root.getX() - r, root.getY() - r, root.getZ() - r,
                root.getX() + r, root.getY() + r, root.getZ() + r);
        List<TntEntity> all = new ArrayList<>(
                world.getEntitiesByClass(TntEntity.class, box, Entity::isAlive));
        if (all.size() < 2) {
            return List.of(all);
        }
        if (ClusterMerge.useCoarseGrouping(all.size(), cfg.exactClusterCap)) {
            return List.of(all);
        }
        IdentityHashMap<double[], TntEntity> byPos = new IdentityHashMap<>(all.size() * 2);
        List<double[]> points = new ArrayList<>(all.size());
        for (TntEntity t : all) {
            double[] p = new double[]{t.getX(), t.getY(), t.getZ()};
            points.add(p);
            byPos.put(p, t);
        }
        List<List<TntEntity>> groups = new ArrayList<>();
        for (List<double[]> comp : ClusterMerge.components(points, cfg.clusterRadius)) {
            List<TntEntity> group = new ArrayList<>(comp.size());
            for (double[] p : comp) {
                group.add(byPos.get(p));
            }
            groups.add(group);
        }
        return groups;
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
        if (cfg.mergeWindowTicks > 0) {
            WINDOWS.add(center[0], center[1], center[2],
                    power * 2.0 + cfg.mergeWindowMargin, serverTick + cfg.mergeWindowTicks);
        }
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
