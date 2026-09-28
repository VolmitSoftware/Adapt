package art.arcane.adapt.gameplay;

import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.item.multiItems.OmniTool;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Set;

public final class EarthFixtures {
    private static final Set<String> STAGES = Set.of("earth-burrow", "earth-chisel", "earth-trophy", "earth-quarry", "earth-spelunker", "earth-omni", "earth-repair", "earth-graves", "earth-treasure", "earth-seismic");

    private EarthFixtures() {
    }

    public static boolean stage(String name, World arena, Player actor, Player opponent) {
        if (!STAGES.contains(name)) return false;
        actor.setFoodLevel(20);
        actor.setSaturation(0);
        actor.getInventory().addItem(new ItemStack(Material.DIAMOND_PICKAXE));
        switch (name) {
            case "earth-graves", "earth-treasure", "earth-seismic" -> {
                actor.getInventory().addItem(new ItemStack(Material.DIAMOND_SHOVEL));
                if (name.equals("earth-seismic")) arena.getBlockAt(4, 100, 1).setType(Material.DIAMOND_ORE, false);
                refill(arena, name.equals("earth-treasure") ? Material.SAND : Material.DIRT);
            }
            case "earth-burrow" -> {
                actor.getInventory().addItem(new ItemStack(Material.DIAMOND_SHOVEL));
                arena.getBlockAt(0, 93, 0).setType(Material.STONE, false);
                for (int y = 94; y <= 99; y++) arena.getBlockAt(0, y, 0).setType(Material.DIRT, false);
            }
            case "earth-trophy" -> {
                arena.getBlockAt(3, 100, 0).setType(Material.DRAGON_HEAD, false);
                XpProvenance.clearPlacementRecord(arena.getBlockAt(3, 100, 0));
                actor.setTotalExperience(0);
                actor.setLevel(0);
                actor.setExp(0);
            }
            case "earth-quarry", "earth-spelunker" -> {
                arena.getBlockAt(3, 100, 0).setType(Material.STONE, false);
                arena.getBlockAt(3, 100, 1).setType(Material.DIAMOND_ORE, false);
                actor.getInventory().addItem(new ItemStack(Material.GLOW_BERRIES, 3), new ItemStack(Material.DIAMOND_ORE));
            }
            case "earth-omni" -> {
                actor.getInventory().addItem(new ItemStack(Material.DIAMOND_AXE));
                arena.getBlockAt(3, 100, 0).setType(Material.OAK_LOG, false);
            }
            case "earth-repair" -> {
                actor.getInventory().clear();
                ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
                Damageable meta = (Damageable) tool.getItemMeta();
                meta.setDamage(200);
                tool.setItemMeta(meta);
                actor.getInventory().addItem(tool);
                for (int y = 100; y <= 102; y++) {
                    for (int z = -2; z <= 2; z++) arena.getBlockAt(3, y, z).setType(Material.STONE, false);
                }
            }
            default -> arena.getBlockAt(3, 100, 0).setType(Material.DIAMOND_ORE, false);
        }
        opponent.teleport(new Location(arena, -5.5, 100, 0.5));
        return true;
    }

    public static void refill(World arena, Material material) {
        for (int y = 100; y <= 102; y++) {
            for (int z = 0; z <= 2; z++) arena.getBlockAt(3, y, z).setType(material, false);
        }
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        JsonArray tools = new JsonArray();
        OmniTool omni = new OmniTool();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !(item.getItemMeta() instanceof Damageable damage)) continue;
            JsonObject tool = new JsonObject();
            tool.addProperty("type", item.getType().getKey().toString());
            tool.addProperty("damage", damage.getDamage());
            tool.addProperty("maxDamage", damage.hasMaxDamage() ? damage.getMaxDamage() : item.getType().getMaxDurability());
            tool.addProperty("components", omni.explode(item).size());
            tools.add(tool);
        }
        JsonArray drops = new JsonArray();
        for (Entity entity : player.getWorld().getEntities()) {
            if (!(entity instanceof Item item)) continue;
            JsonObject drop = new JsonObject();
            drop.addProperty("type", item.getItemStack().getType().getKey().toString());
            drop.addProperty("amount", item.getItemStack().getAmount());
            drops.add(drop);
        }
        result.add("drops", drops);
        result.add("tools", tools);
        result.addProperty("vanillaXp", player.getTotalExperience());
        return result;
    }
}
