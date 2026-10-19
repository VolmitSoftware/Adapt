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
  static final NamespacedKey EXTRA_KEY = new NamespacedKey("adapt", "golem_snowball_extra");

  private GolemSnowball() {
  }

  static void tag(Projectile snowball, int level, UUID target, boolean extra) {
    PersistentDataContainer data = snowball.getPersistentDataContainer();
    data.set(KEY, PersistentDataType.INTEGER, level);
    if (target != null) {
      data.set(TARGET_KEY, PersistentDataType.STRING, target.toString());
    }
    if (extra) {
      data.set(EXTRA_KEY, PersistentDataType.BYTE, (byte) 1);
    }
  }

  static Optional<Tag> read(Projectile snowball) {
    PersistentDataContainer data = snowball.getPersistentDataContainer();
    Integer level = data.get(KEY, PersistentDataType.INTEGER);
    if (level == null || level < 1) {
      return Optional.empty();
    }
    return Optional.of(new Tag(level, target(data.get(TARGET_KEY, PersistentDataType.STRING)), data.get(EXTRA_KEY, PersistentDataType.BYTE) != null));
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

  record Tag(int level, UUID target, boolean extra) {
  }
}
