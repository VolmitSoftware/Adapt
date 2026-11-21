package art.arcane.adapt.fabric.client;

import art.arcane.adapt.client.AdaptClient;
import art.arcane.adapt.client.ClientPlatform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

public final class AdaptFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AdaptClient client = ClientPlatform.start(FabricClientChannel.register(), FabricLoader.getInstance().getConfigDir(), ShiftKeyMapping::new);
        for (KeyMapping mapping : ClientPlatform.keyMappings()) {
            KeyBindingHelper.registerKeyBinding(mapping);
        }
        HudRenderCallback.EVENT.register((graphics, tracker) -> ClientPlatform.renderHud(graphics, tracker.getGameTimeDeltaPartialTick(false)));
        ClientTickEvents.START_CLIENT_TICK.register(minecraft -> client.tickStart());
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> client.tick());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> client.connected());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> client.disconnected());
    }
}
