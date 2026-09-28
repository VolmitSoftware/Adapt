package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.content.item.ItemListings;
import art.arcane.adapt.util.common.scheduling.J;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Strider;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class NatureFixtures implements Listener {
    private static final Set<String> STAGES = Set.of("nature-blaze", "nature-fire", "nature-broker", "nature-strider",
            "nature-luck", "nature-guardian", "nature-empathy", "nature-pact", "nature-bone", "nature-fishing");
    private static final List<LivingEntity> targets = new ArrayList<>();
    private static final JsonArray barters = new JsonArray();
    private static final JsonArray catches = new JsonArray();
    private static final JsonArray dismounts = new JsonArray();
    private static final Set<UUID> priorFishingItems = new HashSet<>();
    private static final Map<UUID, Double> originalMaxHealth = new HashMap<>();
    private static String currentStage;
    private static UUID actorId;
    private static int bites;
    private static int ordinaryBarterItems;

    public static boolean stage(String name, World arena, Player actor, Player opponent) {
        if (!STAGES.contains(name)) return false;
        currentStage = name;
        actorId = actor.getUniqueId();
        targets.clear();
        while (barters.size() > 0) barters.remove(0);
        while (catches.size() > 0) catches.remove(0);
        while (dismounts.size() > 0) dismounts.remove(0);
        bites = 0;
        World world = arena;
        if (Set.of("nature-broker", "nature-strider", "nature-fire", "nature-blaze").contains(name)) {
            NetherFixtures.stage("nether-qa-feast", arena, actor, opponent);
            world = actor.getWorld();
            actor.getInventory().clear();
        }
        originalMaxHealth.putIfAbsent(actor.getUniqueId(), actor.getAttribute(Attribute.MAX_HEALTH).getBaseValue());
        actor.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);
        actor.setHealth(200);
        actor.setFoodLevel(10);
        actor.setSaturation(0);
        opponent.setFoodLevel(10);
        opponent.setSaturation(0);
        opponent.teleport(new Location(world, 10.5, 100, 10.5));
        switch (name) {
            case "nature-blaze", "nature-fire" -> {
                world.getBlockAt(0, 99, -1).setType(name.equals("nature-blaze") ? Material.MAGMA_BLOCK : Material.NETHERRACK, false);
                if (name.equals("nature-fire")) world.getBlockAt(0, 100, -1).setType(Material.FIRE, false);
            }
            case "nature-broker" -> {
                actor.getInventory().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                actor.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 20));
                Piglin piglin = world.spawn(new Location(world, 2.5, 100, 0.5), Piglin.class);
                piglin.setAdult();
                piglin.setImmuneToZombification(true);
                piglin.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0);
                targets.add(piglin);
            }
            case "nature-strider" -> {
                for (int x = -4; x <= 4; x++) {
                    for (int z = -10; z <= -1; z++) world.getBlockAt(x, 99, z).setType(Material.LAVA, false);
                }
                Strider strider = world.spawn(new Location(world, 0.5, 100, -3), Strider.class);
                strider.setAdult();
                strider.setSaddle(true);
                strider.setAI(false);
                targets.add(strider);
            }
            case "nature-luck" -> {
                for (Material reward : ItemListings.getHerbalLuckFood()) {
                    if (!reward.isItem()) throw new IllegalStateException("Luck reward cannot be carried: " + reward);
                }
                for (int x = -2; x <= 2; x++) {
                    for (int z = -3; z <= -1; z++) {
                        world.getBlockAt(x, 99, z).setType(Material.DIRT, false);
                        world.getBlockAt(x, 100, z).setType(Material.DANDELION, false);
                    }
                }
            }
            case "nature-guardian" -> {
                opponent.teleport(new Location(world, 4.5, 100, 0.5));
                opponent.getInventory().addItem(new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 32));
                Wolf wolf = world.spawn(new Location(world, -1.5, 100, 2.5), Wolf.class);
                wolf.setAdult();
                wolf.setOwner(actor);
                wolf.setAI(false);
                wolf.setSitting(true);
                targets.add(wolf);
            }
            case "nature-empathy" -> {
                actor.getInventory().addItem(new ItemStack(Material.BONE, 20));
                for (int i = 0; i < 20; i++) {
                    Wolf wolf = world.spawn(new Location(world, 2.5, 100, 0.5), Wolf.class);
                    wolf.setAdult();
                    wolf.setAI(false);
                    wolf.setCollidable(false);
                    targets.add(wolf);
                }
            }
            case "nature-pact" -> {
                opponent.teleport(new Location(world, 2.5, 100, 0.5));
                opponent.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
            }
            case "nature-bone" -> {
                actor.setHealth(100);
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_AXE));
                for (int i = 0; i < 20; i++) {
                    Cow cow = world.spawn(new Location(world, 2.5, 100, 0.5), Cow.class);
                    cow.setAI(false);
                    cow.setCollidable(false);
                    cow.setHealth(1);
                    targets.add(cow);
                }
            }
            case "nature-fishing" -> {
                ItemStack rod = new ItemStack(Material.FISHING_ROD);
                rod.addEnchantment(Enchantment.LURE, 3);
                actor.getInventory().addItem(rod);
                for (int x = -8; x <= 8; x++) {
                    for (int z = -18; z <= -2; z++) {
                        world.getBlockAt(x, 95, z).setType(Material.STONE, false);
                        for (int y = 96; y <= 99; y++) world.getBlockAt(x, y, z).setType(Material.WATER, false);
                    }
                }
            }
            default -> throw new IllegalArgumentException("Unknown nature stage " + name);
        }
        if (name.equals("nature-pact") || name.equals("nature-guardian")) {
            for (int y = 100; y <= 103; y++) {
                for (int z = -1; z <= 1; z++) world.getBlockAt(-1, y, z).setType(Material.STONE, false);
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void beforeBarter(PiglinBarterEvent event) {
        if ("nature-broker".equals(currentStage) && targets.contains(event.getEntity())) ordinaryBarterItems = event.getOutcome().size();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dismounted(EntityDismountEvent event) {
        if (!"nature-strider".equals(currentStage) || !(event.getEntity() instanceof Player player)
                || !player.getUniqueId().equals(actorId) || !(event.getDismounted() instanceof Strider)) return;
        recordDismount(player, 0);
        J.runEntity(player, () -> recordDismount(player, 2), 2);
        J.runEntity(player, () -> recordDismount(player, 5), 5);
    }

    private static void recordDismount(Player player, int ticks) {
        JsonObject state = new JsonObject();
        state.addProperty("ticks", ticks);
        state.addProperty("y", player.getLocation().getY());
        state.addProperty("feet", player.getLocation().getBlock().getType().getKey().toString());
        state.addProperty("below", player.getLocation().subtract(0, 1, 0).getBlock().getType().getKey().toString());
        dismounts.add(state);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterBarter(PiglinBarterEvent event) {
        if (!"nature-broker".equals(currentStage) || !targets.contains(event.getEntity())) return;
        JsonObject result = new JsonObject();
        result.addProperty("ordinaryStacks", ordinaryBarterItems);
        result.add("outcome", items(event.getOutcome()));
        barters.add(result);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void beforeFish(PlayerFishEvent event) {
        if (!"nature-fishing".equals(currentStage) || !event.getPlayer().getUniqueId().equals(actorId)) return;
        if (event.getState() == PlayerFishEvent.State.BITE) bites++;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        priorFishingItems.clear();
        for (Entity entity : event.getPlayer().getWorld().getEntities()) if (entity instanceof Item) priorFishingItems.add(entity.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterFish(PlayerFishEvent event) {
        if (!"nature-fishing".equals(currentStage) || !event.getPlayer().getUniqueId().equals(actorId)
                || event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught)) return;
        JsonObject result = new JsonObject();
        result.add("ordinary", item(caught.getItemStack()));
        JsonArray extra = new JsonArray();
        for (Entity entity : event.getPlayer().getWorld().getNearbyEntities(event.getPlayer().getLocation(), 2, 2, 2)) {
            if (entity instanceof Item dropped && entity != caught && !priorFishingItems.contains(entity.getUniqueId())) extra.add(item(dropped.getItemStack()));
        }
        result.add("extra", extra);
        catches.add(result);
    }

    public static void reset(Player player) {
        Double previous = originalMaxHealth.remove(player.getUniqueId());
        if (previous == null) return;
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(previous);
        player.setHealth(Math.min(player.getHealth(), player.getAttribute(Attribute.MAX_HEALTH).getValue()));
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        result.addProperty("bites", bites);
        result.add("barters", barters);
        result.add("catches", catches);
        result.add("dismounts", dismounts);
        result.addProperty("inLava", player.isInLava());
        JsonArray hooks = new JsonArray();
        for (Entity entity : player.getWorld().getEntities()) {
            if (!(entity instanceof FishHook hook) || hook.getShooter() != player) continue;
            JsonObject fishing = new JsonObject();
            fishing.addProperty("state", hook.getState().name());
            fishing.addProperty("x", hook.getLocation().getX());
            fishing.addProperty("y", hook.getLocation().getY());
            fishing.addProperty("z", hook.getLocation().getZ());
            fishing.addProperty("block", hook.getLocation().getBlock().getType().getKey().toString());
            fishing.addProperty("openWater", hook.isInOpenWater());
            fishing.addProperty("waitTime", hook.getWaitTime());
            fishing.addProperty("timeUntilBite", hook.getTimeUntilBite());
            hooks.add(fishing);
        }
        result.add("hooks", hooks);
        JsonArray entities = new JsonArray();
        for (LivingEntity target : targets) {
            if (target.getWorld() != player.getWorld()) continue;
            JsonObject state = new JsonObject();
            state.addProperty("id", target.getEntityId());
            state.addProperty("dead", target.isDead());
            state.addProperty("health", target.getHealth());
            if (target instanceof Tameable pet) {
                state.addProperty("tamed", pet.isTamed());
                state.addProperty("owned", pet.getOwner() != null && pet.getOwner().getUniqueId().equals(player.getUniqueId()));
            }
            entities.add(state);
        }
        result.add("targets", entities);
        JsonArray globes = new JsonArray();
        for (Entity entity : player.getWorld().getEntities()) {
            if (!(entity instanceof Item dropped) || !dropped.getPersistentDataContainer().has(NamespacedKey.fromString("adapt:tragoul-globe"), PersistentDataType.BYTE)) continue;
            JsonObject globe = item(dropped.getItemStack());
            globe.addProperty("id", dropped.getEntityId());
            globe.addProperty("x", dropped.getLocation().getX());
            globe.addProperty("y", dropped.getLocation().getY());
            globe.addProperty("z", dropped.getLocation().getZ());
            globes.add(globe);
        }
        result.add("globes", globes);
        JsonObject stats = new JsonObject();
        for (String name : List.of("tragoul.bone-harvest.orbs-collected", "tragoul.blood-pact.health-sacrificed",
                "taming.wild-empathy.tames", "taming.guardian-instinct.intercepts", "nether.strider-bond.lava-rescues", "nether.fire-resist.negated", "herbalism.luck.lucky-drops")) {
            stats.addProperty(name, Adapt.instance.getAdaptServer().getPlayer(player).getData().getStat(name));
        }
        result.add("stats", stats);
        result.addProperty("floor", player.getLocation().subtract(0, 1, 0).getBlock().getType().getKey().toString());
        return result;
    }

    private static JsonArray items(List<ItemStack> items) {
        JsonArray result = new JsonArray();
        for (ItemStack stack : items) result.add(item(stack));
        return result;
    }

    private static JsonObject item(ItemStack stack) {
        JsonObject result = new JsonObject();
        result.addProperty("type", stack.getType().getKey().toString());
        result.addProperty("amount", stack.getAmount());
        return result;
    }
}
