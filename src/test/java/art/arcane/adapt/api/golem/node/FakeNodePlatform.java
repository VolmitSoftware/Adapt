package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;

final class FakeNodePlatform implements NodePlatform {
  final Map<UUID, GolemRoster> rosters = new HashMap<>();
  final Set<UUID> online = new HashSet<>();
  final Map<UUID, Integer> rosterSaves = new HashMap<>();
  final Map<String, NodeFaces> store = new HashMap<>();
  final Map<UUID, World> worlds = new HashMap<>();
  final List<Scheduled> scheduled = new ArrayList<>();
  final List<Runnable> asyncTasks = new ArrayList<>();
  final List<String> events = new ArrayList<>();
  final Set<String> powered = new HashSet<>();
  final Map<UUID, Player> players = new HashMap<>();
  boolean ownsEverything = true;
  boolean deferAsync;
  boolean refuseRunAt;
  int busySaves;
  RuntimeException saveFailure;
  int chunkReads;
  int powerReads;
  long now = 1_000_000L;

  @Override
  public Optional<GolemRoster> roster(UUID owner) {
    return Optional.ofNullable(rosters.get(owner));
  }

  @Override
  public void rosterChanged(UUID owner) {
    rosterSaves.merge(owner, 1, Integer::sum);
  }

  @Override
  public boolean isOnline(UUID owner) {
    return online.contains(owner);
  }

  @Override
  public NodeFaces read(World world, int x, int y, int z) {
    return store.getOrDefault(key(world.getUID(), x, y, z), NodeFaces.EMPTY);
  }

  @Override
  public void write(World world, int x, int y, int z, NodeFaces faces) {
    if (faces.isEmpty()) {
      store.remove(key(world.getUID(), x, y, z));
    } else {
      store.put(key(world.getUID(), x, y, z), faces);
    }
  }

  @Override
  public Map<NodeKey, NodeRecord> readChunk(World world, int chunkX, int chunkZ) {
    chunkReads++;
    Map<NodeKey, NodeRecord> nodes = new HashMap<>();
    for (Map.Entry<String, NodeFaces> entry : store.entrySet()) {
      String[] parts = entry.getKey().split(",");
      UUID worldId = UUID.fromString(parts[0]);
      int x = Integer.parseInt(parts[1]);
      int y = Integer.parseInt(parts[2]);
      int z = Integer.parseInt(parts[3]);
      if (!worldId.equals(world.getUID()) || x >> 4 != chunkX || z >> 4 != chunkZ) {
        continue;
      }
      for (NodeRecord record : entry.getValue().records()) {
        nodes.put(new NodeKey(worldId, x, y, z, record.face()), record);
      }
    }
    return nodes;
  }

  @Override
  public boolean runAt(Location location, Runnable task, int delayTicks) {
    if (refuseRunAt) {
      return false;
    }
    if (delayTicks <= 0) {
      task.run();
    } else {
      scheduled.add(new Scheduled(location, task, delayTicks));
    }
    return true;
  }

  @Override
  public void async(Runnable task, int delayTicks) {
    if (delayTicks <= 0 && !deferAsync) {
      task.run();
    } else {
      asyncTasks.add(task);
    }
  }

  @Override
  public boolean owns(Location location) {
    return ownsEverything;
  }

  @Override
  public boolean owns(Entity entity) {
    return ownsEverything;
  }

  @Override
  public boolean savePlate(World world, int x, int z) {
    events.add("save " + x + "," + z);
    if (saveFailure != null) {
      throw saveFailure;
    }
    if (busySaves > 0) {
      busySaves--;
      return false;
    }
    return true;
  }

  @Override
  public boolean powered(World world, int x, int y, int z) {
    powerReads++;
    return powered.contains(x + "," + y + "," + z);
  }

  @Override
  public World world(UUID id) {
    return worlds.get(id);
  }

  @Override
  public ItemStack icon(NodeType type) {
    return mock(ItemStack.class);
  }

  @Override
  public Player player(UUID owner) {
    return players.get(owner);
  }

  @Override
  public long nowMillis() {
    return now;
  }

  void put(UUID world, int x, int y, int z, NodeFaces faces) {
    store.put(key(world, x, y, z), faces);
  }

  NodeFaces stored(UUID world, int x, int y, int z) {
    return store.getOrDefault(key(world, x, y, z), NodeFaces.EMPTY);
  }

  void runScheduled() {
    List<Scheduled> due = List.copyOf(scheduled);
    scheduled.clear();
    for (Scheduled task : due) {
      task.task().run();
    }
  }

  void runAsync() {
    List<Runnable> due = List.copyOf(asyncTasks);
    asyncTasks.clear();
    for (Runnable task : due) {
      task.run();
    }
  }

  void drainAsync() {
    while (!asyncTasks.isEmpty()) {
      runAsync();
    }
  }

  private static String key(UUID world, int x, int y, int z) {
    return world + "," + x + "," + y + "," + z;
  }

  record Scheduled(Location location, Runnable task, int delayTicks) {
  }
}
