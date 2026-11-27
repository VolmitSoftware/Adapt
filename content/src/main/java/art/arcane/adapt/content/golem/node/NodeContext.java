package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.content.golem.GolemTarget;
import art.arcane.adapt.content.golem.GolemTask;
import art.arcane.adapt.content.golem.GolemTaskBoard;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class NodeContext {
  private final NodePlatform platform;
  private final NodeScanPass pass;
  private final GolemTaskBoard board;
  private final ScanBudget budget;
  private final Adaptation<?> policy;
  private final long nowMillis;
  private Map<Long, Optional<InventoryRef>> blocks;
  private Map<Long, Optional<InventoryRef>> containers;
  private NodeStatus status;

  NodeContext(NodePlatform platform, NodeScanPass pass, GolemTaskBoard board, ScanBudget budget, Adaptation<?> policy, long nowMillis) {
    this.platform = Objects.requireNonNull(platform);
    this.pass = Objects.requireNonNull(pass);
    this.board = Objects.requireNonNull(board);
    this.budget = Objects.requireNonNull(budget);
    this.policy = policy;
    this.nowMillis = nowMillis;
  }

  public WorldRef world() {
    return pass.world();
  }

  public NodeKey key() {
    return pass.key();
  }

  public NodeRecord record() {
    return pass.record();
  }

  public UUID owner() {
    return pass.record().owner();
  }

  public Optional<PlayerRef> onlineOwner() {
    return Optional.ofNullable(platform.player(owner()));
  }

  public long nowMillis() {
    return nowMillis;
  }

  public BlockRef anchor() {
    NodeKey key = pass.key();
    return pass.world().blockAt(key.x(), key.y(), key.z());
  }

  public Optional<BlockRef> block(int areaIndex) {
    int[] position = NodeArea.position(pass.key(), pass.record(), areaIndex);
    WorldRef world = pass.world();
    if (!platform.chunkLoaded(world, position[0] >> 4, position[2] >> 4)
        || !platform.owns(new Loc(world, position[0], position[1], position[2]))) {
      return Optional.empty();
    }
    return Optional.of(world.blockAt(position[0], position[1], position[2]));
  }

  public boolean owns(BlockRef block) {
    return platform.owns(block.location());
  }

  public Optional<InventoryRef> container(BlockRef block) {
    if (blocks == null) {
      blocks = new HashMap<>(4);
      containers = new HashMap<>(4);
    }
    long blockKey = NodeContainers.blockKey(block.x(), block.y(), block.z());
    Optional<InventoryRef> cached = blocks.get(blockKey);
    if (cached != null) {
      return cached;
    }
    Optional<InventoryRef> resolved = resolve(block);
    blocks.put(blockKey, resolved);
    return resolved;
  }

  public GolemTask.Builder task(String type, GolemTarget target) {
    return GolemTask.builder(pass.key(), pass.record().type(), type, target);
  }

  public void surveyCriteria(long criteria) {
    if (pass.discovery() != null && pass.current().getAsBoolean()) {
      pass.discovery().surveyCriteria(criteria);
    }
  }

  public boolean queryDue() {
    return pass.discovery() == null || pass.discovery().queryDue(nowMillis);
  }

  public void queryResult(boolean found) {
    if (pass.discovery() != null && pass.current().getAsBoolean()) {
      pass.discovery().queryResult(found, nowMillis);
    }
  }

  public void candidate(int areaIndex, boolean candidate) {
    if (pass.discovery() != null && pass.current().getAsBoolean()) {
      pass.discovery().remember(areaIndex, candidate);
    }
  }

  public boolean post(GolemTask task) {
    if (!pass.current().getAsBoolean() || !board.node(pass.key()).filter(pass.record()::equals).isPresent()) {
      return false;
    }
    if (!task.node().filter(pass.key()::equals).isPresent()) {
      throw new IllegalArgumentException("Node " + pass.key() + " cannot post a task for " + task);
    }
    GolemTaskBoard.Offer offer = board.offer(task, pass.maxOpenTasks());
    if (!pass.current().getAsBoolean()) {
      if (offer == GolemTaskBoard.Offer.ADDED) {
        board.withdraw(task);
      }
      return false;
    }
    if (offer == GolemTaskBoard.Offer.ADDED) {
      budget.taken();
    }
    return offer != GolemTaskBoard.Offer.FULL;
  }

  public List<GolemTask> tasks() {
    return board.tasks(pass.key());
  }

  public boolean withdraw(GolemTask task) {
    return task.node().filter(pass.key()::equals).isPresent() && board.withdraw(task);
  }

  public void status(NodeStatus next) {
    status = Objects.requireNonNull(next);
  }

  Optional<NodeStatus> status() {
    return Optional.ofNullable(status);
  }

  private Optional<InventoryRef> resolve(BlockRef block) {
    PlayerRef owner = platform.player(owner());
    if (policy == null || owner == null || !owns(block)) {
      return Optional.empty();
    }
    Optional<InventoryRef> inventory = NodeContainers.inventory(block);
    if (inventory.isEmpty()) {
      return inventory;
    }
    long containerKey = NodeContainers.key(block, inventory.get());
    Optional<InventoryRef> shared = containers.get(containerKey);
    if (shared != null) {
      return shared;
    }
    Optional<InventoryRef> allowed = policy.canAccessChest(owner, block.location()) ? inventory : Optional.empty();
    containers.put(containerKey, allowed);
    return allowed;
  }
}
