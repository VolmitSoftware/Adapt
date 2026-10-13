package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.chronos.TemporalFreezeRegistry;
import art.arcane.adapt.util.common.scheduling.J;
import com.google.gson.JsonObject;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

public final class TemporalAuditFixtures {
    private TemporalAuditFixtures() {
    }

    public static JsonObject execute(Player player, String[] args) {
        if (!J.isFoliaThreading() && !player.getWorld().getName().startsWith("adapt_gameplay_")) throw new IllegalStateException("Gameplay arena required");
        switch (args[2]) {
            case "void-prepare" -> {
                learn(player, "rift-void-skin");
                player.getInventory().clear();
                player.getInventory().setItem(0, new ItemStack(Material.ENDER_PEARL, 2));
                player.setHealth(6D);
                player.getAttribute(Attribute.MAX_ABSORPTION).setBaseValue(8D);
                player.setAbsorptionAmount(8D);
                player.setNoDamageTicks(0);
            }
            case "void-hit" -> player.damage(18D);
            case "overlap" -> { return overlap(player, args[3]); }
            case "recall-prepare" -> {
                learn(player, "chronos-instant-recall");
                player.getInventory().clear();
                player.getInventory().setItem(0, new ItemStack(Material.CLOCK, 2));
                player.setGameMode(GameMode.SURVIVAL);
                player.setAllowFlight(false);
                if (!J.isFoliaThreading()) player.teleport(player.getLocation().clone().add(0D, 3D, 0D));
                player.setGravity(false);
            }
            case "recall" -> {
                Adaptation<?> bomb = adaptation("chronos-time-bomb");
                invoke(bomb, "freezePlayer", player, UUID.randomUUID());
                Object state = state(bomb, "frozenPlayers", player);
                invoke(adaptation("chronos-instant-recall"), "attemptRecall", player);
                invoke(bomb, "unfreezePlayer", player, state);
                map(bomb, "frozenPlayers").remove(player.getUniqueId());
            }
            case "trident" -> { return trident(player); }
            case "snapshot" -> { }
            default -> throw new IllegalArgumentException("Unknown temporal fixture operation");
        }
        return snapshot(player);
    }

    private static JsonObject overlap(Player player, String first) {
        learn(player, "chronos-stasis-field");
        Adaptation<?> stasis = adaptation("chronos-stasis-field");
        Adaptation<?> bomb = adaptation("chronos-time-bomb");
        for (Object existing : map(stasis, "bubbles").values().toArray()) invoke(stasis, "retireBubble", existing, false);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(new ItemStack(Material.AMETHYST_SHARD, 2));
        map(stasis, "cooldowns").remove(player.getUniqueId());
        PlayerInteractEvent interaction = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                player.getInventory().getItemInMainHand(), null, null, EquipmentSlot.HAND);
        invoke(stasis, "on", interaction);
        Object bubble = map(stasis, "bubbles").values().stream().findFirst().orElseThrow();
        Arrow arrow = player.getWorld().spawn(player.getLocation().clone().add(0D, 1D, 1D), Arrow.class);
        Vector initial = new Vector(0.4D, 0.2D, 0D);
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
        map(bomb, "frozenEntities").remove(arrow.getUniqueId());
        result.addProperty("gravity", arrow.hasGravity());
        result.addProperty("persistent", arrow.isPersistent());
        result.addProperty("speed", arrow.getVelocity().length());
        result.addProperty("remainingFrozen", TemporalFreezeRegistry.isFrozen(arrow));
        arrow.remove();
        player.setSneaking(false);
        return result;
    }

    private static JsonObject trident(Player player) {
        learn(player, "seaborne-trident-mastery");
        Adaptation<?> mastery = adaptation("seaborne-trident-mastery");
        Adaptation<?> bomb = adaptation("chronos-time-bomb");
        Trident trident = player.getWorld().spawn(player.getLocation().clone().add(0D, 2D, 3D), Trident.class);
        trident.setShooter(player);
        trident.setVelocity(new Vector(0.4D, 0D, 0D));
        invoke(bomb, "freezeEntity", trident, UUID.randomUUID());
        Object state = state(bomb, "frozenEntities", trident);
        Location target = player.getLocation().clone().add(0D, 1D, 0D);
        invoke(mastery, "applyRecallTargetOwned", trident, trident.getUniqueId(), player, 5, 100, target);
        JsonObject result = new JsonObject();
        result.addProperty("frozenSpeed", trident.getVelocity().length());
        invoke(bomb, "unfreezeEntity", trident, state);
        map(bomb, "frozenEntities").remove(trident.getUniqueId());
        invoke(mastery, "applyRecallTargetOwned", trident, trident.getUniqueId(), player, 5, 100, target);
        result.addProperty("recallSpeed", trident.getVelocity().length());
        trident.remove();
        return result;
    }

    private static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        result.addProperty("dead", player.isDead());
        result.addProperty("health", player.getHealth());
        result.addProperty("absorption", player.getAbsorptionAmount());
        result.addProperty("mode", player.getGameMode().name());
        result.addProperty("allowFlight", player.getAllowFlight());
        result.addProperty("flying", player.isFlying());
        result.addProperty("pearls", player.getInventory().all(Material.ENDER_PEARL).values().stream().mapToInt(ItemStack::getAmount).sum());
        result.addProperty("escapes", Adapt.instance.getAdaptServer().getPlayer(player).getData().getStat("rift.void-skin.escapes"));
        result.addProperty("now", System.currentTimeMillis());
        JsonObject deadlines = new JsonObject();
        for (String id : new String[]{"tragoul-last-rites", "chronos-hourglass-guard", "rift-void-skin"}) {
            deadlines.addProperty(id, player.getPersistentDataContainer().getOrDefault(
                    new NamespacedKey(Adapt.instance, "death_save_" + id.replace('-', '_')), PersistentDataType.LONG, 0L));
        }
        result.add("deathSaveDeadlines", deadlines);
        result.addProperty("borrowedDebt", player.getPersistentDataContainer().get(
                new NamespacedKey(Adapt.instance, "chronos_borrowed_time_debt"), PersistentDataType.STRING));
        return result;
    }

    private static void learn(Player player, String name) {
        AdaptPlayer owner = Adapt.instance.getAdaptServer().getPlayer(player);
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                owner.getData().getSkillLine(skill.getName()).setAdaptation(adaptation,
                        adaptation.getName().equals(name) ? adaptation.getMaxLevel() : 0);
            }
        }
    }

    private static Adaptation<?> adaptation(String name) {
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (adaptation.getName().equals(name)) return adaptation;
            }
        }
        throw new IllegalArgumentException("Unknown adaptation " + name);
    }

    private static Object state(Object target, String field, Entity entity) {
        Object state = map(target, field).get(entity.getUniqueId());
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
