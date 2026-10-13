package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.architect.ArchitectScaffolder;
import art.arcane.adapt.util.common.scheduling.J;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.papermc.paper.event.inventory.ItemCraftedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemsAuditFixtures implements Listener {
    private static final NamespacedKey EXACT_RECIPE = new NamespacedKey("adapt_gameplay", "marked_diamond");
    private static final Map<UUID, JsonArray> CLICKS = new HashMap<>();
    private static final Map<UUID, Integer> CRAFTED = new HashMap<>();
    private static final Map<UUID, RetiredRefund> RETIREMENTS = new ConcurrentHashMap<>();

    public static JsonObject execute(Player player, String[] args) {
        if (args[2].startsWith("retirement-")) {
            return retirement(player, args);
        }
        if (!player.getWorld().getName().startsWith("adapt_gameplay_")) {
            throw new IllegalStateException("Items audit requires the gameplay arena");
        }
        switch (args[2]) {
            case "prepare" -> prepare(player, args[3]);
            case "fill" -> fill(player);
            case "space" -> player.getInventory().setItem(9, null);
            case "clear-cooldown" -> player.setCooldown(Material.GRINDSTONE, 0);
            case "configure" -> configure(args[3], args[4], Double.parseDouble(args[5]));
            case "snapshot" -> {
            }
            default -> throw new IllegalArgumentException("Unknown item audit operation " + args[2]);
        }
        return snapshot(player);
    }

    private static JsonObject retirement(Player player, String[] args) {
        JsonObject result = new JsonObject();
        if (args[2].equals("retirement-arm")) {
            ItemStack box = named(Material.PURPLE_SHULKER_BOX, "Retired Retention Box", 1);
            BlockStateMeta metadata = (BlockStateMeta) box.getItemMeta();
            ShulkerBox state = (ShulkerBox) metadata.getBlockState();
            state.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
            metadata.setBlockState(state);
            box.setItemMeta(metadata);
            RETIREMENTS.put(player.getUniqueId(), new RetiredRefund(player, player.getLocation(), box));
            result.addProperty("owner", player.getUniqueId().toString());
            result.add("original", item(box));
        } else if (args[2].equals("retirement-refund")) {
            RetiredRefund refund = Objects.requireNonNull(RETIREMENTS.remove(UUID.fromString(args[3])));
            if (refund.owner().isOnline()) {
                throw new IllegalStateException("Refund owner must have logged out");
            }
            if (!J.isOwnedByCurrentRegion(refund.source())) {
                throw new IllegalStateException("Observer must own the original refund region");
            }
            try {
                Method method = ArchitectScaffolder.class.getDeclaredMethod("restitute", Player.class, Location.class, List.class, Runnable.class);
                method.setAccessible(true);
                method.invoke(null, refund.owner(), refund.source(), List.of(refund.item()), (Runnable) () -> {
                    throw new IllegalStateException("Retired owner cannot receive inventory delivery");
                });
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot invoke Scaffolder restitution", failure);
            }
            result.addProperty("ownerOnline", false);
        } else if (!args[2].equals("retirement-snapshot")) {
            throw new IllegalArgumentException("Unknown retirement operation " + args[2]);
        }
        JsonArray drops = new JsonArray();
        for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (entity instanceof Item drop) {
                drops.add(item(drop.getItemStack()));
            }
        }
        result.add("drops", drops);
        return result;
    }

    private record RetiredRefund(Player owner, Location source, ItemStack item) {
    }

    private static void prepare(Player player, String mode) {
        player.closeInventory();
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getInventory().setHeldItemSlot(0);
        player.teleport(new Location(player.getWorld(), 0.5, 100, 0.5));
        player.setCooldown(Material.GRINDSTONE, 0);
        player.setLevel(0);
        player.setExp(0);
        player.setTotalExperience(0);
        CLICKS.put(player.getUniqueId(), new JsonArray());
        CRAFTED.put(player.getUniqueId(), 0);
        World world = player.getWorld();
        for (Item drop : world.getEntitiesByClass(Item.class)) {
            drop.remove();
        }
        world.getBlockAt(2, 100, 1).setType(Material.AIR, false);
        world.getBlockAt(2, 100, 2).setType(Material.CRAFTING_TABLE, false);
        switch (mode) {
            case "scaffolder" -> {
                world.getBlockAt(2, 100, 2).setType(Material.STONE, false);
                ItemStack box = named(Material.PURPLE_SHULKER_BOX, "Retention Box", 1);
                BlockStateMeta metadata = (BlockStateMeta) box.getItemMeta();
                ShulkerBox state = (ShulkerBox) metadata.getBlockState();
                state.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
                metadata.setBlockState(state);
                box.setItemMeta(metadata);
                player.getInventory().setItem(0, box);
            }
            case "provisioner" -> player.getInventory().setItem(0, new ItemStack(Material.WHEAT, 12));
            case "grindstone" -> {
                world.getBlockAt(2, 100, 2).setType(Material.GRINDSTONE, false);
                ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
                pickaxe.addUnsafeEnchantment(Enchantment.UNBREAKING, 2);
                player.getInventory().setItem(0, pickaxe);
            }
            case "bulk", "bulk-valid" -> {
                Bukkit.removeRecipe(EXACT_RECIPE);
                ItemStack marked = named(Material.DIAMOND, "Marked Diamond", 1);
                ShapedRecipe recipe = new ShapedRecipe(EXACT_RECIPE, new ItemStack(Material.EMERALD));
                recipe.shape("A");
                recipe.setIngredient('A', new RecipeChoice.ExactChoice(marked));
                if (!Bukkit.addRecipe(recipe)) {
                    throw new IllegalStateException("Exact-choice recipe registration failed");
                }
                player.getInventory().setItem(0, named(Material.DIAMOND, "Marked Diamond", mode.equals("bulk-valid") ? 9 : 1));
                player.getInventory().setItem(1, new ItemStack(Material.DIAMOND, 8));
            }
            default -> throw new IllegalArgumentException("Unknown item audit mode " + mode);
        }
    }

    private static void fill(Player player) {
        for (int slot = 0; slot < player.getInventory().getStorageContents().length; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        }
    }

    private static void configure(String adaptationName, String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Configuration requires a finite value");
        }
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (!adaptation.getName().equals(adaptationName)) {
                    continue;
                }
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
                    return;
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Cannot configure " + adaptationName + "/" + fieldName, failure);
                }
            }
        }
        throw new IllegalArgumentException("Unknown adaptation " + adaptationName);
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        result.add("inventory", items(player.getInventory()));
        result.add("top", items(player.getOpenInventory().getTopInventory()));
        result.add("cursor", item(player.getItemOnCursor()));
        result.addProperty("experience", player.calculateTotalExperiencePoints());
        result.addProperty("crafted", CRAFTED.getOrDefault(player.getUniqueId(), 0));
        result.add("clicks", CLICKS.getOrDefault(player.getUniqueId(), new JsonArray()));
        JsonArray drops = new JsonArray();
        for (Item drop : player.getWorld().getEntitiesByClass(Item.class)) {
            drops.add(item(drop.getItemStack()));
        }
        result.add("drops", drops);
        AdaptPlayer runtime = Objects.requireNonNull(Adapt.instance.getAdaptServer().getPlayer(player));
        JsonObject stats = new JsonObject();
        for (Map.Entry<String, Double> entry : runtime.getData().getStats().entrySet()) {
            stats.addProperty(entry.getKey(), entry.getValue());
        }
        result.add("stats", stats);
        return result;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(InventoryClickEvent event) {
        JsonArray clicks = CLICKS.get(event.getWhoClicked().getUniqueId());
        if (clicks == null || clicks.size() >= 80) {
            return;
        }
        JsonObject click = new JsonObject();
        click.addProperty("slot", event.getRawSlot());
        click.addProperty("action", event.getAction().name());
        click.addProperty("cancelled", event.isCancelled());
        clicks.add(click);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ItemCraftedEvent event) {
        if (CRAFTED.containsKey(event.getPlayer().getUniqueId())) {
            CRAFTED.merge(event.getPlayer().getUniqueId(), event.getCraftedItem().getAmount(), Integer::sum);
        }
    }

    private static JsonArray items(Inventory inventory) {
        JsonArray items = new JsonArray();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.getType().isAir()) {
                JsonObject value = item(stack);
                value.addProperty("slot", slot);
                items.add(value);
            }
        }
        return items;
    }

    private static JsonObject item(ItemStack stack) {
        JsonObject result = new JsonObject();
        if (stack == null || stack.getType().isAir()) {
            return result;
        }
        result.addProperty("type", stack.getType().name());
        result.addProperty("amount", stack.getAmount());
        ItemMeta metadata = stack.getItemMeta();
        result.addProperty("name", metadata != null && metadata.hasDisplayName() ? metadata.getDisplayName() : "");
        if (metadata instanceof BlockStateMeta state && state.getBlockState() instanceof ShulkerBox box) {
            result.add("contents", items(box.getInventory()));
        }
        return result;
    }

    private static ItemStack named(Material material, String name, int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta metadata = item.getItemMeta();
        metadata.setDisplayName(name);
        item.setItemMeta(metadata);
        return item;
    }
}
