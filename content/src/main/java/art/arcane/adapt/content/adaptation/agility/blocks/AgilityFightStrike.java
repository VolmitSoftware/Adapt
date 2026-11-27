package art.arcane.adapt.content.adaptation.agility.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.adaptation.agility.AgilityDash;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;

/** The player dashes along the horizontal look direction, thrown the same way as Air Dash. */
public class AgilityFightStrike extends StrikeBlock<AgilityFightStrike.Config> {
  public AgilityFightStrike() {
    super(SkillAgility.ID, StrikeForm.FIGHT);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("feather"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    Vec3 direction = AgilityDash.direction(player);
    if (direction == null || player.isFlying() || player.isGliding() || player.vehicle() != null) {
      return Outcome.MISSED;
    }
    AgilityDash.launch(this, player, direction, use.scaled(getConfig().forceAtFirstLevel, getConfig().forceAtTopLevel),
        getConfig().upwardLift);
    return Outcome.INSTANT;
  }

  @ConfigDescription("You dash 3 blocks where you look.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Horizontal dash velocity at level 1, in blocks per tick; 0.3 carries about 3 blocks.", impact = "Higher values dash farther.")
    double forceAtFirstLevel = 0.3D;
    @ConfigDoc(value = "Horizontal dash velocity at the top level, in blocks per tick.", impact = "Higher values dash farther.")
    double forceAtTopLevel = 0.4D;
    @ConfigDoc(value = "Upward velocity added to the dash, in blocks per tick.", impact = "Higher values lift the dash off the ground.")
    double upwardLift = 0.12D;
  }
}
