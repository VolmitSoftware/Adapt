package art.arcane.adapt.gameplay;

import art.arcane.adapt.content.adaptation.herbalism.HerbalismLuck;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDismountEvent;
import art.arcane.adapt.platform.event.entity.PiglinBarterEvent;
import art.arcane.adapt.platform.event.player.PlayerFishEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NatureFixtures {
    private static final Set<String> STAGES = Set.of("nature-blaze", "nature-fire", "nature-broker", "nature-strider",
            "nature-luck", "nature-guardian", "nature-empathy", "nature-pact", "nature-bone", "nature-fishing");
    private static final Set<String> NETHER_STAGES = Set.of("nature-broker", "nature-strider", "nature-fire", "nature-blaze");
    private static final Id MAX_HEALTH = Id.minecraft("max_health");
    private static final Id STRIDER = Id.minecraft("strider");
    private static final Id FISHING_BOBBER = Id.minecraft("fishing_bobber");
    private static final DataKey<Byte> GLOBE_KEY = DataKey.adapt("tragoul-globe", DataCodecs.BYTE);
    private static final String NO_COLLIDE_TEAM = "adaptqa_nocollide";
    private static final List<LivingRef> targets = new ArrayList<>();
    private static final JsonArray barters = new JsonArray();
    private static final JsonArray catches = new JsonArray();
    private static final JsonArray dismounts = new JsonArray();
    private static final Set<UUID> priorFishingItems = new HashSet<>();
    private static final Map<UUID, Double> originalMaxHealth = new HashMap<>();
    private static String currentStage;
    private static UUID actorId;
    private static int bites;
    private static int ordinaryBarterItems;

    public static void install() {
        Fixtures.register(new NatureFixtures());
    }

    static boolean usesNether(String name) {
        return NETHER_STAGES.contains(name);
    }

    public static boolean stage(String name, WorldRef arena, PlayerRef actor, PlayerRef opponent) {
        if (!STAGES.contains(name)) return false;
        currentStage = name;
        actorId = actor.uuid();
        targets.clear();
        while (barters.size() > 0) barters.remove(0);
        while (catches.size() > 0) catches.remove(0);
        while (dismounts.size() > 0) dismounts.remove(0);
        bites = 0;
        WorldRef world = arena;
        if (usesNether(name)) {
            NetherFixtures.stage("nether-qa-feast", arena, actor, opponent);
            world = NetherFixtures.world(arena);
            Fixtures.clearInventory(actor);
        }
        originalMaxHealth.putIfAbsent(actor.uuid(), actor.attributeBase(MAX_HEALTH));
        actor.setAttributeBase(MAX_HEALTH, 200);
        actor.setHealth(200);
        actor.setFoodLevel(10);
        actor.setSaturation(0);
        opponent.setFoodLevel(10);
        opponent.setSaturation(0);
        Fixtures.teleport(opponent, world, 10.5, 100, 10.5);
        switch (name) {
            case "nature-blaze", "nature-fire" -> {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -8; z < 0; z++) {
                        Fixtures.set(world, x, 99, z, name.equals("nature-blaze") ? "magma_block" : "netherrack");
                        if (name.equals("nature-fire")) {
                            Fixtures.set(world, x, 100, z, "fire");
                        }
                    }
                }
            }
            case "nature-broker" -> {
                actor.inventory().setArmor(EquipmentSlot.HEAD, Fixtures.item("golden_helmet"));
                Fixtures.give(actor, "gold_ingot", 20);
                LivingRef piglin = (LivingRef) Fixtures.summon(world, "piglin", 2.5, 100, 0.5, "{IsBaby:0b,IsImmuneToZombification:1b}");
                piglin.setAttributeBase(Id.minecraft("movement_speed"), 0);
                targets.add(piglin);
            }
            case "nature-strider" -> {
                for (int x = -4; x <= 4; x++) {
                    for (int z = -10; z <= -1; z++) Fixtures.set(world, x, 99, z, "lava");
                }
                LivingRef strider = Fixtures.spawnLiving(world, "strider", 0.5, 100, -3);
                List<EntityRef> jockeys = strider.passengers();
                strider.eject();
                for (EntityRef jockey : jockeys) jockey.remove();
                adult(strider);
                strider.setEquipment(EquipmentSlot.SADDLE, Fixtures.item("saddle"));
                strider.setAi(false);
                targets.add(strider);
            }
            case "nature-luck" -> {
                for (Id reward : HerbalismLuck.LUCK_FOOD) {
                    if (!Fixtures.items().isItem(reward)) throw new IllegalStateException("Luck reward cannot be carried: " + reward);
                }
                for (int x = -2; x <= 2; x++) {
                    for (int z = -3; z <= -1; z++) {
                        Fixtures.set(world, x, 99, z, "dirt");
                        Fixtures.set(world, x, 100, z, "dandelion");
                    }
                }
            }
            case "nature-guardian" -> {
                Fixtures.teleport(opponent, world, 4.5, 100, 0.5);
                Fixtures.give(opponent, "bow", 1);
                Fixtures.give(opponent, "arrow", 32);
                LivingRef wolf = Fixtures.spawnLiving(world, "wolf", -1.5, 100, 2.5);
                adult(wolf);
                Fixtures.platform().setOwner(wolf, actor);
                wolf.setAi(false);
                Fixtures.host().animals().setSitting(wolf, true);
                targets.add(wolf);
            }
            case "nature-empathy" -> {
                Fixtures.give(actor, "bone", 20);
                for (int i = 0; i < 20; i++) {
                    LivingRef wolf = Fixtures.spawnLiving(world, "wolf", 2.5, 100, 0.5);
                    adult(wolf);
                    wolf.setAi(false);
                    noCollide(wolf);
                    targets.add(wolf);
                }
            }
            case "nature-pact" -> {
                Fixtures.teleport(opponent, world, 2.5, 100, 0.5);
                Fixtures.give(opponent, "wooden_sword", 1);
            }
            case "nature-bone" -> {
                actor.setHealth(100);
                Fixtures.give(actor, "wooden_axe", 1);
                for (int i = 0; i < 20; i++) {
                    LivingRef cow = Fixtures.spawnStill(world, "cow", 2.5, 100, 0.5);
                    noCollide(cow);
                    cow.setHealth(1);
                    targets.add(cow);
                }
            }
            case "nature-fishing" -> {
                Fixtures.give(actor, Fixtures.enchanted("fishing_rod", "lure", 3));
                for (int x = -8; x <= 8; x++) {
                    for (int z = -18; z <= -2; z++) {
                        Fixtures.set(world, x, 95, z, "stone");
                        for (int y = 96; y <= 99; y++) Fixtures.set(world, x, y, z, "water");
                    }
                }
            }
            default -> throw new IllegalArgumentException("Unknown nature stage " + name);
        }
        if (name.equals("nature-pact") || name.equals("nature-guardian")) {
            for (int y = 100; y <= 103; y++) {
                for (int z = -1; z <= 1; z++) Fixtures.set(world, -1, y, z, "stone");
            }
        }
        return true;
    }

    @Handle(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void beforeBarter(PiglinBarterEvent event) {
        if ("nature-broker".equals(currentStage) && isTarget(event.piglin())) ordinaryBarterItems = event.outcome().size();
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dismounted(EntityDismountEvent event) {
        if (!"nature-strider".equals(currentStage) || !(event.entity() instanceof PlayerRef player)
                || !player.uuid().equals(actorId) || !event.dismounted().type().equals(STRIDER)) return;
        recordDismount(player, 0);
        Tasks.runEntity(player, () -> recordDismount(player, 2), 2);
        Tasks.runEntity(player, () -> recordDismount(player, 5), 5);
    }

    private static void recordDismount(PlayerRef player, int ticks) {
        Loc at = player.location();
        JsonObject state = new JsonObject();
        state.addProperty("ticks", ticks);
        state.addProperty("y", at.y());
        state.addProperty("feet", at.world().blockAt(at).type().toString());
        state.addProperty("below", at.world().blockAt(at.add(0, -1, 0)).type().toString());
        dismounts.add(state);
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterBarter(PiglinBarterEvent event) {
        if (!"nature-broker".equals(currentStage) || !isTarget(event.piglin())) return;
        JsonObject result = new JsonObject();
        result.addProperty("ordinaryStacks", ordinaryBarterItems);
        result.add("outcome", items(event.outcome()));
        barters.add(result);
    }

    @Handle(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void beforeFish(PlayerFishEvent event) {
        if (!"nature-fishing".equals(currentStage) || !event.player().uuid().equals(actorId)) return;
        if (event.state() == PlayerFishEvent.State.BITE) bites++;
        if (event.state() != PlayerFishEvent.State.CAUGHT_FISH) return;
        priorFishingItems.clear();
        for (EntityRef entity : event.player().world().entities()) if (entity instanceof ItemEntityRef) priorFishingItems.add(entity.uuid());
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterFish(PlayerFishEvent event) {
        if (!"nature-fishing".equals(currentStage) || !event.player().uuid().equals(actorId)
                || event.state() != PlayerFishEvent.State.CAUGHT_FISH || !(event.caught() instanceof ItemEntityRef caught)) return;
        JsonObject result = new JsonObject();
        result.add("ordinary", item(caught.item()));
        JsonArray extra = new JsonArray();
        PlayerRef player = event.player();
        for (EntityRef entity : player.world().nearbyEntities(player.location().position(), 2, 2, 2)) {
            if (entity instanceof ItemEntityRef dropped && !entity.uuid().equals(caught.uuid())
                    && !priorFishingItems.contains(entity.uuid())) extra.add(item(dropped.item()));
        }
        result.add("extra", extra);
        catches.add(result);
    }

    public static void reset(PlayerRef player) {
        Double previous = originalMaxHealth.remove(player.uuid());
        if (previous == null) return;
        player.setAttributeBase(MAX_HEALTH, previous);
        player.setHealth(Math.min(player.health(), player.attributeValue(MAX_HEALTH)));
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        result.addProperty("bites", bites);
        result.add("barters", barters);
        result.add("catches", catches);
        result.add("dismounts", dismounts);
        result.addProperty("inLava", player.isInLava());
        JsonArray hooks = new JsonArray();
        for (EntityRef entity : player.world().entities()) {
            if (!(entity instanceof ProjectileRef hook) || !hook.type().equals(FISHING_BOBBER)
                    || !player.uuid().equals(hook.shooterId())) continue;
            JsonObject fishing = Fixtures.platform().fishingHookState(hook);
            JsonObject ordered = new JsonObject();
            ordered.add("state", fishing.get("state"));
            ordered.addProperty("x", hook.location().x());
            ordered.addProperty("y", hook.location().y());
            ordered.addProperty("z", hook.location().z());
            ordered.addProperty("block", hook.world().blockAt(hook.location()).type().toString());
            ordered.add("openWater", fishing.get("openWater"));
            ordered.add("waitTime", fishing.get("waitTime"));
            ordered.add("timeUntilBite", fishing.get("timeUntilBite"));
            hooks.add(ordered);
        }
        result.add("hooks", hooks);
        JsonArray entities = new JsonArray();
        for (LivingRef handle : targets) {
            LivingRef target = Fixtures.entity(handle.uuid()) instanceof LivingRef live ? live : handle;
            if (!target.world().uid().equals(player.world().uid())) continue;
            JsonObject state = new JsonObject();
            state.addProperty("id", target.entityId());
            state.addProperty("dead", target.isDead());
            state.addProperty("health", target.health());
            if (target.has(EntityTrait.TAMEABLE)) {
                UUID owner = target.tameOwner();
                state.addProperty("tamed", Fixtures.host().animals().isTamed(target));
                state.addProperty("owned", owner != null && owner.equals(player.uuid()));
            }
            entities.add(state);
        }
        result.add("targets", entities);
        JsonArray globes = new JsonArray();
        for (EntityRef entity : player.world().entities()) {
            if (!(entity instanceof ItemEntityRef dropped) || !dropped.data().has(GLOBE_KEY)) continue;
            JsonObject globe = item(dropped.item());
            globe.addProperty("id", dropped.entityId());
            globe.addProperty("x", dropped.location().x());
            globe.addProperty("y", dropped.location().y());
            globe.addProperty("z", dropped.location().z());
            globes.add(globe);
        }
        result.add("globes", globes);
        JsonObject stats = new JsonObject();
        for (String name : List.of("tragoul.bone-harvest.orbs-collected", "tragoul.blood-pact.health-sacrificed",
                "taming.wild-empathy.tames", "taming.guardian-instinct.intercepts", "nether.strider-bond.lava-rescues", "nether.fire-resist.negated", "herbalism.luck.lucky-drops")) {
            stats.addProperty(name, Fixtures.adaptPlayer(player).getData().getStat(name));
        }
        result.add("stats", stats);
        Loc at = player.location();
        result.addProperty("floor", at.world().blockAt(at.add(0, -1, 0)).type().toString());
        return result;
    }

    private static boolean isTarget(EntityRef entity) {
        for (LivingRef target : targets) {
            if (target.uuid().equals(entity.uuid())) return true;
        }
        return false;
    }

    private static void adult(LivingRef entity) {
        if (!Fixtures.host().animals().isAdult(entity)) Fixtures.host().animals().setAge(entity, 0);
    }

    private static void noCollide(LivingRef entity) {
        Fixtures.command("team add " + NO_COLLIDE_TEAM);
        Fixtures.command("team modify " + NO_COLLIDE_TEAM + " collisionRule never");
        Fixtures.command("team join " + NO_COLLIDE_TEAM + " " + entity.uuid());
    }


    private static JsonArray items(List<ItemRef> items) {
        JsonArray result = new JsonArray();
        for (ItemRef stack : items) result.add(item(stack));
        return result;
    }

    private static JsonObject item(ItemRef stack) {
        JsonObject result = new JsonObject();
        result.addProperty("type", stack.type().toString());
        result.addProperty("amount", stack.amount());
        return result;
    }
}
