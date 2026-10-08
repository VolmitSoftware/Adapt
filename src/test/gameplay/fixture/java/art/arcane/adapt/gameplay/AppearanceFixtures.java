package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.content.gui.ConfigGui;
import art.arcane.adapt.content.item.ExperienceOrb;
import art.arcane.adapt.content.item.KnowledgeOrb;
import art.arcane.adapt.util.common.misc.CustomModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public final class AppearanceFixtures {
    private AppearanceFixtures() {
    }

    public static JsonObject execute(Player player, String[] args) {
        AdaptPlayer runtime = Objects.requireNonNull(Adapt.instance.getAdaptServer().getPlayer(player));
        if (!runtime.isRuntimeReady()) throw new IllegalStateException("Player runtime is not ready");
        switch (args[2]) {
            case "give" -> {
                player.closeInventory();
                player.getInventory().clear();
                player.setGameMode(GameMode.valueOf(args[5]));
                player.getInventory().setHeldItemSlot(0);
                ItemStack orb = args[3].equals("experience")
                    ? ExperienceOrb.with("agility", 7) : KnowledgeOrb.with("agility", 7);
                orb.setAmount(2);
                if (args[4].equals("offhand")) player.getInventory().setItemInOffHand(orb);
                else player.getInventory().setItemInMainHand(orb);
            }
            case "models" -> {
                Material material = Material.valueOf(args[3]);
                String raw = "[items.experience-orb]\nmaterial = \"" + material.name()
                    + "\"\nmodel = 71\n[items.knowledge-orb]\nmaterial = \"" + material.name() + "\"\nmodel = 72\n";
                if (!CustomModel.reloadSnapshot(raw, Adapt.instance.getDataFile("models.toml"))) {
                    throw new IllegalStateException("Model snapshot rejected");
                }
            }
            case "configure" -> {
                PermissionAttachment permission = player.addAttachment(Adapt.instance, "adapt.configurator", true);
                try {
                    ConfigGui.open(player, args.length > 3 ? args[3] : "");
                } finally {
                    player.removeAttachment(permission);
                }
            }
            case "snapshot" -> { }
            default -> throw new IllegalArgumentException("Unknown appearance action");
        }
        PlayerSkillLine line = runtime.getSkillLine("agility");
        JsonObject result = new JsonObject();
        result.addProperty("knowledge", line.getKnowledge());
        result.addProperty("xp", line.getXp() + line.getPooledXp());
        result.addProperty("xpMultiplier", line.getMultiplier());
        result.addProperty("vanillaXp", player.calculateTotalExperiencePoints());
        result.add("main", item(player.getInventory().getItemInMainHand()));
        result.add("offhand", item(player.getInventory().getItemInOffHand()));
        result.addProperty("title", player.getOpenInventory().getTitle());
        JsonArray slots = new JsonArray();
        for (ItemStack stack : player.getOpenInventory().getTopInventory().getContents()) slots.add(item(stack));
        result.add("slots", slots);
        return result;
    }

    private static JsonObject item(ItemStack stack) {
        JsonObject result = new JsonObject();
        if (stack == null || stack.getType().isAir()) return result;
        result.addProperty("material", stack.getType().name());
        result.addProperty("amount", stack.getAmount());
        ItemMeta meta = stack.getItemMeta();
        result.addProperty("name", meta.getDisplayName());
        result.addProperty("encodedName", encode(meta.getDisplayName()));
        if (meta.hasCustomModelData()) result.addProperty("model", meta.getCustomModelData());
        JsonArray lore = new JsonArray();
        JsonArray encodedLore = new JsonArray();
        if (meta.hasLore()) {
            for (String line : Objects.requireNonNull(meta.getLore())) {
                lore.add(line);
                encodedLore.add(encode(line));
            }
        }
        result.add("lore", lore);
        result.add("encodedLore", encodedLore);
        return result;
    }
    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
