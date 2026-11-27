package art.arcane.adapt.content.adaptation.nether.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.content.adaptation.nether.NetherBlazeLeech;
import art.arcane.adapt.content.adaptation.nether.NetherFireSources;
import art.arcane.adapt.content.skill.SkillNether;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/**
 * Fires when the player takes fire, burning, lava or magma damage, as Blaze Leech recognises it. The burn counts as
 * farmed when a placed fire source is within 2 blocks, and a lingering burn stays farmed for 10 s after that contact.
 */
public class NetherTrigger extends TriggerBlock<NetherTrigger.Config> {
  private static final int SOURCE_RADIUS = 2;
  private static final long LINGERING_BURN_MILLIS = 10_000L;
  private final Map<UUID, Long> farmedContact = playerState();

  public NetherTrigger() {
    super(SkillNether.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("blaze_powder"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageEvent e) {
    if (e.entity() instanceof PlayerRef player && NetherBlazeLeech.isFireDamage(e) && hasActiveAdaptation(player)) {
      fire(player, null, player.location(), farmed(player, e.cause()));
    }
  }

  private boolean farmed(PlayerRef player, DamageCause cause) {
    long now = System.currentTimeMillis();
    if (NetherFireSources.placedNear(player.world().blockAt(player.location()), SOURCE_RADIUS)) {
      farmedContact.put(player.uuid(), now);
      return true;
    }
    Long contact = farmedContact.get(player.uuid());
    return cause == DamageCause.FIRE_TICK && contact != null && now - contact <= LINGERING_BURN_MILLIS;
  }

  @ConfigDescription("Take fire damage.")
  public static class Config extends MutationBlock.Config {
  }
}
