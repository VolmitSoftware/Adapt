package art.arcane.adapt.content.adaptation.herbalism.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismField;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowables;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowthAura;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowthPulses;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Crops and saplings around the work location grow one stage, through Growth Aura's sphere sweep, permission check and
 * effects. Grown crops count as bone-mealed, so harvesting them cannot set the strike off again as a natural harvest.
 */
public class HerbalismWorkStrike extends StrikeBlock<HerbalismWorkStrike.Config> {
  private static final String STAGE = "stage";

  public HerbalismWorkStrike() {
    super("block-herbalism-strike-work");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bone_meal"));
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    Loc center = use.center();
    AtomicInteger grown = new AtomicInteger();
    HerbalismField.scan(center.world(), center,
        use.scaled(getConfig().radiusAtFirstLevel, getConfig().radiusAtTopLevel), getConfig().blockBudget,
        (BlockRef block) -> {
          if (grow(player, block)) {
            grown.incrementAndGet();
          }
        });
    if (grown.get() <= 0) {
      return Outcome.MISSED;
    }
    addStat(player, "harvest.mutated.grown", grown.get());
    return Outcome.INSTANT;
  }

  private boolean grow(PlayerRef player, BlockRef block) {
    BlockStateRef state = block.state();
    BlockStateRef next;
    if (state.age() >= 0 && state.age() < state.maxAge() && HerbalismGrowables.isCrop(block.type())) {
      next = state.with("age", Integer.toString(state.age() + 1));
    } else if (block.type().path().endsWith("_sapling") && "0".equals(state.property(STAGE))) {
      next = state.with(STAGE, "1");
    } else {
      return false;
    }
    if (!HerbalismGrowthPulses.mayGrow(this, player, block)) {
      return false;
    }
    block.setState(next, true);
    XpProvenance.recordBonemeal(block);
    BlockStateRef grown = block.state();
    HerbalismGrowthAura.growFx(this, block, grown.age() >= 0 && grown.age() >= grown.maxAge());
    return true;
  }

  @ConfigDescription("Crops and saplings within 4 blocks grow one stage.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Radius in blocks at level 1.", impact = "Higher values grow a wider field.")
    double radiusAtFirstLevel = 4D;
    @ConfigDoc(value = "Radius in blocks at the top level.", impact = "Higher values grow a wider field.")
    double radiusAtTopLevel = 6D;
    @ConfigDoc(value = "Most blocks one strike inspects.", impact = "Lower values cap the work of one strike in large fields.")
    int blockBudget = 2_048;
  }
}
