package art.arcane.adapt.api.golem;

import org.bukkit.entity.Entity;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

final class GolemBrains implements GolemLifecycle {
  private final GolemBrain.Context context;
  private final Function<GolemHandle, Optional<GolemController>> controllers;
  private final Map<UUID, GolemBrain> brains = new ConcurrentHashMap<>();

  GolemBrains(GolemBrain.Context context, Function<GolemHandle, Optional<GolemController>> controllers) {
    this.context = Objects.requireNonNull(context);
    this.controllers = Objects.requireNonNull(controllers);
  }

  @Override
  public void awake(GolemHandle handle) {
    GolemBrain current = brains.get(handle.entityId());
    if (current != null && current.handle() == handle) {
      current.start();
      return;
    }
    Optional<GolemController> controller = controllers.apply(handle);
    if (controller.isEmpty()) {
      return;
    }
    GolemBrain created = new GolemBrain(handle, controller.get(), context);
    GolemBrain previous = brains.put(handle.entityId(), created);
    if (previous != null) {
      previous.detach();
    }
    created.start();
  }

  @Override
  public void asleep(GolemHandle handle) {
    GolemBrain current = brains.get(handle.entityId());
    if (current != null && current.handle() == handle) {
      current.sleep();
    }
  }

  @Override
  public void detached(GolemHandle handle) {
    GolemBrain current = brains.get(handle.entityId());
    if (current != null && current.handle() == handle && brains.remove(handle.entityId(), current)) {
      current.detach();
    }
  }

  Optional<GolemBrain> brain(Entity entity) {
    return entity == null ? Optional.empty() : Optional.ofNullable(brains.get(entity.getUniqueId()));
  }
}
