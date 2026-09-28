package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.content.adaptation.crafting.CraftingDeconstruction;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;

public final class CraftingFixtures implements Listener {
    private static final Map<UUID, JsonArray> INTERACTIONS = new HashMap<>();
    private static final Set<String> STAGES = Set.of("craft-bulk", "craft-thrifty", "craft-provision", "craft-masterwork",
            "craft-tinkerer", "craft-deconstruction", "craft-leather", "craft-backpack", "craft-station",
            "craft-stonecutter", "craft-shape", "craft-placement");

    public CraftingFixtures() {
    }

    public static boolean stage(String name, World world, Player actor, Player opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        INTERACTIONS.put(actor.getUniqueId(), new JsonArray());
        opponent.teleport(new Location(world, -5.5, 100, 5.5));
        actor.setFoodLevel(10);
        actor.setSaturation(0);
        switch (name) {
            case "craft-bulk" -> actor.getInventory().addItem(new ItemStack(Material.OAK_LOG, 9));
            case "craft-thrifty" -> actor.getInventory().addItem(new ItemStack(Material.OAK_LOG, 20));
            case "craft-provision" -> actor.getInventory().addItem(new ItemStack(Material.WHEAT, 36));
            case "craft-masterwork" -> actor.getInventory().addItem(new ItemStack(Material.OAK_PLANKS, 12), new ItemStack(Material.STICK, 24));
            case "craft-tinkerer" -> {
                actor.getInventory().addItem(damagedSword(Enchantment.SHARPNESS, 3), damagedSword(Enchantment.UNBREAKING, 2));
            }
            case "craft-deconstruction" -> {
                actor.getInventory().addItem(new ItemStack(Material.SHEARS), new ItemStack(Material.IRON_CHESTPLATE));
            }
            case "craft-leather" -> {
                actor.getInventory().addItem(new ItemStack(Material.ROTTEN_FLESH));
                world.getBlockAt(2, 100, 0).setType(Material.CAMPFIRE, false);
            }
            case "craft-backpack" -> actor.getInventory().addItem(new ItemStack(Material.LEATHER, 8), new ItemStack(Material.CHEST), new ItemStack(Material.APPLE, 3));
            case "craft-station" -> actor.getInventory().addItem(new ItemStack(Material.CRAFTING_TABLE), new ItemStack(Material.OAK_LOG));
            case "craft-stonecutter" -> actor.getInventory().setItemInOffHand(new ItemStack(Material.STONECUTTER));
            case "craft-shape" -> world.getBlockAt(2, 100, 0).setType(Material.OAK_LOG, false);
            case "craft-placement" -> {
                actor.getInventory().addItem(new ItemStack(Material.OAK_PLANKS, 32));
                for (int y = 100; y <= 102; y++) {
                    for (int z = -1; z <= 1; z++) {
                        world.getBlockAt(3, y, z).setType(Material.OAK_PLANKS, false);
                    }
                }
            }
            default -> throw new IllegalStateException("Unhandled crafting fixture stage " + name);
        }
        if (Set.of("craft-bulk", "craft-thrifty", "craft-provision", "craft-masterwork", "craft-tinkerer", "craft-backpack").contains(name)) {
            world.getBlockAt(2, 100, 2).setType(Material.CRAFTING_TABLE, false);
        }
        return true;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        result.add("interactions", INTERACTIONS.getOrDefault(player.getUniqueId(), new JsonArray()));
        result.add("inventory", inventory(player.getInventory().getContents()));
        result.add("top", inventory(player.getOpenInventory().getTopInventory().getContents()));
        result.addProperty("window", player.getOpenInventory().getType().name());
        result.addProperty("eyeHeight", player.getEyeHeight());
        result.addProperty("targetData", player.getWorld().getBlockAt(2, 100, 0).getBlockData().getAsString());
        CraftingDeconstruction salvage = null;
        if (player.getInventory().getItemInMainHand().getType() == Material.SHEARS) {
            RayTraceResult ray = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 6, entity -> entity instanceof Item);
            if (ray != null && ray.getHitEntity() != null) result.addProperty("rayItemId", ray.getHitEntity().getEntityId());
            for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
                for (Adaptation<?> adaptation : skill.getAdaptations()) {
                    if (adaptation instanceof CraftingDeconstruction deconstruction) salvage = deconstruction;
                }
            }
        }
        JsonArray drops = new JsonArray();
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Item dropped && dropped.getLocation().distanceSquared(player.getLocation()) < 144) {
                JsonObject item = item(dropped.getItemStack());
                item.addProperty("entityId", dropped.getEntityId());
                item.addProperty("x", dropped.getLocation().getX());
                item.addProperty("y", dropped.getLocation().getY());
                item.addProperty("z", dropped.getLocation().getZ());
                if (salvage != null) {
                    item.addProperty("pickupEligible", salvage.canSnatchItem(player, dropped));
                    item.addProperty("sourceValue", salvage.getValue(dropped.getItemStack()));
                    item.addProperty("ironIngotValue", salvage.getValue(Material.IRON_INGOT));
                    item.addProperty("recipeCount", Bukkit.getRecipesFor(new ItemStack(dropped.getItemStack().getType())).size());
                    if (dropped.getItemStack().getType().getMaxDurability() > 0) {
                        ItemStack damagedLookup = new ItemStack(dropped.getItemStack().getType());
                        damagedLookup.setDurability((short) -1);
                        item.addProperty("damagedRecipeCount", Bukkit.getRecipesFor(damagedLookup).size());
                    }
                    item.add("salvage", inventory(salvage.getDeconstructionOfferings(dropped.getItemStack()).toArray(ItemStack[]::new)));
                }
                drops.add(item);
            }
        }
        result.add("drops", drops);
        JsonArray placement = new JsonArray();
        for (int y = 100; y <= 102; y++) {
            for (int z = -1; z <= 1; z++) {
                placement.add(player.getWorld().getBlockAt(2, y, z).getType().getKey().toString());
            }
        }
        result.add("placement", placement);
        return result;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        JsonArray interactions = INTERACTIONS.get(event.getPlayer().getUniqueId());
        if (interactions == null || interactions.size() >= 20) {
            return;
        }
        JsonObject observation = new JsonObject();
        observation.addProperty("action", event.getAction().name());
        observation.addProperty("cancelled", event.isCancelled());
        observation.addProperty("useItem", event.useItemInHand().name());
        observation.addProperty("useBlock", event.useInteractedBlock().name());
        observation.addProperty("hand", String.valueOf(event.getHand()));
        observation.addProperty("held", event.getPlayer().getInventory().getItemInMainHand().getType().name());
        observation.addProperty("sneaking", event.getPlayer().isSneaking());
        interactions.add(observation);
    }

    private static JsonArray inventory(ItemStack[] stacks) {
        JsonArray result = new JsonArray();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.getType().isAir()) {
                result.add(item(stack));
            }
        }
        return result;
    }

    private static JsonObject item(ItemStack stack) {
        JsonObject result = new JsonObject();
        result.addProperty("type", stack.getType().getKey().toString());
        result.addProperty("amount", stack.getAmount());
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof Damageable damageable) {
            result.addProperty("damage", damageable.getDamage());
            result.addProperty("maxDamage", damageable.hasMaxDamage() ? damageable.getMaxDamage() : stack.getType().getMaxDurability());
            result.addProperty("baseMaxDamage", stack.getType().getMaxDurability());
        }
        JsonObject enchants = new JsonObject();
        for (Map.Entry<Enchantment, Integer> entry : stack.getEnchantments().entrySet()) {
            enchants.addProperty(entry.getKey().getKey().toString(), entry.getValue());
        }
        result.add("enchantments", enchants);
        return result;
    }

    private static ItemStack damagedSword(Enchantment enchantment, int level) {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        sword.addUnsafeEnchantment(enchantment, level);
        Damageable meta = (Damageable) sword.getItemMeta();
        meta.setDamage(200);
        sword.setItemMeta(meta);
        return sword;
    }
}
