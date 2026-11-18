package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.entity.PlayerRef;

@FunctionalInterface
public interface GolemBindListener {
  void bound(PlayerRef owner, GolemHandle handle);
}
