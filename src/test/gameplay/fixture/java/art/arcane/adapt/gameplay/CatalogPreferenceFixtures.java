package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.XPMultiplier;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryPolymath;
import art.arcane.adapt.content.adaptation.axe.AxeIrisFeller;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingBookshelfAttunement;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismDropToInventory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CatalogPreferenceFixtures {
    private static String previousPowerDefault;
    private static boolean previousPowerEditable;
    private static Integer previousFellerHunger;
    private static int previousFellerCooldown;

    private CatalogPreferenceFixtures() {
    }

    public static JsonObject execute(Player player, String[] args) {
        AdaptPlayer runtime = Objects.requireNonNull(Adapt.instance.getAdaptServer().getPlayer(player));
        if (!runtime.isRuntimeReady()) throw new IllegalStateException("Player runtime is not ready");
        return switch (args[2]) {
            case "offers" -> offers(player);
            case "pickup" -> pickup(player, runtime, Boolean.parseBoolean(args[3]));
            case "polymath" -> polymath(player, runtime);
            case "power-lock" -> lockPower(Boolean.parseBoolean(args[3]));
            case "iris-policy" -> irisPolicy(Boolean.parseBoolean(args[3]));
            default -> throw new IllegalArgumentException("Unknown catalog behavior " + args[2]);
        };
    }

    private static JsonObject offers(Player player) {
        EnchantmentOffer[] offers = {new EnchantmentOffer(Enchantment.SHARPNESS, 1, 5), null, null};
        Block table = player.getWorld().getBlockAt(3, 100, 3);
        table.setType(Material.ENCHANTING_TABLE, false);
        PrepareItemEnchantEvent event = new PrepareItemEnchantEvent(player, MenuType.ENCHANTMENT.create(player), table,
            new ItemStack(Material.DIAMOND_SWORD), offers, 15);
        adaptation(EnchantingBookshelfAttunement.class).on(event);
        JsonObject result = new JsonObject();
        result.addProperty("cost", offers[0].getCost());
        result.addProperty("level", offers[0].getEnchantmentLevel());
        return result;
    }

    private static JsonObject pickup(Player player, AdaptPlayer runtime, boolean seedsOnly) {
        HerbalismDropToInventory pickup = adaptation(HerbalismDropToInventory.class);
        runtime.getData().getSkillLine("herbalism").setAdaptation(pickup, pickup.getMaxLevel());
        if (!PlayerPreferences.set(pickup, runtime, HerbalismDropToInventory.SEEDS_ONLY,
            seedsOnly ? CommonPreferences.Toggle.ON : CommonPreferences.Toggle.OFF)) {
            throw new IllegalStateException("Seed preference rejected");
        }
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_HOE));
        Block crop = player.getWorld().getBlockAt(2, 100, 2);
        Item seeds = player.getWorld().dropItem(crop.getLocation(), new ItemStack(Material.WHEAT_SEEDS, 2));
        Item wheat = player.getWorld().dropItem(crop.getLocation(), new ItemStack(Material.WHEAT, 3));
        seeds.setPickupDelay(Integer.MAX_VALUE);
        wheat.setPickupDelay(Integer.MAX_VALUE);
        List<Item> drops = new ArrayList<>(List.of(seeds, wheat));
        try {
            BlockDropItemEvent event = new BlockDropItemEvent(crop, crop.getState(), player, drops);
            pickup.on(event);
            JsonObject result = new JsonObject();
            result.addProperty("seeds", count(player, Material.WHEAT_SEEDS));
            result.addProperty("wheat", count(player, Material.WHEAT));
            JsonArray ordinary = new JsonArray();
            for (Item item : drops) ordinary.add(item.getItemStack().getType().name());
            result.add("ordinaryDrops", ordinary);
            return result;
        } finally {
            seeds.remove();
            wheat.remove();
        }
    }

    private static JsonObject polymath(Player player, AdaptPlayer runtime) {
        DiscoveryPolymath polymath = adaptation(DiscoveryPolymath.class);
        runtime.getData().getSkillLine("discovery").setAdaptation(polymath, polymath.getMaxLevel());
        runtime.getData().globalXPMultiplier(XPMultiplier.owned(polymath.getName(), 0.4D, 60000L));
        runtime.getData().globalXPMultiplier(XPMultiplier.owned("fixture-other", 0.7D, 60000L));
        runtime.getData().globalXPMultiplier(0.2D, 60000L);
        if (!PlayerPreferences.set(polymath, runtime, CommonPreferences.ENABLED, CommonPreferences.Toggle.OFF)) {
            throw new IllegalStateException("Polymath disable rejected");
        }
        JsonObject result = new JsonObject();
        result.addProperty("active", polymath.getActiveLevel(player));
        result.addProperty("learned", polymath.getLevel(runtime));
        JsonArray sources = new JsonArray();
        for (XPMultiplier multiplier : runtime.getData().getMultipliers()) {
            sources.add(multiplier.getSource() == null ? "unowned" : multiplier.getSource());
        }
        result.add("sources", sources);
        return result;
    }

    private static JsonObject lockPower(boolean locked) {
        PreferencePolicy policy = PlayerPreferences.policy(adaptation(EnchantingBookshelfAttunement.class),
            EnchantingBookshelfAttunement.POWER);
        if (locked && previousPowerDefault == null) {
            previousPowerDefault = policy.defaultValue;
            previousPowerEditable = policy.playerEditable;
            policy.defaultValue = CommonPreferences.Scale.QUARTER.name();
            policy.playerEditable = false;
        } else if (!locked && previousPowerDefault != null) {
            policy.defaultValue = previousPowerDefault;
            policy.playerEditable = previousPowerEditable;
            previousPowerDefault = null;
        }
        JsonObject result = new JsonObject();
        result.addProperty("locked", !policy.playerEditable);
        return result;
    }

    private static JsonObject irisPolicy(boolean controlled) {
        Object config = adaptation(AxeIrisFeller.class).getConfig();
        try {
            Field hunger = config.getClass().getDeclaredField("hungerCost");
            Field cooldown = config.getClass().getDeclaredField("cooldownSeconds");
            hunger.setAccessible(true);
            cooldown.setAccessible(true);
            if (controlled && previousFellerHunger == null) {
                previousFellerHunger = hunger.getInt(config);
                previousFellerCooldown = cooldown.getInt(config);
                hunger.setInt(config, 0);
                cooldown.setInt(config, 0);
            } else if (!controlled && previousFellerHunger != null) {
                hunger.setInt(config, previousFellerHunger);
                cooldown.setInt(config, previousFellerCooldown);
                previousFellerHunger = null;
            }
            JsonObject result = new JsonObject();
            result.addProperty("hunger", hunger.getInt(config));
            result.addProperty("cooldown", cooldown.getInt(config));
            return result;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not configure controlled Iris felling trial", failure);
        }
    }

    private static int count(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) count += item.getAmount();
        }
        return count;
    }

    private static <T extends Adaptation<?>> T adaptation(Class<T> type) {
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (type.isInstance(adaptation)) return type.cast(adaptation);
            }
        }
        throw new IllegalArgumentException(type.getSimpleName());
    }
}
