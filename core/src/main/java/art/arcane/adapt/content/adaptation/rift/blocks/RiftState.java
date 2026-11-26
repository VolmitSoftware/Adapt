package art.arcane.adapt.content.adaptation.rift.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.StateBlock;
import art.arcane.adapt.content.adaptation.rift.RiftTeleports;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.Map;
import java.util.UUID;

/** Holds for a few seconds after a teleport by ender pearl, chorus fruit or blink. */
public class RiftState extends StateBlock<RiftState.Config> {
  private final Map<UUID, Long> lastTeleport = playerState();

  public RiftState() {
    super("block-rift-state");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("chorus_fruit"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    PlayerRef player = e.player();
    if (RiftTeleports.byRift(e) && hasActiveAdaptation(player)) {
      lastTeleport.put(player.uuid(), System.currentTimeMillis());
    }
  }

  @Override
  public boolean holds(PlayerRef player) {
    return heldWithin(player, 0L);
  }

  @Override
  public boolean heldWithin(PlayerRef player, long windowMillis) {
    Long teleported = lastTeleport.get(player.uuid());
    return teleported != null && System.currentTimeMillis() - teleported <= getConfig().windowMillis + windowMillis;
  }

  @ConfigDescription("You teleported in the last 5 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long after a teleport the State holds, in milliseconds.", impact = "Higher values keep the State longer.")
    long windowMillis = 5_000L;
  }
}
