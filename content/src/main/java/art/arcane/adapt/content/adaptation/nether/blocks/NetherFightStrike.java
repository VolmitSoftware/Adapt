package art.arcane.adapt.content.adaptation.nether.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.adaptation.nether.NetherMagmaRetort;
import art.arcane.adapt.content.skill.SkillNether;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** The target catches fire through Magma Skin's burn, which never shortens a longer fire. */
public class NetherFightStrike extends StrikeBlock<NetherFightStrike.Config> {
  public NetherFightStrike() {
    super(SkillNether.ID, StrikeForm.FIGHT);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("fire_charge"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    PlayerRef player = use.player();
    if (target == null || isProtectedFriendly(player, target)
        || !(target instanceof PlayerRef ? canPVP(player, target.location()) : canPVE(player, target.location()))) {
      return Outcome.MISSED;
    }
    int ticks = (int) (use.scaled(getConfig().burnMillisAtFirstLevel, getConfig().burnMillisAtTopLevel) / 50D);
    return NetherMagmaRetort.burn(target, ticks) ? Outcome.lasting(target, ticks) : Outcome.MISSED;
  }

  @ConfigDescription("The target burns for 4 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the target burns at level 1, in milliseconds.", impact = "Higher values burn the target longer.")
    double burnMillisAtFirstLevel = 4_000D;
    @ConfigDoc(value = "How long the target burns at the top level, in milliseconds.", impact = "Higher values burn the target longer.")
    double burnMillisAtTopLevel = 6_000D;
  }
}
