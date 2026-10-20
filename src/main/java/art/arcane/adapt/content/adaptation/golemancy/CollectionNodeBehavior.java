package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.node.NodeBehavior;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.ScanBudget;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

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
    if (!node.queryDue()) {
      return;
    }
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
    Predicate<Material> accepts = box::accepts;
    for (Item item : PaperCompat.nearbyEntitiesByType(Item.class, center, box.halfX(), box.halfY(), box.halfZ())) {
      Location at = item.getLocation();
      ItemStack stack = item.getItemStack();
      if (box.contains(at.getX(), at.getY(), at.getZ()) && GathererWork.collectable(stack) && ReactSuperStacks.accepts(stack, accepts)
          && work.eligible(item, owner, now)) {
        collectable.put(item.getUniqueId(), item);
      }
    }
    if (!collectable.isEmpty() || covered(world, box)) {
      node.queryResult(!collectable.isEmpty());
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
    if (record == null || key == null || !GathererWork.PICKUP.equals(task.type()) || !(target instanceof Item item)) {
      return GolemActResult.FAILED;
    }
    return work.pickUp(golem, item, GatherArea.nodes(key.world(), List.of(GatherArea.Box.of(key, record))));
  }

  private static boolean covered(World world, GatherArea.Box box) {
    int minX = (int) Math.floor(box.centerX() - box.halfX()) >> 4;
    int maxX = (int) Math.ceil(box.centerX() + box.halfX()) - 1 >> 4;
    int minZ = (int) Math.floor(box.centerZ() - box.halfZ()) >> 4;
    int maxZ = (int) Math.ceil(box.centerZ() + box.halfZ()) - 1 >> 4;
    for (int chunkX = minX; chunkX <= maxX; chunkX++) {
      for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          return false;
        }
      }
    }
    return true;
  }
}
