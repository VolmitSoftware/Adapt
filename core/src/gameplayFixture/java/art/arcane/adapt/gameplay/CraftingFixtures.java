package art.arcane.adapt.gameplay;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.engine.item.ItemDurability;
import art.arcane.adapt.content.adaptation.crafting.CraftingDeconstruction;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeHost;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CraftingFixtures {
    private static final Map<UUID, JsonArray> INTERACTIONS = new HashMap<>();
    private static final Set<String> STAGES = Set.of("craft-bulk", "craft-thrifty", "craft-provision", "craft-masterwork",
            "craft-tinkerer", "craft-deconstruction", "craft-leather", "craft-backpack", "craft-station",
            "craft-stonecutter", "craft-shape", "craft-placement");
    private static final Map<String, String> WINDOW_BLOCKS = Map.ofEntries(
            Map.entry("crafting_table", "WORKBENCH"), Map.entry("stonecutter", "STONECUTTER"),
            Map.entry("anvil", "ANVIL"), Map.entry("chipped_anvil", "ANVIL"), Map.entry("damaged_anvil", "ANVIL"),
            Map.entry("enchanting_table", "ENCHANTING"), Map.entry("grindstone", "GRINDSTONE"),
            Map.entry("chest", "CHEST"), Map.entry("trapped_chest", "CHEST"), Map.entry("barrel", "BARREL"),
            Map.entry("smithing_table", "SMITHING"), Map.entry("loom", "LOOM"), Map.entry("cartography_table", "CARTOGRAPHY"),
            Map.entry("furnace", "FURNACE"), Map.entry("smoker", "SMOKER"), Map.entry("blast_furnace", "BLAST_FURNACE"),
            Map.entry("ender_chest", "ENDER_CHEST"));

    public CraftingFixtures() {
    }

    public static void install() {
        Fixtures.register(new CraftingFixtures());
    }

    public static boolean stage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        INTERACTIONS.put(actor.uuid(), new JsonArray());
        Fixtures.teleport(opponent, world, -5.5, 100, 5.5);
        actor.setFoodLevel(10);
        actor.setSaturation(0);
        switch (name) {
            case "craft-bulk" -> Fixtures.give(actor, "oak_log", 9);
            case "craft-thrifty" -> {
                Fixtures.give(actor, "oak_log", 20);
                Fixtures.give(actor, "oak_planks", 48);
                Fixtures.give(actor, "cobblestone", 64);
                Fixtures.give(actor, "iron_ingot", 16);
                Fixtures.give(actor, "redstone", 16);
            }
            case "craft-provision" -> Fixtures.give(actor, "wheat", 36);
            case "craft-masterwork" -> {
                Fixtures.give(actor, "oak_planks", 12);
                Fixtures.give(actor, "stick", 24);
            }
            case "craft-tinkerer" -> {
                Fixtures.give(actor, damagedSword("sharpness", 3));
                Fixtures.give(actor, damagedSword("unbreaking", 2));
            }
            case "craft-deconstruction" -> {
                Fixtures.give(actor, "shears", 1);
                Fixtures.give(actor, "iron_chestplate", 1);
            }
            case "craft-leather" -> {
                Fixtures.give(actor, "rotten_flesh", 1);
                Fixtures.set(world, 2, 100, 0, "campfire");
            }
            case "craft-backpack" -> {
                Fixtures.give(actor, "leather", 8);
                Fixtures.give(actor, "chest", 1);
                Fixtures.give(actor, "apple", 3);
            }
            case "craft-station" -> {
                Fixtures.give(actor, "crafting_table", 1);
                Fixtures.give(actor, "oak_log", 1);
            }
            case "craft-stonecutter" -> actor.inventory().setOffHand(Fixtures.item("stonecutter"));
            case "craft-shape" -> Fixtures.set(world, 2, 100, 0, "oak_log");
            case "craft-placement" -> {
                Fixtures.give(actor, "oak_planks", 32);
                for (int y = 100; y <= 102; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Fixtures.set(world, 3, y, z, "oak_planks");
                    }
                }
            }
            default -> throw new IllegalStateException("Unhandled crafting fixture stage " + name);
        }
        if (Set.of("craft-bulk", "craft-thrifty", "craft-provision", "craft-masterwork", "craft-tinkerer", "craft-backpack").contains(name)) {
            Fixtures.set(world, 2, 100, 2, "crafting_table");
        }
        return true;
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        WorldRef world = player.world();
        Loc eye = player.eyeLocation();
        InventoryRef top = player.openMenuInventory();
        result.add("interactions", INTERACTIONS.getOrDefault(player.uuid(), new JsonArray()));
        result.add("inventory", inventory(player.inventory().contents()));
        result.add("top", top == null ? new JsonArray() : inventory(top.contents()));
        result.addProperty("window", window(player, top));
        result.addProperty("eyeHeight", eye.y() - player.location().y());
        result.addProperty("targetData", world.blockAt(2, 100, 0).state().asString());
        CraftingDeconstruction salvage = null;
        if (player.inventory().mainHand().type().equals(Id.minecraft("shears"))) {
            EntityRef hit = world.rayTraceEntity(eye.position(), eye.direction(), 6, 0, entity -> entity instanceof ItemEntityRef);
            if (hit != null) result.addProperty("rayItemId", hit.entityId());
            salvage = Fixtures.adaptation(CraftingDeconstruction.class);
        }
        RecipeHost recipes = AdaptCore.platform().recipes();
        Loc at = player.location();
        JsonArray drops = new JsonArray();
        for (EntityRef entity : world.entities()) {
            if (entity instanceof ItemEntityRef dropped && dropped.location().distanceSquared(at) < 144) {
                ItemRef stack = dropped.item();
                JsonObject item = item(stack);
                item.addProperty("entityId", dropped.entityId());
                item.addProperty("x", dropped.location().x());
                item.addProperty("y", dropped.location().y());
                item.addProperty("z", dropped.location().z());
                if (salvage != null) {
                    ItemRef lookup = Fixtures.items().create(stack.type(), 1);
                    item.addProperty("pickupEligible", salvage.canSnatchItem(player, dropped));
                    item.addProperty("sourceValue", salvage.getValue(stack));
                    item.addProperty("ironIngotValue", salvage.getValue(Id.minecraft("iron_ingot")));
                    item.addProperty("recipeCount", recipes.recipesFor(lookup).size());
                    if (lookup.maxDamage() > 0) {
                        item.addProperty("damagedRecipeCount", recipes.recipesFor(lookup).size());
                    }
                    item.add("salvage", inventory(salvage.getDeconstructionOfferings(stack)));
                }
                drops.add(item);
            }
        }
        result.add("drops", drops);
        JsonArray placement = new JsonArray();
        for (int y = 100; y <= 102; y++) {
            for (int z = -1; z <= 1; z++) {
                placement.add(world.blockAt(2, y, z).type().toString());
            }
        }
        result.add("placement", placement);
        return result;
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        JsonArray interactions = INTERACTIONS.get(event.player().uuid());
        if (interactions == null || interactions.size() >= 20) {
            return;
        }
        JsonObject observation = new JsonObject();
        observation.addProperty("action", event.action().name());
        observation.addProperty("cancelled", event.isCancelled());
        observation.addProperty("useItem", event.useItem().name());
        observation.addProperty("useBlock", event.useBlock().name());
        observation.addProperty("hand", event.hand() == null ? "null" : event.hand() == Hand.MAIN_HAND ? "HAND" : "OFF_HAND");
        observation.addProperty("held", materialName(event.player().inventory().mainHand()));
        observation.addProperty("sneaking", event.player().isSneaking());
        interactions.add(observation);
    }

    private static String window(PlayerRef player, InventoryRef top) {
        if (top == null) {
            return "CRAFTING";
        }
        Loc location = player.openMenuLocation();
        if (location != null) {
            String block = WINDOW_BLOCKS.get(location.world().blockAt(location).type().path());
            if (block != null) {
                return block;
            }
        }
        return switch (top.size()) {
            case 10 -> "WORKBENCH";
            case 2 -> "STONECUTTER";
            case 3 -> "ANVIL";
            default -> "CHEST";
        };
    }

    private static String materialName(ItemRef item) {
        return item == null || item.isEmpty() ? "AIR" : item.type().path().toUpperCase(Locale.ROOT);
    }

    private static JsonArray inventory(List<ItemRef> stacks) {
        JsonArray result = new JsonArray();
        for (ItemRef stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                result.add(item(stack));
            }
        }
        return result;
    }

    private static JsonObject item(ItemRef stack) {
        JsonObject result = new JsonObject();
        result.addProperty("type", stack.type().toString());
        result.addProperty("amount", stack.amount());
        int baseMaxDamage = Fixtures.items().create(stack.type(), 1).maxDamage();
        result.addProperty("damage", stack.damage());
        int maxDamage = ItemDurability.maxDamage(stack);
        result.addProperty("maxDamage", maxDamage > 0 ? maxDamage : baseMaxDamage);
        result.addProperty("baseMaxDamage", baseMaxDamage);
        JsonObject enchants = new JsonObject();
        for (Map.Entry<Id, Integer> entry : stack.enchantments().entrySet()) {
            enchants.addProperty(entry.getKey().toString(), entry.getValue());
        }
        result.add("enchantments", enchants);
        return result;
    }

    private static ItemRef damagedSword(String enchantment, int level) {
        ItemRef sword = Fixtures.enchanted("iron_sword", enchantment, level);
        sword.setDamage(200);
        return sword;
    }
}
