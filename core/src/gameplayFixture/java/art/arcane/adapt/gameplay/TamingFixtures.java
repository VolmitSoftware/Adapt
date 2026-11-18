package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TamingFixtures {
    private static final Set<String> STAGES = Set.of("taming-passive", "taming-regeneration", "taming-recall",
            "taming-fetch", "taming-shared", "taming-last-breath", "taming-alpha", "taming-mounted",
            "taming-stable", "taming-battle");
    private static final Map<UUID, JsonObject> PET_HITS = new HashMap<>();
    private static final Id WOLF = Id.minecraft("wolf");

    private TamingFixtures() {
    }

    public static void install() {
        Fixtures.register(new TamingFixtures());
    }

    public static boolean stage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        PET_HITS.remove(actor.uuid());
        actor.setFoodLevel(10);
        actor.setSaturation(0);
        opponent.setFoodLevel(10);
        opponent.setSaturation(0);
        Fixtures.teleport(opponent, world, 0.5, 100, 5.5);
        actor.setCooldown(Fixtures.id("lead"), 0);
        if (name.equals("taming-mounted")) {
            Fixtures.teleport(opponent, world, 4.5, 100, 0.5);
            LivingRef horse = (LivingRef) world.spawn(Fixtures.id("horse"), Fixtures.loc(world, 2.5, 100, 0.5),
                    pet -> Fixtures.platform().setOwner(pet, actor));
            horse.setAi(false);
            horse.setEquipment(EquipmentSlot.SADDLE, Fixtures.item("saddle"));
            Fixtures.tag(horse, "adapt-qa-taming-pet");
            return true;
        }
        double x = name.equals("taming-recall") ? 9.5 : 2.5;
        LivingRef wolf = (LivingRef) world.spawn(Fixtures.id("wolf"), Fixtures.loc(world, x, 100, 0.5), pet -> {
            if (!name.equals("taming-stable")) {
                Fixtures.platform().setOwner(pet, actor);
            }
        });
        Fixtures.tag(wolf, "adapt-qa-taming-pet");
        wolf.setAi(name.equals("taming-fetch") || name.equals("taming-battle"));
        Fixtures.host().animals().setSitting(wolf, name.equals("taming-alpha"));
        switch (name) {
            case "taming-regeneration" -> wolf.setHealth(8);
            case "taming-recall" -> Fixtures.give(actor, "lead", 1);
            case "taming-fetch" -> world.dropItem(new Vec3(7.5, 100.1, 0.5), Fixtures.item("diamond", 3)).setPickupDelay(0);
            case "taming-shared" -> {
                Fixtures.teleport(wolf, world, 0.5, 100, 2.5);
                Fixtures.teleport(opponent, world, 2.5, 100, 0.5);
                Fixtures.give(opponent, "wooden_sword", 1);
            }
            case "taming-last-breath" -> {
                wolf.setHealth(1);
                Fixtures.give(actor, "wooden_sword", 1);
            }
            case "taming-alpha" -> {
                Fixtures.teleport(wolf, world, 0.5, 100, 2.5);
                Fixtures.give(actor, "bone", 16);
                cow(world, 2.5, 10);
            }
            case "taming-stable" -> Fixtures.give(actor, "bone", 64);
            case "taming-battle" -> {
                Fixtures.teleport(wolf, world, 2.5, 100, 2.5);
                cow(world, 2.5, 3);
            }
            default -> { }
        }
        return true;
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        result.addProperty("mounted", player.vehicle() != null);
        JsonObject hit = PET_HITS.get(player.uuid());
        if (hit != null) {
            result.add("petHit", hit);
        }
        for (EntityRef entity : player.world().entities()) {
            if (!(entity instanceof LivingRef living)) {
                continue;
            }
            for (String tag : Fixtures.tags(living)) {
                if (tag.startsWith("adapt-qa-taming-")) {
                    result.add(tag.substring("adapt-qa-taming-".length()), state(living));
                }
            }
        }
        return result;
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPetDamage(EntityDamageByEntityEvent event) {
        EntityRef pet = event.damager();
        if (pet == null || !pet.has(EntityTrait.TAMEABLE) || pet.tameOwner() == null) {
            return;
        }
        PlayerRef owner = Fixtures.host().player(pet.tameOwner());
        if (owner == null || !Fixtures.owns(owner) || !Fixtures.isFixtureWorld(owner.world())) {
            return;
        }
        JsonObject hit = new JsonObject();
        hit.addProperty("targetId", event.entity().entityId());
        hit.addProperty("petId", pet.entityId());
        hit.addProperty("damage", event.finalDamage());
        PET_HITS.put(owner.uuid(), hit);
    }

    private static JsonObject state(LivingRef entity) {
        JsonObject result = new JsonObject();
        result.addProperty("entityId", entity.entityId());
        result.addProperty("health", entity.health());
        result.addProperty("alive", !entity.isDead());
        Loc location = entity.location();
        result.addProperty("x", location.x());
        result.addProperty("y", location.y());
        result.addProperty("z", location.z());
        if (entity.has(EntityTrait.TAMEABLE)) {
            result.addProperty("tamed", Fixtures.host().animals().isTamed(entity));
        }
        if (entity.type().equals(WOLF)) {
            result.addProperty("sitting", Fixtures.host().animals().isSitting(entity));
        }
        if (entity instanceof MobRef mob && mob.target() != null) {
            result.addProperty("targetId", mob.target().entityId());
        }
        JsonObject attributes = new JsonObject();
        for (Id attribute : Fixtures.host().registries().ids(RegistryKind.ATTRIBUTE)) {
            if (entity.hasAttribute(attribute)) {
                attributes.addProperty(attribute.toString(), entity.attributeValue(attribute));
            }
        }
        result.add("attributes", attributes);
        JsonObject effects = new JsonObject();
        for (EffectInstance effect : entity.effects()) {
            effects.addProperty(effect.type().toString(), effect.amplifier());
        }
        result.add("effects", effects);
        return result;
    }



    private static void cow(WorldRef world, double x, double health) {
        LivingRef cow = Fixtures.spawnStill(world, "cow", x, 100, 0.5);
        cow.setHealth(health);
        Fixtures.tag(cow, "adapt-qa-taming-target");
    }
}
