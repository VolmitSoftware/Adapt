package art.arcane.adapt.api.adaptation;

import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class AdaptationDamageTargets {
  private AdaptationDamageTargets() {
  }

  public static boolean allows(Entity target, boolean ignorePassiveMobs) {
    if (!ignorePassiveMobs || target instanceof Player) {
      return true;
    }
    if (!(target instanceof Enemy)) {
      return false;
    }
    return switch (target.getType()) {
      case ENDERMAN, PIGLIN, ZOMBIFIED_PIGLIN, SPIDER, CAVE_SPIDER -> false;
      default -> true;
    };
  }
}
