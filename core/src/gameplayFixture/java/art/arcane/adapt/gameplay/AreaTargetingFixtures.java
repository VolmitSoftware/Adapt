package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AreaTargetingFixtures {
    private static final DataKey<String> SERVANT_OWNER = DataKey.adapt("tragoul_servant_owner", DataCodecs.STRING);
    private static final DataKey<String> DECOY_OWNER = DataKey.adapt("shadow_decoy_owner", DataCodecs.STRING);
    private static final Map<String, LivingRef> targets = new LinkedHashMap<>();

    private AreaTargetingFixtures() {
    }

    public static boolean stage(String name, WorldRef arena, PlayerRef actor, PlayerRef opponent) {
        if (!name.startsWith("area-targeting-")) return false;
        targets.clear();
        String trial = name.substring("area-targeting-".length());
        actor.setFoodLevel(20);
        actor.setSaturation(0);
        Fixtures.teleport(opponent, arena, 18.5, 100, 18.5);
        LivingRef primary = spawn(arena, "primary", "cow", 2.5, 0.5);
        primary.setHealth(trial.equals("corpse") ? 1 : 20);
        spawn(arena, "cow", "cow", 3.5, 1.7);
        spawn(arena, "wolf", "wolf", 3.5, -0.7);
        spawn(arena, "enderman", "enderman", 4.5, 0.5);
        if (trial.equals("plague")) spawn(arena, "hostile", "creeper", 4.5, 2.0);
        else spawn(arena, "hostile", "husk", 4.5, 2.0);
        LivingRef pet = spawn(arena, "pet", "wolf", 2.5, -1.8);
        Fixtures.platform().setOwner(pet, actor);
        Fixtures.host().animals().setSitting(pet, true);
        LivingRef servant = spawn(arena, "servant", "skeleton", 2.5, 2.8);
        servant.setEquipment(EquipmentSlot.HEAD, Fixtures.item("iron_helmet"));
        servant.data().set(SERVANT_OWNER, actor.uuid().toString());
        LivingRef decoy = spawn(arena, "decoy", "armor_stand", 1.5, 2.8);
        decoy.data().set(DECOY_OWNER, actor.uuid().toString());
        String weapon = switch (trial) {
            case "cleave", "smash" -> "iron_axe";
            case "earth" -> "diamond_shovel";
            case "cyclone" -> "wooden_sword";
            case "corpse" -> "wooden_axe";
            case "plague" -> "netherite_sword";
            default -> throw new IllegalArgumentException("Unknown area targeting trial: " + trial);
        };
        Fixtures.give(actor, weapon, 1);
        if (trial.equals("plague")) {
            Fixtures.give(actor, "bow", 1);
            ItemRef arrows = Fixtures.item("tipped_arrow", 4);
            arrows.setPotionContents(PotionContents.of(Id.minecraft("long_poison")));
            Fixtures.give(actor, arrows);
        }

        return true;
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, LivingRef> entry : targets.entrySet()) {
            LivingRef target = resolve(entry.getValue());
            if (!target.world().uid().equals(player.world().uid())) continue;
            JsonObject state = new JsonObject();
            state.addProperty("entityId", target.entityId());
            state.addProperty("health", target.health());
            state.addProperty("dead", target.isDead());
            JsonArray effects = new JsonArray();
            for (EffectInstance effect : target.effects()) effects.add(effect.type().toString());
            state.add("effects", effects);
            result.add(entry.getKey(), state);
        }
        return result;
    }

    private static LivingRef resolve(LivingRef held) {
        return Fixtures.entity(held.uuid()) instanceof LivingRef fresh ? fresh : held;
    }


    private static LivingRef spawn(WorldRef arena, String name, String type, double x, double z) {
        LivingRef target = Fixtures.spawnStill(arena, type, x, 100, z);
        target.setRemoveWhenFarAway(false);
        target.setAttributeBase(Id.minecraft("max_health"), 40);
        target.setHealth(40);
        targets.put(name, target);
        return target;
    }
}
