package art.arcane.adapt.gameplay;

import art.arcane.adapt.content.item.BoundEnderPearl;
import art.arcane.adapt.content.item.BoundEyeOfEnder;
import com.google.gson.JsonObject;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RiftFixtures implements Listener {
    private static final Set<String> STAGES = Set.of("rift-descent", "rift-blink", "rift-magnet", "rift-pocket",
            "rift-rebound", "rift-visage", "rift-taglock", "rift-void-skin", "rift-gate", "rift-access", "rift-conduit");
    private static final Map<UUID, JsonObject> TARGETS = new HashMap<>();
    private static final Map<UUID, JsonObject> TELEPORTS = new HashMap<>();

    private RiftFixtures() {
    }

    public static void install(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new RiftFixtures(), plugin);
    }

    public static boolean stage(String name, World world, Player actor, Player opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        TARGETS.remove(actor.getUniqueId());
        TELEPORTS.remove(actor.getUniqueId());
        actor.getEnderChest().clear();
        actor.setCooldown(Material.ENDER_PEARL, 0);
        actor.setCooldown(Material.ENDER_EYE, 0);
        actor.setFoodLevel(20);
        actor.setSaturation(0);
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);
        opponent.teleport(new Location(world, 0.5, 100, 10.5));
        switch (name) {
            case "rift-descent" -> {
                ItemStack potion = new ItemStack(Material.POTION);
                PotionMeta meta = (PotionMeta) potion.getItemMeta();
                meta.setBasePotionType(PotionType.WATER);
                meta.addCustomEffect(new PotionEffect(PotionEffectType.LEVITATION, 200, 0), true);
                potion.setItemMeta(meta);
                actor.getInventory().addItem(potion);
            }
            case "rift-magnet" -> world.dropItem(new Location(world, 5.5, 100.1, 0.5), new ItemStack(Material.DIAMOND, 3)).setPickupDelay(0);
            case "rift-pocket" -> {
                actor.getEnderChest().addItem(new ItemStack(Material.STONE, 16));
                world.getBlockAt(3, 100, 0).setType(Material.STONE, false);
                actor.getInventory().addItem(new ItemStack(Material.DIAMOND, 3));
            }
            case "rift-rebound" -> {
                actor.getInventory().addItem(new ItemStack(Material.ENDER_PEARL));
                for (int x : new int[]{-8, 8}) {
                    for (int y = 100; y <= 105; y++) {
                        for (int z = -3; z <= 3; z++) {
                            world.getBlockAt(x, y, z).setType(Material.STONE, false);
                        }
                    }
                }
            }
            case "rift-visage" -> {
                world.setTime(18000);
                actor.getInventory().addItem(new ItemStack(Material.ENDER_PEARL));
                Enderman enderman = world.spawn(new Location(world, 4.5, 100, 0.5), Enderman.class);
                enderman.addScoreboardTag("adapt-qa-rift-target");
                for (int x = 3; x <= 5; x++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x != 4 || z != 0) {
                            world.getBlockAt(x, 100, z).setType(Material.OAK_FENCE, false);
                        }
                    }
                }
                world.getBlockAt(4, 103, 0).setType(Material.STONE, false);
            }
            case "rift-taglock" -> {
                actor.getInventory().addItem(new ItemStack(Material.ENDER_PEARL));
                Cow cow = world.spawn(new Location(world, 2.5, 100, 0.5), Cow.class);
                cow.setAI(false);
                cow.addScoreboardTag("adapt-qa-rift-target");
                for (int y = 100; y <= 104; y++) {
                    for (int z = -2; z <= 2; z++) {
                        world.getBlockAt(9, y, z).setType(Material.STONE, false);
                    }
                }
            }
            case "rift-void-skin" -> {
                actor.setHealth(2);
                actor.getInventory().addItem(new ItemStack(Material.ENDER_PEARL));
                opponent.teleport(new Location(world, 2.5, 100, 0.5));
                opponent.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
            }
            case "rift-gate" -> actor.getInventory().addItem(BoundEyeOfEnder.io.withData(new BoundEyeOfEnder.Data(null)));
            case "rift-access" -> {
                actor.getInventory().addItem(BoundEnderPearl.io.withData(new BoundEnderPearl.Data(null)));
                chest(world, 3, 0, 4);
            }
            case "rift-conduit" -> {
                actor.getInventory().addItem(new ItemStack(Material.ENDER_PEARL));
                chest(world, 3, -2, 7);
                chest(world, 3, 2, 0);
            }
            default -> { }
        }
        return true;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        result.add("enderChest", contents(player.getEnderChest()));
        JsonObject targeting = TARGETS.get(player.getUniqueId());
        if (targeting != null) {
            result.add("targeting", targeting);
        }
        JsonObject teleport = TELEPORTS.get(player.getUniqueId());
        if (teleport != null) {
            result.add("teleport", teleport);
        }
        JsonObject drops = new JsonObject();
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Item item) {
                String type = item.getItemStack().getType().name();
                drops.addProperty(type, (drops.has(type) ? drops.get(type).getAsInt() : 0) + item.getItemStack().getAmount());
            }
            if (entity instanceof LivingEntity target && entity.getScoreboardTags().contains("adapt-qa-rift-target")) {
                JsonObject state = new JsonObject();
                state.addProperty("entityId", target.getEntityId());
                state.addProperty("health", target.getHealth());
                state.addProperty("x", target.getLocation().getX());
                state.addProperty("y", target.getLocation().getY());
                state.addProperty("z", target.getLocation().getZ());
                state.addProperty("targetingPlayer", target instanceof Mob mob && player.equals(mob.getTarget()));
                result.add("target", state);
            }
        }
        result.add("drops", drops);
        JsonObject chests = new JsonObject();
        for (int z : new int[]{-2, 0, 2}) {
            if (player.getWorld().getBlockAt(3, 100, z).getState() instanceof Container container) {
                chests.add(Integer.toString(z), contents(container.getInventory()));
            }
        }
        result.add("chests", chests);
        ItemStack hand = player.getInventory().getItemInMainHand();
        result.addProperty("accessBound", BoundEnderPearl.isBindableItem(hand) && BoundEnderPearl.getBlock(hand) != null);
        result.addProperty("gateBound", BoundEyeOfEnder.isBindableItem(hand) && BoundEyeOfEnder.getLocation(hand) != null);
        return result;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player player)
                || !event.getEntity().getScoreboardTags().contains("adapt-qa-rift-target")) {
            return;
        }
        JsonObject result = new JsonObject();
        result.addProperty("cancelled", event.isCancelled());
        result.addProperty("reason", event.getReason().name());
        result.addProperty("entityId", event.getEntity().getEntityId());
        TARGETS.put(player.getUniqueId(), result);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!player.getName().startsWith("AQA") || !player.getWorld().getName().startsWith("adapt_gameplay_")) {
            return;
        }
        JsonObject result = new JsonObject();
        result.addProperty("cause", event.getCause().name());
        result.addProperty("fromX", event.getFrom().getX());
        result.addProperty("fromZ", event.getFrom().getZ());
        result.addProperty("toX", event.getTo().getX());
        result.addProperty("toZ", event.getTo().getZ());
        TELEPORTS.put(player.getUniqueId(), result);
    }

    private static JsonObject contents(Inventory inventory) {
        JsonObject result = new JsonObject();
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            String type = item.getType().name();
            result.addProperty(type, (result.has(type) ? result.get(type).getAsInt() : 0) + item.getAmount());
        }
        return result;
    }

    private static void chest(World world, int x, int z, int diamonds) {
        Block block = world.getBlockAt(x, 100, z);
        block.setType(Material.CHEST, false);
        Container chest = (Container) block.getState();
        chest.getInventory().clear();
        if (diamonds > 0) {
            chest.getInventory().addItem(new ItemStack(Material.DIAMOND, diamonds));
        }
    }
}
