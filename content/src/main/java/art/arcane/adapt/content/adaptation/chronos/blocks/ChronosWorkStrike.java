package art.arcane.adapt.content.adaptation.chronos.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.adaptation.chronos.ChronosAccelerate;
import art.arcane.adapt.content.adaptation.chronos.ChronosAura;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BrewingStandRef;
import art.arcane.adapt.platform.world.FurnaceRef;
import art.arcane.adapt.platform.world.WorldRef;

/**
 * Furnaces and brewing stands within the radius skip ahead and crops grow a stage, through Accelerate's aura steps and
 * their permission checks and effects. Grown crops count as bone-mealed, so their harvest is not a natural one.
 */
public class ChronosWorkStrike extends StrikeBlock<ChronosWorkStrike.Config> {
  public ChronosWorkStrike() {
    super(SkillChronos.ID, StrikeForm.WORK);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("clock"));
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    Loc center = use.center();
    WorldRef world = center.world();
    double radius = use.scaled(getConfig().radiusAtFirstLevel, getConfig().radiusAtTopLevel);
    int reach = (int) Math.ceil(radius);
    int ticks = (int) (getConfig().advanceMillis / 50L);
    int advanced = 0;
    int inspected = 0;
    for (int x = -reach; x <= reach; x++) {
      for (int y = -reach; y <= reach; y++) {
        for (int z = -reach; z <= reach && inspected < getConfig().blockBudget; z++) {
          if ((x * x) + (y * y) + (z * z) > radius * radius) {
            continue;
          }
          inspected++;
          if (advance(player, world.blockAt(center.blockX() + x, center.blockY() + y, center.blockZ() + z), ticks)) {
            advanced++;
          }
        }
      }
    }
    if (advanced <= 0) {
      return Outcome.MISSED;
    }
    addStat(player, "chronos.mutated.advanced", advanced);
    return Outcome.INSTANT;
  }

  private boolean advance(PlayerRef player, BlockRef block, int ticks) {
    Id type = block.type();
    if (ChronosAccelerate.FURNACE_TYPES.contains(type)) {
      FurnaceRef furnace = block.furnace().orElse(null);
      if (furnace == null || !ChronosAura.advanceFurnace(this, player, block, furnace, ticks)) {
        return false;
      }
      ChronosAccelerate.stationFx(this, block.world(), block.x(), block.y(), block.z(), true);
      return true;
    }
    if (ChronosAccelerate.BREWING_STAND.equals(type)) {
      BrewingStandRef stand = block.brewingStand().orElse(null);
      if (stand == null || !ChronosAura.advanceBrewingStand(this, player, block, stand, ticks)) {
        return false;
      }
      ChronosAccelerate.stationFx(this, block.world(), block.x(), block.y(), block.z(), false);
      return true;
    }
    return ChronosAura.growable(block) && ChronosAura.advanceCrop(this, player, block);
  }

  @ConfigDescription("Crops, furnaces and brewing stands within 4 blocks advance 10 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Smelting and brewing time skipped, in milliseconds.", impact = "Higher values finish furnaces and brews sooner.")
    long advanceMillis = 10_000L;
    @ConfigDoc(value = "Radius in blocks at level 1.", impact = "Higher values reach more stations and crops.")
    double radiusAtFirstLevel = 4D;
    @ConfigDoc(value = "Radius in blocks at the top level.", impact = "Higher values reach more stations and crops.")
    double radiusAtTopLevel = 6D;
    @ConfigDoc(value = "Most blocks one strike inspects.", impact = "Lower values cap the work of one strike.")
    int blockBudget = 2_048;
  }
}
