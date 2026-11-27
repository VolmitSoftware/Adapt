package art.arcane.adapt.content.adaptation.seaborrne.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneWater;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneWet;
import art.arcane.adapt.content.skill.SkillSeaborne;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

/**
 * Fires when the player drops from open air into water, or when a living attacker hits them while they are wet. A hit
 * from a spawner, egg or bred mob counts as farmed.
 */
public class SeaborneTrigger extends TriggerBlock<SeaborneTrigger.Config> {
  public SeaborneTrigger() {
    super(SkillSeaborne.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("water_bucket"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    Loc from = e.from();
    Loc to = e.to();
    PlayerRef player = e.player();
    if (!e.changedBlock() || to.y() >= from.y()
        || !SeaborneWater.isWater(to.world().blockAt(to)) || SeaborneWater.isWater(from.world().blockAt(from))) {
      return;
    }
    fire(player, null, to, false);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef player) || !SeaborneWet.isWet(player) || !hasActiveAdaptation(player)) {
      return;
    }
    EntityRef source = e.causingEntity() instanceof LivingRef ? e.causingEntity() : e.damager();
    if (!(source instanceof LivingRef attacker) || attacker.uuid().equals(player.uuid())) {
      return;
    }
    fire(player, attacker, player.location(), Mutations.farmed(player, attacker));
  }

  @ConfigDescription("Dive into water, or get hit while wet.")
  public static class Config extends MutationBlock.Config {
  }
}
