package art.arcane.adapt.util.data;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Id;

/** The shared {@code volmit:no-stack} entity flag that tells mob stacker plugins to leave an entity alone. */
public final class StackExclusion {
  private static final DataKey<Byte> KEY = DataKey.of(Id.of("volmit", "no-stack"), DataCodecs.BYTE);
  private static final byte FLAG = (byte) 1;

  private StackExclusion() {
  }

  public static void exclude(EntityRef entity) {
    if (entity != null) {
      entity.data().set(KEY, FLAG);
    }
  }

  public static void include(EntityRef entity) {
    if (entity != null) {
      entity.data().remove(KEY);
    }
  }

  public static boolean isExcluded(EntityRef entity) {
    return entity != null && entity.data().has(KEY);
  }
}
