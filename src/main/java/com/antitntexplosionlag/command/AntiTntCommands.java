package com.antitntexplosionlag.command;

import com.antitntexplosionlag.AntiTntExplosionLagMod;
import com.antitntexplosionlag.config.AntiTntConfig;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /antitnt status|stats|reload} — read-only diagnostics plus config
 * reload. Requires operator level 2; never mutates the world.
 */
public final class AntiTntCommands {
    private AntiTntCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("antitnt")
                .requires(src -> src.hasPermissionLevel(2))
                .then(literal("status").executes(ctx -> {
                    AntiTntConfig cfg = AntiTntConfig.get();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                            "[AntiTNT] master=" + cfg.masterEnabled
                                    + " dedup=" + cfg.dedupEnabled
                                    + " throttle=" + cfg.effectThrottleEnabled
                                    + " (maxFx=" + cfg.maxEffectsPerTick
                                    + " radius=" + cfg.throttleRadius + ")"
                                    + " staged=" + cfg.stagedProcessingEnabled
                                    + " (budget=" + cfg.stagedBlocksPerTick + ")"
                                    + " diag=" + cfg.diagnosticsEnabled), false);
                    return 1;
                }))
                .then(literal("stats").executes(ctx -> {
                    long tick = AntiTntExplosionLagMod.serverTick();
                    String msg = "[AntiTNT] tick=" + tick
                            + " tntThisTick=" + AntiTntExplosionLagMod.CHAINS.tntThisTick(tick)
                            + " explosionsThisTick=" + AntiTntExplosionLagMod.CHAINS.explosionsThisTick(tick)
                            + " maxTnt=" + AntiTntExplosionLagMod.CHAINS.maxTntPerTick()
                            + " maxExplosions=" + AntiTntExplosionLagMod.CHAINS.maxExplosionsPerTick()
                            + " throttledFx=" + AntiTntExplosionLagMod.throttler().suppressedTotal()
                            + " stagedPending=" + AntiTntExplosionLagMod.stagedPending()
                            + " collectAvg=" + String.format("%.2f", AntiTntExplosionLagMod.COLLECT_PROFILER.averageMs()) + "ms"
                            + " collectP95=" + String.format("%.2f", AntiTntExplosionLagMod.COLLECT_PROFILER.percentileMs(95)) + "ms"
                            + " affectAvg=" + String.format("%.2f", AntiTntExplosionLagMod.AFFECT_PROFILER.averageMs()) + "ms";
                    ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
                    return 1;
                }))
                .then(literal("reload").executes(ctx -> {
                    AntiTntConfig.load();
                    AntiTntExplosionLagMod.rebuildThrottler();
                    ctx.getSource().sendFeedback(() -> Text.literal("[AntiTNT] config reloaded."), true);
                    return 1;
                })));
    }
}
