package art.arcane.adapt.content.adaptation.chronos.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.content.adaptation.chronos.ChronosGesture;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.Id;

/**
 * Fires on sneak and swap hands with a clock in either hand. A swap Rewind or Second Chance used still counts; one
 * another ability or a signature took does not. The gesture is never farmed.
 */
public class ChronosTrigger extends TriggerBlock<ChronosTrigger.Config> {
  static final Id CLOCK = Id.minecraft("clock");

  public ChronosTrigger() {
    super(SkillChronos.ID);
    registerConfiguration(Config.class);
    setIcon(CLOCK);
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef player = e.player();
    boolean chronos = ChronosGesture.take(e);
    if ((e.isCancelled() && !chronos) || !player.isSneaking() || !hasActiveAdaptation(player)
        || !(CLOCK.equals(player.inventory().mainHand().type()) || CLOCK.equals(player.inventory().offHand().type()))) {
      return;
    }
    fire(player, null, player.location(), false);
  }

  @ConfigDescription("Sneak and swap hands while carrying a clock.")
  public static class Config extends MutationBlock.Config {
  }
}
