package art.arcane.adapt.content.adaptation.kinetics.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.content.adaptation.kinetics.KineticsWindburst;
import art.arcane.adapt.content.skill.SkillKinetics;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fires when the player lands from a fall of at least 4 blocks, even when the fall damage itself was prevented, unless
 * a signature took the smash that ended the fall.
 */
public class KineticsTrigger extends TriggerBlock<KineticsTrigger.Config> {
  private static final long SMASH_LANDING_MILLIS = 3000L;
  private static final Map<UUID, Long> SMASHED = new ConcurrentHashMap<>();

  public KineticsTrigger() {
    super(SkillKinetics.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("pointed_dripstone"));
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDamageEvent e) {
    if (e.cause() != DamageCause.FALL || !(e.entity() instanceof PlayerRef player) || landedFromSmash(player)
        || !KineticsWindburst.burstReady(player.fallDistance(), getConfig().minFallDistance)
        || !hasActiveAdaptation(player)) {
      return;
    }
    fire(player, null, player.location(), false);
  }

  /** A signature took {@code player}'s smash; the landing that follows within a few seconds does not count. */
  public static void smashed(PlayerRef player) {
    SMASHED.put(player.uuid(), System.currentTimeMillis() + SMASH_LANDING_MILLIS);
  }

  private static boolean landedFromSmash(PlayerRef player) {
    Long until = SMASHED.remove(player.uuid());
    return until != null && until >= System.currentTimeMillis();
  }

  @ConfigDescription("Land from a fall of 4 or more blocks.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Shortest fall that counts, in blocks.", impact = "Higher values ask for a longer fall.")
    double minFallDistance = 4D;
  }
}
