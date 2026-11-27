package art.arcane.adapt.gameplay;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeHost;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomRecipeFixtures {
    private static final int HEIGHT = 100;
    private static final List<Id> RECIPES = List.of(Target.CORE.key(), Target.NODE.key());
    private static final Map<UUID, RecipeCase> CASES = new ConcurrentHashMap<>();

    private CustomRecipeFixtures() {
    }

    public static JsonObject execute(PlayerRef player, String[] args) {
        if (!Fixtures.isFixtureWorld(player.world())) {
            throw new IllegalStateException("Custom recipe checks require the gameplay arena");
        }
        if (args[2].equals("ready")) {
            JsonObject result = new JsonObject();
            result.addProperty("ready", Fixtures.adaptPlayer(player) != null
                && RECIPES.stream().allMatch(key -> recipes().recipe(key).isPresent()));
            return result;
        }
        switch (args[2]) {
            case "prepare" -> prepare(player, new RecipeCase(
                Mode.valueOf(args[3].toUpperCase(Locale.ROOT)),
                Target.valueOf(args[4].toUpperCase(Locale.ROOT)),
                Variant.valueOf(args[5].toUpperCase(Locale.ROOT))));
            case "cleanup" -> {
                player.closeInventory();
                Fixtures.clearInventory(player);
                CASES.remove(player.uuid());
                JsonObject result = new JsonObject();
                result.addProperty("cleaned", true);
                return result;
            }
            case "snapshot" -> {
            }
            default -> throw new IllegalArgumentException("Unknown custom recipe action: " + args[2]);
        }
        return snapshot(player);
    }

    private static RecipeHost recipes() {
        return AdaptCore.platform().recipes();
    }

    private static void prepare(PlayerRef player, RecipeCase test) {
        player.closeInventory();
        Fixtures.clearInventory(player);
        for (String name : List.of("adapt:golem/animus_binding", "adapt:golem/node_weaving", "adapt:golem/guardian_core")) {
            GolemControlsFixtures.learn(player, name, 1);
        }
        WorldRef world = player.world();
        Fixtures.set(world, 0, HEIGHT, 0, "crafting_table");
        world.blockAt(3, HEIGHT, 0).setType(Fixtures.id("air"), true);
        world.blockAt(4, HEIGHT, 0).setType(Fixtures.id("air"), true);
        Fixtures.set(world, 5, HEIGHT, 0, "chest");
        clear(world.blockAt(5, HEIGHT, 0).blockInventory());
        Fixtures.teleport(player, world, 1.5D, HEIGHT, 2.5D);
        ItemRef ingredient = ingredient(test);
        if (test.mode() == Mode.BOOK) {
            player.inventory().setItem(0, ingredient);
            if (test.target() == Target.CORE) {
                player.inventory().setItem(1, Fixtures.item("iron_sword"));
                player.inventory().setItem(2, Fixtures.item("iron_ingot", 2));
                player.inventory().setItem(3, Fixtures.item("shield"));
            } else {
                player.inventory().setItem(1, Fixtures.item("chest"));
            }
        } else {
            prepareCrafter(world, test.target(), ingredient);
        }
        player.inventory().setHeldSlot(8);
        for (Id key : RECIPES) {
            recipes().undiscover(player, key);
        }
        for (Id key : RECIPES) {
            recipes().discover(player, key);
        }
        CASES.put(player.uuid(), test);
    }

    private static void clear(InventoryRef inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.clear(slot);
        }
    }

    private static void prepareCrafter(WorldRef world, Target target, ItemRef ingredient) {
        Fixtures.set(world, 4, HEIGHT, 0, "crafter");
        Fixtures.set(world, 4, HEIGHT, 0, "crafter[orientation=east_up]");
        InventoryRef grid = world.blockAt(4, HEIGHT, 0).container();
        clear(grid);
        grid.setItem(4, ingredient);
        if (target == Target.CORE) {
            grid.setItem(1, Fixtures.item("iron_sword"));
            grid.setItem(3, Fixtures.item("iron_ingot"));
            grid.setItem(5, Fixtures.item("iron_ingot"));
            grid.setItem(7, Fixtures.item("shield"));
        } else {
            grid.setItem(1, Fixtures.item("chest"));
        }
        Fixtures.set(world, 3, HEIGHT, 0, "lever");
        world.blockAt(3, HEIGHT, 0).setState(Fixtures.state("lever[face=floor,facing=east,powered=false]"), true);
    }

    private static ItemRef ingredient(RecipeCase test) {
        ItemRef canonical = test.target().blank();
        ItemRef item = switch (test.variant()) {
            case CANONICAL, DECORATED, WRONG_MATERIAL -> canonical;
            case VANILLA -> Fixtures.items().create(canonical.type());
            case WRONG_VARIANT -> test.target() == Target.CORE
                ? GolemItems.core(GolemCoreType.GATHERER) : GolemItems.node(NodeType.GUARD);
            case MISSING -> null;
        };
        if (item == null) {
            return Fixtures.items().empty();
        }
        if (test.variant() == Variant.DECORATED) {
            item.setDisplayName("Workshop component");
            item.setLore(List.of("A renamed component", "The custom identity stays intact"));
        } else if (test.variant() == Variant.WRONG_MATERIAL) {
            item = item.copyAs(Id.minecraft("diamond"));
            if (GolemItems.kindOf(item).isEmpty()) {
                throw new IllegalStateException("Wrong-material fixture lost its copied custom tag");
            }
        }
        item.setAmount(2);
        return item;
    }

    private static JsonObject snapshot(PlayerRef player) {
        RecipeCase test = Objects.requireNonNull(CASES.get(player.uuid()), "Prepare a custom recipe case first");
        JsonObject result = new JsonObject();
        result.addProperty("mode", test.mode().name());
        result.addProperty("target", test.target().name());
        result.addProperty("variant", test.variant().name());
        result.addProperty("recipe", test.target().key().toString());
        result.addProperty("canonicalMaterial", GolemControlsFixtures.name(test.target().blank().type()));
        List<ItemRef> storage = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            storage.add(player.inventory().item(slot));
        }
        result.add("inventory", items(storage));
        result.add("cursor", item(player.inventory().cursor()));
        JsonObject crafting = new JsonObject();
        InventoryRef top = player.openMenuInventory();
        if (top == null || top.size() == 10) {
            List<ItemRef> matrix = new ArrayList<>();
            for (int slot = 1; top != null && slot < 10; slot++) {
                matrix.add(top.item(slot));
            }
            crafting.add("matrix", items(matrix));
            crafting.add("result", item(top == null ? null : top.item(0)));
        }
        result.add("crafting", crafting);
        if (test.mode() == Mode.CRAFTER) {
            WorldRef world = player.world();
            result.add("crafter", items(world.blockAt(4, HEIGHT, 0).container().contents()));
            result.add("output", items(world.blockAt(5, HEIGHT, 0).blockInventory().contents()));
            result.addProperty("powered", "true".equals(world.blockAt(3, HEIGHT, 0).state().property("powered")));
        }
        return result;
    }

    private static JsonArray items(List<ItemRef> stacks) {
        JsonArray result = new JsonArray();
        for (ItemRef stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                result.add(item(stack));
            }
        }
        return result;
    }

    private static JsonElement item(ItemRef stack) {
        if (stack == null || stack.isEmpty()) {
            return JsonNull.INSTANCE;
        }
        JsonObject result = new JsonObject();
        result.addProperty("material", GolemControlsFixtures.name(stack.type()));
        result.addProperty("amount", stack.amount());
        result.addProperty("kind", GolemItems.kindOf(stack).map(kind -> kind.id()).orElse(""));
        GolemItems.coreOf(stack).ifPresent(role -> result.addProperty("role", role.id()));
        GolemItems.nodeTypeOf(stack).ifPresent(node -> result.addProperty("node", node.id()));
        result.addProperty("name", stack.hasDisplayName() ? stack.displayName() : "");
        result.addProperty("loreLines", stack.lore() == null ? 0 : stack.lore().size());
        return result;
    }

    private enum Mode {
        BOOK, CRAFTER
    }

    private enum Variant {
        CANONICAL, DECORATED, VANILLA, WRONG_VARIANT, WRONG_MATERIAL, MISSING
    }

    private enum Target {
        CORE, NODE;

        private Id key() {
            return Id.of("adapt", this == CORE ? "golemancy-guardian-core" : "golemancy-deposit-node");
        }

        private ItemRef blank() {
            return this == CORE ? GolemItems.blankCore() : GolemItems.blankNode();
        }
    }

    private record RecipeCase(Mode mode, Target target, Variant variant) {
    }
}
