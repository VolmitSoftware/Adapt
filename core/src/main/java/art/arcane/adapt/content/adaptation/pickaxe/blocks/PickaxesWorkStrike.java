package art.arcane.adapt.content.adaptation.pickaxe.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeQuarrySense;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeVein;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Ores around the work location glow for the player alone, through Quarry Sense's private block displays. */
public class PickaxesWorkStrike extends StrikeBlock<PickaxesWorkStrike.Config> {
  public PickaxesWorkStrike() {
    super("block-pickaxe-strike-work");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("spyglass"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    Loc center = use.center();
    WorldRef world = center.world();
    double radius = use.scaled(getConfig().radiusAtFirstLevel, getConfig().radiusAtTopLevel);
    int reach = (int) Math.ceil(radius);
    List<Loc> ores = new ArrayList<>();
    for (int x = -reach; x <= reach; x++) {
      for (int y = -reach; y <= reach; y++) {
        for (int z = -reach; z <= reach; z++) {
          int blockY = center.blockY() + y;
          if ((x * x) + (y * y) + (z * z) > radius * radius || blockY < world.minHeight() || blockY >= world.maxHeight()) {
            continue;
          }
          Loc at = new Loc(world, center.blockX() + x, blockY, center.blockZ() + z);
          if (PickaxeVein.isOre(world.blockAt(at).type())) {
            ores.add(at);
          }
        }
      }
    }
    ores.sort(Comparator.comparingDouble((Loc at) -> at.distanceSquared(center)));
    int ticks = (int) (getConfig().glowMillis / 50L);
    int shown = 0;
    for (Loc at : ores.subList(0, Math.min(ores.size(), getConfig().maxOres))) {
      Id ore = world.blockAt(at).type();
      if (PickaxeQuarrySense.showOre(this, player, at, ore, PickaxeQuarrySense.oreColor(ore), ticks)) {
        shown++;
      }
    }
    return shown > 0 ? Outcome.INSTANT : Outcome.MISSED;
  }

  @ConfigDescription("Ores within 6 blocks glow for you for 5 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Radius searched for ores at level 1, in blocks.", impact = "Higher values reveal ores farther away.")
    double radiusAtFirstLevel = 6D;
    @ConfigDoc(value = "Radius searched for ores at the top level, in blocks.", impact = "Higher values reveal ores farther away.")
    double radiusAtTopLevel = 9D;
    @ConfigDoc(value = "How long the ores glow, in milliseconds.", impact = "Higher values keep the ores lit longer.")
    long glowMillis = 5_000L;
    @ConfigDoc(value = "Most ores lit by one strike, nearest first.", impact = "Lower values send fewer displays.")
    int maxOres = 24;
  }
}
