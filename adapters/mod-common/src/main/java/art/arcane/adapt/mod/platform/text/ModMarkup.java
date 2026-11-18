package art.arcane.adapt.mod.platform.text;

import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.mod.platform.ModPlatform;
import art.arcane.adapt.mod.platform.ModText;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import art.arcane.adapt.util.common.format.C;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adapt markup through the bundled MiniMessage, the modded twin of the Bukkit markup port. */
public final class ModMarkup implements MarkupPort {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final Pattern TAG = Pattern.compile("<(?!/)([!?#]?[a-zA-Z0-9_:#-]+)");
    private static final Set<String> COLOR_TAGS = Set.of("color", "colour", "c", "gradient", "rainbow", "transition",
            "pride", "reset", "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "grey", "dark_gray", "dark_grey", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");

    @Override
    public String normalize(String mixedMarkup) {
        return Markup.toMiniMessage(mixedMarkup);
    }

    @Override
    public String legacy(String mixedMarkup) {
        if (mixedMarkup == null || mixedMarkup.isEmpty()) {
            return "";
        }
        return LEGACY.serialize(ModText.adventure(mixedMarkup));
    }

    @Override
    public String plain(String mixedMarkup) {
        return C.stripColor(legacy(mixedMarkup));
    }

    @Override
    public String renderMiniMessage(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return "";
        }
        try {
            return LEGACY.serialize(MINI_MESSAGE.deserialize(miniMessage));
        } catch (RuntimeException invalid) {
            return legacy(miniMessage);
        }
    }

    @Override
    public String escapeTags(String text) {
        return text == null ? "" : MINI_MESSAGE.escapeTags(text);
    }

    @Override
    public String jsonToMarkup(String componentJson) {
        if (componentJson == null || componentJson.isBlank()) {
            return "";
        }
        try {
            MinecraftServer server = ModPlatform.server();
            DynamicOps<JsonElement> ops = server == null ? JsonOps.INSTANCE : server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            return ComponentSerialization.CODEC.parse(ops, JsonParser.parseString(componentJson)).result()
                    .map(ModText::markup)
                    .orElse("");
        } catch (RuntimeException unreadable) {
            return "";
        }
    }

    @Override
    public boolean hasColorTags(String miniMessage) {
        if (miniMessage == null || miniMessage.indexOf('<') < 0) {
            return false;
        }
        Matcher matcher = TAG.matcher(miniMessage);
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            if (name.startsWith("#")) {
                return true;
            }
            int colon = name.indexOf(':');
            if (COLOR_TAGS.contains(colon < 0 ? name : name.substring(0, colon))) {
                return true;
            }
        }
        return false;
    }
}
