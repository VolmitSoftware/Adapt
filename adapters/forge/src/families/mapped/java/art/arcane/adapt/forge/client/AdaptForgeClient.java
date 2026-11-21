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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Client wiring; AdaptForge calls it only when Forge runs a client. */
public final class AdaptForgeClient {
    private AdaptForgeClient() {
    }

    public static void initialize() {
        AdaptClient client = ClientPlatform.start(new ForgeClientChannel(), FMLPaths.CONFIGDIR.get(),
                (String name, InputConstants.Key key, String category) -> new KeyMapping(name, KeyConflictContext.IN_GAME, KeyModifier.SHIFT, key, category));
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            for (KeyMapping mapping : ClientPlatform.keyMappings()) {
                event.register(mapping);
            }
        });
        modBus.addListener((AddGuiOverlayLayersEvent event) -> event.getLayeredDraw().add(ClientPlatform.hudLayer(),
                (graphics, tracker) -> ClientPlatform.renderHud(graphics, tracker.getGameTimeDeltaPartialTick(false))));
        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener((TickEvent.ClientTickEvent.Pre event) -> client.tickStart());
        bus.addListener((TickEvent.ClientTickEvent.Post event) -> client.tick());
        bus.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> client.connected());
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> client.disconnected());
    }
}
