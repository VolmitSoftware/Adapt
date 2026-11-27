package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

interface NodePlatform {
  Optional<GolemRoster> roster(UUID owner);

  void rosterChanged(UUID owner);

  boolean isOnline(UUID owner);

  NodeFaces read(WorldRef world, int x, int y, int z);

  void write(WorldRef world, int x, int y, int z, NodeFaces faces);

  Map<NodeKey, NodeRecord> readChunk(WorldRef world, int chunkX, int chunkZ);

  boolean runAt(Loc location, Runnable task, int delayTicks);

  void async(Runnable task, int delayTicks);

  boolean owns(Loc location);

  boolean owns(EntityRef entity);

  boolean savePlate(WorldRef world, int x, int z);

  NodePower powered(WorldRef world, int x, int y, int z);

  boolean chunkLoaded(WorldRef world, int chunkX, int chunkZ);

  WorldRef world(UUID id);

  ItemRef icon(NodeType type);

  PlayerRef player(UUID owner);

  long nowMillis();
}
