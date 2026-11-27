package art.arcane.adapt.content.adaptation.crafting.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.content.skill.SkillCrafting;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/** Holds for a while after the player crafts anything. */
public class CraftingState extends StateBlock<CraftingState.Config> {
  private final Map<UUID, Long> lastCraft = playerState();

  public CraftingState() {
    super(SkillCrafting.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("crafting_table"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ItemCraftedEvent e) {
    if (hasActiveAdaptation(e.player())) {
      lastCraft.put(e.player().uuid(), System.currentTimeMillis());
    }
  }

  @Override
  public boolean holds(PlayerRef player) {
    return heldWithin(player, 0L);
  }

  @Override
  public boolean heldWithin(PlayerRef player, long windowMillis) {
    Long crafted = lastCraft.get(player.uuid());
    return crafted != null && System.currentTimeMillis() - crafted <= getConfig().windowMillis + windowMillis;
  }

  @ConfigDescription("You crafted something in the last 10 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long after a craft the State holds, in milliseconds.", impact = "Higher values keep the State longer.")
    long windowMillis = 10_000L;
  }
}
