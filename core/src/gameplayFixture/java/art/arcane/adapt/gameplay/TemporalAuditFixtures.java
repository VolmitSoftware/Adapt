package art.arcane.adapt.gameplay;

import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.chronos.TemporalFreezeRegistry;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.scheduling.Tasks;
import com.google.gson.JsonObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

public final class TemporalAuditFixtures {
    private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
    private static final Id TOTEM = Id.minecraft("totem_of_undying");

    private TemporalAuditFixtures() {
    }

    public static JsonObject execute(PlayerRef player, String[] args) {
        if (!Tasks.isFoliaThreading() && !Fixtures.isFixtureWorld(player.world())) throw new IllegalStateException("Gameplay arena required");
        switch (args[2]) {
            case "void-prepare" -> {
                learn(player, "adapt:rift/void_skin");
                Fixtures.clearInventory(player);
                player.inventory().setItem(0, Fixtures.item("ender_pearl", 2));
                player.setHealth(6D);
                player.setAttributeBase(Id.minecraft("max_absorption"), 8D);
                player.setAbsorption(8D);
                player.setNoDamageTicks(0);
            }
            case "void-hit" -> { return hit(player, 18D); }
            case "lethal-hit" -> { return hit(player, 100D); }
            case "totem" -> player.inventory().setOffHand(Fixtures.item("totem_of_undying"));
            case "overlap" -> { return overlap(player, args[3]); }
            case "recall-prepare" -> {
                learn(player, "adapt:chronos/instant_recall");
                Fixtures.clearInventory(player);
                player.inventory().setItem(0, Fixtures.item("clock", 2));
                player.setGameMode(GameMode.SURVIVAL);
                player.setAllowFlight(false);
                Fixtures.teleport(player, player.location().add(0D, 3D, 0D));
                player.setGravity(false);
            }
            case "recall" -> {
                Adaptation<?> bomb = Fixtures.adaptation("adapt:chronos/time_bomb");
                invoke(bomb, "freezePlayer", player, UUID.randomUUID());
                Object state = state(bomb, "frozenPlayers", player);
                invoke(Fixtures.adaptation("adapt:chronos/instant_recall"), "attemptRecall", player);
                invoke(bomb, "unfreezePlayer", player, state);
                map(bomb, "frozenPlayers").remove(player.uuid());
            }
            case "trident" -> { return trident(player); }
            case "snapshot" -> { }
            default -> throw new IllegalArgumentException("Unknown temporal fixture operation");
        }
        return snapshot(player);
    }

    // A player who just changed worlds stays invulnerable until the client reports the level loaded
    // (or the server's 60-tick timeout), and the hit is then dropped before any damage event fires.
    // "delivered" tells the scenario whether this hit reached the player, so it can retry an ignored one.
    private static JsonObject hit(PlayerRef player, double amount) {
        JsonObject before = snapshot(player);
        player.damage(amount, (EntityRef) null);
        JsonObject after = snapshot(player);
        boolean delivered = after.get("dead").getAsBoolean()
                || after.get("health").getAsDouble() != before.get("health").getAsDouble()
                || after.get("absorption").getAsDouble() != before.get("absorption").getAsDouble()
                || after.get("pearls").getAsInt() != before.get("pearls").getAsInt()
                || !after.get("deathSaveDeadlines").equals(before.get("deathSaveDeadlines"));
        after.addProperty("delivered", delivered);
        return after;
    }

