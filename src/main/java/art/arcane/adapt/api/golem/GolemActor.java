package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.adaptation.Adaptation;

public interface GolemActor {
  int DEFAULT_ACT_INTERVAL_TICKS = 20;

  Adaptation<?> policy();

  GolemActResult act(GolemContext golem, GolemTask task);

  default int actIntervalTicks(GolemContext golem, GolemTask task) {
    return DEFAULT_ACT_INTERVAL_TICKS;
  }
}
