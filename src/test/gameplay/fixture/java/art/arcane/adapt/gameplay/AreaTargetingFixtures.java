package art.arcane.adapt.gameplay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AreaTargetingFixtures {
    private static final Map<String, LivingEntity> targets = new LinkedHashMap<>();

    private AreaTargetingFixtures() {
    }

    public static boolean stage(String name, World arena, Player actor, Player opponent) {
        if (!name.startsWith("area-targeting-")) return false;
        targets.clear();
        String trial = name.substring("area-targeting-".length());
        actor.setFoodLevel(20);
        actor.setSaturation(0);
        opponent.teleport(new Location(arena, 18.5, 100, 18.5));
        Cow primary = spawn(arena, "primary", Cow.class, 2.5, 0.5);
        primary.setHealth(trial.equals("corpse") ? 1 : 20);
        spawn(arena, "cow", Cow.class, 3.5, 1.7);
        spawn(arena, "wolf", Wolf.class, 3.5, -0.7);
        spawn(arena, "enderman", Enderman.class, 4.5, 0.5);
        if (trial.equals("plague")) spawn(arena, "hostile", Creeper.class, 4.5, 2.0);
        else spawn(arena, "hostile", Husk.class, 4.5, 2.0);
        Wolf pet = spawn(arena, "pet", Wolf.class, 2.5, -1.8);
        pet.setOwner(actor);
        pet.setSitting(true);
        Skeleton servant = spawn(arena, "servant", Skeleton.class, 2.5, 2.8);
        servant.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
        servant.getPersistentDataContainer().set(Objects.requireNonNull(NamespacedKey.fromString("adapt:tragoul_servant_owner")),
                PersistentDataType.STRING, actor.getUniqueId().toString());
        ArmorStand decoy = spawn(arena, "decoy", ArmorStand.class, 1.5, 2.8);
        decoy.getPersistentDataContainer().set(Objects.requireNonNull(NamespacedKey.fromString("adapt:shadow_decoy_owner")),
                PersistentDataType.STRING, actor.getUniqueId().toString());
        Material weapon = switch (trial) {
            case "cleave", "smash" -> Material.IRON_AXE;
            case "earth" -> Material.DIAMOND_SHOVEL;
            case "cyclone" -> Material.WOODEN_SWORD;
            case "corpse" -> Material.WOODEN_AXE;
            case "plague" -> Material.NETHERITE_SWORD;
            default -> throw new IllegalArgumentException("Unknown area targeting trial: " + trial);
        };
        actor.getInventory().addItem(new ItemStack(weapon));
        if (trial.equals("plague")) {
            actor.getInventory().addItem(new ItemStack(Material.BOW));
            ItemStack arrows = new ItemStack(Material.TIPPED_ARROW, 4);
            PotionMeta potion = (PotionMeta) arrows.getItemMeta();
            potion.setBasePotionType(PotionType.LONG_POISON);
            arrows.setItemMeta(potion);
            actor.getInventory().addItem(arrows);
        }

        return true;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, LivingEntity> entry : targets.entrySet()) {
            LivingEntity target = entry.getValue();
            if (target.getWorld() != player.getWorld()) continue;
            JsonObject state = new JsonObject();
            state.addProperty("entityId", target.getEntityId());
            state.addProperty("health", target.getHealth());
            state.addProperty("dead", target.isDead());
            JsonArray effects = new JsonArray();
            for (PotionEffect effect : target.getActivePotionEffects()) effects.add(effect.getType().getKey().toString());
            state.add("effects", effects);
            result.add(entry.getKey(), state);
        }
        return result;
    }

    private static <T extends LivingEntity> T spawn(World arena, String name, Class<T> type, double x, double z) {
        T target = arena.spawn(new Location(arena, x, 100, z), type);
        target.setAI(false);
        Objects.requireNonNull(target.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(40);
        target.setHealth(40);
        targets.put(name, target);
        return target;
    }
}
