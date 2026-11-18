package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Optional;
import java.util.UUID;

final class GolemSnowball {
  static final DataKey<Integer> KEY = DataKey.of(Id.of("adapt", "golem_snowball"), DataCodecs.INT);
  static final DataKey<String> TARGET_KEY = DataKey.of(Id.of("adapt", "golem_snowball_target"), DataCodecs.STRING);
  static final DataKey<Byte> EXTRA_KEY = DataKey.of(Id.of("adapt", "golem_snowball_extra"), DataCodecs.BYTE);

  private GolemSnowball() {
  }

  static void tag(ProjectileRef snowball, int level, UUID target, boolean extra) {
    DataHolder data = snowball.data();
    data.set(KEY, level);
    if (target != null) {
      data.set(TARGET_KEY, target.toString());
    }
    if (extra) {
      data.set(EXTRA_KEY, (byte) 1);
    }
  }

  static Optional<Tag> read(ProjectileRef snowball) {
    DataHolder data = snowball.data();
    Integer level = data.get(KEY);
    if (level == null || level < 1) {
      return Optional.empty();
    }
    return Optional.of(new Tag(level, target(data.get(TARGET_KEY)), data.has(EXTRA_KEY)));
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
