package art.arcane.adapt.content.adaptation.rift.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.TriggerBlock;
import art.arcane.adapt.content.adaptation.rift.RiftTeleports;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

/**
 * Fires a tick after the player arrives from an ender pearl, chorus fruit or blink, so it acts where they landed; a
 * Phase Step blink fires it at most once per {@link RiftTeleports#PHASE_TRIGGER_MILLIS}.
 */
public class RiftTrigger extends TriggerBlock<RiftTrigger.Config> {
  public RiftTrigger() {
    super("block-rift-trigger");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ender_pearl"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    PlayerRef player = e.player();
    if (!RiftTeleports.byRift(e) || !hasActiveAdaptation(player)
        || !RiftTeleports.mayTrigger(e, System.currentTimeMillis())) {
      return;
    }
    Tasks.runEntity(player, () -> {
      if (player.isOnline() && !player.isDead()) {
        fire(player, null, player.location(), false);
      }
    }, 1);
  }

  @ConfigDescription("Teleport by pearl, blink or chorus fruit.")
  public static class Config extends MutationBlock.Config {
  }
}
