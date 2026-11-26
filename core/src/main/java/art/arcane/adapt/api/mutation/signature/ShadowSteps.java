package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.content.adaptation.stealth.StealthCore;
import art.arcane.adapt.content.adaptation.stealth.upgraded.PhantomDecoy;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** What the Stealth signatures borrow from the Stealth skill: its Shadow Step, its detection rules and who hunts you. */
final class ShadowSteps {
  private ShadowSteps() {
  }

  /** Phantom Decoy, which owns Shadow Step and its cooldown; null without the Stealth skill. */
  static @Nullable PhantomDecoy shadowStep(Adaptation<?> owner) {
    Skill<?> stealth = owner.getServer().getSkillRegistry().getSkill("stealth");
    if (stealth == null) {
      return null;
    }
    for (Adaptation<?> upgrade : stealth.getUpgrades()) {
      if (upgrade instanceof PhantomDecoy decoy) {
        return decoy;
      }
    }
    return null;
  }

  /** Stealth's detection rules for {@code observer} looking for {@code player}; false without the Stealth skill. */
  static boolean unseenBy(Adaptation<?> owner, PlayerRef player, LivingRef observer) {
    Skill<?> stealth = owner.getServer().getSkillRegistry().getSkill("stealth");
    StealthCore core = stealth == null ? null : StealthCore.of(stealth);
    return core != null && core.unseenBy(player, observer);
  }

  /** Whether a mob within {@code radius}, other than {@code except}, has the player as its target. */
  static boolean huntedByAnother(PlayerRef player, @Nullable UUID except, double radius) {
    for (EntityRef entity : StealthCore.nearbyEntities(player.location(), radius,
        (EntityRef candidate) -> candidate instanceof MobRef && !candidate.uuid().equals(except))) {
      LivingRef target = ((MobRef) entity).target();
      if (target != null && target.uuid().equals(player.uuid()) && !((MobRef) entity).isDead()) {
        return true;
      }
    }
    return false;
  }
}
