package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemRegistry;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

final class AnimusBindingRules {
  static final double MIN_RITUAL_SECONDS = 0.5D;
  static final double MAX_RITUAL_SECONDS = 30D;
  private static final int TICKS_PER_SECOND = 20;

  private AnimusBindingRules() {
  }

  static <T> T atLevel(List<T> values, int level, T fallback) {
    if (values == null || values.isEmpty() || level < 1) {
      return fallback;
    }
    T value = values.get(Math.min(level, values.size()) - 1);
    return value == null ? fallback : value;
  }

  static int golemCap(List<Integer> capByLevel, int level, int hardCap) {
    return Math.max(0, Math.min(atLevel(capByLevel, level, 0), hardCap));
  }

  static int tithe(List<Integer> titheByLevel, int level) {
    return Math.max(0, atLevel(titheByLevel, level, 0));
  }

  static int ritualTicks(List<Double> secondsByLevel, int level) {
    double seconds = atLevel(secondsByLevel, level, 0D);
    return Double.isFinite(seconds) ? Math.max(1, (int) Math.round(seconds * TICKS_PER_SECOND)) : 1;
  }

  static List<Double> clampRitualSeconds(List<Double> secondsByLevel) {
    List<Double> clamped = new ArrayList<>(secondsByLevel == null ? 0 : secondsByLevel.size());
    if (secondsByLevel == null) {
      return clamped;
    }
    for (Double seconds : secondsByLevel) {
      clamped.add(seconds == null || seconds.isNaN() ? MIN_RITUAL_SECONDS : Math.max(MIN_RITUAL_SECONDS, Math.min(MAX_RITUAL_SECONDS, seconds)));
    }
    return clamped;
  }

  static boolean interrupts(DamageCause cause, boolean byEntity) {
    return byEntity || switch (cause) {
      case FIRE, LAVA, BLOCK_EXPLOSION, ENTITY_EXPLOSION -> true;
      default -> false;
    };
  }

  static boolean bodyUnlocked(GolemBody body, int copperFrameLevel, int ironFrameLevel) {
    return switch (body) {
      case FROST -> true;
      case COPPER -> copperFrameLevel >= 1;
      case IRON -> ironFrameLevel >= 1;
      case BONE -> false;
    };
  }

  static boolean builderExclusive(UUID builder, OptionalLong builtAt, UUID player, long nowMillis, long windowMillis) {
    if (builder == null || builder.equals(player) || builtAt.isEmpty()) {
      return false;
    }
    return nowMillis - builtAt.getAsLong() < windowMillis;
  }

  static BindingVerdict verdict(BindingCheck check) {
    GolemRegistry.BindBlock block = check.registryBlock();
    if (block == GolemRegistry.BindBlock.ALREADY_BOUND) {
      return BindingVerdict.ALREADY_BOUND;
    }
    if (!check.playerCreated() && !check.allowVillageGolems()) {
      return BindingVerdict.VILLAGE_GOLEM;
    }
    if (check.builderExclusive()) {
      return BindingVerdict.NOT_BUILDER;
    }
    if (!check.bodyUnlocked() || block == GolemRegistry.BindBlock.BODY_MISMATCH) {
      return BindingVerdict.BODY_LOCKED;
    }
    if (block == GolemRegistry.BindBlock.CORE_NOT_ALLOWED) {
      return BindingVerdict.CORE_NOT_ALLOWED;
    }
    if (!check.coreUnlocked()) {
      return BindingVerdict.CORE_LOCKED;
    }
    if (!check.regionAllows()) {
      return BindingVerdict.PROTECTED;
    }
    if (block == GolemRegistry.BindBlock.OWNER_DATA_UNAVAILABLE) {
      return BindingVerdict.UNAVAILABLE;
    }
    if (check.ownedGolems() >= check.golemCap() || block == GolemRegistry.BindBlock.PLAYER_CAP) {
      return BindingVerdict.GOLEM_CAP;
    }
    if (block == GolemRegistry.BindBlock.CHUNK_CAP) {
      return BindingVerdict.CHUNK_CAP;
    }
    if (check.playerLevel() < check.tithe()) {
      return BindingVerdict.NOT_ENOUGH_LEVELS;
    }
    return BindingVerdict.READY;
  }
}
