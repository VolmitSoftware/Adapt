package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.node.NodeBehavior;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeFilters;
import art.arcane.adapt.api.golem.node.NodeItemTags;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.ScanBudget;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class CollectionNodeBehavior implements NodeBehavior {
  private final Adaptation<?> policy;
  private final GathererWork work;

  CollectionNodeBehavior(Adaptation<?> policy, GathererWork work) {
    this.policy = Objects.requireNonNull(policy);
    this.work = Objects.requireNonNull(work);
  }

  @Override
  public Adaptation<?> policy() {
    return policy;
  }

  @Override
  public void scan(NodeContext node, ScanBudget budget) {
    NodeRecord record = node.record();
    GatherArea.Box box = GatherArea.Box.of(node.key(), record);
    World world = node.world();
    Location center = new Location(world, box.centerX(), box.centerY(), box.centerZ());
    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(center, box.halfX(), box.halfZ())) {
      return;
    }
    UUID owner = node.owner();
    long now = node.nowMillis();
    Map<UUID, Item> collectable = new LinkedHashMap<>();
    for (Item item : PaperCompat.nearbyEntitiesByType(Item.class, center, box.halfX(), box.halfY(), box.halfZ())) {
      Location at = item.getLocation();
      if (box.contains(at.getX(), at.getY(), at.getZ()) && accepts(record, item.getItemStack()) && work.eligible(item, owner, now)) {
        collectable.put(item.getUniqueId(), item);
      }
    }
    for (GolemTask open : node.tasks()) {
      if (!collectable.containsKey(open.target().entity())) {
        node.withdraw(open);
      }
    }
    for (Item item : collectable.values()) {
      if (!budget.hasTaskRoom()) {
        return;
      }
      node.post(node.task(GathererWork.PICKUP, GolemTarget.entity(item)).build());
    }
  }

  @Override
  public GolemActResult act(GolemContext golem, GolemTask task) {
    NodeRecord record = golem.node().orElse(null);
    NodeKey key = task.node().orElse(null);
    Entity target = golem.targetEntity().orElse(null);
    if (record == null || key == null || !GathererWork.PICKUP.equals(task.type()) || !(target instanceof Item item)
        || !accepts(record, item.getItemStack())) {
      return GolemActResult.FAILED;
    }
    return work.pickUp(golem, item, GatherArea.nodes(key.world(), List.of(GatherArea.Box.of(key, record))));
  }

  private static boolean accepts(NodeRecord record, ItemStack stack) {
    return GathererWork.collectable(stack) && NodeFilters.accepts(record, stack.getType(), NodeItemTags.shared());
  }
}