    private static JsonObject overlap(PlayerRef player, String first) {
        learn(player, "adapt:chronos/stasis_field");
        Adaptation<?> stasis = Fixtures.adaptation("adapt:chronos/stasis_field");
        Adaptation<?> bomb = Fixtures.adaptation("adapt:chronos/time_bomb");
        for (Object existing : map(stasis, "bubbles").values().toArray()) invoke(stasis, "retireBubble", existing, false);
        Fixtures.platform().setSneaking(player, true);
        player.inventory().setMainHand(Fixtures.item("amethyst_shard", 2));
        map(stasis, "cooldowns").remove(player.uuid());
        PlayerInteractEvent interaction = new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR,
                Hand.MAIN_HAND, player.inventory().mainHand(), null, null, null, UseResult.DENY, UseResult.DEFAULT);
        invoke(stasis, "on", interaction);
        Object bubble = map(stasis, "bubbles").values().stream().findFirst().orElseThrow();
        Loc at = player.location().add(0D, 1D, 1D);
        ProjectileRef arrow = (ProjectileRef) Fixtures.spawn(player.world(), "arrow", at.x(), at.y(), at.z());
        Vec3 initial = new Vec3(0.4D, 0.2D, 0D);
        arrow.setVelocity(initial);
        boolean bombFirst = first.equals("bomb");
        if (bombFirst) invoke(bomb, "freezeEntity", arrow, UUID.randomUUID());
        invoke(stasis, "freezeProjectileOwned", bubble, arrow);
        if (!bombFirst) invoke(bomb, "freezeEntity", arrow, UUID.randomUUID());
        Object bombState = state(bomb, "frozenEntities", arrow);
        Object stasisState = state(stasis, "frozenProjectiles", arrow);
        JsonObject result = new JsonObject();
        result.addProperty("frozen", TemporalFreezeRegistry.isFrozen(arrow));
        if (bombFirst) invoke(bomb, "unfreezeEntity", arrow, bombState);
        else invoke(stasis, "restoreProjectileOwned", stasisState);
        result.addProperty("afterFirstGravity", arrow.hasGravity());
        result.addProperty("afterFirstFrozen", TemporalFreezeRegistry.isFrozen(arrow));
        if (bombFirst) invoke(stasis, "restoreProjectileOwned", stasisState);
        else invoke(bomb, "unfreezeEntity", arrow, bombState);
        map(bomb, "frozenEntities").remove(arrow.uuid());
        result.addProperty("gravity", arrow.hasGravity());
        result.addProperty("persistent", arrow.isPersistent());
        result.addProperty("speed", arrow.velocity().length());
        result.addProperty("remainingFrozen", TemporalFreezeRegistry.isFrozen(arrow));
        arrow.remove();
        Fixtures.platform().setSneaking(player, false);
        return result;
    }

    private static JsonObject trident(PlayerRef player) {
        learn(player, "adapt:seaborne/trident_mastery");
        Adaptation<?> mastery = Fixtures.adaptation("adapt:seaborne/trident_mastery");
        Adaptation<?> bomb = Fixtures.adaptation("adapt:chronos/time_bomb");
        Loc at = player.location().add(0D, 2D, 3D);
        ProjectileRef trident = (ProjectileRef) Fixtures.spawn(player.world(), "trident", at.x(), at.y(), at.z());
        trident.setShooter(player);
        trident.setVelocity(new Vec3(0.4D, 0D, 0D));
        invoke(bomb, "freezeEntity", trident, UUID.randomUUID());
        Object state = state(bomb, "frozenEntities", trident);
        Loc target = player.location().add(0D, 1D, 0D);
        invoke(mastery, "applyRecallTargetOwned", trident, trident.uuid(), player, 5, 100, target);
        JsonObject result = new JsonObject();
        result.addProperty("frozenSpeed", trident.velocity().length());
        invoke(bomb, "unfreezeEntity", trident, state);
        map(bomb, "frozenEntities").remove(trident.uuid());
        invoke(mastery, "applyRecallTargetOwned", trident, trident.uuid(), player, 5, 100, target);
        result.addProperty("recallSpeed", trident.velocity().length());
        trident.remove();
        return result;
    }

    private static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        result.addProperty("dead", player.isDead());
        result.addProperty("health", player.health());
        result.addProperty("absorption", player.absorption());
        result.addProperty("mode", player.gameMode().name());
        result.addProperty("allowFlight", player.allowFlight());
        result.addProperty("flying", player.isFlying());
        int pearls = 0;
        for (ItemRef item : player.inventory().contents()) {
            if (item != null && !item.isEmpty() && item.type().equals(ENDER_PEARL)) pearls += item.amount();
        }
        result.addProperty("pearls", pearls);
        ItemRef offHand = player.inventory().offHand();
        result.addProperty("offHandTotem", offHand != null && !offHand.isEmpty() && offHand.type().equals(TOTEM));
        result.addProperty("escapes", Fixtures.adaptPlayer(player).getData().getStat("rift.void-skin.escapes"));
        result.addProperty("now", System.currentTimeMillis());
        JsonObject deadlines = new JsonObject();
        for (String id : new String[]{"adapt:tragoul/last_rites", "adapt:chronos/hourglass_guard", "adapt:rift/void_skin"}) {
            Long deadline = player.data().get(DataKey.adapt("death_save_" + id.replace('-', '_'), DataCodecs.LONG));
            deadlines.addProperty(id, deadline == null ? 0L : deadline);
        }
        result.add("deathSaveDeadlines", deadlines);
        result.addProperty("borrowedDebt", player.data().get(DataKey.adapt("chronos_borrowed_time_debt", DataCodecs.STRING)));
        return result;
    }

    private static void learn(PlayerRef player, String name) {
        AdaptPlayer owner = Fixtures.adaptPlayer(player);
        for (Skill<?> skill : Fixtures.core().getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                owner.getData().getSkillLine(SkillHost.lineKey(skill)).setAdaptation(adaptation,
                        adaptation.getId().toString().equals(name) ? adaptation.getMaxLevel() : 0);
            }
        }
    }

    private static Object state(Object target, String field, EntityRef entity) {
        Object state = map(target, field).get(entity.uuid());
        if (state == null) throw new IllegalStateException("No " + field + " entry");
        return state;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> map(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (Map<Object, Object>) field.get(target);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static Object invoke(Object target, String name, Object... arguments) {
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            Class<?>[] types = method.getParameterTypes();
            boolean matches = true;
            for (int index = 0; index < types.length; index++) {
                if (!types[index].isPrimitive() && !types[index].isInstance(arguments[index])) matches = false;
            }
            if (!matches) continue;
            try {
                method.setAccessible(true);
                return method.invoke(target, arguments);
            } catch (InvocationTargetException error) {
                throw new IllegalStateException(error.getCause());
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        }
        throw new IllegalArgumentException("Missing temporal operation " + name);
    }
}
