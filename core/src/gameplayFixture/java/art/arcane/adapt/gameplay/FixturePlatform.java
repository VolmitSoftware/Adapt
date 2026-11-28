package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonObject;

import java.util.Set;

public interface FixturePlatform {
    String name();

    void requireIsolatedServer();

    WorldRef createArena(String name);

    WorldRef generatedWorld(String name, long seed, Environment environment);

    void unloadWorld(WorldRef world);

    Set<String> tags(EntityRef entity);

    void addTag(EntityRef entity, String tag);

    void sendPlain(PlayerRef player, String text);

    double tps();

    boolean integrationStage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent);

    JsonObject integrationSnapshot(PlayerRef player);

    default Loc integrationSite(String name) {
        return null;
    }

    /** Joins a player with no client behind it, for console-driven checks; only some platforms can. */
    default PlayerRef spawnProbePlayer(String name) {
        throw new UnsupportedOperationException(name() + " cannot join in-process players");
    }

    /** Disconnects a player {@link #spawnProbePlayer} joined. */
    default void removeProbePlayer(PlayerRef player) {
        throw new UnsupportedOperationException(name() + " cannot join in-process players");
    }

    boolean nativeOwns(EntityRef entity);

    JsonObject fishingHookState(EntityRef hook);

    Loc compassTarget(PlayerRef player);

    void setOwner(EntityRef pet, PlayerRef owner);

    default void restoreAir(PlayerRef player) {
    }

    /** Clears the vanilla combat memory (last mob hurt, last attacker) that a fresh pet's owner-target goals act on. */
    void forgetCombat(PlayerRef player);

    default EntityRef summon(WorldRef world, String type, double x, double y, double z, String nbt) {
        EntityRef entity = Fixtures.spawn(world, type, x, y, z);
        if (!nbt.isEmpty()) {
            Fixtures.mergeNbt(entity, nbt);
        }
        return entity;
    }

    default void teleport(EntityRef entity, Loc destination) {
        entity.teleport(destination, TeleportCause.PLUGIN);
    }

    java.util.OptionalInt customModelData(art.arcane.adapt.platform.item.ItemRef item);

    String openMenuTitle(PlayerRef player);

    Runnable grantPermission(PlayerRef player, String node);

    void setSneaking(PlayerRef player, boolean sneaking);

    void meleeAttack(MobRef attacker, LivingRef target);

    boolean burnsInDaylight(MobRef mob);
}
