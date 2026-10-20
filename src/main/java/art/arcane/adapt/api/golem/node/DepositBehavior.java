package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTask;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;

public final class DepositBehavior implements NodeBehavior {
  private final Adaptation<?> policy;

  public DepositBehavior(Adaptation<?> policy) {
    this.policy = Objects.requireNonNull(policy);
  }

  public static boolean accepts(NodeRecord record, ItemStack item) {
    return NodeFilters.accepts(record, item.getType(), NodeItemTags.shared());
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
    NodeRecord record = golem.node().orElse(null);
    NodeKey key = task.node().orElse(null);
    if (record == null || key == null) {
      return GolemActResult.FAILED;
    }
    Block anchor = golem.world().getBlockAt(key.x(), key.y(), key.z());
    if (NodeRules.lacksContainer(record.type(), anchor.getType()) || !NodeRules.active(record, anchor)) {
      return GolemActResult.FAILED;
    }
    if (!golem.canAccessChest(anchor.getLocation())) {
      return GolemActResult.BLOCKED;
    }
    Optional<Inventory> inventory = golem.container(anchor);
    if (inventory.isEmpty()) {
      return GolemActResult.FAILED;
    }
    Material container = anchor.getType();
    if (golem.deliver(inventory.get(), item -> !NodeContainers.nests(container, item) && accepts(record, item)) <= 0) {
      return GolemActResult.FAILED;
    }
    golem.visit(anchor);
    return GolemActResult.DONE;
  }
}
