package art.arcane.adapt.api.golem;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class GolemCoreBehaviors {
  private static final Map<GolemCoreType, GolemCoreBehavior> BEHAVIORS = new ConcurrentHashMap<>();

  private GolemCoreBehaviors() {
  }

  public static void register(GolemCoreType core, GolemCoreBehavior behavior) {
    BEHAVIORS.put(Objects.requireNonNull(core), Objects.requireNonNull(behavior));
  }

  public static void unregister(GolemCoreType core, GolemCoreBehavior behavior) {
    BEHAVIORS.remove(core, behavior);
  }

  public static Optional<GolemCoreBehavior> of(GolemCoreType core) {
    return Optional.ofNullable(BEHAVIORS.get(core));
  }
}
