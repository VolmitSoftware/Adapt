package art.arcane.adapt.api.projectile;

import org.bukkit.entity.Projectile;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProjectileReplacementRegistry {
  private static final ConcurrentHashMap<UUID, Claim> claims = new ConcurrentHashMap<>();

  private ProjectileReplacementRegistry() {
  }

  public static void register(Projectile projectile, Claim claim) {
    claims.put(projectile.getUniqueId(), claim);
  }

  public static void unregister(UUID projectileId) {
    claims.remove(projectileId);
  }

  public static Ticket begin(Projectile source) {
    Claim claim = claims.remove(source.getUniqueId());
    return claim == null ? null : claim.begin(source);
  }

  public static void clear() {
    claims.clear();
  }

  @FunctionalInterface
  public interface Claim {
    Ticket begin(Projectile source);
  }

  public interface Ticket {
    boolean complete(Projectile replacement);

    void cancel();
  }
}
