package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.ability.DoubleJump;
import art.arcane.adapt.api.ability.VelocityBurst;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.internal.AbilityRuntime;
import art.arcane.adapt.engine.ComponentPort;
import art.arcane.adapt.engine.adaptation.VelocityBurstRuntime;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.input.DoubleJumpGesture;

/** {@link AbilityRuntime} over the burst runtime, the jump gesture and the platform's component port. */
final class AbilityBridge implements AbilityRuntime {
  @Override
  public VelocityBurst.Client velocityBursts(Adaptation<?> source, VelocityBurst.Feedback feedback) {
    return VelocityBurstRuntime.register(source, feedback);
  }

  @Override
  public DoubleJump doubleJump() {
    return new DoubleJumpGesture();
  }

  @Override
  public void damageItem(PlayerRef player, EquipmentSlot slot, int amount) {
    port().damageItem(player, slot, amount);
  }

  @Override
  public int experiencePoints(PlayerRef player) {
    return port().totalExperiencePoints(player);
  }

  @Override
  public void setExperiencePoints(PlayerRef player, int points) {
    port().setExperiencePoints(player, points);
  }

  @Override
  public int awardExperience(PlayerRef player, int points) {
    return port().awardExperience(player, points);
  }

  private static ComponentPort port() {
    return AdaptPorts.get(ComponentPort.class, ComponentPort.NONE);
  }
}
