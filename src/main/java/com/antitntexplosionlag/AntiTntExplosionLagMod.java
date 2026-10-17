package com.antitntexplosionlag;

import com.antitntexplosionlag.command.AntiTntCommands;
import com.antitntexplosionlag.config.AntiTntConfig;
import com.antitntexplosionlag.explosion.ChainMonitor;
import com.antitntexplosionlag.explosion.EffectThrottler;
import com.antitntexplosionlag.explosion.ExplosionProfiler;
import com.antitntexplosionlag.explosion.MergeWindows;
import com.antitntexplosionlag.explosion.StagedProcessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.Explosion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
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

    @Override
    public void onInitialize() {
        AntiTntConfig.load();
        rebuildThrottler();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                AntiTntCommands.register(dispatcher));
        ServerTickEvents.START_SERVER_TICK.register(server -> serverTick = server.getTicks());
        ServerTickEvents.END_SERVER_TICK.register(server -> drainStaged());
        AntiTntConfig cfg = AntiTntConfig.get();
        LOG.info("================================================");
        LOG.info("[AntiTNTExplosionLag] MOD ACTIVE — version 1.2.0");
        LOG.info("[AntiTNTExplosionLag] master={} dedup={} throttle={} (maxFx={} radius={})",
                cfg.masterEnabled, cfg.dedupEnabled, cfg.effectThrottleEnabled,
                cfg.maxEffectsPerTick, cfg.throttleRadius);
        LOG.info("[AntiTNTExplosionLag] clusterDetonation={} (radius={} min={} maxPower={}) "
                        + "fieldRadius={} windowTicks={} margin={}",
                cfg.clusterDetonationEnabled, cfg.clusterRadius, cfg.clusterMinSize,
                cfg.clusterMaxPower, cfg.mergeFieldRadius, cfg.mergeWindowTicks,
                cfg.mergeWindowMargin);
        LOG.info("[AntiTNTExplosionLag] stormEffectCap={} stormGapTicks={} "
                        + "autoStageThreshold={} autoStageBudget={}",
                cfg.stormEffectCap, cfg.stormGapTicks,
                cfg.autoStageThreshold, cfg.autoStageBudget);
        LOG.info("[AntiTNTExplosionLag] Use /antitnt status to verify, /antitnt debug for internals.");
        LOG.info("================================================");
    }
}
