package art.arcane.adapt.content.adaptation.pickaxe.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.TriggerBlock;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeVein;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.config.ConfigDescription;

/** Fires when the player mines an ore with a pickaxe. A placed ore counts as farmed. */
public class PickaxesTrigger extends TriggerBlock<PickaxesTrigger.Config> {
  public PickaxesTrigger() {
    super("block-pickaxe-trigger");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_ore"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef player = e.player();
    BlockRef ore = e.block();
    if (e.protectionProbe() || !PickaxeVein.isOre(ore.type()) || !isPickaxe(player.inventory().mainHand())
        || !hasActiveAdaptation(player)) {
      return;
    }
    fire(player, null, ore.center(), MutationPayouts.farmed(ore));
  }

  @ConfigDescription("Mine an ore.")
  public static class Config extends MutationBlock.Config {
  }
}
