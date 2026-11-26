package art.arcane.adapt.content.adaptation.golemancy.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.TriggerBlock;
import art.arcane.adapt.content.adaptation.golemancy.GolemTaskDoneEvent;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/**
 * Fires when one of the player's bound golems finishes a task while the player is nearby, so an unattended golem farm
 * sets nothing off; the work happens where the golem stands.
 */
public class GolemancyTrigger extends TriggerBlock<GolemancyTrigger.Config> {
  public GolemancyTrigger() {
    super("block-golemancy-trigger");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("carved_pumpkin"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(GolemTaskDoneEvent e) {
    PlayerRef owner = e.owner();
    Loc at = e.at();
    Loc ownerAt = owner.location();
    double radius = getConfig().ownerRadius;
    if (!ownerAt.sameWorld(at) || ownerAt.distanceSquared(at) > radius * radius || !hasActiveAdaptation(owner)) {
      return;
    }
    fire(owner, null, at, false);
  }

  @ConfigDescription("Have a golem you own finish a task.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How close the owner must be to the golem, in blocks.", impact = "Higher values let golems farther away set it off.")
    double ownerRadius = 32D;
  }
}
