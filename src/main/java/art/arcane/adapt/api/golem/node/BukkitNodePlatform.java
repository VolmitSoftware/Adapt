package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.data.WorldData;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class BukkitNodePlatform implements NodePlatform {
  @Override
  public Optional<GolemRoster> roster(UUID owner) {
    AdaptPlayer player = onlinePlayer(owner);
    return player == null ? Optional.empty() : Optional.of(player.getData().getGolemancy());
  }

  @Override
  public void rosterChanged(UUID owner) {
    AdaptPlayer player = onlinePlayer(owner);
    if (player != null) {
      player.requestSave();
    }
  }

  @Override
  public boolean isOnline(UUID owner) {
    return Bukkit.getPlayer(owner) != null;
  }

  @Override
  public NodeFaces read(World world, int x, int y, int z) {
    NodeFaces faces = WorldData.loaded(world).map(data -> data.get(x, y, z, NodeFaces.class)).orElse(null);
    return faces == null ? NodeFaces.EMPTY : faces;
  }

  @Override
  public void write(World world, int x, int y, int z, NodeFaces faces) {
    WorldData data = loaded(world);
    if (faces.isEmpty()) {
      data.remove(x, y, z, NodeFaces.class);
    } else {
      data.set(x, y, z, faces);
    }
  }

  @Override
  public Map<NodeKey, NodeRecord> readChunk(World world, int chunkX, int chunkZ) {
    Optional<WorldData> data = WorldData.loaded(world);
    if (data.isEmpty()) {
      return Map.of();
    }
    UUID worldId = world.getUID();
    Map<NodeKey, NodeRecord> nodes = new HashMap<>();
    data.get().iterateChunk(chunkX, chunkZ, NodeFaces.class, (x, y, z, faces) -> {
      for (NodeRecord record : faces.records()) {
        nodes.put(new NodeKey(worldId, x, y, z, record.face()), record);
      }
    });
    return nodes;
  }

  @Override
  public boolean savePlate(World world, int x, int z) {
    return loaded(world).savePlate(x, z);
  }

  @Override
  public boolean powered(World world, int x, int y, int z) {
    Block block = world.getBlockAt(x, y, z);
    return block.isBlockPowered() || (neighborsLoaded(world, x, z) && block.isBlockIndirectlyPowered());
  }

  @Override
  public boolean chunkLoaded(World world, int chunkX, int chunkZ) {
    return world.isChunkLoaded(chunkX, chunkZ);
  }

  @Override
  public boolean runAt(Location location, Runnable task, int delayTicks) {
    return delayTicks <= 0 ? J.runAt(location, task) : J.runAt(location, task, delayTicks);
  }

  @Override
  public void async(Runnable task, int delayTicks) {
    if (delayTicks <= 0) {
      J.a(task);
    } else {
      J.a(task, delayTicks);
    }
  }

  @Override
  public boolean owns(Location location) {
    return J.isOwnedByCurrentRegion(location);
  }

  @Override
  public boolean owns(Entity entity) {
    return J.isOwnedByCurrentRegion(entity);
  }

  @Override
  public World world(UUID id) {
    return Bukkit.getWorld(id);
  }

  @Override
  public ItemStack icon(NodeType type) {
    return new ItemStack(NodeIcons.icon(type));
  }

  @Override
  public Player player(UUID owner) {
    return Bukkit.getPlayer(owner);
  }

  @Override
  public long nowMillis() {
    return System.currentTimeMillis();
  }

  private static WorldData loaded(World world) {
    return WorldData.loaded(world).orElseThrow(() -> new IllegalStateException("World " + world.getName() + " is no longer loaded"));
  }

  private static AdaptPlayer onlinePlayer(UUID owner) {
    Adapt plugin = Adapt.instance;
    AdaptServer server = plugin == null ? null : plugin.getAdaptServer();
    return server == null ? null : server.getOnlineAdaptPlayer(owner);
  }

  private static boolean neighborsLoaded(World world, int x, int z) {
    int chunkX = x >> 4;
    int chunkZ = z >> 4;
    int localX = x & 15;
    int localZ = z & 15;
    return (localX != 0 || world.isChunkLoaded(chunkX - 1, chunkZ))
        && (localX != 15 || world.isChunkLoaded(chunkX + 1, chunkZ))
        && (localZ != 0 || world.isChunkLoaded(chunkX, chunkZ - 1))
        && (localZ != 15 || world.isChunkLoaded(chunkX, chunkZ + 1));
  }
}
