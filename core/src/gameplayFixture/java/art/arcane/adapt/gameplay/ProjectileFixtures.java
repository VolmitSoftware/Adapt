package art.arcane.adapt.gameplay;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ProjectileFixtures {
    private static final Set<String> STAGES = Set.of("projectile-procs", "projectile-heart", "projectile-bounce",
            "projectile-preview", "projectile-web", "projectile-blood", "projectile-snare", "projectile-speed",
            "projectile-trophies", "projectile-smash", "projectile-axe", "projectile-logswap");
    private static final Map<UUID, JsonArray> IMPACTS = new HashMap<>();
    private static final Id COBWEB = Id.minecraft("cobweb");

    private ProjectileFixtures() {
    }

    public static void install() {
        Fixtures.register(new ProjectileFixtures());
    }

    public static boolean stage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        IMPACTS.remove(actor.uuid());
        actor.setFoodLevel(20);
        actor.setSaturation(0);
        actor.setCooldown(Id.minecraft("bow"), 0);
        actor.setCooldown(Id.minecraft("iron_axe"), 0);
        Fixtures.gameRule(world, "natural_health_regeneration", "false");
        Fixtures.teleport(opponent, world, 0.5, 100, 6.5);
        switch (name) {
            case "projectile-procs" -> {
                bow(actor);
                cow(world, 4.5, 200, false);
            }
            case "projectile-heart", "projectile-preview" -> {
                bow(actor);
                cow(world, 8.5, 50, false);
            }
            case "projectile-bounce" -> {
                bow(actor);
                for (int y = 100; y <= 105; y++) {
                    for (int z = -3; z <= 3; z++) {
                        Fixtures.set(world, 6, y, z, "stone");
                        Fixtures.set(world, -6, y, z, "stone");
                    }
                }
            }
            case "projectile-web" -> {
                Fixtures.give(actor, recipeInput("ranged-web-bomb"));
                cow(world, 5.5, 20, false);
            }
            case "projectile-blood" -> cow(world, 2.5, 4, true);
            case "projectile-snare" -> {
                Fixtures.give(actor, recipeInput("hunter-snare"));
                LivingRef zombie = Fixtures.spawnStill(world, "zombie", 3.5, 100, 0.5);
                zombie.setEquipment(EquipmentSlot.HEAD, Fixtures.item("leather_helmet"));
                Fixtures.tag(zombie, "adapt-qa-projectile-target");
            }
            case "projectile-speed" -> Fixtures.teleport(opponent, world, 2.5, 100, 0.5);
            case "projectile-trophies" -> {
                for (int index = 0; index < 20; index++) {
                    double angle = index * Math.PI / 10;
                    LivingRef blaze = Fixtures.spawnStill(world, "blaze", 0.5 + Math.cos(angle) * 2.7, 100, 0.5 + Math.sin(angle) * 2.7);
                    blaze.setGravity(false);
                    blaze.setHealth(1);
                    Fixtures.tag(blaze, "adapt-qa-projectile-trophy-" + index);
                }
            }
            case "projectile-smash" -> {
                Fixtures.give(actor, "iron_axe", 1);
                cow(world, 3.5, 30, false);
            }
            case "projectile-axe" -> {
                Fixtures.give(actor, "iron_axe", 1);
                cow(world, 6.5, 40, false);
            }
            case "projectile-logswap" -> {
                Fixtures.give(actor, "birch_log", 8);
                Fixtures.give(actor, "oak_sapling", 1);
                Fixtures.set(world, 2, 100, 2, "crafting_table");
            }
            default -> throw new IllegalStateException("Unhandled projectile fixture " + name);
        }
        return true;
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        JsonArray impacts = IMPACTS.get(player.uuid());
        result.add("impacts", impacts == null ? new JsonArray() : impacts);
        JsonObject trophies = new JsonObject();
        JsonObject drops = new JsonObject();
        for (EntityRef entity : player.world().entities()) {
            if (entity instanceof ItemEntityRef item) {
                String type = item.item().type().legacyName();
                drops.addProperty(type, (drops.has(type) ? drops.get(type).getAsInt() : 0) + item.item().amount());
            }
            if (!(entity instanceof LivingRef living)) {
                continue;
            }
            for (String tag : Fixtures.tags(living)) {
                if (tag.equals("adapt-qa-projectile-target")) {
                    result.add("target", state(living));
                } else if (tag.startsWith("adapt-qa-projectile-trophy-")) {
                    trophies.add(tag.substring("adapt-qa-projectile-trophy-".length()), state(living));
                }
            }
        }
        result.add("trophies", trophies);
        result.add("drops", drops);
        int webs = 0;
        for (int x = 3; x <= 8; x++) {
            for (int y = 99; y <= 104; y++) {
                for (int z = -3; z <= 3; z++) {
                    if (player.world().blockAt(x, y, z).type().equals(COBWEB)) {
                        webs++;
                    }
                }
            }
        }
        result.addProperty("webs", webs);
        ItemRef hand = player.inventory().mainHand();
        result.addProperty("heldType", hand.type().legacyName());
        result.addProperty("heldDamage", hand.isEmpty() ? 0 : hand.damage());
        return result;
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onImpact(ProjectileHitEvent event) {
        ProjectileRef projectile = event.projectile();
        if (!(projectile.shooter() instanceof PlayerRef player)
                || !Fixtures.owns(player)
                || !Fixtures.isFixtureWorld(player.world())) {
            return;
        }
        JsonObject impact = new JsonObject();
        impact.addProperty("entityId", projectile.entityId());
        impact.addProperty("x", projectile.location().x());
        impact.addProperty("y", projectile.location().y());
        impact.addProperty("z", projectile.location().z());
        impact.addProperty("block", event.hitBlock() != null);
        if (event.hitEntity() != null) {
            impact.addProperty("targetId", event.hitEntity().entityId());
        }
        IMPACTS.computeIfAbsent(player.uuid(), ignored -> new JsonArray()).add(impact);
    }

    private static JsonObject state(LivingRef target) {
        JsonObject result = new JsonObject();
        result.addProperty("entityId", target.entityId());
        result.addProperty("health", target.health());
        result.addProperty("alive", !target.isDead());
        result.addProperty("x", target.location().x());
        result.addProperty("y", target.location().y());
        result.addProperty("z", target.location().z());
        result.addProperty("glowing", target.isGlowing());
        JsonObject effects = new JsonObject();
        for (EffectInstance effect : target.effects()) {
            effects.addProperty(effect.type().toString(), effect.amplifier());
        }
        result.add("effects", effects);
        JsonObject attributes = new JsonObject();
        for (Id attribute : Fixtures.host().registries().ids(RegistryKind.ATTRIBUTE)) {
            if (target.hasAttribute(attribute)) {
                attributes.addProperty(attribute.toString(), target.attributeValue(attribute));
            }
        }
        result.add("attributes", attributes);
        return result;
    }

    private static void bow(PlayerRef actor) {
        Fixtures.give(actor, "bow", 1);
        Fixtures.give(actor, "arrow", 64);
    }

    private static void cow(WorldRef world, double x, double health, boolean ai) {
        LivingRef cow = Fixtures.spawnLiving(world, "cow", x, 100, 0.5);
        cow.setAi(ai);
        Id maximum = Id.minecraft("max_health");
        if (!cow.hasAttribute(maximum)) {
            throw new IllegalStateException("Cow max-health attribute unavailable");
        }
        cow.setAttributeBase(maximum, Math.max(10, health));
        if (health == 200) {
            Id resistance = Id.minecraft("knockback_resistance");
            if (cow.hasAttribute(resistance)) {
                cow.setAttributeBase(resistance, 1);
            }
        }
        cow.setHealth(health);
        Fixtures.tag(cow, "adapt-qa-projectile-target");
    }

    private static ItemRef recipeInput(String name) {
        Optional<RecipeView> recipe = AdaptCore.platform().recipes().recipe(Id.adapt(name));
        if (recipe.isEmpty()) {
            throw new IllegalStateException("Required Adapt recipe is missing: " + name);
        }
        ItemRef input = recipe.get().result().copy();
        input.setAmount(1);
        return input;
    }
}
