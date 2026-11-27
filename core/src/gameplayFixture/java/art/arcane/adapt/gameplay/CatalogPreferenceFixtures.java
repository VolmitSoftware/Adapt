package art.arcane.adapt.gameplay;

import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.XPMultiplier;
import art.arcane.adapt.content.adaptation.axe.AxeIrisFeller;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryPolymath;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingBookshelfAttunement;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismDropToInventory;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.inventory.EnchantOffer;
import art.arcane.adapt.platform.event.inventory.PrepareItemEnchantEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class CatalogPreferenceFixtures {
    private static String previousPowerDefault;
    private static boolean previousPowerEditable;
    private static Integer previousFellerHunger;
    private static int previousFellerCooldown;

    private CatalogPreferenceFixtures() {
    }

    public static JsonObject execute(PlayerRef player, String[] args) {
        AdaptPlayer runtime = Objects.requireNonNull(Fixtures.adaptPlayer(player));
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

    private static JsonObject offers(PlayerRef player) {
        EnchantOffer offer = new EnchantOffer(Id.minecraft("sharpness"), 1, 5);
        BlockRef table = player.world().blockAt(3, 100, 3);
        table.setType(Fixtures.id("enchanting_table"), false);
        PrepareItemEnchantEvent event = new PrepareItemEnchantEvent(player, table,
            Fixtures.item("diamond_sword"), 15, Arrays.asList(offer, null, null));
        Fixtures.adaptation(EnchantingBookshelfAttunement.class).on(event);
        JsonObject result = new JsonObject();
        result.addProperty("cost", offer.cost());
        result.addProperty("level", offer.level());
        return result;
    }

    private static JsonObject pickup(PlayerRef player, AdaptPlayer runtime, boolean seedsOnly) {
        HerbalismDropToInventory pickup = Fixtures.adaptation(HerbalismDropToInventory.class);
        runtime.getData().getSkillLine("herbalism").setAdaptation(pickup, pickup.getMaxLevel());
        if (!PlayerPreferences.set(pickup, runtime, HerbalismDropToInventory.SEEDS_ONLY,
            seedsOnly ? CommonPreferences.Toggle.ON : CommonPreferences.Toggle.OFF)) {
            throw new IllegalStateException("Seed preference rejected");
        }
        player.closeInventory();
        Fixtures.clearInventory(player);
        player.inventory().setMainHand(Fixtures.item("diamond_hoe"));
        BlockRef crop = player.world().blockAt(2, 100, 2);
        Vec3 at = new Vec3(crop.x(), crop.y(), crop.z());
        ItemEntityRef seeds = player.world().dropItem(at, Fixtures.item("wheat_seeds", 2));
        ItemEntityRef wheat = player.world().dropItem(at, Fixtures.item("wheat", 3));
        seeds.setPickupDelay(Integer.MAX_VALUE);
        wheat.setPickupDelay(Integer.MAX_VALUE);
        List<ItemEntityRef> drops = new ArrayList<>(List.of(seeds, wheat));
        try {
            BlockDropItemEvent event = new BlockDropItemEvent(player, crop, crop.state(), new ArrayList<>(drops));
            pickup.on(event);
            JsonObject result = new JsonObject();
            result.addProperty("seeds", FixtureItems.count(player, "wheat_seeds"));
            result.addProperty("wheat", FixtureItems.count(player, "wheat"));
            JsonArray ordinary = new JsonArray();
            for (ItemEntityRef item : event.items()) ordinary.add(item.item().type().legacyName());
            result.add("ordinaryDrops", ordinary);
            return result;
        } finally {
            seeds.remove();
            wheat.remove();
        }
    }

    private static JsonObject polymath(PlayerRef player, AdaptPlayer runtime) {
        DiscoveryPolymath polymath = Fixtures.adaptation(DiscoveryPolymath.class);
        runtime.getData().getSkillLine("discovery").setAdaptation(polymath, polymath.getMaxLevel());
        runtime.getData().globalXPMultiplier(XPMultiplier.owned(polymath.getId().toString(), 0.4D, 60000L));
        runtime.getData().globalXPMultiplier(XPMultiplier.owned("fixture-other", 0.7D, 60000L));
        runtime.getData().globalXPMultiplier(0.2D, 60000L);
        if (!PlayerPreferences.set(polymath, runtime, CommonPreferences.ENABLED, CommonPreferences.Toggle.OFF)) {
            throw new IllegalStateException("Polymath disable rejected");
        }
        JsonObject result = new JsonObject();
        result.addProperty("active", polymath.getActiveLevel(player));
        result.addProperty("learned", polymath.getLevel(PlayerApi.profile(runtime)));
        JsonArray sources = new JsonArray();
        for (XPMultiplier multiplier : runtime.getData().getMultipliers()) {
            sources.add(multiplier.getSource() == null ? "unowned" : multiplier.getSource());
        }
        result.add("sources", sources);
        return result;
    }

    private static JsonObject lockPower(boolean locked) {
        PreferencePolicy policy = PlayerPreferences.policy(Fixtures.adaptation(EnchantingBookshelfAttunement.class),
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
        Object config = Fixtures.adaptation(AxeIrisFeller.class).getConfig();
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
}
