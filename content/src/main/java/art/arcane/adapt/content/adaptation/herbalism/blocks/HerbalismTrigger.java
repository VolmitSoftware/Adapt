package art.arcane.adapt.content.adaptation.herbalism.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowables;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismHarvest;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerHarvestBlockEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

/** Fires when the player breaks or picks a fully grown crop. A bone-mealed crop counts as farmed. */
public class HerbalismTrigger extends TriggerBlock<HerbalismTrigger.Config> {
  public HerbalismTrigger() {
    super(SkillHerbalism.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("wheat"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    if (!e.protectionProbe()) {
      harvested(e.player(), e.block());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerHarvestBlockEvent e) {
    harvested(e.player(), e.harvestedBlock());
  }

  private void harvested(PlayerRef player, BlockRef crop) {
    if (crop == null || !HerbalismGrowables.isCrop(crop.type()) || !HerbalismHarvest.isMature(crop)
        || !hasActiveAdaptation(player)) {
      return;
    }
    fire(player, null, crop.center(), Mutations.farmedHarvest(crop));
  }

  @ConfigDescription("Harvest a mature crop.")
  public static class Config extends MutationBlock.Config {
  }
}
