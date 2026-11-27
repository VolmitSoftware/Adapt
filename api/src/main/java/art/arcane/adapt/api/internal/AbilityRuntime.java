package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.ability.DoubleJump;
import art.arcane.adapt.api.ability.VelocityBurst;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

/** What the engine does for ability helpers: velocity bursts, double jumps, item wear and vanilla experience. */
@ApiStatus.Internal
public interface AbilityRuntime {
  VelocityBurst.Client velocityBursts(Adaptation<?> source, VelocityBurst.Feedback feedback);

  DoubleJump doubleJump();

  void damageItem(PlayerRef player, EquipmentSlot slot, int amount);

  int experiencePoints(PlayerRef player);

  void setExperiencePoints(PlayerRef player, int points);

  int awardExperience(PlayerRef player, int points);
}
