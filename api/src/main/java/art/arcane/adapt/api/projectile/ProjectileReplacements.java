package art.arcane.adapt.api.projectile;

import art.arcane.adapt.platform.entity.ProjectileRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hand-off of an ability's own state from a projectile to the projectile that replaces it (a ricochet or a split
 * spawns a new entity). The owner of a projectile registers a claim; whoever spawns the replacement begins the claim
 * on the old projectile and completes the ticket with the new one, or cancels it. Each claim is used at most once.
 */
@ApiStatus.Experimental
public final class ProjectileReplacements {
  private static final ConcurrentHashMap<UUID, Claim> claims = new ConcurrentHashMap<>();

  private ProjectileReplacements() {
  }

  /** Registers the claim for {@code projectile}, replacing an earlier one. */
  public static void register(ProjectileRef projectile, Claim claim) {
    claims.put(projectile.uuid(), claim);
  }

  /** Drops the claim of a projectile that is gone. */
  public static void unregister(UUID projectileId) {
    claims.remove(projectileId);
  }

  /** Takes the claim of {@code source} and begins it; null when the projectile has no claim or its owner declined. */
  public static @Nullable Ticket begin(ProjectileRef source) {
    Claim claim = claims.remove(source.uuid());
    return claim == null ? null : claim.begin(source);
  }

  /** Drops every claim, when the plugin shuts down. */
  public static void clear() {
    claims.clear();
  }

  /** What the owner of a projectile does when a replacement for it is about to spawn. */
  @FunctionalInterface
  public interface Claim {
    /** A ticket for the replacement of {@code source}, or null to keep the state with the old projectile. */
    @Nullable Ticket begin(ProjectileRef source);
  }

  /** One pending hand-off. Exactly one of the two methods is called. */
  public interface Ticket {
    /** Moves the state onto {@code replacement}; false when the owner refused it. */
    boolean complete(ProjectileRef replacement);

    /** The replacement did not spawn; the state stays with the old projectile. */
    void cancel();
  }
}
