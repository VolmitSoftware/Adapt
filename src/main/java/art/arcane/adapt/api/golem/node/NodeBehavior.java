package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.GolemActor;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTask;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

public interface NodeBehavior extends GolemActor {
  void scan(NodeContext node, ScanBudget budget);

  default Optional<GolemTask> route(GolemContext golem, NodeKey node, List<ItemStack> carried) {
    return Optional.empty();
  }

  default boolean deliversBeforeClaiming() {
    return false;
  }
}
