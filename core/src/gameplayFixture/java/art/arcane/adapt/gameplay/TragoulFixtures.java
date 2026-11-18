package art.arcane.adapt.gameplay;

import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TragoulFixtures {
    private static final DataKey<String> SERVANT_OWNER = DataKey.adapt("tragoul_servant_owner", DataCodecs.STRING);
    private static final DataKey<String> DECOY_OWNER = DataKey.adapt("shadow_decoy_owner", DataCodecs.STRING);
    private static final Map<String, Target> targets = new LinkedHashMap<>();

    private TragoulFixtures() {
    }

    public static boolean stage(String name, WorldRef arena, PlayerRef actor, PlayerRef opponent) {
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
                Fixtures.give(opponent, "wooden_sword", 1);
                if (name.equals("tragoul-qa-marrow")) Fixtures.give(actor, "bone", 4);
                if (name.equals("tragoul-qa-rites")) actor.setHealth(2);
            }
            case "tragoul-qa-siphon" -> {
                actor.setHealth(10);
                Fixtures.give(actor, "wooden_sword", 1);
            }
            case "tragoul-qa-globe", "tragoul-qa-nova", "tragoul-qa-lance" -> {
                Fixtures.teleport(opponent, arena, 18.5, 100, 18.5);
                Fixtures.give(actor, "wooden_sword", 1);
                LivingRef primary = Fixtures.spawnStill(arena, "cow", 2.5, 100, 0.5);
                primary.setHealth(name.equals("tragoul-qa-globe") ? 10 : 1);
                put("primary", primary);
                put("secondary", Fixtures.spawnStill(arena, "husk", 3.5, 100, 2.5));
            }
            case "tragoul-qa-plague" -> {
                Fixtures.teleport(opponent, arena, 18.5, 100, 18.5);
                Fixtures.give(actor, "bow", 1);
                Fixtures.give(actor, "netherite_sword", 1);
                ItemRef arrows = Fixtures.item("tipped_arrow", 4);
                arrows.setPotionContents(new PotionContents(Id.minecraft("long_poison"), null, List.of(), null));
                Fixtures.give(actor, arrows);
                put("primary", Fixtures.spawnStill(arena, "cow", 2.5, 100, 0.5));
                put("secondary", Fixtures.spawnStill(arena, "cow", 4.5, 100, 2.5));
            }
            case "tragoul-qa-servant" -> {
                Fixtures.teleport(opponent, arena, 18.5, 100, 18.5);
                Fixtures.give(actor, "bone", 16);
                put("primary", Fixtures.spawnStill(arena, "cow", 2.5, 100, 0.5));
            }
            case "tragoul-qa-sense" -> {
                Fixtures.teleport(opponent, arena, 18.5, 100, 18.5);
                LivingRef primary = Fixtures.spawnStill(arena, "cow", 2.5, 100, 0.5);
                primary.setHealth(3);
                put("primary", primary);
            }
            default -> throw new IllegalArgumentException("Unknown Tragoul fixture: " + name);
        }
        return true;
    }

    private static void stageTargeting(String trial, WorldRef arena, PlayerRef actor, PlayerRef opponent) {
        Fixtures.teleport(opponent, arena, 18.5, 100, 18.5);
        if (trial.startsWith("thorns-")) {
            if (trial.equals("thorns-wolf")) {
                MobRef attacker = (MobRef) Fixtures.spawnLiving(arena, "wolf", 12.5, 100, 0.5);
                Fixtures.host().animals().setAngry(attacker, true);
                attacker.setTarget(actor);
                put("attacker", attacker);
            } else if (trial.equals("thorns-husk")) {
                MobRef attacker = (MobRef) Fixtures.spawnLiving(arena, "husk", 12.5, 100, 0.5);
                attacker.setTarget(actor);
                put("attacker", attacker);
            } else {
                throw new IllegalArgumentException("Unknown thorns targeting trial: " + trial);
            }
            return;
        }
        Fixtures.give(actor, "wooden_axe", 1);
        LivingRef primary = spawnTarget(arena, "primary", "cow", 2.5, 0.5);
        primary.setHealth(trial.equals("globe") ? 10 : 1);
        LivingRef pet = spawnTarget(arena, "pet", "wolf", 2.5, -0.9);
        Fixtures.host().animals().tame(pet, actor);
        Fixtures.host().animals().setSitting(pet, true);
        LivingRef servant = spawnTarget(arena, "servant", "skeleton", 1.5, -0.9);
        servant.setEquipment(EquipmentSlot.HEAD, Fixtures.item("iron_helmet"));
        servant.data().set(SERVANT_OWNER, actor.uuid().toString());
        LivingRef decoy = spawnTarget(arena, "decoy", "armor_stand", 3.5, -0.9);
        decoy.data().set(DECOY_OWNER, actor.uuid().toString());
        if (trial.equals("globe")) {
            spawnTarget(arena, "cow", "cow", 3.5, 2.5);
            spawnTarget(arena, "wolf", "wolf", 0.5, 3.5);
            spawnTarget(arena, "enderman", "enderman", -2.5, 0.5);
            spawnTarget(arena, "husk", "husk", 0.5, -2.5);
            return;
        }
        switch (trial) {
            case "lance-cow" -> spawnTarget(arena, "protected", "cow", 3.5, 2.5);
            case "lance-wolf" -> spawnTarget(arena, "protected", "wolf", 3.5, 2.5);
            case "lance-enderman" -> spawnTarget(arena, "protected", "enderman", 3.5, 2.5);
            default -> throw new IllegalArgumentException("Unknown targeting trial: " + trial);
        }
        spawnTarget(arena, "husk", "husk", 6.5, 2.5);
    }

    private static LivingRef spawnTarget(WorldRef arena, String name, String type, double x, double z) {
        LivingRef target = Fixtures.spawnStill(arena, type, x, 100, z);
        put(name, target);
        return target;
    }

    private static void put(String name, LivingRef entity) {
        targets.put(name, new Target(entity.uuid(), entity.entityId(), entity));
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        JsonObject targetStates = new JsonObject();
        for (Map.Entry<String, Target> entry : targets.entrySet()) {
            LivingRef target = entry.getValue().resolve();
            if (!target.world().uid().equals(player.world().uid())) continue;
            JsonObject state = new JsonObject();
            state.addProperty("entityId", entry.getValue().entityId());
            state.addProperty("health", target.health());
            state.addProperty("dead", target.isDead());
            state.addProperty("valid", target.isValid());
            JsonArray effects = new JsonArray();
            for (EffectInstance effect : target.effects()) {
                JsonObject detail = new JsonObject();
                detail.addProperty("type", effect.type().toString());
                detail.addProperty("amplifier", effect.amplifier());
                detail.addProperty("duration", effect.durationTicks());
                effects.add(detail);
            }
            state.add("effects", effects);
            LastDamage damage = target.lastDamage();
            if (damage != null && damage.byEntity()) {
                EntityRef source = damage.damager();
                if (source instanceof ProjectileRef projectile && projectile.shooter() != null) source = projectile.shooter();
                state.addProperty("lastDamagerId", source.entityId());
                state.addProperty("lastDamagerServant", source instanceof LivingRef && TragoulSkeletalServant.isServant(source));
            }
            targetStates.add(entry.getKey(), state);
        }
        result.add("targets", targetStates);
        JsonArray servants = new JsonArray();
        for (EntityRef entity : player.world().entities()) {
            if (!(entity instanceof LivingRef skeleton) || !entity.type().equals(Id.minecraft("skeleton"))) continue;
            if (!TragoulSkeletalServant.isServant(skeleton)) continue;
            JsonObject state = new JsonObject();
            state.addProperty("entityId", skeleton.entityId());
            state.addProperty("owner", String.valueOf(TragoulSkeletalServant.getServantOwnerId(skeleton)));
            state.addProperty("health", skeleton.health());
            servants.add(state);
        }
        result.add("servants", servants);
        return result;
    }

    private record Target(UUID id, int entityId, LivingRef handle) {
        LivingRef resolve() {
            return Fixtures.entity(id) instanceof LivingRef live ? live : handle;
        }
    }
}
