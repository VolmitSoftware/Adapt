package art.arcane.adapt.neoforge.client;

import art.arcane.adapt.client.AdaptClient;
import art.arcane.adapt.client.ClientPlatform;
import art.arcane.adapt.mod.AdaptModIdentity;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;

/** Client entry point; NeoForge constructs it only on a client. */
@Mod(value = AdaptModIdentity.ID, dist = Dist.CLIENT)
public final class AdaptNeoForgeClient {
    public AdaptNeoForgeClient(IEventBus modBus) {
        AdaptClient client = ClientPlatform.start(NeoForgeClientChannel.register(modBus), FMLPaths.CONFIGDIR.get(),
                (name, key, category) -> new KeyMapping(name, KeyConflictContext.IN_GAME, KeyModifier.SHIFT, key, category));
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            for (KeyMapping mapping : ClientPlatform.keyMappings()) {
                event.register(mapping);
            }
        });
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(ClientPlatform.hudLayer(),
                (graphics, tracker) -> ClientPlatform.renderHud(graphics, tracker.getGameTimeDeltaPartialTick(false))));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> client.tickStart());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> client.tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> client.connected());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> client.disconnected());
    }
}
