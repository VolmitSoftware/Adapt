package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.ability.MinionKind;
import art.arcane.adapt.api.service.ServiceRegistration;
import art.arcane.adapt.platform.entity.EntityRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** The minion kinds other plugins registered. Kept on the API side, so they survive core restarts. */
@ApiStatus.Internal
public final class MinionKinds {
  private static final List<MinionKind> KINDS = new CopyOnWriteArrayList<>();

  private MinionKinds() {
  }

  public static ServiceRegistration register(MinionKind kind) {
    MinionKind registered = Objects.requireNonNull(kind, "kind");
    KINDS.add(registered);
    return () -> KINDS.remove(registered);
  }

  /** The first registered kind that claims {@code entity}, or null. */
  public static @Nullable MinionKind kindOf(@Nullable EntityRef entity) {
    if (entity == null) {
      return null;
    }
    for (MinionKind kind : KINDS) {
      if (kind.matches(entity)) {
        return kind;
      }
    }
    return null;
  }

  public static @Nullable UUID ownerOf(@Nullable EntityRef entity) {
    MinionKind kind = kindOf(entity);
    return kind == null ? null : kind.ownerOf(entity);
  }
}
