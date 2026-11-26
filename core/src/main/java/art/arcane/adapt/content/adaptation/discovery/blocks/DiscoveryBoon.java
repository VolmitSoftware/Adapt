package art.arcane.adapt.content.adaptation.discovery.blocks;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.fx.ViewerDisplayPort;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.BoonBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryKeenEye;
import art.arcane.adapt.content.adaptation.discovery.KeenEyeScan;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/**
 * Night vision, and the chests around the player glow for them alone as Keen Eye shows them: only chests the player can
 * see and may open, never through walls.
 */
public class DiscoveryBoon extends BoonBlock<DiscoveryBoon.Config> {
  private static final Id NIGHT_VISION = Id.minecraft("night_vision");

  public DiscoveryBoon() {
    super("block-discovery-boon");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ender_eye"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    PlayerRef player = use.player();
    int ticks = (int) (use.scaled(getConfig().durationMillisAtFirstLevel, getConfig().durationMillisAtTopLevel) / 50D);
    player.addEffect(new EffectInstance(NIGHT_VISION, ticks, 0, false, false, true));
    ViewerDisplayPort displays = AdaptPorts.get(ViewerDisplayPort.class, ViewerDisplayPort.NONE);
    for (BlockRef chest : KeenEyeScan.within(player, player.location(), getConfig().chestRange,
        KeenEyeScan.HARD_MAX_HIGHLIGHTS, (Id type) -> !KeenEyeScan.isSpawner(type))) {
      if (!canAccessChest(player, chest.location())) {
        continue;
      }
      displays.showBlock(getName(), "chest-" + chest.x() + ":" + chest.y() + ":" + chest.z(), player,
          new Loc(chest.world(), chest.x(), chest.y(), chest.z()), chest.state(), DiscoveryKeenEye.CHEST_COLOR, ticks);
    }
    return Outcome.lasting(player, ticks);
  }

  @ConfigDescription("Night vision and glowing chests for 8 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the boon lasts at level 1, in milliseconds.", impact = "Higher values keep the sight longer.")
    double durationMillisAtFirstLevel = 8_000D;
    @ConfigDoc(value = "How long the boon lasts at the top level, in milliseconds.", impact = "Higher values keep the sight longer.")
    double durationMillisAtTopLevel = 12_000D;
    @ConfigDoc(value = "How far around the player chests glow, in blocks.", impact = "Higher values light chests farther away.")
    double chestRange = 24D;
  }
}
