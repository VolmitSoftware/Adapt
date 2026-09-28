package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.content.item.ChronoTimeBombItem;
import com.google.gson.JsonArray;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import art.arcane.adapt.content.item.ChronoTimeBottle;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ChronosFixtures implements Listener {
    private static final JsonArray interactions = new JsonArray();
    private static UUID actorId;
    private static UUID targetId;
    private static final List<Location> FURNACES = new ArrayList<>();

    public ChronosFixtures() {
    }

    public static boolean stage(String name, World arena, Player actor, Player opponent) {
        if (name.equals("chronos-recall")) {
            actor.getInventory().addItem(new ItemStack(Material.CLOCK, 2));
            arena.setGameRule(GameRule.NATURAL_REGENERATION, false);
            return true;
        }
        if (name.equals("chronos-stasis") || name.equals("chronos-bomb")) {
            actorId = actor.getUniqueId();
            while (interactions.size() > 0) interactions.remove(0);
            actor.getInventory().addItem(name.equals("chronos-stasis")
                    ? new ItemStack(Material.AMETHYST_SHARD, 2) : ChronoTimeBombItem.withData());
            Cow target = arena.spawn(new Location(arena, 2.5, 100, 2.5), Cow.class);
            targetId = target.getUniqueId();
            return true;
        }
        if (name.equals("chronos-bottle")) {
            actor.getInventory().addItem(ChronoTimeBottle.withStoredSeconds(120));
            arena.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
            arena.getBlockAt(2, 99, 0).setBlockData(Bukkit.createBlockData("minecraft:farmland[moisture=7]"), false);
            arena.getBlockAt(2, 100, 0).setType(Material.WHEAT, false);
            return true;
        }
        if (name.equals("chronos-accelerate")) {
            FURNACES.clear();
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    if (Math.abs(x) <= 1 && Math.abs(z) <= 1) continue;
                    arena.getBlockAt(x, 100, z).setType(Material.FURNACE, false);
                    Furnace furnace = (Furnace) arena.getBlockAt(x, 100, z).getState();
                    furnace.getInventory().setSmelting(new ItemStack(Material.RAW_IRON, 64));
                    furnace.getInventory().setFuel(new ItemStack(Material.COAL, 64));
                    FURNACES.add(furnace.getLocation());
                }
            }
            return true;
        }
        if (name.equals("chronos-fall")) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -10; z <= 3; z++) {
                    arena.getBlockAt(x, 99, z).setType(Material.STONE, false);
                    arena.getBlockAt(x, 100, z).setType(Material.WATER, false);
                }
            }
            arena.getBlockAt(0, 129, 0).setType(Material.STONE, false);
            actor.teleport(new Location(arena, 0.5, 130, 0.5));
            actor.getInventory().addItem(new ItemStack(Material.CLOCK));
            return true;
        }
        if (!name.equals("chronos-combat") && !name.equals("chronos-lethal")) {
            return false;
        }
        actor.setFoodLevel(10);
        actor.setSaturation(0);
        opponent.setFoodLevel(10);
        opponent.setSaturation(0);
        if (name.equals("chronos-lethal")) actor.setHealth(1);
        actor.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
        opponent.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
        return true;
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void beforeInteraction(PlayerInteractEvent event) {
        recordInteraction(event, "before");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterInteraction(PlayerInteractEvent event) {
        recordInteraction(event, "after");
    }

    private void recordInteraction(PlayerInteractEvent event, String phase) {
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(actorId) || event.getMaterial() != Material.AMETHYST_SHARD) return;
        JsonObject entry = new JsonObject();
        entry.addProperty("phase", phase);
        entry.addProperty("action", event.getAction().name());
        entry.addProperty("hand", String.valueOf(event.getHand()));
        entry.addProperty("blockUse", event.useInteractedBlock().name());
        entry.addProperty("itemUse", event.useItemInHand().name());
        entry.addProperty("sneaking", player.isSneaking());
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (!adaptation.getName().equals("chronos-stasis-field")) continue;
                entry.addProperty("activeLevel", adaptation.getActiveLevel(player));
                entry.addProperty("canInteract", adaptation.canInteract(player, player.getLocation()));
            }
        }
        if (interactions.size() >= 32) interactions.remove(0);
        interactions.add(entry);
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        result.add("interactions", interactions);
        double stored = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (ChronoTimeBottle.isBindableItem(item)) stored += ChronoTimeBottle.getStoredSeconds(item);
        }
        result.addProperty("storedSeconds", stored);
        if (player.getWorld().getBlockAt(2, 100, 0).getBlockData() instanceof Ageable crop) {
            result.addProperty("cropAge", crop.getAge());
        }
        int cooked = 0;
        int progress = 0;
        for (Location location : FURNACES) {
            if (location.getWorld() != player.getWorld() || !(location.getBlock().getState() instanceof Furnace furnace)) continue;
            ItemStack output = furnace.getInventory().getResult();
            cooked += output == null ? 0 : output.getAmount();
            progress += furnace.getCookTime();
        }
        result.addProperty("cooked", cooked);
        result.addProperty("cookProgress", progress);
        result.addProperty("serverTick", Bukkit.getCurrentTick());
        Entity target = targetId == null ? null : Bukkit.getEntity(targetId);
        if (target instanceof Cow cow) {
            result.addProperty("ai", cow.hasAI());
            result.addProperty("movementSpeed", cow.getAttribute(Attribute.MOVEMENT_SPEED).getValue());
            result.addProperty("jumpStrength", cow.getAttribute(Attribute.JUMP_STRENGTH).getValue());
            result.addProperty("targetId", cow.getEntityId());
        }
        return result;
    }
}
