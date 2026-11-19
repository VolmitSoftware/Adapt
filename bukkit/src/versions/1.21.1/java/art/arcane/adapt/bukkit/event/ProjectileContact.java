package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.natives.ServerNatives;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Before 1.21.2 a projectile handles a block hit before it moves, so the hit is posted from where the tick began. Core
 * expects the 26.x order (moved to the contact point first): for block hits the projectile stands at the contact point
 * while the event posts and goes back afterwards unless a handler moved it.
 */
final class ProjectileContact {
  /** Puts a projectile at a position in place, keeping its velocity; false when it cannot. */
  interface Placer {
    boolean place(Projectile projectile, Vector position);
  }

  private static final Placer NATIVE = (Projectile projectile, Vector position) ->
      ServerNatives.place(projectile, position.getX(), position.getY(), position.getZ());

  private ProjectileContact() {
  }

  static void post(ProjectileHitEvent event, Runnable post) {
    post(event, post, NATIVE);
  }

  static void post(ProjectileHitEvent event, Runnable post, Placer placer) {
    Projectile projectile = event.getEntity();
    Location start = projectile.getLocation();
    Vector contact = event.getHitBlock() == null ? null : contact(projectile, start);
    if (contact == null || !placer.place(projectile, contact)) {
      post.run();
      return;
    }
    post.run();
    if (projectile.isValid() && projectile.getLocation().toVector().distanceSquared(contact) < 1.0E-8) {
      placer.place(projectile, start.toVector());
    }
  }

  private static Vector contact(Projectile projectile, Location start) {
    Vector velocity = projectile.getVelocity();
    double distance = velocity.length();
    if (distance < 1.0E-6 || start.getWorld() == null) {
      return null;
    }
    RayTraceResult hit = start.getWorld().rayTraceBlocks(start, velocity.clone().normalize(), distance,
        FluidCollisionMode.NEVER, true);
    return hit == null ? null : hit.getHitPosition();
  }
}
