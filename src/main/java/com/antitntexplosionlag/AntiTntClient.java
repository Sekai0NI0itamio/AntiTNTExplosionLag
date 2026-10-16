package com.antitntexplosionlag;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client entrypoint: advances the client tick so cosmetic throttle windows
 * roll over on pure clients (no integrated server). No rendering code is
 * replaced; the client mixin only reroutes duplicate bursts through
 * vanilla's own {@code affectWorld(false)} path.
 */
public class AntiTntClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> AntiTntExplosionLagMod.onClientTick());
    }
}
