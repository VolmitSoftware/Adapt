package art.arcane.adapt.gameplay.demo;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DemoSkin implements Listener {
    private final JavaPlugin plugin;
    private final ProfileProperty texture;

    private DemoSkin(JavaPlugin plugin, ProfileProperty texture) {
        this.plugin = plugin;
        this.texture = texture;
    }

    public static void install(JavaPlugin plugin) {
        Path path = plugin.getDataFolder().toPath().resolve("skin-profile.json");
        if (!Files.exists(path)) {
            return;
        }
        ProfileProperty texture = load(path);
        plugin.getServer().getPluginManager().registerEvents(new DemoSkin(plugin, texture), plugin);
        plugin.getLogger().info("Loaded signed demo skin for studio actor and opponent");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        if (!name.equalsIgnoreCase("AQAClient262") && !name.equalsIgnoreCase("AQAOpponent")) {
            return;
        }
        PlayerProfile profile = event.getPlayerProfile();
        profile.removeProperty("textures");
        profile.setProperty(texture);
        event.setPlayerProfile(profile);
        plugin.getLogger().info("Applied signed demo skin to " + name);
    }

    private static ProfileProperty load(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject document = new JsonParser().parse(reader).getAsJsonObject();
            JsonArray properties = document.getAsJsonArray("properties");
            if (properties == null) {
                throw new IllegalArgumentException("Skin profile requires properties");
            }
            for (JsonElement element : properties) {
                JsonObject property = element.getAsJsonObject();
                if (!"textures".equals(text(property, "name"))) {
                    continue;
                }
                return new ProfileProperty("textures", text(property, "value"), text(property, "signature"));
            }
            throw new IllegalArgumentException("Skin profile contains no textures property");
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Unable to load signed demo skin profile", failure);
        }
    }

    private static String text(JsonObject property, String name) {
        JsonElement value = property.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new IllegalArgumentException("Skin profile property requires " + name);
        }
        return value.getAsString();
    }
}
