package art.arcane.adapt.api.projectile;

import art.arcane.adapt.platform.entity.ProjectileRef;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProjectileReplacementRegistry {
  private static final ConcurrentHashMap<UUID, Claim> claims = new ConcurrentHashMap<>();

  private ProjectileReplacementRegistry() {
  }

  public static void register(ProjectileRef projectile, Claim claim) {
    claims.put(projectile.uuid(), claim);
  }

  public static void unregister(UUID projectileId) {
    claims.remove(projectileId);
  }

  public static Ticket begin(ProjectileRef source) {
    Claim claim = claims.remove(source.uuid());
    return claim == null ? null : claim.begin(source);
  }

  public static void clear() {
    claims.clear();
  }

  @FunctionalInterface
  public interface Claim {
    Ticket begin(ProjectileRef source);
  }

  public interface Ticket {
    boolean complete(ProjectileRef replacement);

    void cancel();
  }
}
