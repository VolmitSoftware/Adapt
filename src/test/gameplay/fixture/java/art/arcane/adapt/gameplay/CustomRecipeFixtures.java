package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Crafter;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomRecipeFixtures {
    private static final int HEIGHT = 100;
    private static final List<NamespacedKey> RECIPES = List.of(Target.CORE.key(), Target.NODE.key());
    private static final Map<UUID, RecipeCase> CASES = new ConcurrentHashMap<>();

    private CustomRecipeFixtures() {
    }

    public static JsonObject execute(Player player, String[] args) {
        if (!player.getWorld().getName().startsWith("adapt_gameplay_")) {
            throw new IllegalStateException("Custom recipe checks require the gameplay arena");
        }
        if (args[2].equals("ready")) {
            JsonObject result = new JsonObject();
            result.addProperty("ready", Adapt.instance.getAdaptServer().getPlayer(player) != null
                && RECIPES.stream().allMatch(key -> Bukkit.getRecipe(key) != null));
            return result;
        }
        switch (args[2]) {
            case "prepare" -> prepare(player, new RecipeCase(
                Mode.valueOf(args[3].toUpperCase(Locale.ROOT)),
                Target.valueOf(args[4].toUpperCase(Locale.ROOT)),
                Variant.valueOf(args[5].toUpperCase(Locale.ROOT))));
            case "cleanup" -> {
                player.closeInventory();
                player.getInventory().clear();
                CASES.remove(player.getUniqueId());
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

    private static void prepare(Player player, RecipeCase test) {
        player.closeInventory();
        player.getInventory().clear();
        for (String name : List.of("golem-animus-binding", "golem-node-weaving", "golem-guardian-core")) {
            learn(player, name);
        }
        World world = player.getWorld();
        world.getBlockAt(0, HEIGHT, 0).setType(Material.CRAFTING_TABLE, false);
        world.getBlockAt(3, HEIGHT, 0).setType(Material.AIR, true);
        world.getBlockAt(4, HEIGHT, 0).setType(Material.AIR, true);
        world.getBlockAt(5, HEIGHT, 0).setType(Material.CHEST, false);
        Chest chest = (Chest) world.getBlockAt(5, HEIGHT, 0).getState();
        chest.getBlockInventory().clear();
        player.teleport(new Location(world, 1.5D, HEIGHT, 2.5D));
        ItemStack ingredient = ingredient(test);
        if (test.mode() == Mode.BOOK) {
            player.getInventory().setItem(0, ingredient);
            if (test.target() == Target.CORE) {
                player.getInventory().setItem(1, new ItemStack(Material.IRON_SWORD));
                player.getInventory().setItem(2, new ItemStack(Material.IRON_INGOT, 2));
                player.getInventory().setItem(3, new ItemStack(Material.SHIELD));
            } else {
                player.getInventory().setItem(1, new ItemStack(Material.CHEST));
            }
        } else {
            prepareCrafter(world, test.target(), ingredient);
        }
        player.getInventory().setHeldItemSlot(8);
        player.undiscoverRecipes(RECIPES);
        player.discoverRecipes(RECIPES);
        CASES.put(player.getUniqueId(), test);
    }

    private static void prepareCrafter(World world, Target target, ItemStack ingredient) {
        world.getBlockAt(4, HEIGHT, 0).setType(Material.CRAFTER, false);
        world.getBlockAt(4, HEIGHT, 0).setBlockData(Bukkit.createBlockData("minecraft:crafter[orientation=east_up]"), false);
        Crafter crafter = (Crafter) world.getBlockAt(4, HEIGHT, 0).getState();
        Inventory grid = crafter.getInventory();
        grid.clear();
        grid.setItem(4, ingredient);
        if (target == Target.CORE) {
            grid.setItem(1, new ItemStack(Material.IRON_SWORD));
            grid.setItem(3, new ItemStack(Material.IRON_INGOT));
            grid.setItem(5, new ItemStack(Material.IRON_INGOT));
            grid.setItem(7, new ItemStack(Material.SHIELD));
        } else {
            grid.setItem(1, new ItemStack(Material.CHEST));
        }
        world.getBlockAt(3, HEIGHT, 0).setType(Material.LEVER, false);
        Switch lever = (Switch) world.getBlockAt(3, HEIGHT, 0).getBlockData();
        lever.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
        lever.setFacing(BlockFace.EAST);
        lever.setPowered(false);
        world.getBlockAt(3, HEIGHT, 0).setBlockData(lever, true);
    }

    private static ItemStack ingredient(RecipeCase test) {
        ItemStack canonical = test.target().blank();
        ItemStack item = switch (test.variant()) {
            case CANONICAL, DECORATED, WRONG_MATERIAL -> canonical;
            case VANILLA -> new ItemStack(canonical.getType());
            case WRONG_VARIANT -> test.target() == Target.CORE
                ? GolemItems.core(GolemCoreType.GATHERER) : GolemItems.node(NodeType.GUARD);
            case MISSING -> null;
        };
        if (item == null) {
            return null;
        }
        if (test.variant() == Variant.DECORATED) {
            ItemMeta metadata = item.getItemMeta();
            metadata.setDisplayName("Workshop component");
            metadata.setLore(List.of("A renamed component", "The custom identity stays intact"));
            item.setItemMeta(metadata);
        } else if (test.variant() == Variant.WRONG_MATERIAL) {
            item.setType(Material.DIAMOND);
            if (GolemItems.kindOf(item).isEmpty()) {
                throw new IllegalStateException("Wrong-material fixture lost its copied custom tag");
            }
        }
        item.setAmount(2);
        return item;
    }

    private static JsonObject snapshot(Player player) {
        RecipeCase test = Objects.requireNonNull(CASES.get(player.getUniqueId()), "Prepare a custom recipe case first");
        JsonObject result = new JsonObject();
        result.addProperty("mode", test.mode().name());
        result.addProperty("target", test.target().name());
        result.addProperty("variant", test.variant().name());
        result.addProperty("recipe", test.target().key().toString());
        result.addProperty("canonicalMaterial", test.target().blank().getType().name());
        result.add("inventory", items(player.getInventory().getStorageContents()));
        result.add("cursor", item(player.getItemOnCursor()));
        JsonObject crafting = new JsonObject();
        if (player.getOpenInventory().getTopInventory() instanceof CraftingInventory grid) {
            crafting.add("matrix", items(grid.getMatrix()));
            crafting.add("result", item(grid.getResult()));
        }
        result.add("crafting", crafting);
        if (test.mode() == Mode.CRAFTER) {
            World world = player.getWorld();
            Crafter crafter = (Crafter) world.getBlockAt(4, HEIGHT, 0).getState();
            Chest output = (Chest) world.getBlockAt(5, HEIGHT, 0).getState();
            result.add("crafter", items(crafter.getInventory().getContents()));
            result.add("output", items(output.getBlockInventory().getContents()));
            result.addProperty("powered", ((Switch) world.getBlockAt(3, HEIGHT, 0).getBlockData()).isPowered());
        }
        return result;
    }

    private static JsonArray items(ItemStack[] stacks) {
        JsonArray result = new JsonArray();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.getType().isAir()) {
                result.add(item(stack));
            }
        }
        return result;
    }

    private static JsonElement item(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return JsonNull.INSTANCE;
        }
        JsonObject result = new JsonObject();
        result.addProperty("material", stack.getType().name());
        result.addProperty("amount", stack.getAmount());
        result.addProperty("kind", GolemItems.kindOf(stack).map(kind -> kind.id()).orElse(""));
        GolemItems.coreOf(stack).ifPresent(role -> result.addProperty("role", role.id()));
        GolemItems.nodeTypeOf(stack).ifPresent(node -> result.addProperty("node", node.id()));
        ItemMeta metadata = stack.getItemMeta();
        result.addProperty("name", metadata != null && metadata.hasDisplayName() ? metadata.getDisplayName() : "");
        result.addProperty("loreLines", metadata != null && metadata.hasLore() ? metadata.getLore().size() : 0);
        return result;
    }

    private static void learn(Player player, String name) {
        AdaptPlayer runtime = Objects.requireNonNull(Adapt.instance.getAdaptServer().getPlayer(player));
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (adaptation.getName().equals(name)) {
                    runtime.getData().getSkillLine(skill.getName()).setAdaptation(adaptation, 1);
                    return;
                }
            }
        }
        throw new IllegalArgumentException("Unknown recipe adaptation: " + name);
    }

    private enum Mode {
        BOOK, CRAFTER
    }

    private enum Variant {
        CANONICAL, DECORATED, VANILLA, WRONG_VARIANT, WRONG_MATERIAL, MISSING
    }

    private enum Target {
        CORE, NODE;

        private NamespacedKey key() {
            return new NamespacedKey("adapt", this == CORE ? "golemancy-guardian-core" : "golemancy-deposit-node");
        }

        private ItemStack blank() {
            return this == CORE ? GolemItems.blankCore() : GolemItems.blankNode();
        }
    }

    private record RecipeCase(Mode mode, Target target, Variant variant) {
    }
}
