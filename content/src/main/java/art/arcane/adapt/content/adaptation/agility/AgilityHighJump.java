package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;

/** The super jump: extra jump strength that lifts the apex to a chosen height, and its take-off effects. */
public final class AgilityHighJump {
  private static final String SLOT_JUMP = "jump";

  private AgilityHighJump() {
  }

  public static void apply(Adaptation<?> owner, PlayerRef p, double apexHeight) {
    AdaptAttributeService.get().apply(p, owner.getId().toString(), SLOT_JUMP, Attributes.JUMP_STRENGTH,
        AgilityJumpPhysics.bonusForHeight(apexHeight), AttributeOperation.ADD_VALUE);
  }

  public static void remove(Adaptation<?> owner, PlayerRef p) {
    AdaptAttributeService.get().remove(p, owner.getId().toString(), SLOT_JUMP, Attributes.JUMP_STRENGTH);
  }

  /** Upward velocity of a jump whose apex is {@code apexHeight} blocks above the take-off. */
  public static double launchVelocity(double apexHeight) {
    return AgilityJumpPhysics.strengthForHeight(apexHeight);
  }

  public static void crouch(Adaptation<?> owner, PlayerRef p) {
    AgilitySuperJump.crouchFx(owner, p);
  }

  public static void burst(Adaptation<?> owner, PlayerRef p, double apexHeight) {
    AgilitySuperJump.burstFx(owner, p, apexHeight);
  }
}
