package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.volmlib.util.bukkit.BukkitExperience;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ExperienceFixtures implements Listener {
    private static final Map<UUID, Observation> observations = new HashMap<>();
    private static final NamespacedKey CHARGE = new NamespacedKey("adapt", "echo-charge");

    public static JsonObject execute(Player player, String[] args) {
        if (args[2].equals("prepare")) {
            prepare(player);
        }
        Observation observation = observations.get(player.getUniqueId());
        if (observation == null) {
            throw new IllegalStateException("Prepare the experience fixture first");
        }
        switch (args[2]) {
            case "prepare", "snapshot" -> {
            }
            case "award" -> BukkitExperience.award(player, Integer.parseInt(args[3]));
            case "override" -> observation.override = Integer.parseInt(args[3]);
            case "natural" -> observation.override = null;
            case "refill" -> observation.golem.setEssence(60);
            case "release" -> GolemancyRuntime.registry().orElseThrow().release(observation.golem);
            case "ore" -> player.getWorld().getBlockAt(3, 100, 2).setType(Material.DIAMOND_ORE, false);
            default -> throw new IllegalArgumentException("Unknown experience action: " + args[2]);
        }
        return snapshot(player, observation);
    }

    public static void cleanup() {
        observations.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void modify(PlayerExpChangeEvent event) {
        Observation observation = observations.get(event.getPlayer().getUniqueId());
        if (observation != null && observation.override != null) {
            event.setAmount(observation.override);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observe(PlayerExpChangeEvent event) {
        Observation observation = observations.get(event.getPlayer().getUniqueId());
        if (observation != null) {
            observation.events++;
            observation.lastAmount = event.getAmount();
        }
    }

    private static void prepare(Player player) {
        if (observations.containsKey(player.getUniqueId())) {
            throw new IllegalStateException("Experience fixture already prepared");
        }
        player.setLevel(0);
        player.setExp(0);
        player.setTotalExperience(0);
        player.getInventory().clear();
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(Enchantment.EFFICIENCY, 1, true);
        book.setItemMeta(meta);
        player.getInventory().setItem(0, book);
        player.getInventory().setItem(1, new ItemStack(Material.DIAMOND_PICKAXE));
        player.getInventory().setItem(2, GolemItems.crook());
        player.getInventory().setHeldItemSlot(0);
        IronGolem golem = player.getWorld().spawn(new Location(player.getWorld(), 2.5, 100, 0.5), IronGolem.class);
        GolemHandle handle = GolemancyRuntime.registry().orElseThrow().bind(player, golem, GolemBody.IRON, GolemCoreType.GATHERER);
        handle.setMode(GolemMode.STAY);
        handle.setEssence(60);
        Observation observation = new Observation();
        observation.golem = handle;
        observations.put(player.getUniqueId(), observation);
    }

    private static JsonObject snapshot(Player player, Observation observation) {
        PlayerData data = Adapt.instance.getAdaptServer().getPlayer(player).getData();
        JsonObject result = new JsonObject();
        result.addProperty("events", observation.events);
        result.addProperty("lastAmount", observation.lastAmount);
        result.addProperty("vanillaXp", player.calculateTotalExperiencePoints());
        result.addProperty("unity", data.getStat("discovery.unity.orbs-distributed"));
        result.addProperty("discoveryXp", data.getSkillLine("discovery").getXp());
        result.addProperty("golem", observation.golem.entity().getEntityId());
        result.addProperty("essence", observation.golem.state().essencePoints());
        result.addProperty("orbs", player.getWorld().getEntitiesByClass(ExperienceOrb.class).size());
        ItemStack book = player.getInventory().getItem(0);
        if (book != null && book.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            result.addProperty("bookCharge", meta.getPersistentDataContainer().getOrDefault(CHARGE, PersistentDataType.INTEGER, 0));
            result.addProperty("bookLevel", meta.getStoredEnchantLevel(Enchantment.EFFICIENCY));
        }
        return result;
    }

    private static final class Observation {
        private Integer override;
        private int events;
        private int lastAmount;
        private GolemHandle golem;
    }
}
