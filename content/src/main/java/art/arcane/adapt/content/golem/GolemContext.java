package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.content.golem.node.NodeContainers;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.IntConsumer;
import java.util.function.ToIntFunction;

public final class GolemContext {
  private final GolemWorker worker;
  private final Scope scope;
  private Map<Long, Boolean> chestAccess;

  GolemContext(GolemWorker worker, Scope scope) {
    this.worker = Objects.requireNonNull(worker);
    this.scope = Objects.requireNonNull(scope);
  }

  public GolemHandle handle() {
    return worker.handle();
  }

  public MobRef golem() {
    return worker.handle().entity();
  }

  public PlayerRef owner() {
    return worker.owner();
  }

  public WorldRef world() {
    return golem().world();
  }

  public long nowMillis() {
    return scope.nowMillis();
  }

  public Optional<NodeRecord> node() {
    return Optional.ofNullable(scope.record());
  }

  public GolemCoreType core() {
    return scope.core();
  }

  public int actIndex() {
    return worker.actIndex();
  }

  public Optional<EntityRef> targetEntity() {
    GolemTask task = scope.task();
    return task == null ? Optional.empty() : worker.platform().targetEntity(task.target(), world());
  }

  public Optional<EntityRef> ownedEntity(UUID entity) {
    return worker.platform().ownedEntity(entity, world());
  }

  public boolean skips(GolemTask task) {
    return worker.skips(task, scope.nowMillis());
  }

  public Loc home() {
    return worker.home();
  }

  public double workRadius() {
    return worker.workRadius();
  }

  public boolean inWorkRadius(Loc location) {
    WorldRef at = location.world();
    return at != null && worker.inWorkRadius(at.uid(), location.x(), location.y(), location.z());
  }

  public double bonusYield() {
    return worker.runes().bonusYield(scope.core());
  }

  public List<ItemRef> withBonusYield(List<ItemRef> drops) {
    return worker.runes().withBonusYield(scope.core(), drops, ThreadLocalRandom.current()::nextDouble);
  }

  public boolean owns(Loc location) {
    return worker.platform().owns(location);
  }

  public boolean canBreak(Loc block) {
    Adaptation<?> policy = scope.policy();
    PlayerRef owner = owner();
    return policy != null && owner != null && owns(block) && policy.canBlockBreak(owner, block);
  }

  public boolean canPlace(Loc block) {
    Adaptation<?> policy = scope.policy();
    PlayerRef owner = owner();
    return policy != null && owner != null && owns(block) && policy.canBlockPlace(owner, block);
  }

  public boolean canInteract(Loc target) {
    Adaptation<?> policy = scope.policy();
    PlayerRef owner = owner();
    return policy != null && owner != null && owns(target) && policy.canInteract(owner, target);
  }

  public boolean canAccessChest(Loc container) {
    Adaptation<?> policy = scope.policy();
    PlayerRef owner = owner();
    if (policy == null || owner == null || !owns(container)) {
      return false;
    }
    if (chestAccess == null) {
      chestAccess = new HashMap<>(4);
    }
    long key = NodeContainers.blockKey(container.blockX(), container.blockY(), container.blockZ());
    return chestAccess.computeIfAbsent(key, ignored -> policy.canAccessChest(owner, container));
  }

  public boolean canHandle(LivingRef animal) {
    Adaptation<?> policy = scope.policy();
    PlayerRef owner = owner();
    return policy != null && owner != null && owns(animal.location()) && policy.canPVE(owner, animal);
  }

  public boolean canDamage(LivingRef victim) {
    PlayerRef owner = owner();
    return owner != null && owns(victim.location()) && GolemProtection.canDamage(scope.policy(), owner, victim);
  }

  public Optional<InventoryRef> container(BlockRef block) {
    return canAccessChest(block.location()) ? NodeContainers.inventory(block) : Optional.empty();
  }

  public int carried() {
    return worker.cargo().count();
  }

  public int capacity() {
    return worker.capacity();
  }

  public int room() {
    return Math.max(0, capacity() - carried());
  }

  public List<ItemRef> carriedItems() {
    return worker.cargo().items();
  }

  public Optional<ItemRef> heldTool() {
    return worker.heldTool();
  }

  public Optional<ItemRef> toolInUse() {
    return worker.toolInUse();
  }

  public void wearTool() {
    worker.wearTool();
  }

  public Optional<ItemRef> carry(ItemRef stack) {
    return worker.cargo().add(stack, capacity());
  }

  public int take(Predicate<ItemRef> matches, int amount) {
    return worker.cargo().take(matches, amount);
  }

  public int deliver(InventoryRef inventory, Predicate<ItemRef> accepts) {
    return deliverUpTo(inventory, item -> accepts.test(item) ? item.amount() : 0);
  }

  public int deliverUpTo(InventoryRef inventory, ToIntFunction<ItemRef> amount) {
    return deliverUpTo(inventory, amount, delivered -> {
    });
  }

  public int deliverUpTo(InventoryRef inventory, ToIntFunction<ItemRef> amount, IntConsumer stackDelivered) {
    int moved = transfer(inventory, amount, stackDelivered);
    if (moved > 0) {
      worker.handle().addItemsMoved(moved);
      GolemWorkStats.moved(worker.handle(), moved);
    }
    return moved;
  }

  public int putBack(InventoryRef inventory, ToIntFunction<ItemRef> amount) {
    return transfer(inventory, amount, delivered -> {
    });
  }

  public void interact(BlockRef container, int ticks) {
    worker.interact(container, ticks);
  }

  public void visit(BlockRef container) {
    if (owns(container.location())) {
      worker.platform().visitLid(container, ContainerLids.CLOSE_TICKS);
    }
  }

  public void workXp(double xp) {
    GolemWorkXp.pay(worker.handle(), scope.core(), xp);
  }

  public void waiting(GolemWorkStatus reason) {
    worker.status(reason);
  }

  public boolean progress() {
    GolemTask task = scope.task();
    return task != null && worker.renew(task, scope.nowMillis());
  }

  private int transfer(InventoryRef inventory, ToIntFunction<ItemRef> amount, IntConsumer stackDelivered) {
    List<ItemRef> items = worker.cargo().items();
    List<ItemRef> remaining = new ArrayList<>(items.size());
    int moved = 0;
    int next = 0;
    boolean touched = false;
    try {
      while (next < items.size()) {
        ItemRef item = items.get(next);
        int wanted = Math.min(item.amount(), amount.applyAsInt(item));
        if (wanted <= 0) {
          remaining.add(item);
          next++;
          continue;
        }
        next++;
        touched = true;
        ItemRef offered = item.copyWithAmount(wanted);
        if (wanted < item.amount()) {
          remaining.add(item.copyWithAmount(item.amount() - wanted));
        }
        int left = 0;
        for (ItemRef leftover : inventory.addItem(offered)) {
          left += leftover.amount();
          remaining.add(leftover);
        }
        int delivered = wanted - left;
        moved += delivered;
        if (delivered > 0) {
          stackDelivered.accept(delivered);
        }
      }
    } finally {
      if (touched) {
        remaining.addAll(items.subList(next, items.size()));
        worker.cargo().keep(remaining);
      }
    }
    return moved;
  }

  record Scope(Adaptation<?> policy, GolemTask task, NodeRecord record, GolemCoreType core, long nowMillis) {
    Scope {
      Objects.requireNonNull(core);
    }
  }
}
