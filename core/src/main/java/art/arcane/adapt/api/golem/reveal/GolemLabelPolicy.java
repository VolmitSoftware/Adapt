package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.platform.entity.PlayerRef;

public interface GolemLabelPolicy {
  boolean hidesLabels(PlayerRef owner);
}
