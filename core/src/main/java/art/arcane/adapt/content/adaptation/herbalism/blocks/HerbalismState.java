package art.arcane.adapt.content.adaptation.herbalism.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.StateBlock;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismBeeShepherd;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowables;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.Set;

/** Holds while the block at the player's feet is a crop, grass, a fern or a flower. */
public class HerbalismState extends StateBlock<HerbalismState.Config> {
  private static final Set<Id> GRASS = Set.of(Id.minecraft("short_grass"), Id.minecraft("grass"),
      Id.minecraft("tall_grass"), Id.minecraft("fern"), Id.minecraft("large_fern"));

  public HerbalismState() {
    super("block-herbalism-state");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("short_grass"));
  }

  @Override
  public boolean holds(PlayerRef player) {
    Loc feet = player.location();
    Id type = feet.world().blockAt(feet).type();
    return GRASS.contains(type) || HerbalismGrowables.isCrop(type) || HerbalismBeeShepherd.isFlower(type);
  }

  @ConfigDescription("You stand in crops, grass or flowers.")
  public static class Config extends MutationBlock.Config {
  }
}
