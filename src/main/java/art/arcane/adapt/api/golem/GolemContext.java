package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.node.NodeContainers;
import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
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
import java.util.function.Predicate;

public final class GolemContext {
  private final GolemWorker worker;
  private final Adaptation<?> policy;
  private final GolemTask task;
  private final NodeRecord record;
  private final long nowMillis;
  private Map<Long, Boolean> chestAccess;
  private int capacity = -1;

  GolemContext(GolemWorker worker, Adaptation<?> policy, GolemTask task, NodeRecord record, long nowMillis) {
    this.worker = Objects.requireNonNull(worker);
    this.policy = policy;
    this.task = task;
    this.record = record;
    this.nowMillis = nowMillis;
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
    return nowMillis;
  }

  public Optional<NodeRecord> node() {
    return Optional.ofNullable(record);
  }

  public GolemCoreType core() {
    GolemHandle handle = worker.handle();
    if (task == null) {
      return handle.core();
    }
    return GolemTaskMatch.consumer(task.nodeType(), handle.state().cores()).orElse(handle.core());
  }

  public boolean owns(Location location) {
    return worker.platform().owns(location);
  }

  public boolean canBreak(Location block) {
    Player owner = owner();
    return policy != null && owner != null && owns(block) && policy.canBlockBreak(owner, block);
  }

  public boolean canPlace(Location block) {
    Player owner = owner();
    return policy != null && owner != null && owns(block) && policy.canBlockPlace(owner, block);
  }

  public boolean canInteract(Location target) {
    Player owner = owner();
    return policy != null && owner != null && owns(target) && policy.canInteract(owner, target);
  }

  public boolean canAccessChest(Location container) {
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

  public boolean canDamage(LivingEntity victim) {
    Player owner = owner();
    return owner != null && owns(victim.getLocation()) && GolemProtection.canDamage(policy, owner, victim);
  }

  public Optional<Inventory> container(Block block) {
    return canAccessChest(block.getLocation()) ? NodeContainers.inventory(block) : Optional.empty();
  }

  public int carried() {
    return worker.cargo().count();
  }

  public int capacity() {
    if (capacity < 0) {
      capacity = GolemCapacity.carry(worker.handle());
    }
    return capacity;
  }

  public int room() {
    return Math.max(0, capacity() - carried());
  }

  public List<ItemStack> carriedItems() {
    return worker.cargo().items();
  }

  public Optional<ItemStack> carry(ItemStack stack) {
    return worker.cargo().add(stack, capacity());
  }

  public int deliver(Inventory inventory, Predicate<ItemStack> accepts) {
    List<ItemStack> remaining = new ArrayList<>();
    int moved = 0;
    for (ItemStack item : worker.cargo().items()) {
      if (!accepts.test(item)) {
        remaining.add(item);
        continue;
      }
      int left = 0;
      for (ItemStack leftover : inventory.addItem(item.clone()).values()) {
        left += leftover.getAmount();
        remaining.add(leftover);
      }
      moved += item.getAmount() - left;
    }
    if (moved > 0) {
      worker.cargo().keep(remaining);
      worker.handle().addItemsMoved(moved);
    }
    return moved;
  }

  public void interact(Block container, int ticks) {
    worker.controller().interact(container, ticks);
  }

  public void workXp(double xp) {
    GolemWorkXp.pay(worker.handle(), core(), xp);
  }

  public boolean progress() {
    return task != null && worker.renew(task, nowMillis);
  }
}
