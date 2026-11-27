package art.arcane.adapt.content.adaptation.excavation.blocks;

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
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.content.adaptation.excavation.ExcavationBlocks;
import art.arcane.adapt.content.skill.SkillExcavation;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.Set;

/**
 * The soft ground under the target slows it. Dirt that a water bottle could turn to mud turns to mud, where the player
 * may break blocks, and turns back when the slow ends.
 */
public class ExcavationFightStrike extends StrikeBlock<ExcavationFightStrike.Config> {
  private static final String SLOT = "mire";
  private static final Id MUD = Id.minecraft("mud");
  private static final Set<Id> MUDDABLE = Set.of(Id.minecraft("dirt"), Id.minecraft("coarse_dirt"),
      Id.minecraft("rooted_dirt"));

  public ExcavationFightStrike() {
    super(SkillExcavation.ID, StrikeForm.FIGHT);
    registerConfiguration(Config.class);
    setIcon(MUD);
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    PlayerRef player = use.player();
    if (target == null || isProtectedFriendly(player, target)
        || !(target instanceof PlayerRef ? canPVP(player, target.location()) : canPVE(player, target.location()))) {
      return Outcome.MISSED;
    }
    Loc feet = target.location();
    BlockRef ground = feet.world().blockAt(feet.blockX(), feet.blockY() - 1, feet.blockZ());
    if (!ExcavationBlocks.isShovelable(ground.type())) {
      return Outcome.MISSED;
    }
    int ticks = (int) (getConfig().slowMillis / 50L);
    double slow = use.scaled(getConfig().slowAtFirstLevel, getConfig().slowAtTopLevel);
    boolean scheduled = AdaptApi.scheduler().runEntity(target, () -> {
      if (!target.isValid() || target.isDead()) {
        return;
      }
      Id soil = ground.type();
      if (MUDDABLE.contains(soil) && canBlockBreak(player, ground.location())
          && ProtectionProbes.attemptBlockBreakProbe(player, ground)) {
        ground.setType(MUD, true);
        AdaptApi.scheduler().runAt(ground.location(), () -> {
          if (MUD.equals(ground.type())) {
            ground.setType(soil, true);
          }
        }, ticks);
      }
      AdaptAttributeService.get().applyTimed(target, getId().toString(), SLOT, Attributes.MOVEMENT_SPEED, -slow,
          AttributeOperation.ADD_MULTIPLIED_TOTAL, ticks);
      BlockStateRef mud = AdaptApi.platform().host().items().blockState(MUD);
      if (mud != null) {
        fx(target.location().add(0D, 0.1D, 0D), FxPriority.COMBAT)
            .particle(Particles.blockCrack(mud), 14, 0D, 0D, 0D, 0.4D, 0.05D);
      }
    });
    return scheduled ? Outcome.lasting(target, ticks) : Outcome.MISSED;
  }

  @ConfigDescription("The ground under the target turns to mud, slowing it for 3 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the target is slowed, in milliseconds.", impact = "Higher values slow the target for longer.")
    long slowMillis = 3_000L;
    @ConfigDoc(value = "Movement speed taken at level 1, as a fraction.", impact = "Higher values slow the target more.")
    double slowAtFirstLevel = 0.4D;
    @ConfigDoc(value = "Movement speed taken at the top level, as a fraction.", impact = "Higher values slow the target more.")
    double slowAtTopLevel = 0.7D;
  }
}
