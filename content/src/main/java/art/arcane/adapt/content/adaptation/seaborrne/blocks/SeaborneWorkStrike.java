package art.arcane.adapt.content.adaptation.seaborrne.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.content.skill.SkillSeaborne;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.Set;

/** Fire around the work location goes out and dry farmland is soaked, each where the player may change blocks. */
public class SeaborneWorkStrike extends StrikeBlock<SeaborneWorkStrike.Config> {
  private static final Set<Id> FIRE = Set.of(Id.minecraft("fire"), Id.minecraft("soul_fire"));
  private static final Id FARMLAND = Id.minecraft("farmland");
  private static final Id AIR = Id.minecraft("air");
  private static final String MOISTURE = "moisture";
  private static final String WET = "7";

  public SeaborneWorkStrike() {
    super(SkillSeaborne.ID, StrikeForm.WORK);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("water_bucket"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    Loc center = use.center();
    WorldRef world = center.world();
    double radius = use.scaled(getConfig().radiusAtFirstLevel, getConfig().radiusAtTopLevel);
    int reach = (int) Math.ceil(radius);
    int changed = 0;
    int inspected = 0;
    for (int x = -reach; x <= reach; x++) {
      for (int y = -reach; y <= reach; y++) {
        for (int z = -reach; z <= reach && inspected < getConfig().blockBudget; z++) {
          if ((x * x) + (y * y) + (z * z) > radius * radius) {
            continue;
          }
          inspected++;
          if (soak(player, world.blockAt(center.blockX() + x, center.blockY() + y, center.blockZ() + z))) {
            changed++;
          }
        }
      }
    }
    if (changed <= 0) {
      return Outcome.MISSED;
    }
    addStat(player, "seaborne.mutated.soaked", changed);
    return Outcome.INSTANT;
  }

  private boolean soak(PlayerRef player, BlockRef block) {
    Loc at = block.location();
    if (FIRE.contains(block.type())) {
      if (!canBlockBreak(player, at) || !ProtectionProbes.attemptBlockBreakProbe(player, block)) {
        return false;
      }
      block.setType(AIR, true);
      fx(block.center(), FxPriority.TRANSITION).particle(Particles.CLOUD, 4, 0D, 0D, 0D, 0.2D, 0.02D);
      return true;
    }
    if (!FARMLAND.equals(block.type())) {
      return false;
    }
    BlockStateRef state = block.state();
    if (WET.equals(state.property(MOISTURE)) || !canBlockPlace(player, at)
        || !ProtectionProbes.attemptBlockPlaceProbe(player, block)) {
      return false;
    }
    block.setState(state.with(MOISTURE, WET), true);
    fx(block.center().add(0D, 0.6D, 0D), FxPriority.TRANSITION).particle(Particles.SPLASH, 6, 0D, 0D, 0D, 0.3D, 0.05D);
    return true;
  }

  @ConfigDescription("Fire within 4 blocks goes out and farmland within 4 blocks is watered.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Radius in blocks at level 1.", impact = "Higher values douse and water a wider area.")
    double radiusAtFirstLevel = 4D;
    @ConfigDoc(value = "Radius in blocks at the top level.", impact = "Higher values douse and water a wider area.")
    double radiusAtTopLevel = 6D;
    @ConfigDoc(value = "Most blocks one strike inspects.", impact = "Lower values cap the work of one strike.")
    int blockBudget = 2_048;
  }
}
