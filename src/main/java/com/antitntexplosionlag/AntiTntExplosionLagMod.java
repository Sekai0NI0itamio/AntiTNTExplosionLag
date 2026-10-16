package com.antitntexplosionlag;

import com.antitntexplosionlag.command.AntiTntCommands;
import com.antitntexplosionlag.config.AntiTntConfig;
import com.antitntexplosionlag.explosion.ChainMonitor;
import com.antitntexplosionlag.explosion.EffectThrottler;
import com.antitntexplosionlag.explosion.ExplosionProfiler;
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
                        + "staged={} (budget={}) diag={}",
                cfg.masterEnabled, cfg.dedupEnabled, cfg.effectThrottleEnabled,
                cfg.maxEffectsPerTick, cfg.throttleRadius,
                cfg.stagedProcessingEnabled, cfg.stagedBlocksPerTick, cfg.diagnosticsEnabled);
    }
}
