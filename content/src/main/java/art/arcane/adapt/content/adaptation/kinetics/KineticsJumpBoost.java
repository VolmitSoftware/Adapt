package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.List;

/**
 * A standing jump-strength bonus plus a short floaty hop: extra jump strength for one jump and lower gravity for the
 * float window. Attribute slots carry the owning adaptation's name.
 */
public final class KineticsJumpBoost {
  public static final List<PlatformNeed> NEEDS = List.of(PlatformNeed.attribute(Attributes.JUMP_STRENGTH),
      PlatformNeed.attribute(Attributes.GRAVITY));
  private static final String SLOT_BASE_JUMP = "base-jump";
  private static final String SLOT_HOP = "hop";
  private static final String SLOT_FLOAT = "float";
  private static final long HOP_DURATION_TICKS = 10L;

  private final Adaptation<?> owner;
  private final Cooldowns fxCooldown;

  public KineticsJumpBoost(Adaptation<?> owner, Cooldowns fxCooldown) {
    this.owner = owner;
    this.fxCooldown = fxCooldown;
  }

  public record Hop(double jumpBonus, double gravityReduction, long floatWindowTicks) {
  }

  /** Jump strength to add so a standing jump clears {@code extraHeight} more blocks than vanilla. */
  public static double strengthBonusForExtraHeight(double extraHeight) {
    return KineticsJumpPhysics.bonusForHeight(KineticsJumpPhysics.VANILLA_JUMP_HEIGHT + Math.max(0D, extraHeight));
  }

  /** Jump strength to add so a standing jump clears {@code height} blocks in all. */
  public static double strengthBonusForHeight(double height) {
    return KineticsJumpPhysics.bonusForHeight(height);
  }

  public void applyBase(PlayerRef p, double jumpStrengthBonus) {
    AdaptAttributeService.get().apply(p, owner.getId().toString(), SLOT_BASE_JUMP, Attributes.JUMP_STRENGTH,
        jumpStrengthBonus, AttributeOperation.ADD_VALUE);
  }

  public void removeBase(PlayerRef p) {
    AdaptAttributeService.get().remove(p, owner.getId().toString(), SLOT_BASE_JUMP, Attributes.JUMP_STRENGTH);
  }

  public void hop(PlayerRef p, Hop hop) {
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.applyTimed(p, owner.getId().toString(), SLOT_HOP, Attributes.JUMP_STRENGTH, hop.jumpBonus(), AttributeOperation.ADD_VALUE, HOP_DURATION_TICKS);
    attributes.applyTimed(p, owner.getId().toString(), SLOT_FLOAT, Attributes.GRAVITY, -hop.gravityReduction(), AttributeOperation.ADD_MULTIPLIED_TOTAL, hop.floatWindowTicks());

    if (fxCooldown.isReady(p.uuid(), 1000)) {
      fxCooldown.mark(p.uuid());
      KineticsMoonJump.hopFx(owner, p.location());
    }
  }
}
