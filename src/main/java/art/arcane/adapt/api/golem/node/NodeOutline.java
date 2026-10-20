package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import org.bukkit.DyeColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

public final class NodeOutline {
  public static final String CHANNEL = "golemancy-node-outline";
  public static final int DURATION_TICKS = 200;
  private static final int MAX_BOXES = 24;
  private static final double RANGE = 32D;
  private static final long TICK_MILLIS = 50L;
  private static final int REFRESH_MARGIN_TICKS = 10;

  private final Function<DyeColor, BlockData> blocks;
  private final LongSupplier clock;
  private final Map<UUID, Map<NodeKey, Outlined>> outlined = new ConcurrentHashMap<>();
  private final Map<UUID, NodeKey> selected = new ConcurrentHashMap<>();

  public NodeOutline() {
    this(NodeOutline::glass, System::currentTimeMillis);
  }

  NodeOutline(Function<DyeColor, BlockData> blocks, LongSupplier clock) {
    this.blocks = Objects.requireNonNull(blocks);
    this.clock = Objects.requireNonNull(clock);
  }

  public static BlockData glass(DyeColor channel) {
    Material glass = channel == null ? Material.WHITE_STAINED_GLASS : Material.valueOf(channel.name() + "_STAINED_GLASS");
    return glass.createBlockData();
  }

  public void update(Player viewer, NodeIndex index, int pollTicks) {
    UUID viewerId = viewer.getUniqueId();
    Location location = viewer.getLocation();
    List<NodeIndex.Entry> nearby = new ArrayList<>(index.near(viewer.getWorld().getUID(), location.getX(), location.getY(), location.getZ(), RANGE));
    nearby.removeIf(entry -> !entry.record().owner().equals(viewerId));
    nearby.sort(Comparator.comparingDouble(entry -> entry.key().distanceSquared(location.getX(), location.getY(), location.getZ())));
    Map<NodeKey, Outlined> previous = outlined.computeIfAbsent(viewerId, ignored -> new HashMap<>());
    Map<NodeKey, Outlined> next = new HashMap<>();
    long now = clock.getAsLong();
    for (int position = 0; position < Math.min(MAX_BOXES, nearby.size()); position++) {
      NodeIndex.Entry entry = nearby.get(position);
      boolean editing = entry.key().equals(selected.get(viewerId));
      Outlined shown = previous.get(entry.key());
      if (shown == null || shown.editing() != editing || !sameShape(shown.record(), entry.record())
          || now >= shown.expiresAt() - (pollTicks + REFRESH_MARGIN_TICKS) * TICK_MILLIS) {
        clear(viewerId, entry.key());
        shown = new Outlined(entry.key(), entry.record(), now + DURATION_TICKS * TICK_MILLIS, editing);
        showBox(viewer, shown);
      }
      next.put(entry.key(), shown);
    }
    for (NodeKey key : previous.keySet()) {
      if (!next.containsKey(key)) {
        clear(viewerId, key);
      }
    }
    outlined.put(viewerId, next);
  }

  public void forget(UUID viewer) {
    selected.remove(viewer);
    if (outlined.remove(viewer) != null) {
      ViewerDisplayDirector.clearViewer(CHANNEL, viewer);
    }
  }

  public void shutdown() {
    selected.clear();
    outlined.clear();
    ViewerDisplayDirector.clearChannel(CHANNEL);
  }

  public void select(UUID viewer, NodeKey key) {
    if (key == null) {
      selected.remove(viewer);
    } else {
      selected.put(viewer, key);
    }
  }

  private void showBox(Player viewer, Outlined target) {
    NodeCuboid area = target.record().area();
    int[] center = NodeArea.center(target.key());
    Location minimum = new Location(viewer.getWorld(), center[0] + area.minX(), center[1] + area.minY(), center[2] + area.minZ());
    Vector size = new Vector(area.spanX(), area.spanY(), area.spanZ());
    BlockData glass = blocks.apply(target.record().channel());
    String id = boxKey(target.key());
    Color glow = target.editing() ? target.record().channel() == null ? Color.WHITE : target.record().channel().getColor() : null;
    ViewerDisplayDirector.showCuboid(CHANNEL, id + "-outside", viewer, minimum, size, glass, glow, false, DURATION_TICKS);
    ViewerDisplayDirector.showCuboid(CHANNEL, id + "-inside", viewer, minimum, size, glass, glow, true, DURATION_TICKS);
  }

  private static String boxKey(NodeKey key) {
    return key.x() + ":" + key.y() + ":" + key.z() + ":" + key.face();
  }

  private static void clear(UUID viewer, NodeKey key) {
    ViewerDisplayDirector.clearViewerKey(CHANNEL, boxKey(key) + "-outside", viewer);
    ViewerDisplayDirector.clearViewerKey(CHANNEL, boxKey(key) + "-inside", viewer);
  }

  private static boolean sameShape(NodeRecord drawn, NodeRecord current) {
    return drawn.area().equals(current.area()) && drawn.channel() == current.channel();
  }

  private record Outlined(NodeKey key, NodeRecord record, long expiresAt, boolean editing) {
  }
}
