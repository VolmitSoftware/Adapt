package art.arcane.adapt.content.adaptation.tragoul.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.TriggerBlock;
import art.arcane.adapt.content.adaptation.tragoul.TragoulBloodPact;
import art.arcane.adapt.content.adaptation.tragoul.TragoulDrain;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/**
 * Fires when a single hit takes at least the configured health from the player after mitigation, measured as Blood
 * Pact measures it. The attacker, resolved through projectiles as Healing resolves it, is the target.
 */
public class TragOulTrigger extends TriggerBlock<TragOulTrigger.Config> {
  public TragOulTrigger() {
    super("block-tragoul-trigger");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("redstone"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef player)
        || !TragoulBloodPact.isTriggeringDamage(e.finalDamage(), getConfig().minimumHearts * 2D)
        || !hasActiveAdaptation(player)) {
      return;
    }
    LivingRef attacker = TragoulDrain.resolveAttacker(e);
    if (attacker != null && attacker.uuid().equals(player.uuid())) {
      attacker = null;
    }
    fire(player, attacker, player.location(), attacker != null && MutationPayouts.farmed(player, attacker));
  }

  @ConfigDescription("Lose 2 or more hearts in one hit.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Hearts one hit must take, after armor and other reductions.", impact = "Lower values fire on lighter hits.")
    double minimumHearts = 2D;
  }
}
