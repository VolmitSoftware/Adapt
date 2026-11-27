package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.content.golem.GolemActResult;
import art.arcane.adapt.content.golem.GolemContext;
import art.arcane.adapt.content.golem.GolemTarget;
import art.arcane.adapt.content.golem.GolemTask;
import art.arcane.adapt.content.golem.node.NodeBehavior;
import art.arcane.adapt.content.golem.node.NodeContext;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.ScanBudget;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;

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
    WorldRef world = node.world();
    Loc center = new Loc(world, box.centerX(), box.centerY(), box.centerZ(), 0F, 0F);
    if (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().ownsArea(center, box.halfX(), box.halfZ())) {
      return;
    }
    UUID owner = node.owner();
    long now = node.nowMillis();
    Map<UUID, ItemEntityRef> collectable = new LinkedHashMap<>();
    Predicate<Id> accepts = box::accepts;
    Box area = Box.around(new Vec3(box.centerX(), box.centerY(), box.centerZ()), box.halfX(), box.halfY(), box.halfZ());
    for (EntityRef nearby : world.entitiesIn(area, entity -> entity instanceof ItemEntityRef)) {
      ItemEntityRef item = (ItemEntityRef) nearby;
      Loc at = item.location();
      ItemRef stack = item.item();
      if (box.contains(at.x(), at.y(), at.z()) && GathererWork.collectable(stack) && ReactSuperStacks.accepts(stack, accepts)
          && work.eligible(item, owner, now)) {
        collectable.put(item.uuid(), item);
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
    for (ItemEntityRef item : collectable.values()) {
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
    EntityRef target = golem.targetEntity().orElse(null);
    if (record == null || key == null || !GathererWork.PICKUP.equals(task.type()) || !(target instanceof ItemEntityRef item)) {
      return GolemActResult.FAILED;
    }
    return work.pickUp(golem, item, GatherArea.nodes(key.world(), List.of(GatherArea.Box.of(key, record))));
  }

  private static boolean covered(WorldRef world, GatherArea.Box box) {
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
