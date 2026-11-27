package art.arcane.adapt.content.adaptation.excavation.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.content.adaptation.excavation.ExcavationBlocks;
import art.arcane.adapt.content.skill.SkillExcavation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/** Holds for a few seconds after the player digs a soft block with a shovel. */
public class ExcavationState extends StateBlock<ExcavationState.Config> {
  private final Map<UUID, Long> lastDug = playerState();

  public ExcavationState() {
    super(SkillExcavation.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("wooden_shovel"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef player = e.player();
    if (!e.protectionProbe() && ExcavationBlocks.isShovelable(e.block().type())
        && ItemKinds.isShovel(player.inventory().mainHand()) && hasActiveAdaptation(player)) {
      lastDug.put(player.uuid(), System.currentTimeMillis());
    }
  }

  @Override
  public boolean holds(PlayerRef player) {
    Long dug = lastDug.get(player.uuid());
    return dug != null && System.currentTimeMillis() - dug <= getConfig().windowMillis;
  }

  @ConfigDescription("You dug a soft block in the last 4 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long after a dig the State holds, in milliseconds.", impact = "Higher values keep the State longer.")
    long windowMillis = 4_000L;
  }
}
