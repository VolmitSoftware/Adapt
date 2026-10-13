package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.Objects;

public final class DemolitionFixtures {
    private static final String TOOL_NAME = "Groundwork Tool";
    private static final String BLOCK_NAME = "Restitution Stone";
    private static final Map<String, Integer> LEARNED_PROFILE = Map.ofEntries(
        Map.entry("agility-air-dash", 4), Map.entry("agility-super-jump", 4), Map.entry("agility-wall-jump", 5),
        Map.entry("architect-foundation", 5), Map.entry("architect-demolition", 5), Map.entry("axe-chop", 5),
        Map.entry("axe-throwing-axe", 4), Map.entry("axe-wood-veinminer", 5), Map.entry("discovery-archaeologist", 6),
        Map.entry("discovery-better-mending", 6), Map.entry("discovery-insight", 4), Map.entry("discovery-keen-eye", 5),
        Map.entry("discovery-sixth-sense", 5), Map.entry("discovery-villager-att", 5), Map.entry("hunter-blood-trail", 5),
        Map.entry("pickaxe-autosmelt", 4), Map.entry("pickaxe-tunnel-bore", 3), Map.entry("pickaxe-unbreakable-pact", 3),
        Map.entry("excavation-grave-digger", 5), Map.entry("excavation-haste", 3), Map.entry("excavation-tunneler", 5)
    );

    private DemolitionFixtures() {
    }

    public static JsonObject execute(Player player, String[] args) {
        if (args[2].equals("prepare")) prepare(player, args[3]);
        if (args[2].equals("soil")) player.getWorld().getBlockAt(2, 99, 0).setType(Material.DIRT, false);
        if (args[2].equals("profile")) applyProfile(player);
        return snapshot(player);
    }

    private static void applyProfile(Player player) {
        AdaptPlayer runtime = Objects.requireNonNull(Adapt.instance.getAdaptServer().getPlayer(player));
        int matched = 0;
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                Integer configured = LEARNED_PROFILE.get(adaptation.getName());
                int level = configured == null ? 0 : configured;
                if (level > adaptation.getMaxLevel()) throw new IllegalStateException("Profile level exceeds " + adaptation.getName());
                runtime.getData().getSkillLine(skill.getName()).setAdaptation(adaptation, level);
                if (configured != null) matched++;
            }
        }
        if (matched != LEARNED_PROFILE.size()) throw new IllegalStateException("The complete learned profile was not found");
    }

    private static void prepare(Player player, String mode) {
        World world = player.getWorld();
        if (!world.getName().startsWith("adapt_gameplay_")) throw new IllegalStateException("Demolition fixture requires the gameplay arena");
        for (Item item : world.getEntitiesByClass(Item.class)) item.remove();
        player.getInventory().clear();
        player.getInventory().setHeldItemSlot(0);
        player.teleport(new Location(world, 0.5, 100, 0.5));
        world.getBlockAt(2, 100, 0).setType(Material.AIR, false);
        world.getBlockAt(2, 100, 1).setType(Material.AIR, false);
        world.getBlockAt(2, 100, 2).setType(Material.AIR, false);
        world.getBlockAt(2, 99, 0).setType(Material.DIRT, false);
        Material material = switch (mode) {
            case "path" -> Material.DIAMOND_SHOVEL;
            case "till" -> Material.DIAMOND_HOE;
            case "placed" -> Material.STONE;
            default -> throw new IllegalArgumentException("Unknown demolition fixture mode " + mode);
        };
        ItemStack item = new ItemStack(material);
        ItemMeta metadata = item.getItemMeta();
        metadata.setDisplayName(material == Material.STONE ? BLOCK_NAME : TOOL_NAME);
        if (metadata instanceof Damageable damageable) damageable.setDamage(37);
        item.setItemMeta(metadata);
        player.getInventory().setItem(0, item);
        if (mode.equals("placed")) world.getBlockAt(2, 100, 2).setType(Material.STONE, false);
    }

    private static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        JsonArray items = new JsonArray();
        for (ItemStack item : player.getInventory().getContents()) add(items, item, "inventory");
        for (Item item : player.getWorld().getEntitiesByClass(Item.class)) add(items, item.getItemStack(), "dropped");
        result.add("items", items);
        result.addProperty("soil", player.getWorld().getBlockAt(2, 99, 0).getType().name());
        result.addProperty("placed", player.getWorld().getBlockAt(2, 100, 1).getType().name());
        JsonObject learned = new JsonObject();
        AdaptPlayer runtime = Objects.requireNonNull(Adapt.instance.getAdaptServer().getPlayer(player));
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                int level = adaptation.getLevel(runtime);
                if (level > 0) learned.addProperty(adaptation.getName(), level);
            }
        }
        result.add("learned", learned);
        return result;
    }

    private static void add(JsonArray items, ItemStack item, String source) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return;
        JsonObject entry = new JsonObject();
        entry.addProperty("material", item.getType().name());
        entry.addProperty("amount", item.getAmount());
        entry.addProperty("source", source);
        ItemMeta metadata = item.getItemMeta();
        entry.addProperty("name", metadata != null && metadata.hasDisplayName() ? metadata.getDisplayName() : "");
        if (metadata instanceof Damageable damageable) entry.addProperty("damage", damageable.getDamage());
        items.add(entry);
    }
}
