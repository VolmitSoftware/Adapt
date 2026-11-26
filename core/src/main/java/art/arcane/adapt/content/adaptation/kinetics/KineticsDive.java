package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A mace dive: heavier gravity, less air drag and extra downward speed each tick until the player lands. The dive maps
 * belong to the owning adaptation, and the attribute slots carry its name.
 */
public final class KineticsDive {
  public static final List<PlatformNeed> NEEDS = List.of(PlatformNeed.nativeAttribute(Attributes.GRAVITY));
  private static final String SLOT_GRAVITY = "dive-gravity";
  private static final String SLOT_DRAG = "dive-drag";
  private static final int REFRESH_TICKS = 8;

  private final Adaptation<?> owner;
  private final Map<UUID, Boolean> diveMarked;
  private final Map<UUID, Integer> acceleratedAtTick;

  public KineticsDive(Adaptation<?> owner, Map<UUID, Boolean> diveMarked, Map<UUID, Integer> acceleratedAtTick) {
    this.owner = owner;
    this.diveMarked = diveMarked;
    this.acceleratedAtTick = acceleratedAtTick;
  }

  /** How hard one dive pulls the player down. */
  public record Pull(double gravityBoost, double dragCut, double acceleration, double terminalFallSpeed) {
  }

  /** Keeps the dive going for a few more ticks; true only on the first refresh of a dive. */
  public boolean refresh(PlayerRef p, Pull pull) {
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.applyTimed(p, owner.getName(), SLOT_GRAVITY, Attributes.GRAVITY, pull.gravityBoost(), AttributeOperation.ADD_MULTIPLIED_TOTAL, REFRESH_TICKS);
    if (attributeExists(Attributes.AIR_DRAG_MODIFIER)) {
      attributes.applyTimed(p, owner.getName(), SLOT_DRAG, Attributes.AIR_DRAG_MODIFIER, -pull.dragCut(), AttributeOperation.ADD_MULTIPLIED_TOTAL, REFRESH_TICKS);
    }
    accelerate(p, pull);
    return diveMarked.putIfAbsent(p.uuid(), Boolean.TRUE) == null;
  }

  public void end(PlayerRef p) {
    if (diveMarked.isEmpty() && acceleratedAtTick.isEmpty()) {
      return;
    }

    UUID id = p.uuid();
    boolean hadDiveState = diveMarked.remove(id) != null;
    if (acceleratedAtTick.remove(id) != null) {
      hadDiveState = true;
    }
    if (hadDiveState) {
      removeModifiers(p);
    }
  }

  public void removeModifiers(PlayerRef p) {
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.remove(p, owner.getName(), SLOT_GRAVITY, Attributes.GRAVITY);
    if (attributeExists(Attributes.AIR_DRAG_MODIFIER)) {
      attributes.remove(p, owner.getName(), SLOT_DRAG, Attributes.AIR_DRAG_MODIFIER);
    }
  }

  private void accelerate(PlayerRef p, Pull pull) {
    int currentTick = p.ticksLived();
    Integer previousTick = acceleratedAtTick.put(p.uuid(), currentTick);
    if (!shouldAccelerateAtTick(previousTick, currentTick)) {
      return;
    }

    Vec3 velocity = p.velocity();
    double acceleratedY = acceleratedDownwardVelocity(velocity.y(), pull.acceleration(), pull.terminalFallSpeed());
    if (acceleratedY < velocity.y()) {
      p.setVelocity(new Vec3(velocity.x(), acceleratedY, velocity.z()));
    }
  }

  static double acceleratedDownwardVelocity(double currentY, double acceleration, double terminalFallSpeed) {
    if (!Double.isFinite(currentY) || currentY >= 0D) {
      return currentY;
    }

    double safeAcceleration = Double.isFinite(acceleration) ? Math.min(2D, Math.max(0D, acceleration)) : 0D;
    double safeTerminal = Double.isFinite(terminalFallSpeed) ? Math.min(10D, Math.max(0D, terminalFallSpeed)) : 0D;
    if (safeAcceleration <= 0D || safeTerminal <= 0D || currentY <= -safeTerminal) {
      return currentY;
    }
    return Math.max(-safeTerminal, currentY - safeAcceleration);
  }

  static boolean shouldAccelerateAtTick(Integer previousTick, int currentTick) {
    return previousTick == null || previousTick != currentTick;
  }

  private static boolean attributeExists(Id attribute) {
    return AdaptCore.platform().host().registries().exists(RegistryKind.ATTRIBUTE, attribute);
  }
}
