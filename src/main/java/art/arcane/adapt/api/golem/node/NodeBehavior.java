package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTask;

public interface NodeBehavior {
  int DEFAULT_ACT_INTERVAL_TICKS = 20;

  Adaptation<?> policy();

  void scan(NodeContext node, ScanBudget budget);

  Result act(GolemContext golem, GolemTask task);

  default int actIntervalTicks(GolemContext golem, GolemTask task) {
    return DEFAULT_ACT_INTERVAL_TICKS;
  }

  enum Result {
    WORKING,
    DONE,
    FAILED,
    BLOCKED
  }
}
