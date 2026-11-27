package art.arcane.adapt.content.adaptation.kinetics.blocks;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.adaptation.kinetics.KineticsJumpBoost;
import art.arcane.adapt.content.adaptation.kinetics.KineticsMoonJump;
import art.arcane.adapt.content.skill.SkillKinetics;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/**
 * Primes the player's next jump: extra jump strength, sized with Kinetics' jump physics, lasts until that jump or
 * until the window runs out.
 */
public class KineticsWorkStrike extends StrikeBlock<KineticsWorkStrike.Config> {
  private static final String SLOT = "primed-jump";

  private final Map<UUID, Long> primed = playerState();

  public KineticsWorkStrike() {
    super(SkillKinetics.ID, StrikeForm.WORK);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("slime_block"));
    needs(PlatformNeed.attribute(Attributes.JUMP_STRENGTH));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    double height = use.scaled(getConfig().jumpHeightAtFirstLevel, getConfig().jumpHeightAtTopLevel);
    double bonus = KineticsJumpBoost.strengthBonusForHeight(height);
    int ticks = (int) (getConfig().windowMillis / 50L);
    if (bonus <= 0D || ticks <= 0) {
      return Outcome.MISSED;
    }
    AdaptAttributeService.get().applyTimed(player, getId().toString(), SLOT, Attributes.JUMP_STRENGTH, bonus,
        AttributeOperation.ADD_VALUE, ticks);
    primed.put(player.uuid(), System.currentTimeMillis() + getConfig().windowMillis);
    return Outcome.lasting(player, ticks);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerJumpEvent e) {
    PlayerRef player = e.player();
    Long until = primed.remove(player.uuid());
    if (until == null) {
      return;
    }
    AdaptAttributeService.get().remove(player, getId().toString(), SLOT, Attributes.JUMP_STRENGTH);
    if (System.currentTimeMillis() <= until) {
      KineticsMoonJump.hopFx(this, player.location());
    }
  }

  @ConfigDescription("Your next jump within 5 s is 3 blocks high.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the primed jump waits, in milliseconds.", impact = "Higher values leave more time to jump.")
    long windowMillis = 5_000L;
    @ConfigDoc(value = "Height of the primed jump at level 1, in blocks.", impact = "Higher values jump higher.")
    double jumpHeightAtFirstLevel = 3D;
    @ConfigDoc(value = "Height of the primed jump at the top level, in blocks.", impact = "Higher values jump higher.")
    double jumpHeightAtTopLevel = 4D;
  }
}
