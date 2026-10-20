package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

public final class NodeOutline {
  public static final String CHANNEL = "golemancy-node-outline";
  public static final double LOOK_DISTANCE = 6D;
  public static final int DURATION_TICKS = 200;
  private static final long TICK_MILLIS = 50L;
  private static final int REFRESH_MARGIN_TICKS = 10;
  private static final double THICKNESS = 0.04D;
  private static final String EDGE_KEY = "edge-";
  private static final int EDGES = 12;

  private final Function<DyeColor, BlockData> blocks;
  private final LongSupplier clock;
  private final Map<UUID, Outlined> outlined = new ConcurrentHashMap<>();

  public NodeOutline() {
    this(NodeOutline::glass, System::currentTimeMillis);
  }

  NodeOutline(Function<DyeColor, BlockData> blocks, LongSupplier clock) {
    this.blocks = Objects.requireNonNull(blocks);
    this.clock = Objects.requireNonNull(clock);
  }

  public static List<Edge> edges(NodeKey key, NodeCuboid area) {
    int[] center = NodeArea.center(key);
    double minX = center[0] + area.minX();
    double minY = center[1] + area.minY();
    double minZ = center[2] + area.minZ();
    double maxX = center[0] + area.maxX() + 1D;
    double maxY = center[1] + area.maxY() + 1D;
    double maxZ = center[2] + area.maxZ() + 1D;
    List<Edge> edges = new ArrayList<>(EDGES);
    for (double y : new double[]{minY, maxY}) {
      for (double z : new double[]{minZ, maxZ}) {
        edges.add(new Edge(new Vector(minX, y, z), new Vector(maxX, y, z)));
      }
    }
    for (double x : new double[]{minX, maxX}) {
      for (double z : new double[]{minZ, maxZ}) {
        edges.add(new Edge(new Vector(x, minY, z), new Vector(x, maxY, z)));
      }
    }
    for (double x : new double[]{minX, maxX}) {
      for (double y : new double[]{minY, maxY}) {
        edges.add(new Edge(new Vector(x, y, minZ), new Vector(x, y, maxZ)));
      }
    }
    return edges;
  }

  public static void show(String channel, Player viewer, List<Edge> edges, BlockData block, Color glow) {
    World world = viewer.getWorld();
    for (int index = 0; index < edges.size(); index++) {
      Edge edge = edges.get(index);
      ViewerDisplayDirector.showLine(channel, EDGE_KEY + index, viewer, edge.start().toLocation(world), edge.end().toLocation(world),
          block, glow, THICKNESS, DURATION_TICKS);
    }
  }

  public static BlockData glass(DyeColor channel) {
    Material glass = channel == null ? Material.WHITE_STAINED_GLASS : Material.valueOf(channel.name() + "_STAINED_GLASS");
    return glass.createBlockData();
  }

  public void update(Player viewer, NodeIndex index, int pollTicks) {
    UUID viewerId = viewer.getUniqueId();
    NodeKey key = lookedAt(viewer);
    NodeRecord record = key == null ? null : index.get(key).filter(found -> found.owner().equals(viewerId)).orElse(null);
    if (record == null) {
      forget(viewerId);
      return;
    }
    long now = clock.getAsLong();
    Outlined previous = outlined.get(viewerId);
    boolean same = previous != null && previous.key().equals(key) && sameShape(previous.record(), record);
    if (same && now < previous.expiresAt() - (pollTicks + REFRESH_MARGIN_TICKS) * TICK_MILLIS) {
      return;
    }
    if (previous != null && !same) {
      ViewerDisplayDirector.clearViewer(CHANNEL, viewerId);
    }
    Outlined next = new Outlined(key, record, now + DURATION_TICKS * TICK_MILLIS);
    outlined.put(viewerId, next);
    draw(viewer, next);
  }

  public void forget(UUID viewer) {
    if (outlined.remove(viewer) != null) {
      ViewerDisplayDirector.clearViewer(CHANNEL, viewer);
    }
  }

  public void shutdown() {
    outlined.clear();
    ViewerDisplayDirector.clearChannel(CHANNEL);
  }

  private void draw(Player viewer, Outlined target) {
    DyeColor channel = target.record().channel();
    show(CHANNEL, viewer, edges(target.key(), target.record().area()), blocks.apply(channel), channel == null ? Color.WHITE : channel.getColor());
  }

  private static boolean sameShape(NodeRecord drawn, NodeRecord current) {
    return drawn.area().equals(current.area()) && drawn.channel() == current.channel();
  }

  private static NodeKey lookedAt(Player viewer) {
    Location eye = viewer.getEyeLocation();
    RayTraceResult hit = viewer.getWorld().rayTraceBlocks(eye, eye.getDirection(), LOOK_DISTANCE, FluidCollisionMode.NEVER, true);
    if (hit == null) {
      return null;
    }
    Block block = hit.getHitBlock();
    BlockFace face = hit.getHitBlockFace();
    return block == null || face == null || !NodeRecord.isNodeFace(face) ? null : NodeKey.of(block, face);
  }

  public record Edge(Vector start, Vector end) {
  }

  private record Outlined(NodeKey key, NodeRecord record, long expiresAt) {
  }
}
