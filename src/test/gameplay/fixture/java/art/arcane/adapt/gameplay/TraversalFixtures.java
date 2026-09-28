package art.arcane.adapt.gameplay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExhaustionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Cod;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public final class TraversalFixtures implements Listener {
    private static LivingEntity target;
    private static final List<Block> elevatedBlocks = new ArrayList<>();
    private static final Map<String, double[]> exhaustionEvents = new HashMap<>();
    private static UUID actorId;

    public TraversalFixtures() {
    }

    public static boolean stage(String name, World arena, Player actor, Player opponent) {
        if (!name.startsWith("traverse-qa-")) return false;
        target = null;
        actorId = actor.getUniqueId();
        exhaustionEvents.clear();
        actor.setExhaustion(0);
        actor.setFoodLevel(20);
        actor.setSaturation(5);
        opponent.setFoodLevel(17);
        opponent.setSaturation(0);
        switch (name) {
            case "traverse-qa-runway" -> MovementFixtures.stage("move-runway", arena, actor);
            case "traverse-qa-plate" -> arena.getBlockAt(0, 100, -3).setType(Material.STONE_PRESSURE_PLATE, false);
            case "traverse-qa-vault" -> {
                for (int x = -2; x <= 2; x++) arena.getBlockAt(x, 100, -2).setType(Material.OAK_FENCE, true);
            }
            case "traverse-qa-ladder" -> {
                for (int y = 100; y <= 120; y++) {
                    arena.getBlockAt(0, y, -1).setType(Material.STONE, false);
                    Directional ladder = (Directional) Material.LADDER.createBlockData();
                    ladder.setFacing(BlockFace.SOUTH);
                    arena.getBlockAt(0, y, 0).setBlockData(ladder, false);
                    if (y > 108) {
                        elevatedBlocks.add(arena.getBlockAt(0, y, -1));
                        elevatedBlocks.add(arena.getBlockAt(0, y, 0));
                    }
                }
                actor.teleport(new Location(arena, 0.5, 104, 0.5));
            }
            case "traverse-qa-roll", "traverse-qa-soft" -> {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -8; z <= -1; z++) {
                        arena.getBlockAt(x, 99, z).setType(name.equals("traverse-qa-soft") ? Material.SPONGE : Material.STONE, false);
                    }
                    for (int z = 0; z <= 2; z++) arena.getBlockAt(x, 107, z).setType(Material.STONE, false);
                }
                actor.teleport(new Location(arena, 0.5, 108, 0.5));
            }
            case "traverse-qa-rubber" -> {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) arena.getBlockAt(x, 99, z).setType(Material.HONEY_BLOCK, false);
                }
            }
            case "traverse-qa-coral" -> actor.getInventory().addItem(new ItemStack(Material.TUBE_CORAL_BLOCK, 2));
            case "traverse-qa-salvage", "traverse-qa-fish", "traverse-qa-ink", "traverse-qa-tide" -> {
                MovementFixtures.stage("move-hydro", arena, actor);
                if (name.equals("traverse-qa-salvage")) arena.getBlockAt(2, 100, 2).setType(Material.CHEST, false);
                if (name.equals("traverse-qa-fish")) {
                    Cod fish = arena.spawn(new Location(arena, 2.5, 101, -3.5), Cod.class);
                    fish.setAware(false);
                    fish.setGravity(false);
                    target = fish;
                }
                if (name.equals("traverse-qa-ink")) {
                    Drowned drowned = arena.spawn(new Location(arena, 2.5, 100, -1.5), Drowned.class);
                    drowned.setAI(false);
                    target = drowned;
                }
            }
            case "traverse-qa-trident" -> {
                actor.setFoodLevel(17);
                actor.getInventory().addItem(new ItemStack(Material.TRIDENT));
            }
            case "traverse-qa-assassinate", "traverse-qa-shadowmeld" -> {
                opponent.teleport(new Location(arena, 18.5, 100, 18.5));
                if (name.equals("traverse-qa-assassinate")) {
                    Cow cow = arena.spawn(new Location(arena, 2.5, 100, 0.5), Cow.class);
                    cow.setAI(false);
                    target = cow;
                }
            }
            default -> throw new IllegalArgumentException("Unknown traversal fixture: " + name);
        }
        return true;
    }

    public static void reset() {
        for (Block block : elevatedBlocks) block.setType(Material.AIR, false);
        elevatedBlocks.clear();
        exhaustionEvents.clear();
        actorId = null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void beforeExhaustion(EntityExhaustionEvent event) {
        recordExhaustion(event, 0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterExhaustion(EntityExhaustionEvent event) {
        recordExhaustion(event, 1);
    }

    private void recordExhaustion(EntityExhaustionEvent event, int phase) {
        if (!event.getEntity().getUniqueId().equals(actorId)) return;
        double[] totals = exhaustionEvents.computeIfAbsent(event.getExhaustionReason().name(), key -> new double[2]);
        totals[phase] += event.getExhaustion();
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        result.addProperty("exhaustion", player.getExhaustion());
        JsonObject reasons = new JsonObject();
        for (Map.Entry<String, double[]> entry : exhaustionEvents.entrySet()) {
            JsonObject totals = new JsonObject();
            totals.addProperty("before", entry.getValue()[0]);
            totals.addProperty("after", entry.getValue()[1]);
            reasons.add(entry.getKey(), totals);
        }
        result.add("exhaustionEvents", reasons);
        result.addProperty("pose", player.getPose().name());
        result.addProperty("climbing", player.isClimbing());
        if (target != null && target.getWorld() == player.getWorld()) {
            JsonObject state = new JsonObject();
            state.addProperty("entityId", target.getEntityId());
            state.addProperty("health", target.getHealth());
            state.addProperty("dead", target.isDead());
            state.addProperty("distance", target.getLocation().distance(player.getLocation()));
            JsonArray effects = new JsonArray();
            for (PotionEffect effect : target.getActivePotionEffects()) {
                JsonObject detail = new JsonObject();
                detail.addProperty("type", effect.getType().getKey().toString());
                detail.addProperty("amplifier", effect.getAmplifier());
                effects.add(detail);
            }
            state.add("effects", effects);
            result.add("target", state);
        }
        return result;
    }
}
