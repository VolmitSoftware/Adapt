package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.node.NodeContainers;
import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

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

  public Mob golem() {
    return worker.handle().entity();
  }

  public Player owner() {
    return worker.owner();
  }

  public World world() {
    return golem().getWorld();
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

  public Optional<Entity> targetEntity() {
    GolemTask task = scope.task();
    return task == null ? Optional.empty() : worker.platform().targetEntity(task.target(), world());
  }

  public Optional<Entity> ownedEntity(UUID entity) {
    return worker.platform().ownedEntity(entity, world());
  }

  public boolean skips(GolemTask task) {
    return worker.skips(task, scope.nowMillis());
  }

  public Location home() {
    return worker.home().clone();
  }

  public double workRadius() {
    return worker.workRadius();
  }

  public boolean inWorkRadius(Location location) {
    World at = location.getWorld();
    return at != null && worker.inWorkRadius(at.getUID(), location.getX(), location.getY(), location.getZ());
  }

  public double bonusYield() {
    return worker.runes().bonusYield(scope.core());
  }

  public List<ItemStack> withBonusYield(List<ItemStack> drops) {
    return worker.runes().withBonusYield(scope.core(), drops, ThreadLocalRandom.current()::nextDouble);
  }

  public boolean owns(Location location) {
    return worker.platform().owns(location);
  }

  public boolean canBreak(Location block) {
    Adaptation<?> policy = scope.policy();
    Player owner = owner();
    return policy != null && owner != null && owns(block) && policy.canBlockBreak(owner, block);
  }

  public boolean canPlace(Location block) {
    Adaptation<?> policy = scope.policy();
    Player owner = owner();
    return policy != null && owner != null && owns(block) && policy.canBlockPlace(owner, block);
  }

  public boolean canInteract(Location target) {
    Adaptation<?> policy = scope.policy();
    Player owner = owner();
    return policy != null && owner != null && owns(target) && policy.canInteract(owner, target);
  }

  public boolean canAccessChest(Location container) {
    Adaptation<?> policy = scope.policy();
    Player owner = owner();
    if (policy == null || owner == null || !owns(container)) {
      return false;
    }
    if (chestAccess == null) {
      chestAccess = new HashMap<>(4);
    }
    long key = NodeContainers.blockKey(container.getBlockX(), container.getBlockY(), container.getBlockZ());
    return chestAccess.computeIfAbsent(key, ignored -> policy.canAccessChest(owner, container));
  }

  public boolean canHandle(LivingEntity animal) {
    Adaptation<?> policy = scope.policy();
    Player owner = owner();
    return policy != null && owner != null && owns(animal.getLocation()) && policy.canPVE(owner, animal);
  }

  public boolean canDamage(LivingEntity victim) {
    Player owner = owner();
    return owner != null && owns(victim.getLocation()) && GolemProtection.canDamage(scope.policy(), owner, victim);
  }

  public Optional<Inventory> container(Block block) {
    return canAccessChest(block.getLocation()) ? NodeContainers.inventory(block) : Optional.empty();
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

  public List<ItemStack> carriedItems() {
    return worker.cargo().items();
  }

  public Optional<ItemStack> heldTool() {
    return worker.heldTool();
  }

  public Optional<ItemStack> toolInUse() {
    return worker.toolInUse();
  }

  public void wearTool() {
    worker.wearTool();
  }

  public Optional<ItemStack> carry(ItemStack stack) {
    return worker.cargo().add(stack, capacity());
  }

  public int take(Predicate<ItemStack> matches, int amount) {
    return worker.cargo().take(matches, amount);
  }

  public int deliver(Inventory inventory, Predicate<ItemStack> accepts) {
    return deliverUpTo(inventory, item -> accepts.test(item) ? item.getAmount() : 0);
  }

  public int deliverUpTo(Inventory inventory, ToIntFunction<ItemStack> amount) {
    return deliverUpTo(inventory, amount, delivered -> {
    });
  }

  public int deliverUpTo(Inventory inventory, ToIntFunction<ItemStack> amount, IntConsumer stackDelivered) {
    int moved = transfer(inventory, amount, stackDelivered);
    if (moved > 0) {
      worker.handle().addItemsMoved(moved);
      GolemWorkStats.moved(worker.handle(), moved);
    }
    return moved;
  }

  public int putBack(Inventory inventory, ToIntFunction<ItemStack> amount) {
    return transfer(inventory, amount, delivered -> {
    });
  }

  public void interact(Block container, int ticks) {
    worker.interact(container, ticks);
  }

  public void visit(Block container) {
    if (owns(container.getLocation())) {
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

  private int transfer(Inventory inventory, ToIntFunction<ItemStack> amount, IntConsumer stackDelivered) {
    List<ItemStack> items = worker.cargo().items();
    List<ItemStack> remaining = new ArrayList<>(items.size());
    int moved = 0;
    int next = 0;
    boolean touched = false;
    try {
      while (next < items.size()) {
        ItemStack item = items.get(next);
        int wanted = Math.min(item.getAmount(), amount.applyAsInt(item));
        if (wanted <= 0) {
          remaining.add(item);
          next++;
          continue;
        }
        next++;
        touched = true;
        ItemStack offered = item.clone();
        offered.setAmount(wanted);
        if (wanted < item.getAmount()) {
          ItemStack kept = item.clone();
          kept.setAmount(item.getAmount() - wanted);
          remaining.add(kept);
        }
        int left = 0;
        for (ItemStack leftover : inventory.addItem(offered).values()) {
          left += leftover.getAmount();
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
