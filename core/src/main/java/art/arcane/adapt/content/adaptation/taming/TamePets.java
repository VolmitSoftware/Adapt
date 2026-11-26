package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** A player's living tamed pets around them, and the leap a pet makes toward a point. */
public final class TamePets {
  private TamePets() {
  }

  /** A living, tamed pet whose owner is {@code ownerId}. */
  public static boolean isOwnedPet(EntityRef entity, UUID ownerId) {
    return entity instanceof LivingRef living && entity.has(EntityTrait.TAMEABLE) && living.isValid() && !living.isDead()
        && ownerId.equals(entity.tameOwner()) && AdaptCore.platform().host().animals().isTamed(entity);
  }

  /** Every pet of {@code owner} within {@code radius}. */
  public static List<LivingRef> near(PlayerRef owner, double radius) {
    List<LivingRef> pets = new ArrayList<>();
    for (EntityRef entity : owner.nearbyEntities(radius, radius, radius)) {
      if (isOwnedPet(entity, owner.uuid())) {
        pets.add((LivingRef) entity);
      }
    }
    return pets;
  }

  /** The pet of {@code owner} within {@code radius} nearest to them that {@code eligible} accepts, or null. */
  public static @Nullable LivingRef nearest(PlayerRef owner, double radius, Predicate<LivingRef> eligible) {
    LivingRef nearest = null;
    double nearestSquared = Double.MAX_VALUE;
    Loc origin = owner.location();
    for (EntityRef entity : owner.nearbyEntities(radius, radius, radius)) {
      if (!isOwnedPet(entity, owner.uuid()) || !eligible.test((LivingRef) entity)) {
        continue;
      }
      double distanceSquared = entity.location().distanceSquared(origin);
      if (distanceSquared < nearestSquared) {
        nearestSquared = distanceSquared;
        nearest = (LivingRef) entity;
      }
    }
    return nearest;
  }

  /** Throws {@code pet} toward {@code toward} at {@code strength}, always with some lift; call on the pet's thread. */
  public static void leap(LivingRef pet, Loc toward, double strength) {
    if (!pet.location().sameWorld(toward) || strength <= 0) {
      return;
    }
    Vec3 leap = toward.position().subtract(pet.location().position());
    if (leap.lengthSquared() > 1.0E-4) {
      Vec3 scaled = leap.normalize().multiply(strength);
      pet.setVelocity(scaled.withY(Math.max(0.25, scaled.y())));
    }
  }
}
