package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.entity.ExperienceOrbRef;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.PrepareAnvilEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class KnowledgeFixtures {
    private static final Set<String> STAGES = Set.of("knowledge-mending", "knowledge-quick", "knowledge-infusion",
            "knowledge-tome", "knowledge-reroll", "knowledge-cleanse", "knowledge-anvil", "knowledge-echo",
            "knowledge-unity", "knowledge-resist", "knowledge-notes", "knowledge-enchant");
    private static final Map<UUID, AnvilCost> ANVIL_COSTS = new HashMap<>();

    private KnowledgeFixtures() {
    }

    public static void install() {
        Fixtures.register(new KnowledgeFixtures());
    }

    public static boolean stage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        ANVIL_COSTS.clear();
        actor.setLevel(30);
        actor.setExpProgress(0);
        actor.setEnchantmentSeed(424242);
        actor.setFoodLevel(10);
        actor.setSaturation(0);
        opponent.setFoodLevel(10);
        opponent.setSaturation(0);
        Fixtures.teleport(opponent, world, -5.5, 100, 5.5);
        switch (name) {
            case "knowledge-mending" -> {
                ItemRef tool = Fixtures.enchanted("diamond_pickaxe", "mending", 1);
                tool.setDamage(240);
                Fixtures.give(actor, tool);
                Fixtures.set(world, 2, 100, 0, "stone");
            }
            case "knowledge-quick", "knowledge-infusion", "knowledge-anvil" -> {
                ItemRef sword = Fixtures.item("iron_sword");
                if (name.equals("knowledge-anvil")) {
                    sword.setEnchantment(Id.minecraft("sharpness"), 3);
                }
                Fixtures.give(actor, sword);
                Fixtures.give(actor, book(Map.of("sharpness", 3)));
                Fixtures.set(world, 2, 100, 0, "anvil");
            }
            case "knowledge-tome" -> {
                Map<String, Integer> enchants = new LinkedHashMap<>();
                enchants.put("sharpness", 3);
                enchants.put("unbreaking", 2);
                Fixtures.give(actor, book(enchants));
                Fixtures.set(world, 2, 100, 0, "anvil");
            }
            case "knowledge-reroll", "knowledge-enchant" -> {
                Fixtures.set(world, 2, 100, 0, "enchanting_table");
                Fixtures.give(actor, "iron_sword", 1);
                Fixtures.give(actor, "lapis_lazuli", 16);
            }
            case "knowledge-cleanse" -> {
                ItemRef sword = Fixtures.item("iron_sword");
                sword.setEnchantment(Id.minecraft("vanishing_curse"), 1);
                sword.setEnchantment(Id.minecraft("sharpness"), 2);
                Fixtures.give(actor, sword);
                Fixtures.set(world, 2, 100, 0, "grindstone");
            }
            case "knowledge-echo", "knowledge-unity" -> {
                if (name.equals("knowledge-echo")) {
                    Fixtures.give(actor, book(Map.of("sharpness", 1)));
                }
                ((ExperienceOrbRef) Fixtures.spawn(world, "experience_orb", 12.5, 100.2, 0.5)).setExperience(40);
            }
            case "knowledge-resist" -> {
                actor.setHealth(10);
                Fixtures.teleport(opponent, world, 2.5, 100, 0.5);
                opponent.inventory().setMainHand(Fixtures.item("wooden_sword"));
            }
            case "knowledge-notes" -> {
                Fixtures.give(actor, "wooden_sword", 1);
                cow(world, "first", 2.5, 0.5, 1);
                cow(world, "second", 2.5, 1.6, 10);
            }
            default -> throw new IllegalStateException("Unhandled knowledge fixture stage " + name);
        }
        return true;
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        InventoryRef top = player.openMenuInventory();
        result.addProperty("level", player.level());
        result.addProperty("experience", totalExperiencePoints(player));
        result.addProperty("seed", player.enchantmentSeed());
        result.add("inventory", items(player.inventory().contents()));
        result.add("cursor", item(player.inventory().cursor()));
        result.add("top", top == null ? new JsonArray() : items(top.contents()));
        AnvilCost anvil = ANVIL_COSTS.get(player.uuid());
        if (top != null && anvil != null && top.equals(anvil.window())) {
            result.addProperty("repairCost", anvil.cost());
        }
        JsonArray drops = new JsonArray();
        JsonObject targets = new JsonObject();
        for (EntityRef entity : player.world().entities()) {
            if (entity instanceof ItemEntityRef dropped && dropped.location().distanceSquared(player.location()) < 144) {
                drops.add(item(dropped.item()));
            }
            if (!(entity instanceof LivingRef target)) {
                continue;
            }
            for (String tag : Fixtures.tags(target)) {
                if (tag.startsWith("adapt-qa-knowledge-")) {
                    JsonObject state = new JsonObject();
                    state.addProperty("entityId", target.entityId());
                    state.addProperty("health", target.health());
                    targets.add(tag.substring("adapt-qa-knowledge-".length()), state);
                }
            }
        }
        result.add("drops", drops);
        result.add("targets", targets);
        return result;
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void on(PrepareAnvilEvent event) {
        PlayerRef player = event.player();
        if (player == null || event.kind() != InventoryKind.ANVIL) {
            return;
        }
        InventoryRef window = player.openMenuInventory();
        if (window != null) {
            ANVIL_COSTS.put(player.uuid(), new AnvilCost(window, event.repairCost()));
        }
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

    private static JsonArray items(List<ItemRef> items) {
        JsonArray result = new JsonArray();
        for (ItemRef stack : items) {
            if (stack != null && !stack.isEmpty()) {
                result.add(item(stack));
            }
        }
        return result;
    }

    private static JsonObject item(ItemRef item) {
        JsonObject result = new JsonObject();
        boolean empty = item == null || item.isEmpty();
        result.addProperty("type", empty ? "minecraft:air" : item.type().toString());
        if (empty) {
            return result;
        }
        result.addProperty("amount", item.amount());
        result.addProperty("damage", item.damage());
        JsonObject enchantments = new JsonObject();
        Map<Id, Integer> enchants = item.type().equals(Id.minecraft("enchanted_book")) ? item.storedEnchantments() : item.enchantments();
        for (Map.Entry<Id, Integer> entry : enchants.entrySet()) {
            enchantments.addProperty(entry.getKey().toString(), entry.getValue());
        }
        result.add("enchantments", enchantments);
        return result;
    }

    private static ItemRef book(Map<String, Integer> enchants) {
        ItemRef book = Fixtures.item("enchanted_book");
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            book.setStoredEnchantment(Fixtures.id(entry.getKey()), entry.getValue());
        }
        return book;
    }

    private static void cow(WorldRef world, String label, double x, double z, double health) {
        LivingRef cow = Fixtures.spawnStill(world, "cow", x, 100, z);
        cow.setHealth(health);
        Fixtures.tag(cow, "adapt-qa-knowledge-" + label);
    }

    private record AnvilCost(InventoryRef window, int cost) {
    }
}
