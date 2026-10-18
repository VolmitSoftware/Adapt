package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTask;
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

  public static boolean active(NodeRecord record, Block anchor) {
    if (record.redstone() == RedstoneMode.IGNORE) {
      return true;
    }
    return NodeRules.active(record.redstone(), anchor.isBlockPowered() || anchor.isBlockIndirectlyPowered());
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
  public Result act(GolemContext golem, GolemTask task) {
    NodeRecord record = golem.node().orElse(null);
    if (record == null) {
      return Result.FAILED;
    }
    NodeKey key = task.node();
    Block anchor = golem.world().getBlockAt(key.x(), key.y(), key.z());
    if (!active(record, anchor)) {
      return Result.FAILED;
    }
    if (!golem.canAccessChest(anchor.getLocation())) {
      return Result.BLOCKED;
    }
    Optional<Inventory> inventory = golem.container(anchor);
    if (inventory.isEmpty()) {
      return Result.FAILED;
    }
    return golem.deliver(inventory.get(), item -> accepts(record, item)) > 0 ? Result.DONE : Result.FAILED;
  }
}
