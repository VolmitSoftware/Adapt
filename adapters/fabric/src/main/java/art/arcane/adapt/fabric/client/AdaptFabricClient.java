package art.arcane.adapt.fabric.client;

import art.arcane.adapt.client.AdaptClient;
import art.arcane.adapt.client.ClientPlatform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

public final class AdaptFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AdaptClient client = ClientPlatform.start(FabricClientChannel.register(), FabricLoader.getInstance().getConfigDir(), ShiftKeyMapping::new);
        for (KeyMapping mapping : ClientPlatform.keyMappings()) {
            KeyMappingHelper.registerKeyMapping(mapping);
        }
        HudElementRegistry.addLast(ClientPlatform.hudLayer(), (graphics, tracker) -> ClientPlatform.renderHud(graphics, tracker.getGameTimeDeltaPartialTick(false)));
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> client.tick());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> client.connected());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> client.disconnected());
    }
}
