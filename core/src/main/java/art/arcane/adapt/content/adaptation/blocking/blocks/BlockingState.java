package art.arcane.adapt.content.adaptation.blocking.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.StateBlock;
import art.arcane.adapt.content.adaptation.blocking.BlockingShields;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.Map;
import java.util.UUID;

/**
 * Holds while the player has a shield raised. Asked for a recent window, it answers whether their shield blocked a
 * hit within it.
 */
public class BlockingState extends StateBlock<BlockingState.Config> {
  private final Map<UUID, Long> lastBlock = playerState();

  public BlockingState() {
    super("block-blocking-state");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("shield"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    PlayerRef defender = BlockingShields.blocker(this, e);
    if (defender != null && hasActiveAdaptation(defender)) {
      lastBlock.put(defender.uuid(), System.currentTimeMillis());
    }
  }

  @Override
  public boolean holds(PlayerRef player) {
    return player.isBlocking() && BlockingShields.held(this, player);
  }

  @Override
  public boolean heldWithin(PlayerRef player, long windowMillis) {
    Long blocked = lastBlock.get(player.uuid());
    return blocked != null && System.currentTimeMillis() - blocked <= windowMillis;
  }

  @ConfigDescription("Your shield is raised.")
  public static class Config extends MutationBlock.Config {
  }
}
