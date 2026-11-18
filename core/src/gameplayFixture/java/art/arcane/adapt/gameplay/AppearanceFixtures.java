package art.arcane.adapt.gameplay;

import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.content.gui.ConfigGui;
import art.arcane.adapt.content.item.ExperienceOrb;
import art.arcane.adapt.content.item.KnowledgeOrb;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.util.common.misc.CustomModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

public final class AppearanceFixtures {
    private AppearanceFixtures() {
    }

    public static JsonObject execute(PlayerRef player, String[] args) {
        AdaptPlayer runtime = Objects.requireNonNull(Fixtures.adaptPlayer(player));
        if (!runtime.isRuntimeReady()) throw new IllegalStateException("Player runtime is not ready");
        switch (args[2]) {
            case "give" -> {
                player.closeInventory();
                Fixtures.clearInventory(player);
                player.setGameMode(GameMode.valueOf(args[5]));
                player.inventory().setHeldSlot(0);
                ItemRef orb = args[3].equals("experience")
                    ? ExperienceOrb.with(Map.of("agility", 7D)) : KnowledgeOrb.with(Map.of("agility", 7));
                orb.setAmount(2);
                if (args[4].equals("offhand")) player.inventory().setOffHand(orb);
                else player.inventory().setMainHand(orb);
                player.updateInventory();
            }
            case "models" -> {
                String material = args[3].toUpperCase(Locale.ROOT);
                if (Fixtures.items().create(Fixtures.id(material.toLowerCase(Locale.ROOT)), 1).isEmpty()) {
                    throw new IllegalArgumentException("Unknown material " + args[3]);
                }
                String raw = "[items.experience-orb]\nmaterial = \"" + material
                    + "\"\nmodel = 71\n[items.knowledge-orb]\nmaterial = \"" + material + "\"\nmodel = 72\n";
                if (!CustomModel.reloadSnapshot(raw, AdaptEnvironment.dataFile("models.toml"))) {
                    throw new IllegalStateException("Model snapshot rejected");
                }
            }
            case "configure" -> {
                Runnable revoke = Fixtures.platform().grantPermission(player, "adapt.configurator");
                try {
                    ConfigGui.open(player, args.length > 3 ? args[3] : "");
                } finally {
                    revoke.run();
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
        result.addProperty("vanillaXp", totalExperiencePoints(player));
        result.add("main", item(player.inventory().mainHand()));
        result.add("offhand", item(player.inventory().offHand()));
        result.addProperty("title", Fixtures.platform().openMenuTitle(player));
        JsonArray slots = new JsonArray();
        InventoryRef top = player.openMenuInventory();
        if (top != null) {
            for (ItemRef stack : top.contents()) slots.add(item(stack));
        }
        result.add("slots", slots);
        return result;
    }

    private static JsonObject item(ItemRef stack) {
        JsonObject result = new JsonObject();
        if (stack == null || stack.isEmpty()) return result;
        result.addProperty("material", FixtureItems.material(stack.type()));
        result.addProperty("amount", stack.amount());
        String name = FixtureItems.name(stack);
        result.addProperty("name", name);
        result.addProperty("encodedName", encode(name));
        OptionalInt model = Fixtures.platform().customModelData(stack);
        if (model.isPresent()) result.addProperty("model", model.getAsInt());
        JsonArray lore = new JsonArray();
        JsonArray encodedLore = new JsonArray();
        for (String raw : stack.lore()) {
            String line = FixtureItems.legacy(raw);
            lore.add(line);
            encodedLore.add(encode(line));
        }
        result.add("lore", lore);
        result.add("encodedLore", encodedLore);
        return result;
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static int totalExperiencePoints(PlayerRef player) {
        int level = player.level();
        int points = 0;
        for (int current = 0; current < level; current++) {
            points += experienceToNextLevel(current);
        }
        return points + Math.round(experienceToNextLevel(level) * player.expProgress());
    }

    private static int experienceToNextLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        if (level >= 15) {
            return 37 + (level - 15) * 5;
        }
        return 7 + level * 2;
    }
}
