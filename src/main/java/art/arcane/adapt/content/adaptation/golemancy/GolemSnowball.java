package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.UUID;

final class GolemSnowball {
  static final NamespacedKey KEY = new NamespacedKey("adapt", "golem_snowball");
  static final NamespacedKey TARGET_KEY = new NamespacedKey("adapt", "golem_snowball_target");

  private GolemSnowball() {
  }

  static void tag(Projectile snowball, int level, UUID target) {
    PersistentDataContainer data = snowball.getPersistentDataContainer();
    data.set(KEY, PersistentDataType.INTEGER, level);
    if (target != null) {
      data.set(TARGET_KEY, PersistentDataType.STRING, target.toString());
    }
  }

  static Optional<Tag> read(Projectile snowball) {
    PersistentDataContainer data = snowball.getPersistentDataContainer();
    Integer level = data.get(KEY, PersistentDataType.INTEGER);
    if (level == null || level < 1) {
      return Optional.empty();
    }
    return Optional.of(new Tag(level, target(data.get(TARGET_KEY, PersistentDataType.STRING))));
  }

  private static UUID target(String stored) {
    if (stored == null) {
      return null;
    }
    try {
      return UUID.fromString(stored);
    } catch (IllegalArgumentException malformed) {
      return null;
    }
  }

  record Tag(int level, UUID target) {
  }
}
