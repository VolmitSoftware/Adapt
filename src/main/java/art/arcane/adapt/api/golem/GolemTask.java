package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.golem.node.NodeKey;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class GolemTask {
  private final NodeKey node;
  private final NodeType nodeType;
  private final String type;
  private final GolemTarget target;
  private final ItemStack item;
  private final Set<GolemBody> bodies;
  private final TaskKey key;
  private final AtomicReference<Lease> lease = new AtomicReference<>();
  private volatile boolean closed;

  private GolemTask(Builder builder) {
    this.node = builder.node;
    this.nodeType = builder.nodeType;
    this.type = builder.type;
    this.target = builder.target;
    this.item = builder.item == null ? null : builder.item.clone();
    this.bodies = Set.copyOf(builder.bodies);
    this.key = TaskKey.of(type, target, item);
  }

  public static Builder builder(NodeKey node, NodeType nodeType, String type, GolemTarget target) {
    return new Builder(Objects.requireNonNull(node), Objects.requireNonNull(nodeType), type, target);
  }

  public static Builder builder(String type, GolemTarget target) {
    return new Builder(null, null, type, target);
  }

  public Optional<NodeKey> node() {
    return Optional.ofNullable(node);
  }

  public Optional<NodeType> nodeType() {
    return Optional.ofNullable(nodeType);
  }

  public String type() {
    return type;
  }

  public GolemTarget target() {
    return target;
  }

  public Optional<ItemStack> item() {
    return item == null ? Optional.empty() : Optional.of(item.clone());
  }

  public Set<GolemBody> bodies() {
    return bodies;
  }

  public boolean allows(GolemBody body) {
    return bodies.contains(body);
  }

  public boolean isClosed() {
    return closed;
  }

  public Optional<UUID> holder(long nowMillis) {
    Lease current = lease.get();
    return current == null || closed || current.expiresAt() <= nowMillis ? Optional.empty() : Optional.of(current.golem());
  }

  public boolean heldBy(UUID golem, long nowMillis) {
    Lease current = lease.get();
    return !closed && current != null && current.expiresAt() > nowMillis && current.golem().equals(golem);
  }

  @Override
  public String toString() {
    String source = nodeType == null ? "core" : nodeType.id();
    return "GolemTask{" + type + " " + source + " " + target.kind() + " " + target.blockX() + "," + target.blockY() + "," + target.blockZ() + "}";
  }

  NodeKey nodeKey() {
    return node;
  }

  TaskKey key() {
    return key;
  }

  long leaseRemaining(long nowMillis) {
    Lease current = lease.get();
    return current == null ? 0L : Math.max(0L, current.expiresAt() - nowMillis);
  }

  boolean openFor(UUID golem, long nowMillis) {
    Lease current = lease.get();
    return !closed && (current == null || current.expiresAt() <= nowMillis || current.golem().equals(golem));
  }

  boolean tryClaim(UUID golem, long nowMillis, long leaseMillis) {
    while (true) {
      Lease current = lease.get();
      if (closed || (current != null && current.expiresAt() > nowMillis && !current.golem().equals(golem))) {
        return false;
      }
      Lease next = new Lease(golem, nowMillis + leaseMillis);
      if (lease.compareAndSet(current, next)) {
        if (closed) {
          lease.compareAndSet(next, null);
          return false;
        }
        return true;
      }
    }
  }

  boolean renew(UUID golem, long nowMillis, long leaseMillis) {
    Lease current = lease.get();
    if (closed || current == null || current.expiresAt() <= nowMillis || !current.golem().equals(golem)) {
      return false;
    }
    return lease.compareAndSet(current, new Lease(golem, nowMillis + leaseMillis));
  }

  boolean release(UUID golem) {
    Lease current = lease.get();
    return current != null && current.golem().equals(golem) && lease.compareAndSet(current, null);
  }

  boolean heldByAnyone(long nowMillis) {
    Lease current = lease.get();
    return !closed && current != null && current.expiresAt() > nowMillis;
  }

  boolean holderIs(UUID golem) {
    Lease current = lease.get();
    return current != null && current.golem().equals(golem);
  }

  boolean purgeExpired(long nowMillis) {
    Lease current = lease.get();
    return current != null && current.expiresAt() <= nowMillis && lease.compareAndSet(current, null);
  }

  void close() {
    closed = true;
  }

  private record Lease(UUID golem, long expiresAt) {
  }

  public static final class Builder {
    private final NodeKey node;
    private final NodeType nodeType;
    private final String type;
    private final GolemTarget target;
    private ItemStack item;
    private Set<GolemBody> bodies = EnumSet.allOf(GolemBody.class);

    private Builder(NodeKey node, NodeType nodeType, String type, GolemTarget target) {
      this.node = node;
      this.nodeType = nodeType;
      this.type = Objects.requireNonNull(type);
      this.target = Objects.requireNonNull(target);
      if (type.isBlank()) {
        throw new IllegalArgumentException("A golem task needs a type");
      }
    }

    public Builder item(ItemStack next) {
      item = next;
      return this;
    }

    public Builder bodies(Set<GolemBody> next) {
      if (next.isEmpty()) {
        throw new IllegalArgumentException("A golem task must allow at least one body");
      }
      bodies = EnumSet.copyOf(next);
      return this;
    }

    public GolemTask build() {
      return new GolemTask(this);
    }
  }
}
