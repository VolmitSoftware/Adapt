package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.ability.MinionKind;
import art.arcane.adapt.platform.entity.EntityRef;

import java.util.Optional;
import java.util.UUID;

final class BoundGolemKind implements MinionKind {
  @Override
  public boolean matches(EntityRef entity) {
    return GolemState.has(entity);
  }

  @Override
  public UUID ownerOf(EntityRef entity) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    Optional<UUID> owner = registry.isPresent() ? registry.get().ownerOf(entity) : GolemState.read(entity).map(GolemState::owner);
    return owner.orElse(null);
  }
}
