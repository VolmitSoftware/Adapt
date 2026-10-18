package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

interface NodePlatform {
  Optional<GolemRoster> roster(UUID owner);

  void rosterChanged(UUID owner);

  boolean isOnline(UUID owner);

  NodeFaces read(World world, int x, int y, int z);

  void write(World world, int x, int y, int z, NodeFaces faces);

  Map<NodeKey, NodeRecord> readChunk(World world, int chunkX, int chunkZ);

  boolean runAt(Location location, Runnable task, int delayTicks);

  void async(Runnable task, int delayTicks);

  boolean owns(Location location);

  boolean owns(Entity entity);

  boolean savePlate(World world, int x, int z);

  boolean powered(World world, int x, int y, int z);

  World world(UUID id);

  ItemStack icon(NodeType type);

  Player player(UUID owner);

  long nowMillis();
}
