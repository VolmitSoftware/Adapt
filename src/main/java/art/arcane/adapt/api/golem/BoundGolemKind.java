package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.minion.MinionKind;
import org.bukkit.entity.Entity;

import java.util.Optional;
import java.util.UUID;

final class BoundGolemKind implements MinionKind {
  @Override
  public boolean matches(Entity entity) {
    return GolemState.has(entity);
  }

  @Override
  public UUID ownerOf(Entity entity) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    Optional<UUID> owner = registry.isPresent() ? registry.get().ownerOf(entity) : GolemState.read(entity).map(GolemState::owner);
    return owner.orElse(null);
  }
}
