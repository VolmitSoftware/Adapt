package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.fx.ViewerDisplayPort;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockStateRef;

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
  private static final Rgb WHITE = Rgb.of(0xFFFFFF);

  private final Function<DyeColor, BlockStateRef> blocks;
  private final LongSupplier clock;
  private final Map<UUID, Map<NodeKey, Outlined>> outlined = new ConcurrentHashMap<>();
  private final Map<UUID, NodeKey> selected = new ConcurrentHashMap<>();

  public NodeOutline() {
    this(NodeOutline::glass, System::currentTimeMillis);
  }

  NodeOutline(Function<DyeColor, BlockStateRef> blocks, LongSupplier clock) {
    this.blocks = Objects.requireNonNull(blocks);
    this.clock = Objects.requireNonNull(clock);
  }

  public static BlockStateRef glass(DyeColor channel) {
    Id glass = Id.minecraft((channel == null ? DyeColor.WHITE : channel).id() + "_stained_glass");
    return AdaptCore.platform().host().items().blockState(glass);
  }

  public void update(PlayerRef viewer, NodeIndex index, int pollTicks) {
    UUID viewerId = viewer.uuid();
    Loc location = viewer.location();
    List<NodeIndex.Entry> nearby = new ArrayList<>(index.near(viewer.world().uid(), location.x(), location.y(), location.z(), RANGE));
    nearby.removeIf((NodeIndex.Entry entry) -> !entry.record().owner().equals(viewerId));
    nearby.sort(Comparator.comparingDouble((NodeIndex.Entry entry) -> entry.key().distanceSquared(location.x(), location.y(), location.z())));
    Map<NodeKey, Outlined> previous = outlined.computeIfAbsent(viewerId, (UUID ignored) -> new HashMap<>());
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
      displays().clearViewer(CHANNEL, viewer);
    }
  }

  public void shutdown() {
    selected.clear();
    outlined.clear();
    displays().clearChannel(CHANNEL);
  }

  public void select(UUID viewer, NodeKey key) {
    if (key == null) {
      selected.remove(viewer);
    } else {
      selected.put(viewer, key);
    }
  }

  private void showBox(PlayerRef viewer, Outlined target) {
    NodeCuboid area = target.record().area();
    int[] center = NodeArea.center(target.key());
    Loc minimum = new Loc(viewer.world(), center[0] + area.minX(), center[1] + area.minY(), center[2] + area.minZ());
    Vec3 size = new Vec3(area.spanX(), area.spanY(), area.spanZ());
    BlockStateRef glass = blocks.apply(target.record().channel());
    String id = boxKey(target.key());
    Rgb glow = target.editing() ? target.record().channel() == null ? WHITE : target.record().channel().color() : null;
    displays().showCuboid(CHANNEL, id + "-outside", viewer, minimum, size, glass, glow, false, DURATION_TICKS);
    displays().showCuboid(CHANNEL, id + "-inside", viewer, minimum, size, glass, glow, true, DURATION_TICKS);
  }

  private static ViewerDisplayPort displays() {
    return AdaptPorts.get(ViewerDisplayPort.class, ViewerDisplayPort.NONE);
  }

  private static String boxKey(NodeKey key) {
    return key.x() + ":" + key.y() + ":" + key.z() + ":" + key.face();
  }

  private static void clear(UUID viewer, NodeKey key) {
    displays().clearViewerKey(CHANNEL, boxKey(key) + "-outside", viewer);
    displays().clearViewerKey(CHANNEL, boxKey(key) + "-inside", viewer);
  }

  private static boolean sameShape(NodeRecord drawn, NodeRecord current) {
    return drawn.area().equals(current.area()) && drawn.channel() == current.channel();
  }

  private record Outlined(NodeKey key, NodeRecord record, long expiresAt, boolean editing) {
  }
}
