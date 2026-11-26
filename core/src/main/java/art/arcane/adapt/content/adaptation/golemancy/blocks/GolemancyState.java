package art.arcane.adapt.content.adaptation.golemancy.blocks;

import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemWorkStatus;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.StateBlock;
import art.arcane.adapt.content.adaptation.golemancy.OwnedGolems;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.Set;

/** Holds while a golem of the player's is on a job nearby: working, walking to work or delivering. */
public class GolemancyState extends StateBlock<GolemancyState.Config> {
  private static final Set<GolemWorkStatus> BUSY = Set.of(GolemWorkStatus.WORKING, GolemWorkStatus.MOVING,
      GolemWorkStatus.DELIVERING);

  public GolemancyState() {
    super("block-golemancy-state");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("piston"));
  }

  @Override
  public boolean holds(PlayerRef player) {
    return OwnedGolems.nearest(player, player.location(), getConfig().radius,
        (GolemHandle handle) -> BUSY.contains(handle.workStatus())) != null;
  }

  @ConfigDescription("A golem of yours is working within 16 blocks.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How close the working golem must be, in blocks.", impact = "Higher values count golems farther away.")
    double radius = 16D;
  }
}
