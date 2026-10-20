package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTaskBoard;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

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
  private Map<Long, Optional<Inventory>> blocks;
  private Map<Long, Optional<Inventory>> containers;
  private NodeStatus status;

  NodeContext(NodePlatform platform, NodeScanPass pass, GolemTaskBoard board, ScanBudget budget, Adaptation<?> policy, long nowMillis) {
    this.platform = Objects.requireNonNull(platform);
    this.pass = Objects.requireNonNull(pass);
    this.board = Objects.requireNonNull(board);
    this.budget = Objects.requireNonNull(budget);
    this.policy = policy;
    this.nowMillis = nowMillis;
  }

  public World world() {
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

  public Optional<Player> onlineOwner() {
    return Optional.ofNullable(platform.player(owner()));
  }

  public long nowMillis() {
    return nowMillis;
  }

  public Block anchor() {
    NodeKey key = pass.key();
    return pass.world().getBlockAt(key.x(), key.y(), key.z());
  }

  public Optional<Block> block(int areaIndex) {
    int[] position = NodeArea.position(pass.key(), pass.record(), areaIndex);
    World world = pass.world();
    if (!platform.chunkLoaded(world, position[0] >> 4, position[2] >> 4)
        || !platform.owns(new Location(world, position[0], position[1], position[2]))) {
      return Optional.empty();
    }
    return Optional.of(world.getBlockAt(position[0], position[1], position[2]));
  }

  public boolean owns(Block block) {
    return platform.owns(block.getLocation());
  }

  public Optional<Inventory> container(Block block) {
    if (blocks == null) {
      blocks = new HashMap<>(4);
      containers = new HashMap<>(4);
    }
    long blockKey = NodeContainers.blockKey(block.getX(), block.getY(), block.getZ());
    Optional<Inventory> cached = blocks.get(blockKey);
    if (cached != null) {
      return cached;
    }
    Optional<Inventory> resolved = resolve(block);
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

  private Optional<Inventory> resolve(Block block) {
    Player owner = platform.player(owner());
    if (policy == null || owner == null || !owns(block)) {
      return Optional.empty();
    }
    Optional<Inventory> inventory = NodeContainers.inventory(block);
    if (inventory.isEmpty()) {
      return inventory;
    }
    long containerKey = NodeContainers.key(block, inventory.get());
    Optional<Inventory> shared = containers.get(containerKey);
    if (shared != null) {
      return shared;
    }
    Optional<Inventory> allowed = policy.canAccessChest(owner, block.getLocation()) ? inventory : Optional.empty();
    containers.put(containerKey, allowed);
    return allowed;
  }
}
