package art.arcane.adapt.gameplay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.Map;
import java.util.Set;

public final class KnowledgeFixtures {
    private static final Set<String> STAGES = Set.of("knowledge-mending", "knowledge-quick", "knowledge-infusion",
            "knowledge-tome", "knowledge-reroll", "knowledge-cleanse", "knowledge-anvil", "knowledge-echo",
            "knowledge-unity", "knowledge-resist", "knowledge-notes", "knowledge-enchant");

    private KnowledgeFixtures() {
    }

    public static boolean stage(String name, World world, Player actor, Player opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        actor.setLevel(30);
        actor.setExp(0);
        actor.setEnchantmentSeed(424242);
        actor.setFoodLevel(10);
        actor.setSaturation(0);
        opponent.setFoodLevel(10);
        opponent.setSaturation(0);
        opponent.teleport(new Location(world, -5.5, 100, 5.5));
        switch (name) {
            case "knowledge-mending" -> {
                ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
                tool.addUnsafeEnchantment(Enchantment.MENDING, 1);
                Damageable meta = (Damageable) tool.getItemMeta();
                meta.setDamage(240);
                tool.setItemMeta(meta);
                actor.getInventory().addItem(tool);
                world.getBlockAt(2, 100, 0).setType(Material.STONE, false);
            }
            case "knowledge-quick", "knowledge-infusion", "knowledge-anvil" -> {
                ItemStack sword = new ItemStack(Material.IRON_SWORD);
                if (name.equals("knowledge-anvil")) {
                    sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 3);
                }
                actor.getInventory().addItem(sword, book(Map.of(Enchantment.SHARPNESS, 3)));
                world.getBlockAt(2, 100, 0).setType(Material.ANVIL, false);
            }
            case "knowledge-tome" -> {
                actor.getInventory().addItem(book(Map.of(Enchantment.SHARPNESS, 3, Enchantment.UNBREAKING, 2)));
                world.getBlockAt(2, 100, 0).setType(Material.ANVIL, false);
            }
            case "knowledge-reroll", "knowledge-enchant" -> {
                world.getBlockAt(2, 100, 0).setType(Material.ENCHANTING_TABLE, false);
                actor.getInventory().addItem(new ItemStack(Material.IRON_SWORD), new ItemStack(Material.LAPIS_LAZULI, 16));
            }
            case "knowledge-cleanse" -> {
                ItemStack sword = new ItemStack(Material.IRON_SWORD);
                sword.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
                sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 2);
                actor.getInventory().addItem(sword);
                world.getBlockAt(2, 100, 0).setType(Material.GRINDSTONE, false);
            }
            case "knowledge-echo", "knowledge-unity" -> {
                if (name.equals("knowledge-echo")) {
                    actor.getInventory().addItem(book(Map.of(Enchantment.SHARPNESS, 1)));
                }
                ExperienceOrb orb = world.spawn(new Location(world, 12.5, 100.2, 0.5), ExperienceOrb.class);
                orb.setExperience(40);
            }
            case "knowledge-resist" -> {
                actor.setHealth(10);
                opponent.teleport(new Location(world, 2.5, 100, 0.5));
                opponent.getInventory().setItemInMainHand(new ItemStack(Material.WOODEN_SWORD));
            }
            case "knowledge-notes" -> {
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                cow(world, "first", 2.5, 0.5, 1);
                cow(world, "second", 2.5, 1.6, 10);
            }
            default -> throw new IllegalStateException("Unhandled knowledge fixture stage " + name);
        }
        return true;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        result.addProperty("level", player.getLevel());
        result.addProperty("experience", player.calculateTotalExperiencePoints());
        result.addProperty("seed", player.getEnchantmentSeed());
        result.add("inventory", items(player.getInventory().getContents()));
        result.add("cursor", item(player.getItemOnCursor()));
        result.add("top", items(player.getOpenInventory().getTopInventory().getContents()));
        if (player.getOpenInventory() instanceof AnvilView anvil) {
            result.addProperty("repairCost", anvil.getRepairCost());
        }
        JsonArray drops = new JsonArray();
        JsonObject targets = new JsonObject();
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Item dropped && dropped.getLocation().distanceSquared(player.getLocation()) < 144) {
                drops.add(item(dropped.getItemStack()));
            }
            if (!(entity instanceof LivingEntity target)) {
                continue;
            }
            for (String tag : target.getScoreboardTags()) {
                if (tag.startsWith("adapt-qa-knowledge-")) {
                    JsonObject state = new JsonObject();
                    state.addProperty("entityId", target.getEntityId());
                    state.addProperty("health", target.getHealth());
                    targets.add(tag.substring("adapt-qa-knowledge-".length()), state);
                }
            }
        }
        result.add("drops", drops);
        result.add("targets", targets);
        return result;
    }

    private static JsonArray items(ItemStack[] items) {
        JsonArray result = new JsonArray();
        for (ItemStack stack : items) {
            if (stack != null && !stack.getType().isAir()) {
                result.add(item(stack));
            }
        }
        return result;
    }

    private static JsonObject item(ItemStack item) {
        JsonObject result = new JsonObject();
        result.addProperty("type", item == null ? "minecraft:air" : item.getType().getKey().toString());
        if (item == null || item.getType().isAir()) {
            return result;
        }
        result.addProperty("amount", item.getAmount());
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            result.addProperty("damage", damageable.getDamage());
        }
        JsonObject enchantments = new JsonObject();
        Map<Enchantment, Integer> enchants = meta instanceof EnchantmentStorageMeta book ? book.getStoredEnchants() : item.getEnchantments();
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            enchantments.addProperty(entry.getKey().getKey().toString(), entry.getValue());
        }
        result.add("enchantments", enchantments);
        return result;
    }

    private static ItemStack book(Map<Enchantment, Integer> enchants) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            meta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
        }
        book.setItemMeta(meta);
        return book;
    }

    private static void cow(World world, String label, double x, double z, double health) {
        Cow cow = world.spawn(new Location(world, x, 100, z), Cow.class);
        cow.setAI(false);
        cow.setHealth(health);
        cow.addScoreboardTag("adapt-qa-knowledge-" + label);
    }
}
