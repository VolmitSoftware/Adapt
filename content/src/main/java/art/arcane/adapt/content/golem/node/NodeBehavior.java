package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.content.golem.GolemActor;
import art.arcane.adapt.content.golem.GolemContext;
import art.arcane.adapt.content.golem.GolemTask;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.List;
import java.util.Optional;

public interface NodeBehavior extends GolemActor {
  void scan(NodeContext node, ScanBudget budget);

  default Optional<GolemTask> route(GolemContext golem, NodeKey node, List<ItemRef> carried) {
    return Optional.empty();
  }

  default boolean deliversBeforeClaiming() {
    return false;
  }
}
