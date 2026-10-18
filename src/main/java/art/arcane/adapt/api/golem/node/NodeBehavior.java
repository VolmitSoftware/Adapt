package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.GolemActor;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTask;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

public interface NodeBehavior extends GolemActor {
  void scan(NodeContext node, ScanBudget budget);

  /**
   * Plans where a golem takes the items it carries, asked before the default Deposit, home chest or hold route when
   * this node's job is the last job the golem acted on. Return empty to use the default route.
   * <p>
   * A returned task is a private job that never goes on a board. The golem walks to its target and this behavior's
   * {@link #act} runs it there. During that act {@link GolemContext#node()} is this routing node's record, whether or
   * not the task names a node, and {@link GolemContext#core()} is the core that consumes this node type. A FAILED or
   * BLOCKED act backs the task off for 10 seconds and ends the job, and the next route asks this node again. Removing
   * this node, or switching it off by redstone while the golem walks the route, ends the job and sends the items to the
   * default route. A later job of a core with no node, and walking back home, also clear the last worked node.
   */
  default Optional<GolemTask> route(GolemContext golem, NodeKey node, List<ItemStack> carried) {
    return Optional.empty();
  }

  default boolean deliversBeforeClaiming() {
    return false;
  }
}
