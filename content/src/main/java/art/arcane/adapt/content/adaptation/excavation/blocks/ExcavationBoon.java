package art.arcane.adapt.content.adaptation.excavation.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.content.adaptation.excavation.ExcavationSoftFall;
import art.arcane.adapt.content.skill.SkillExcavation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/** The player's next fall deals no damage and lands in Soft Fall's cushion. Misses while a landing is already held. */
public class ExcavationBoon extends BoonBlock<ExcavationBoon.Config> {
  private final Map<UUID, Boolean> cushioned = playerState();

  public ExcavationBoon() {
    super(SkillExcavation.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("hay_block"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    return cushioned.put(use.player().uuid(), Boolean.TRUE) == null ? Outcome.INSTANT : Outcome.MISSED;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (e.cause() != DamageCause.FALL || !(e.entity() instanceof PlayerRef player)
        || !cushioned.containsKey(player.uuid()) || !hasActiveAdaptation(player)) {
      return;
    }
    cushioned.remove(player.uuid());
    e.setCancelled(true);
    ExcavationSoftFall.cushionRing(this, player.location());
  }

  @ConfigDescription("A safe landing on your next fall.")
  public static class Config extends MutationBlock.Config {
  }
}
