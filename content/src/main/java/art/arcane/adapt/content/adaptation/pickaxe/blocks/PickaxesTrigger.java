package art.arcane.adapt.content.adaptation.pickaxe.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeVein;
import art.arcane.adapt.content.skill.SkillPickaxes;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

/** Fires when the player mines an ore with a pickaxe. A placed ore counts as farmed. */
public class PickaxesTrigger extends TriggerBlock<PickaxesTrigger.Config> {
  public PickaxesTrigger() {
    super(SkillPickaxes.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_ore"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef player = e.player();
    BlockRef ore = e.block();
    if (e.protectionProbe() || !PickaxeVein.isOre(ore.type()) || !ItemKinds.isPickaxe(player.inventory().mainHand())
        || !hasActiveAdaptation(player)) {
      return;
    }
    fire(player, null, ore.center(), Mutations.farmed(ore));
  }

  @ConfigDescription("Mine an ore.")
  public static class Config extends MutationBlock.Config {
  }
}
