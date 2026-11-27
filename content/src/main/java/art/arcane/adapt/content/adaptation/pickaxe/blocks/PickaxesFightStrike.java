package art.arcane.adapt.content.adaptation.pickaxe.blocks;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.skill.SkillPickaxes;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockStateRef;

/** A burst of rock stuns the target: it cannot move for a moment. */
public class PickaxesFightStrike extends StrikeBlock<PickaxesFightStrike.Config> {
  private static final String SLOT = "stun";
  private static final Id STONE = Id.minecraft("stone");

  public PickaxesFightStrike() {
    super(SkillPickaxes.ID, StrikeForm.FIGHT);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("cobblestone"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    PlayerRef player = use.player();
    if (target == null || isProtectedFriendly(player, target)
        || !(target instanceof PlayerRef ? canPVP(player, target.location()) : canPVE(player, target.location()))) {
      return Outcome.MISSED;
    }
    int ticks = (int) (use.scaled(getConfig().stunMillisAtFirstLevel, getConfig().stunMillisAtTopLevel) / 50D);
    BlockStateRef rock = AdaptApi.platform().host().items().blockState(STONE);
    boolean scheduled = AdaptApi.scheduler().runEntity(target, () -> {
      if (!target.isValid() || target.isDead()) {
        return;
      }
      AdaptAttributeService.get().applyTimed(target, getId().toString(), SLOT, Attributes.MOVEMENT_SPEED, -1D,
          AttributeOperation.ADD_MULTIPLIED_TOTAL, ticks);
      target.setVelocity(Vec3.ZERO);
      if (rock != null) {
        fx(target.location().add(0D, 0.6D, 0D), FxPriority.COMBAT)
            .particle(Particles.blockCrack(rock), 18, 0D, 0D, 0D, 0.45D, 0.1D);
      }
    });
    return scheduled ? Outcome.lasting(target, ticks) : Outcome.MISSED;
  }

  @ConfigDescription("The target is stunned by a burst of rock for 1 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the target is stunned at level 1, in milliseconds.", impact = "Higher values hold the target longer.")
    double stunMillisAtFirstLevel = 1_000D;
    @ConfigDoc(value = "How long the target is stunned at the top level, in milliseconds.", impact = "Higher values hold the target longer.")
    double stunMillisAtTopLevel = 1_750D;
  }
}
