package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.content.golem.GolemActResult;
import art.arcane.adapt.content.golem.GolemContext;
import art.arcane.adapt.content.golem.GolemTask;
import art.arcane.adapt.content.golem.node.NodeBehavior;
import art.arcane.adapt.content.golem.node.NodeContainers;
import art.arcane.adapt.content.golem.node.NodeContext;
import art.arcane.adapt.content.golem.node.NodeStatus;
import art.arcane.adapt.content.golem.node.ScanBudget;

import java.util.Objects;

final class FilterNodeBehavior implements NodeBehavior {
  private final Adaptation<?> policy;

  FilterNodeBehavior(Adaptation<?> policy) {
    this.policy = Objects.requireNonNull(policy);
  }

  @Override
  public Adaptation<?> policy() {
    return policy;
  }

  @Override
  public void scan(NodeContext node, ScanBudget budget) {
    node.container(node.anchor()).filter(NodeContainers::full).ifPresent(inventory -> node.status(NodeStatus.FULL));
  }

  @Override
  public GolemActResult act(GolemContext golem, GolemTask task) {
    return GolemActResult.FAILED;
  }
}
