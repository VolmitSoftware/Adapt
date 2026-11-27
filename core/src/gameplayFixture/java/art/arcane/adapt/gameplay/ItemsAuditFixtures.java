package art.arcane.adapt.gameplay;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.engine.ComponentPort;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ItemsAuditFixtures {
    private static final Id EXACT_RECIPE = Id.of("adapt_gameplay", "marked_diamond");
    private static final Id GRINDSTONE = Id.minecraft("grindstone");
    private static final Map<UUID, JsonArray> CLICKS = new HashMap<>();
    private static final Map<UUID, Integer> CRAFTED = new HashMap<>();

    public static void install() {
        Fixtures.register(new ItemsAuditFixtures());
    }

    public static JsonObject execute(PlayerRef player, String[] args) {
        if (!Fixtures.isFixtureWorld(player.world())) {
            throw new IllegalStateException("Items audit requires the gameplay arena");
        }
        switch (args[2]) {
            case "prepare" -> prepare(player, args[3]);
            case "fill" -> fill(player);
            case "space" -> player.inventory().clear(9);
            case "clear-cooldown" -> player.setCooldown(GRINDSTONE, 0);
            case "configure" -> configure(args[3], args[4], Double.parseDouble(args[5]));
            case "snapshot" -> {
            }
            case "block" -> {
                JsonObject result = snapshot(player);
                result.add("block", block(player.world().blockAt(Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]))));
                return result;
            }
            default -> throw new IllegalArgumentException("Unknown item audit operation " + args[2]);
        }
        return snapshot(player);
    }

    private static JsonObject block(BlockRef block) {
        JsonObject result = new JsonObject();
        result.addProperty("type", block.type().toString());
        InventoryRef container = block.container();
        result.add("contents", container == null ? new JsonArray() : items(container));
        return result;
    }

    private static ComponentPort experience() {
        return AdaptPorts.get(ComponentPort.class, ComponentPort.NONE);
    }

    static ItemRef shulker(String name) {
        ItemRef box = named("purple_shulker_box", name, 1);
        box.setContainerContents(List.of(Fixtures.item("diamond", 3)));
        return box;
    }

    private static void prepare(PlayerRef player, String mode) {
        player.closeInventory();
        player.inventory().setCursor(Fixtures.items().empty());
        Fixtures.clearInventory(player);
        player.inventory().setHeldSlot(0);
        WorldRef world = player.world();
        Fixtures.teleport(player, world, 0.5, 100, 0.5);
        player.setCooldown(GRINDSTONE, 0);
        player.setLevel(0);
        player.setExpProgress(0F);
        experience().setTotalExperience(player, 0);
        CLICKS.put(player.uuid(), new JsonArray());
        CRAFTED.put(player.uuid(), 0);
        for (EntityRef entity : world.entities()) {
            if (entity instanceof ItemEntityRef) {
                entity.remove();
            }
        }
        Fixtures.set(world, 2, 100, 1, "air");
        Fixtures.set(world, 2, 100, 2, "crafting_table");
        switch (mode) {
            case "scaffolder" -> {
                Fixtures.set(world, 2, 100, 2, "stone");
                player.inventory().setItem(0, shulker("Retention Box"));
            }
            case "provisioner" -> player.inventory().setItem(0, Fixtures.item("wheat", 12));
            case "grindstone" -> {
                Fixtures.set(world, 2, 100, 2, "grindstone");
                player.inventory().setItem(0, Fixtures.enchanted("iron_pickaxe", "unbreaking", 2));
            }
            case "bulk", "bulk-valid" -> {
                AdaptCore.platform().recipes().unregister(EXACT_RECIPE);
                ItemRef marked = named("diamond", "Marked Diamond", 1);
                RecipeSpec recipe = RecipeSpec.shaped(EXACT_RECIPE, List.of("A"), Map.of('A', new Ingredient.Exact(marked)), Fixtures.item("emerald")).withRequiredLevel(0);
                if (!AdaptCore.platform().recipes().add(recipe)) {
                    throw new IllegalStateException("Exact-choice recipe registration failed");
                }
                player.inventory().setItem(0, named("diamond", "Marked Diamond", mode.equals("bulk-valid") ? 9 : 1));
                player.inventory().setItem(1, Fixtures.item("diamond", 8));
            }
            default -> throw new IllegalArgumentException("Unknown item audit mode " + mode);
        }
    }

    private static void fill(PlayerRef player) {
        for (int slot = 0; slot < 36; slot++) {
            player.inventory().setItem(slot, Fixtures.item("stone", 64));
        }
    }

    private static void configure(String adaptationName, String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Configuration requires a finite value");
        }
        Adaptation<?> adaptation = Fixtures.adaptation(adaptationName);
        Object config = adaptation.getConfig();
        try {
            Field field = config.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            if (field.getType() == int.class) {
                field.setInt(config, (int) value);
            } else if (field.getType() == double.class) {
                field.setDouble(config, value);
            } else {
                throw new IllegalArgumentException("Unsupported numeric configuration " + fieldName);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot configure " + adaptationName + "/" + fieldName, failure);
        }
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        result.add("inventory", items(player.inventory()));
        InventoryRef top = player.openMenuInventory();
        result.add("top", top == null ? new JsonArray() : items(top));
        result.add("cursor", item(player.inventory().cursor()));
        result.addProperty("experience", experience().totalExperiencePoints(player));
        result.addProperty("crafted", CRAFTED.getOrDefault(player.uuid(), 0));
        result.add("clicks", CLICKS.getOrDefault(player.uuid(), new JsonArray()));
        JsonArray drops = new JsonArray();
        for (EntityRef entity : player.world().entities()) {
            if (entity instanceof ItemEntityRef drop) {
                drops.add(item(drop.item()));
            }
        }
        result.add("drops", drops);
        AdaptPlayer runtime = Objects.requireNonNull(Fixtures.adaptPlayer(player));
        JsonObject stats = new JsonObject();
        for (Map.Entry<String, Double> entry : runtime.getData().getStats().entrySet()) {
            stats.addProperty(entry.getKey(), entry.getValue());
        }
        result.add("stats", stats);
        return result;
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void on(InventoryClickEvent event) {
        JsonArray clicks = CLICKS.get(event.player().uuid());
        if (clicks == null || clicks.size() >= 80) {
            return;
        }
        JsonObject click = new JsonObject();
        click.addProperty("slot", event.rawSlot());
        click.addProperty("action", event.action().name());
        click.addProperty("cancelled", event.isCancelled());
        clicks.add(click);
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void on(ItemCraftedEvent event) {
        if (CRAFTED.containsKey(event.player().uuid())) {
            CRAFTED.merge(event.player().uuid(), event.crafted().amount(), Integer::sum);
        }
    }

    private static JsonArray items(InventoryRef inventory) {
        JsonArray items = new JsonArray();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemRef stack = inventory.item(slot);
            if (stack != null && !stack.isEmpty()) {
                JsonObject value = item(stack);
                value.addProperty("slot", slot);
                items.add(value);
            }
        }
        return items;
    }

    static JsonObject item(ItemRef stack) {
        JsonObject result = new JsonObject();
        if (stack == null || stack.isEmpty()) {
            return result;
        }
        result.addProperty("type", GolemControlsFixtures.name(stack.type()));
        result.addProperty("amount", stack.amount());
        result.addProperty("name", stack.hasDisplayName() ? stack.displayName() : "");
        stack.containerContents().ifPresent(contents -> {
            JsonArray stored = new JsonArray();
            for (int slot = 0; slot < contents.size(); slot++) {
                ItemRef content = contents.get(slot);
                if (content != null && !content.isEmpty()) {
                    JsonObject value = item(content);
                    value.addProperty("slot", slot);
                    stored.add(value);
                }
            }
            result.add("contents", stored);
        });
        return result;
    }

    static ItemRef named(String type, String name, int amount) {
        ItemRef item = Fixtures.item(type, amount);
        item.setDisplayName(name);
        return item;
    }
}
