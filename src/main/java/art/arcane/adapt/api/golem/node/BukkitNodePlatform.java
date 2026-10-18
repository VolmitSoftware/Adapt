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
    NodeFaces faces = WorldData.of(world).get(x, y, z, NodeFaces.class);
    return faces == null ? NodeFaces.EMPTY : faces;
  }

  @Override
  public void write(World world, int x, int y, int z, NodeFaces faces) {
    WorldData data = WorldData.of(world);
    if (faces.isEmpty()) {
      data.remove(x, y, z, NodeFaces.class);
    } else {
      data.set(x, y, z, faces);
    }
  }

  @Override
  public Map<NodeKey, NodeRecord> readChunk(World world, int chunkX, int chunkZ) {
    UUID worldId = world.getUID();
    Map<NodeKey, NodeRecord> nodes = new HashMap<>();
    WorldData.of(world).iterateChunk(chunkX, chunkZ, NodeFaces.class, (x, y, z, faces) -> {
      for (NodeRecord record : faces.records()) {
        nodes.put(new NodeKey(worldId, x, y, z, record.face()), record);
      }
    });
    return nodes;
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
  public World world(UUID id) {
    return Bukkit.getWorld(id);
  }

  @Override
  public ItemStack icon(NodeType type) {
    return new ItemStack(NodeIcons.icon(type));
  }

  private static AdaptPlayer onlinePlayer(UUID owner) {
    Adapt plugin = Adapt.instance;
    AdaptServer server = plugin == null ? null : plugin.getAdaptServer();
    return server == null ? null : server.getOnlineAdaptPlayer(owner);
  }
}
