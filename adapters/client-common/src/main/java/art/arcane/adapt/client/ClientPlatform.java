package art.arcane.adapt.client;

import art.arcane.adapt.client.net.ClientTransport;
import art.arcane.adapt.client.net.MinecraftClientNet;
import art.arcane.adapt.client.ui.MinecraftUiDraw;
import art.arcane.adapt.client.ui.MinecraftUiHost;
import art.arcane.adapt.client.ui.MinecraftUiResources;
import art.arcane.adapt.client.ui.MinecraftUiWorld;
import art.arcane.adapt.mod.AdaptModIdentity;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** This version's Minecraft side of {@link AdaptClient}: key mappings, the HUD layer and the screen host. */
public final class ClientPlatform implements ClientKeys {
    private static ClientPlatform platform;

    private final Map<AdaptKey, KeyMapping> mappings = new EnumMap<>(AdaptKey.class);
    private final MinecraftUiDraw hud = new MinecraftUiDraw();
    private final AdaptClient client;

    /** Builds the binding for a key whose default holds Shift, with the loader's own way of requiring it. */
    @FunctionalInterface
    public interface ShiftKeys {
        KeyMapping create(String name, InputConstants.Key key, KeyMapping.Category category);
    }

    private ClientPlatform(ClientTransport transport, Path configDirectory, ShiftKeys shiftKeys) {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(AdaptModIdentity.ID, "main"));
        for (AdaptKey key : AdaptKey.values()) {
            InputConstants.Key bound = defaultKey(key);
            mappings.put(key, key.shiftByDefault() ? shiftKeys.create(key.translationKey(), bound, category)
                    : new KeyMapping(key.translationKey(), bound.getType(), bound.getValue(), category));
        }
        client = AdaptClient.initialize(new MinecraftUiHost(), this, MinecraftClientNet.create(transport, configDirectory),
                new MinecraftUiWorld());
        client.onTick(MinecraftUiResources.SPRITES::refresh);
    }

    private static InputConstants.Key defaultKey(AdaptKey key) {
        return switch (key) {
            case OPEN_SKILLS -> InputConstants.getKey("key.keyboard.slash");
            case OPEN_HUD_EDITOR, TOGGLE_HUD -> InputConstants.UNKNOWN;
        };
    }

    /** Creates the client once, from the loader's client entry point, with its {@code adapt:ui} transport. */
    public static synchronized AdaptClient start(ClientTransport transport, Path configDirectory, ShiftKeys shiftKeys) {
        if (platform == null) {
            platform = new ClientPlatform(transport, configDirectory, shiftKeys);
        }
        return platform.client;
    }

    public static List<KeyMapping> keyMappings() {
        return List.copyOf(platform.mappings.values());
    }

    public static Identifier hudLayer() {
        return Identifier.fromNamespaceAndPath(AdaptModIdentity.ID, "hud");
    }

    public static void renderHud(GuiGraphicsExtractor graphics, float tickDelta) {
        ClientPlatform current = platform;
        if (current != null && !ClientHudGate.hidden()) {
            current.client.renderHud(current.hud.bind(graphics), tickDelta);
        }
    }

    @Override
    public void poll(Consumer<AdaptKey> pressed) {
        for (Map.Entry<AdaptKey, KeyMapping> entry : mappings.entrySet()) {
            while (entry.getValue().consumeClick()) {
                pressed.accept(entry.getKey());
            }
        }
    }
}
