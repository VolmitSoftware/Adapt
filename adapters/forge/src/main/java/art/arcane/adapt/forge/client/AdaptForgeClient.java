package art.arcane.adapt.forge.client;

import art.arcane.adapt.client.AdaptClient;
import art.arcane.adapt.client.ClientPlatform;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;

/** Client wiring; AdaptForge calls it only when Forge runs a client. */
public final class AdaptForgeClient {
    private AdaptForgeClient() {
    }

    public static void initialize() {
        AdaptClient client = ClientPlatform.start(new ForgeClientChannel(), FMLPaths.CONFIGDIR.get(),
                (String name, InputConstants.Key key, KeyMapping.Category category) -> new KeyMapping(name, KeyConflictContext.IN_GAME, KeyModifier.SHIFT, key, category, 0));
        RegisterKeyMappingsEvent.BUS.addListener((RegisterKeyMappingsEvent event) -> {
            for (KeyMapping mapping : ClientPlatform.keyMappings()) {
                event.register(mapping);
            }
        });
        AddGuiOverlayLayersEvent.BUS.addListener((AddGuiOverlayLayersEvent event) -> event.getLayeredDraw().add(ClientPlatform.hudLayer(),
                (graphics, tracker) -> ClientPlatform.renderHud(graphics, tracker.getGameTimeDeltaPartialTick(false))));
        TickEvent.ClientTickEvent.Post.BUS.addListener((TickEvent.ClientTickEvent.Post event) -> client.tick());
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> client.connected());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> client.disconnected());
    }
}
