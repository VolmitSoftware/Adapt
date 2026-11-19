package art.arcane.adapt.bukkit.event;

import org.bukkit.event.entity.ProjectileHitEvent;

/** Where a projectile stands while its hit is posted: on 26.x it has already moved to the contact point. */
final class ProjectileContact {
  private ProjectileContact() {
  }

  static void post(ProjectileHitEvent event, Runnable post) {
    post.run();
  }
}
