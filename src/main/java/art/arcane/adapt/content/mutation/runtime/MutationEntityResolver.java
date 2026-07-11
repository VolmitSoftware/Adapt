package art.arcane.adapt.content.mutation.runtime;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

final class MutationEntityResolver {
  MutationEntityResolver() {
  }

  Player playerSource(Entity entity) {
    return entity instanceof Player player ? player : null;
  }

  UUID projectilePlayerSourceId(Entity entity) {
    if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
      return player.getUniqueId();
    }
    return null;
  }

  UUID packOwnerId(Entity entity) {
    if (entity instanceof Player player) {
      return player.getUniqueId();
    }
    if (entity instanceof Projectile projectile) {
      ProjectileSource shooter = projectile.getShooter();
      if (shooter instanceof Player player) {
        return player.getUniqueId();
      }
      if (shooter instanceof Tameable tameable) {
        return tameable.getOwnerUniqueId();
      }
    }
    if (entity instanceof Tameable tameable) {
      return tameable.getOwnerUniqueId();
    }
    return null;
  }

  Entity sourceEntity(Entity entity) {
    return entity;
  }

  UUID packContributorId(Entity entity) {
    if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
      return shooter.getUniqueId();
    }
    return entity == null ? null : entity.getUniqueId();
  }

  boolean packOwnedBy(Entity entity, UUID ownerId) {
    if (entity == null || ownerId == null) {
      return false;
    }
    if (entity instanceof Tameable tameable) {
      return ownerId.equals(tameable.getOwnerUniqueId());
    }
    if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Tameable tameable) {
      return ownerId.equals(tameable.getOwnerUniqueId());
    }
    return false;
  }
}
