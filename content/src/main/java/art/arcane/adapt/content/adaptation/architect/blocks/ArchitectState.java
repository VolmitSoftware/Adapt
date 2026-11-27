package art.arcane.adapt.content.adaptation.architect.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/** Holds for a few seconds after the player places a block. */
public class ArchitectState extends StateBlock<ArchitectState.Config> {
  private final Map<UUID, Long> lastPlaced = playerState();

  public ArchitectState() {
    super(SkillArchitect.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("oak_planks"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    PlayerRef player = e.player();
    if (!e.protectionProbe() && e.canBuild() && hasActiveAdaptation(player)) {
      lastPlaced.put(player.uuid(), System.currentTimeMillis());
    }
  }

  @Override
  public boolean holds(PlayerRef player) {
    Long placed = lastPlaced.get(player.uuid());
    return placed != null && System.currentTimeMillis() - placed <= getConfig().windowMillis;
  }

  @ConfigDescription("You placed a block in the last 4 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long after a placement the State holds, in milliseconds.", impact = "Higher values keep the State longer.")
    long windowMillis = 4_000L;
  }
}
