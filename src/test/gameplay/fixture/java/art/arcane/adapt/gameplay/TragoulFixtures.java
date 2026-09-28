package art.arcane.adapt.gameplay;

import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class TragoulFixtures {
    private static final Map<String, LivingEntity> targets = new LinkedHashMap<>();

    private TragoulFixtures() {
    }

    public static boolean stage(String name, World arena, Player actor, Player opponent) {
        if (!name.startsWith("tragoul-qa-")) return false;
        targets.clear();
        actor.setFoodLevel(17);
        actor.setSaturation(0);
        opponent.setFoodLevel(17);
        opponent.setSaturation(0);
        if (name.startsWith("tragoul-qa-targeting-")) {
            stageTargeting(name.substring("tragoul-qa-targeting-".length()), arena, actor, opponent);
            return true;
        }
        switch (name) {
            case "tragoul-qa-defend", "tragoul-qa-marrow", "tragoul-qa-rites" -> {
                opponent.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                if (name.equals("tragoul-qa-marrow")) actor.getInventory().addItem(new ItemStack(Material.BONE, 4));
                if (name.equals("tragoul-qa-rites")) actor.setHealth(2);
            }
            case "tragoul-qa-siphon" -> {
                actor.setHealth(10);
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
            }
            case "tragoul-qa-globe", "tragoul-qa-nova", "tragoul-qa-lance" -> {
                opponent.teleport(new Location(arena, 18.5, 100, 18.5));
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                Cow primary = arena.spawn(new Location(arena, 2.5, 100, 0.5), Cow.class);
                primary.setAI(false);
                primary.setHealth(name.equals("tragoul-qa-globe") ? 10 : 1);
                targets.put("primary", primary);
                Husk secondary = arena.spawn(new Location(arena, 3.5, 100, 2.5), Husk.class);
                secondary.setAI(false);
                targets.put("secondary", secondary);
            }
            case "tragoul-qa-plague" -> {
                opponent.teleport(new Location(arena, 18.5, 100, 18.5));
                actor.getInventory().addItem(new ItemStack(Material.BOW), new ItemStack(Material.NETHERITE_SWORD));
                ItemStack arrows = new ItemStack(Material.TIPPED_ARROW, 4);
                PotionMeta meta = (PotionMeta) arrows.getItemMeta();
                meta.setBasePotionType(PotionType.LONG_POISON);
                arrows.setItemMeta(meta);
                actor.getInventory().addItem(arrows);
                Cow primary = arena.spawn(new Location(arena, 2.5, 100, 0.5), Cow.class);
                primary.setAI(false);
                targets.put("primary", primary);
                Cow secondary = arena.spawn(new Location(arena, 4.5, 100, 2.5), Cow.class);
                secondary.setAI(false);
                targets.put("secondary", secondary);
            }
            case "tragoul-qa-servant" -> {
                opponent.teleport(new Location(arena, 18.5, 100, 18.5));
                actor.getInventory().addItem(new ItemStack(Material.BONE, 16));
                Cow primary = arena.spawn(new Location(arena, 2.5, 100, 0.5), Cow.class);
                primary.setAI(false);
                targets.put("primary", primary);
            }
            case "tragoul-qa-sense" -> {
                opponent.teleport(new Location(arena, 18.5, 100, 18.5));
                Cow primary = arena.spawn(new Location(arena, 2.5, 100, 0.5), Cow.class);
                primary.setAI(false);
                primary.setHealth(3);
                targets.put("primary", primary);
            }
            default -> throw new IllegalArgumentException("Unknown Tragoul fixture: " + name);
        }
        return true;
    }

    private static void stageTargeting(String trial, World arena, Player actor, Player opponent) {
        opponent.teleport(new Location(arena, 18.5, 100, 18.5));
        if (trial.startsWith("thorns-")) {
            if (trial.equals("thorns-wolf")) {
                Wolf attacker = arena.spawn(new Location(arena, 12.5, 100, 0.5), Wolf.class);
                attacker.setAngry(true);
                attacker.setTarget(actor);
                targets.put("attacker", attacker);
            } else if (trial.equals("thorns-husk")) {
                Husk attacker = arena.spawn(new Location(arena, 12.5, 100, 0.5), Husk.class);
                attacker.setTarget(actor);
                targets.put("attacker", attacker);
            } else {
                throw new IllegalArgumentException("Unknown thorns targeting trial: " + trial);
            }
            return;
        }
        actor.getInventory().addItem(new ItemStack(Material.WOODEN_AXE));
        Cow primary = spawnTarget(arena, "primary", Cow.class, 2.5, 0.5);
        primary.setHealth(trial.equals("globe") ? 10 : 1);
        Wolf pet = spawnTarget(arena, "pet", Wolf.class, 2.5, -0.9);
        pet.setOwner(actor);
        pet.setSitting(true);
        Skeleton servant = spawnTarget(arena, "servant", Skeleton.class, 1.5, -0.9);
        servant.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
        servant.getPersistentDataContainer().set(Objects.requireNonNull(NamespacedKey.fromString("adapt:tragoul_servant_owner")),
                PersistentDataType.STRING, actor.getUniqueId().toString());
        ArmorStand decoy = spawnTarget(arena, "decoy", ArmorStand.class, 3.5, -0.9);
        decoy.getPersistentDataContainer().set(Objects.requireNonNull(NamespacedKey.fromString("adapt:shadow_decoy_owner")),
                PersistentDataType.STRING, actor.getUniqueId().toString());
        if (trial.equals("globe")) {
            spawnTarget(arena, "cow", Cow.class, 3.5, 2.5);
            spawnTarget(arena, "wolf", Wolf.class, 0.5, 3.5);
            spawnTarget(arena, "enderman", Enderman.class, -2.5, 0.5);
            spawnTarget(arena, "husk", Husk.class, 0.5, -2.5);
            return;
        }
        switch (trial) {
            case "lance-cow" -> spawnTarget(arena, "protected", Cow.class, 3.5, 2.5);
            case "lance-wolf" -> spawnTarget(arena, "protected", Wolf.class, 3.5, 2.5);
            case "lance-enderman" -> spawnTarget(arena, "protected", Enderman.class, 3.5, 2.5);
            default -> throw new IllegalArgumentException("Unknown targeting trial: " + trial);
        }
        spawnTarget(arena, "husk", Husk.class, 6.5, 2.5);
    }

    private static <T extends LivingEntity> T spawnTarget(World arena, String name, Class<T> type, double x, double z) {
        T target = arena.spawn(new Location(arena, x, 100, z), type);
        target.setAI(false);
        targets.put(name, target);
        return target;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        JsonObject targetStates = new JsonObject();
        for (Map.Entry<String, LivingEntity> entry : targets.entrySet()) {
            LivingEntity target = entry.getValue();
            if (target.getWorld() != player.getWorld()) continue;
            JsonObject state = new JsonObject();
            state.addProperty("entityId", target.getEntityId());
            state.addProperty("health", target.getHealth());
            state.addProperty("dead", target.isDead());
            state.addProperty("valid", target.isValid());
            JsonArray effects = new JsonArray();
            for (PotionEffect effect : target.getActivePotionEffects()) {
                JsonObject detail = new JsonObject();
                detail.addProperty("type", effect.getType().getKey().toString());
                detail.addProperty("amplifier", effect.getAmplifier());
                detail.addProperty("duration", effect.getDuration());
                effects.add(detail);
            }
            state.add("effects", effects);
            if (target.getLastDamageCause() instanceof EntityDamageByEntityEvent damage) {
                Entity source = damage.getDamager();
                if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) source = shooter;
                state.addProperty("lastDamagerId", source.getEntityId());
                state.addProperty("lastDamagerServant", source instanceof LivingEntity living && TragoulSkeletalServant.isServant(living));
            }
            targetStates.add(entry.getKey(), state);
        }
        result.add("targets", targetStates);
        JsonArray servants = new JsonArray();
        for (Skeleton skeleton : player.getWorld().getEntitiesByClass(Skeleton.class)) {
            if (!TragoulSkeletalServant.isServant(skeleton)) continue;
            JsonObject state = new JsonObject();
            state.addProperty("entityId", skeleton.getEntityId());
            state.addProperty("owner", String.valueOf(TragoulSkeletalServant.getServantOwnerId(skeleton)));
            state.addProperty("health", skeleton.getHealth());
            servants.add(state);
        }
        result.add("servants", servants);
        return result;
    }
}
