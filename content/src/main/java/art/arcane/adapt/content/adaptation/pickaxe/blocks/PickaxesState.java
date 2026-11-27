package art.arcane.adapt.content.adaptation.pickaxe.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.content.skill.SkillPickaxes;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/** Holds for a few seconds after the player breaks a block with a pickaxe. */
public class PickaxesState extends StateBlock<PickaxesState.Config> {
  private final Map<UUID, Long> lastMined = playerState();

  public PickaxesState() {
    super(SkillPickaxes.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("stone_pickaxe"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef player = e.player();
    if (!e.protectionProbe() && ItemKinds.isPickaxe(player.inventory().mainHand()) && hasActiveAdaptation(player)) {
      lastMined.put(player.uuid(), System.currentTimeMillis());
    }
  }

  @Override
  public boolean holds(PlayerRef player) {
    Long mined = lastMined.get(player.uuid());
    return mined != null && System.currentTimeMillis() - mined <= getConfig().windowMillis;
  }

  @ConfigDescription("You mined a block in the last 4 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long after mining a block the State holds, in milliseconds.", impact = "Higher values keep the State longer.")
    long windowMillis = 4_000L;
  }
}
