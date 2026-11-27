package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.UUID;

/** Projectile kinds shared by the ranged skill and the per-projectile state its adaptations keep. */
public final class RangedProjectiles {
  public static final Id ARROW = Id.minecraft("arrow");
  public static final Id SPECTRAL_ARROW = Id.minecraft("spectral_arrow");
  public static final Id TRIDENT = Id.minecraft("trident");
  public static final Id SNOWBALL = Id.minecraft("snowball");
  public static final Id EGG = Id.minecraft("egg");

  private RangedProjectiles() {
  }

  /** Arrows, spectral arrows and tridents (Bukkit's AbstractArrow). */
  public static boolean isArrowLike(ProjectileRef projectile) {
    Id type = projectile.type();
    return type.equals(ARROW) || type.equals(SPECTRAL_ARROW) || type.equals(TRIDENT);
  }

  /**
   * Drops the state kept for a projectile once it leaves the world for good, one tick later so listeners of the
   * event that removed it still read it; unloads keep it, as plugin metadata did.
   */
  public static void forgetRemoved(EntityRef entity, EntityRemoveEvent.Cause cause) {
    if (entity instanceof ProjectileRef
        && cause != EntityRemoveEvent.Cause.UNLOAD
        && cause != EntityRemoveEvent.Cause.CHANGED_DIMENSION) {
      UUID entityId = entity.uuid();
      AdaptApi.scheduler().runGlobal(() -> ProjectileMemo.forgetAll(entityId), 1);
    }
  }
}
