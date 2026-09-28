package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.world.AdaptPlayer;
import com.google.gson.JsonObject;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HarvestFixtures implements Listener {
    private static final Set<String> STAGES = Set.of("harvest-deep", "harvest-obsidian", "harvest-stone",
            "harvest-fragile", "harvest-spawner", "harvest-wet", "harvest-earth", "harvest-fall",
            "harvest-seeds", "harvest-spores", "harvest-compost", "harvest-growth", "harvest-bees", "harvest-shield");
    private static final List<Block> CELLS = new ArrayList<>();
    private static final Map<UUID, JsonObject> FALLS = new HashMap<>();

    private HarvestFixtures() {
    }

    public static void install(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new HarvestFixtures(), plugin);
    }

    public static boolean stage(String name, World world, Player actor, Player opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        CELLS.clear();
        FALLS.remove(actor.getUniqueId());
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);
        actor.setFoodLevel(20);
        actor.setSaturation(0);
        actor.setCooldown(Material.COMPOSTER, 0);
        actor.setCooldown(Material.WHEAT_SEEDS, 0);
        actor.setCooldown(Material.DIAMOND_SHOVEL, 0);
        opponent.teleport(new Location(world, 0.5, 100, 6.5));
        switch (name) {
            case "harvest-deep" -> toolBlock(world, actor, Material.DIAMOND_PICKAXE, Material.DEEPSLATE);
            case "harvest-obsidian" -> toolBlock(world, actor, Material.DIAMOND_PICKAXE, Material.OBSIDIAN);
            case "harvest-stone" -> {
                toolBlock(world, actor, Material.DIAMOND_PICKAXE, Material.STONE);
                cell(world, 3, 100, 1, Material.STONE);
                cell(world, 3, 101, 0, Material.STONE);
                cell(world, 3, 101, 1, Material.STONE);
            }
            case "harvest-fragile" -> {
                cell(world, 3, 100, 0, Material.STONE);
                ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
                Damageable meta = (Damageable) pickaxe.getItemMeta();
                meta.setDamage(Material.DIAMOND_PICKAXE.getMaxDurability() - 1);
                pickaxe.setItemMeta(meta);
                actor.getInventory().addItem(pickaxe);
            }
            case "harvest-spawner" -> toolBlock(world, actor, Material.DIAMOND_PICKAXE, Material.SPAWNER);
            case "harvest-wet" -> {
                toolBlock(world, actor, Material.DIAMOND_SHOVEL, Material.CLAY);
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        world.getBlockAt(x, 100, z).setType(Material.WATER, false);
                    }
                }
            }
            case "harvest-earth" -> {
                actor.getInventory().addItem(new ItemStack(Material.DIAMOND_SHOVEL));
                Zombie zombie = world.spawn(new Location(world, 3.5, 100, 0.5), Zombie.class);
                zombie.setAI(false);
                zombie.getEquipment().setHelmet(new ItemStack(Material.LEATHER_HELMET));
                zombie.addScoreboardTag("adapt-qa-harvest-target");
            }
            case "harvest-fall" -> {
                for (int x = -4; x <= 4; x++) {
                    for (int z = -4; z <= 4; z++) {
                        cell(world, x, 99, z, Material.DIRT);
                    }
                }
                world.getBlockAt(0, 107, 0).setType(Material.STONE, false);
                actor.teleport(new Location(world, 0.5, 108, 0.5));
            }
            case "harvest-seeds" -> {
                farmland(world, 3, false);
                actor.getInventory().addItem(new ItemStack(Material.WHEAT_SEEDS, 64));
            }
            case "harvest-spores" -> {
                for (int x = -6; x <= 9; x++) {
                    for (int z = -6; z <= 6; z++) {
                        cell(world, x, 99, z, Material.DIRT);
                    }
                }
                world.getBlockAt(3, 99, 0).setType(Material.MYCELIUM, false);
                actor.getInventory().addItem(new ItemStack(Material.RED_MUSHROOM, 64));
            }
            case "harvest-compost" -> {
                cell(world, 3, 100, 0, Material.COMPOSTER);
                world.dropItem(new Location(world, 4.5, 100.1, 0.5), new ItemStack(Material.WHEAT_SEEDS, 64)).setPickupDelay(0);
            }
            case "harvest-growth", "harvest-bees" -> {
                farmland(world, 18, true);
                if (name.equals("harvest-bees")) {
                    actor.getInventory().addItem(new ItemStack(Material.DANDELION));
                    Bee bee = world.spawn(new Location(world, 3.5, 102, 0.5), Bee.class);
                    bee.setAI(false);
                }
            }
            case "harvest-shield" -> {
                opponent.teleport(new Location(world, 2.5, 100, 0.5));
                opponent.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
            }
            default -> throw new IllegalStateException("Unhandled harvest stage " + name);
        }
        return true;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        if (FALLS.containsKey(player.getUniqueId())) result.add("fall", FALLS.get(player.getUniqueId()));
        JsonObject blocks = new JsonObject();
        int crops = 0;
        int cropAgeTotal = 0;
        int mycelium = 0;
        for (Block block : CELLS) {
            if (block.getWorld() != player.getWorld()) {
                continue;
            }
            if (CELLS.size() <= 16) {
                String key = block.getX() + "," + block.getY() + "," + block.getZ();
                blocks.addProperty(key, block.getType().name());
            }
            if (block.getType() == Material.MYCELIUM) {
                mycelium++;
            }
            Block above = block.getRelative(0, 1, 0);
            if (above.getType() == Material.WHEAT && above.getBlockData() instanceof Ageable ageable) {
                crops++;
                cropAgeTotal += ageable.getAge();
            }
            if (block.getType() == Material.COMPOSTER && block.getBlockData() instanceof Levelled levelled) {
                result.addProperty("compostLevel", levelled.getLevel());
            }
        }
        result.add("blocks", blocks);
        result.addProperty("crops", crops);
        result.addProperty("cropAgeTotal", cropAgeTotal);
        result.addProperty("mycelium", mycelium);
        ItemStack hand = player.getInventory().getItemInMainHand();
        result.addProperty("heldType", hand.getType().name());
        result.addProperty("heldDamage", hand.getItemMeta() instanceof Damageable damageable ? damageable.getDamage() : 0);
        JsonObject drops = new JsonObject();
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Item item) {
                String material = item.getItemStack().getType().name();
                int before = drops.has(material) ? drops.get(material).getAsInt() : 0;
                drops.addProperty(material, before + item.getItemStack().getAmount());
            }
            if (entity instanceof LivingEntity target && entity.getScoreboardTags().contains("adapt-qa-harvest-target")) {
                JsonObject state = new JsonObject();
                state.addProperty("health", target.getHealth());
                state.addProperty("x", target.getLocation().getX());
                JsonObject effects = new JsonObject();
                for (PotionEffect effect : target.getActivePotionEffects()) {
                    effects.addProperty(effect.getType().getKey().toString(), effect.getAmplifier());
                }
                state.add("effects", effects);
                result.add("target", state);
            }
        }
        result.add("drops", drops);
        return result;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void beforeFall(EntityDamageEvent event) {
        observeFall(event, "before");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterFall(EntityDamageEvent event) {
        observeFall(event, "after");
    }

    private void observeFall(EntityDamageEvent event, String phase) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)
                || !player.getName().startsWith("AQA")) return;
        JsonObject state = new JsonObject();
        Location at = player.getLocation();
        state.addProperty("x", at.getX());
        state.addProperty("y", at.getY());
        state.addProperty("z", at.getZ());
        state.addProperty("world", at.getWorld().getName());
        state.addProperty("damage", event.getDamage());
        state.addProperty("cancelled", event.isCancelled());
        AdaptPlayer adapted = Adapt.instance.getAdaptServer().getPlayer(player);
        AdaptPlayer.FxPosition position = adapted.getFxPosition();
        if (position != null) {
            JsonObject feedback = new JsonObject();
            feedback.addProperty("x", position.x());
            feedback.addProperty("y", position.y());
            feedback.addProperty("z", position.z());
            feedback.addProperty("world", position.world().getName());
            state.add("feedbackPosition", feedback);
        }
        FALLS.computeIfAbsent(player.getUniqueId(), ignored -> new JsonObject()).add(phase, state);
    }

    private static void toolBlock(World world, Player actor, Material tool, Material block) {
        actor.getInventory().addItem(new ItemStack(tool));
        cell(world, 3, 100, 0, block);
    }

    private static void farmland(World world, int radius, boolean crops) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                cell(world, x, 99, z, Material.FARMLAND);
                if (crops) {
                    world.getBlockAt(x, 100, z).setType(Material.WHEAT, false);
                }
            }
        }
    }

    private static void cell(World world, int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(material, false);
        CELLS.add(block);
    }
}
