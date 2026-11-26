package art.arcane.adapt.api.xp;

import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;

import java.util.Set;

/**
 * Remembers mobs a farm can produce on demand (spawners, eggs, buckets, breeding, portal and golem farms, built
 * withers) so kill rewards can skip them.
 */
public final class MobOrigin {
  private static final DataKey<Byte> FARMED_KEY = DataKey.adapt("xp-farmed-mob", DataCodecs.BYTE);
  private static final Set<CreatureSpawnEvent.Reason> FARMED_REASONS = Set.of(CreatureSpawnEvent.Reason.SPAWNER,
      CreatureSpawnEvent.Reason.SPAWNER_EGG, CreatureSpawnEvent.Reason.DISPENSE_EGG, CreatureSpawnEvent.Reason.EGG,
      CreatureSpawnEvent.Reason.BUCKET, CreatureSpawnEvent.Reason.BREEDING, CreatureSpawnEvent.Reason.NETHER_PORTAL,
      CreatureSpawnEvent.Reason.VILLAGE_DEFENSE, CreatureSpawnEvent.Reason.BUILD_IRONGOLEM,
      CreatureSpawnEvent.Reason.BUILD_SNOWMAN, CreatureSpawnEvent.Reason.BUILD_WITHER, CreatureSpawnEvent.Reason.ENDER_PEARL,
      CreatureSpawnEvent.Reason.DUPLICATION, CreatureSpawnEvent.Reason.COMMAND);

  private MobOrigin() {
  }

  public static void record(EntityRef entity, CreatureSpawnEvent.Reason reason) {
    if (isFarmedReason(reason)) {
      entity.data().set(FARMED_KEY, (byte) 1);
    }
  }

  public static boolean isFarmedReason(CreatureSpawnEvent.Reason reason) {
    return FARMED_REASONS.contains(reason);
  }

  public static boolean isFarmed(EntityRef entity) {
    return entity.data().has(FARMED_KEY);
  }

  /** True when killing the victim may pay a kill reward: not farmed, not a minion or grave mob, not the killer's pet. */
  public static boolean paysKillReward(PlayerRef killer, EntityRef victim) {
    return !isFarmed(victim) && !MinionIdentity.isMinion(victim) && !CombatXp.isGraveMob(victim)
        && !killer.uuid().equals(victim.tameOwner());
  }
}
