package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.rift.RiftBlink;
import art.arcane.adapt.content.event.AdaptAdaptationTeleportEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

public final class BlinkPreferencesFixtures implements Listener {
    private static final Map<UUID, JsonObject> attacks = new HashMap<>();
    private static final Map<UUID, Integer> teleports = new HashMap<>();
    private static final Map<UUID, JsonObject> destinations = new HashMap<>();
    private static final Set<UUID> denied = new HashSet<>();

    public BlinkPreferencesFixtures() {
    }

    public static JsonObject execute(Player player, String[] args) {
        RiftBlink blink = blink();
        AdaptPlayer runtime = Objects.requireNonNull(Adapt.instance.getAdaptServer().getPlayer(player));
        if (!runtime.isRuntimeReady()) throw new IllegalStateException("Player runtime is not ready");
        boolean changed = false;
        switch (args[2]) {
            case "snapshot" -> { }
            case "open" -> blink.openGui(player);
            case "level" -> runtime.getData().getSkillLine("rift").setAdaptation(blink, Integer.parseInt(args[3]));
            case "enabled" -> changed = PlayerPreferences.set(blink, runtime, CommonPreferences.ENABLED, CommonPreferences.Toggle.valueOf(args[3]));
            case "phasing" -> changed = PlayerPreferences.set(blink, runtime, RiftBlink.PHASING, RiftBlink.Phasing.valueOf(args[3]));
            case "targeting" -> changed = PlayerPreferences.set(blink, runtime, RiftBlink.TARGETING, RiftBlink.Targeting.valueOf(args[3]));
            case "activation" -> changed = PlayerPreferences.set(blink, runtime, RiftBlink.ACTIVATION, RiftBlink.Activation.valueOf(args[3]));
            case "direction" -> changed = PlayerPreferences.set(blink, runtime, RiftBlink.DIRECTION, RiftBlink.Direction.valueOf(args[3]));
            case "direction-lock" -> PlayerPreferences.policy(blink, RiftBlink.DIRECTION).playerEditable = !Boolean.parseBoolean(args[3]);
            case "reactive-direction" -> changed = PlayerPreferences.set(blink, runtime, RiftBlink.REACTIVE_DIRECTION, RiftBlink.ReactiveDirection.valueOf(args[3]));
            case "lock" -> PlayerPreferences.policy(blink, RiftBlink.ACTIVATION).playerEditable = !Boolean.parseBoolean(args[3]);
            case "deny" -> {
                if (Boolean.parseBoolean(args[3])) denied.add(player.getUniqueId());
                else denied.remove(player.getUniqueId());
            }
            case "position" -> {
                player.closeInventory();
                player.setHealth(20);
                player.setNoDamageTicks(0);
                player.setFallDistance(0);
                player.setVelocity(new Vector());
                player.teleport(new Location(player.getWorld(), Double.parseDouble(args[3]), 100, 0.5, -90, 0));
            }
            case "weapons" -> {
                player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD), new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 16));
            }
            case "terrain" -> {
                for (int x = -30; x <= 40; x++) {
                    for (int z = -4; z <= 4; z++) player.getWorld().getBlockAt(x, 99, z).setType(Material.STONE, false);
                }
                for (int y = 100; y < 105; y++) {
                    for (int z = -1; z <= 1; z++) player.getWorld().getBlockAt(8, y, z).setType(Material.STONE, false);
                }
            }
            default -> throw new IllegalArgumentException("Unknown Blink fixture action " + args[2]);
        }
        JsonObject result = snapshot(blink, player, runtime);
        result.addProperty("changed", changed);
        return result;
    }

    private static JsonObject snapshot(RiftBlink blink, Player player, AdaptPlayer runtime) {
        int level = blink.getLevel(player);
        JsonObject result = new JsonObject();
        result.addProperty("player", player.getName());
        result.addProperty("level", level);
        result.addProperty("enabled", PlayerPreferences.resolve(blink, runtime.getData(), level, CommonPreferences.ENABLED).name());
        result.addProperty("phasing", PlayerPreferences.resolve(blink, runtime.getData(), level, RiftBlink.PHASING).name());
        result.addProperty("targeting", PlayerPreferences.resolve(blink, runtime.getData(), level, RiftBlink.TARGETING).name());
        result.addProperty("activation", PlayerPreferences.resolve(blink, runtime.getData(), level, RiftBlink.ACTIVATION).name());
        result.addProperty("direction", PlayerPreferences.resolve(blink, runtime.getData(), level, RiftBlink.DIRECTION).name());
        result.addProperty("savedDirection", runtime.getData().getPreferences().get("rift-blink", "direction"));
        result.addProperty("reactive-direction", PlayerPreferences.resolve(blink, runtime.getData(), level, RiftBlink.REACTIVE_DIRECTION).name());
        result.addProperty("health", player.getHealth());
        result.addProperty("x", player.getX());
        result.addProperty("y", player.getY());
        result.addProperty("z", player.getZ());
        result.addProperty("processId", ProcessHandle.current().pid());
        result.addProperty("teleports", teleports.getOrDefault(player.getUniqueId(), 0));
        result.addProperty("savedActivation", runtime.getData().getPreferences().get("rift-blink", "activation"));
        result.add("attack", attacks.get(player.getUniqueId()));
        result.add("destination", destinations.get(player.getUniqueId()));
        Inventory inventory = player.getOpenInventory().getTopInventory();
        JsonArray slots = new JsonArray();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || !item.hasItemMeta()) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("slot", slot);
            entry.addProperty("material", item.getType().name());
            entry.addProperty("name", item.getItemMeta().getDisplayName());
            slots.add(entry);
        }
        result.add("slots", slots);
        return result;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void denyTeleport(AdaptAdaptationTeleportEvent event) {
        if (event.getAdaptation() instanceof RiftBlink && denied.contains(event.getPlayer().getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void observeTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN && event.getPlayer().getName().startsWith("AQA")) {
            teleports.merge(event.getPlayer().getUniqueId(), 1, Integer::sum);
            JsonObject destination = new JsonObject();
            destination.addProperty("x", event.getTo().getX());
            destination.addProperty("y", event.getTo().getY());
            destination.addProperty("z", event.getTo().getZ());
            destination.addProperty("dx", event.getTo().getX() - event.getFrom().getX());
            destination.addProperty("dy", event.getTo().getY() - event.getFrom().getY());
            destination.addProperty("dz", event.getTo().getZ() - event.getFrom().getZ());
            destinations.put(event.getPlayer().getUniqueId(), destination);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observeAttack(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.getName().startsWith("AQA")) return;
        JsonObject hit = new JsonObject();
        hit.addProperty("cancelled", event.isCancelled());
        hit.addProperty("cause", event.getCause().name());
        hit.addProperty("damage", event.getFinalDamage());
        hit.addProperty("tick", player.getTicksLived());
        attacks.put(player.getUniqueId(), hit);
    }

    private static RiftBlink blink() {
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (adaptation instanceof RiftBlink blink) return blink;
            }
        }
        throw new IllegalStateException("Blink is not registered");
    }
}
