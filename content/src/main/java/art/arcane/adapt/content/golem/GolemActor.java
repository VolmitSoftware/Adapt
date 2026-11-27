package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.content.Adaptation;


public interface GolemActor {
  int DEFAULT_ACT_INTERVAL_TICKS = 20;

  Adaptation<?> policy();

  GolemActResult act(GolemContext golem, GolemTask task);

  default int actIntervalTicks(GolemContext golem, GolemTask task) {
    return DEFAULT_ACT_INTERVAL_TICKS;
  }

  default GolemToolPlan tools(GolemContext golem, GolemTask task) {
    return GolemToolPlan.NONE;
  }
}
