package art.arcane.adapt.content.adaptation.discovery.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.TriggerBlock;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryFindEvent;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;

/**
 * Fires when the Discovery skill records something the player had never found: a block, item, creature, biome, world,
 * recipe, food, enchantment or effect. A new chunk alone does not count. Each find happens once, so none is farmed.
 */
public class DiscoveryTrigger extends TriggerBlock<DiscoveryTrigger.Config> {
  public DiscoveryTrigger() {
    super("block-discovery-trigger");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("compass"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(DiscoveryFindEvent e) {
    PlayerRef player = e.player();
    if (!e.chunk() && hasActiveAdaptation(player)) {
      fire(player, null, e.at(), false);
    }
  }

  @ConfigDescription("Discover something new.")
  public static class Config extends MutationBlock.Config {
  }
}
