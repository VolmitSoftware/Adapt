package art.arcane.adapt.engine.fx;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.BlockDisplayRef;
import art.arcane.adapt.platform.entity.display.Brightness;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Quat;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Transform;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Private block displays on the SPI alone: a non-persistent glowing block display hidden by default and shown to
 * one viewer, removed after its duration (persistent ones only when cleared) or when its channel or viewer is cleared.
 */
public final class PrivateBlockDisplays implements ViewerDisplayPort {
  public static final PrivateBlockDisplays INSTANCE = new PrivateBlockDisplays();
  private static final int MAX_DURATION_TICKS = 20 * 60;
  private static final Transform OUTLINE = new Transform(new Vec3(-0.01D, -0.01D, -0.01D), Quat.IDENTITY,
      new Vec3(1.02D, 1.02D, 1.02D), Quat.IDENTITY);

  private final Map<Key, Shown> displays = new ConcurrentHashMap<>();

  private PrivateBlockDisplays() {
  }

  @Override
  public boolean showBlock(String channel, String key, PlayerRef viewer, Loc location, BlockStateRef block,
                           Rgb glowColor, int durationTicks) {
    if (viewer == null || !viewer.isOnline() || location == null || location.world() == null || block == null) {
      return false;
    }
    Key displayKey = new Key(channel, key, viewer.uuid());
    int ticks = Math.max(1, Math.min(MAX_DURATION_TICKS, durationTicks));
    return submit(displayKey, viewer, location, block, glowColor, OUTLINE, ticks);
  }

  @Override
  public boolean showLine(String channel, String key, PlayerRef viewer, Loc start, Loc end, BlockStateRef block,
                          Rgb glowColor, double thickness, int durationTicks) {
    if (viewer == null || !viewer.isOnline() || start == null || end == null || start.world() == null
        || !start.sameWorld(end) || block == null) {
      return false;
    }
    Vec3 delta = end.position().subtract(start.position());
    if (!delta.isFinite() || delta.lengthSquared() <= 1.0E-8D) {
      return false;
    }
    double safeThickness = Double.isFinite(thickness) ? Math.max(0.015D, Math.min(0.5D, thickness)) : 0.05D;
    Key displayKey = new Key(channel, key, viewer.uuid());
    int ticks = Math.max(1, Math.min(MAX_DURATION_TICKS, durationTicks));
    return submit(displayKey, viewer, start, block, glowColor, lineTransform(delta, safeThickness), ticks);
  }

  @Override
  public boolean showCuboid(String channel, String key, PlayerRef viewer, Loc minimum, Vec3 size, BlockStateRef block,
                            Rgb glowColor, boolean inside, int durationTicks) {
    if (viewer == null || !viewer.isOnline() || minimum == null || minimum.world() == null || block == null
        || size == null || !size.isFinite() || size.x() <= 0D || size.y() <= 0D || size.z() <= 0D) {
      return false;
    }
    Key displayKey = new Key(channel, key, viewer.uuid());
    int ticks = Math.max(1, Math.min(MAX_DURATION_TICKS, durationTicks));
    return submit(displayKey, viewer, minimum, block, glowColor, cuboidTransform(size, inside), ticks);
  }

  @Override
  public boolean showPersistentBlock(String channel, String key, PlayerRef viewer, Loc location, BlockStateRef block,
                                     Rgb glowColor) {
    if (viewer == null || !viewer.isOnline() || location == null || location.world() == null || block == null) {
      return false;
    }
    return submit(new Key(channel, key, viewer.uuid()), viewer, location, block, glowColor, OUTLINE, 0);
  }

  @Override
  public boolean isShowing(String channel, String key, UUID viewerId, Loc location) {
    if (channel == null || key == null || viewerId == null || location == null) {
      return false;
    }
    Shown shown = displays.get(new Key(channel, key, viewerId));
    return shown != null && shown.display().isValid() && shown.block().equals(location.blockPos())
        && shown.display().world().equals(location.world());
  }

  static Transform cuboidTransform(Vec3 size, boolean inside) {
    Vec3 scale = new Vec3((float) size.x(), (float) size.y(), (float) size.z());
    return inside
        ? new Transform(scale, Quat.IDENTITY, scale.multiply(-1D), Quat.IDENTITY)
        : new Transform(Vec3.ZERO, Quat.IDENTITY, scale, Quat.IDENTITY);
  }

  static Transform lineTransform(Vec3 delta, double thickness) {
    Vec3 direction = delta.normalize();
    Quat rotation = rotationFromZ(direction);
    Vec3 offset = rotate(rotation, new Vec3(-thickness * 0.5D, -thickness * 0.5D, 0D));
    return new Transform(offset, rotation, new Vec3(thickness, thickness, delta.length()), Quat.IDENTITY);
  }

  private static Quat rotationFromZ(Vec3 direction) {
    double dot = direction.z();
    if (dot < -0.999999D) {
      return new Quat(1.0F, 0.0F, 0.0F, 0.0F);
    }
    double x = -direction.y();
    double y = direction.x();
    double w = 1.0D + dot;
    double length = Math.sqrt((x * x) + (y * y) + (w * w));
    return new Quat((float) (x / length), (float) (y / length), 0.0F, (float) (w / length));
  }

  private static Vec3 rotate(Quat q, Vec3 v) {
    Vec3 axis = new Vec3(q.x(), q.y(), q.z());
    Vec3 t = axis.cross(v).multiply(2.0D);
    return v.add(t.multiply(q.w())).add(axis.cross(t));
  }

  private boolean submit(Key displayKey, PlayerRef viewer, Loc location, BlockStateRef block, Rgb glowColor,
                         Transform transform, int ticks) {
    if (Tasks.isOwnedByCurrentRegion(location)) {
      spawn(displayKey, viewer, location, block, glowColor, transform, ticks);
      return true;
    }
    return Tasks.runAt(location, () -> spawn(displayKey, viewer, location, block, glowColor, transform, ticks));
  }

  @Override
  public void clearViewer(String channel, UUID viewerId) {
    clear((Key key) -> key.channel().equals(channel) && key.viewerId().equals(viewerId));
  }

  /** Removes every display shown to the viewer, in all channels. */
  public void clearViewer(UUID viewerId) {
    clear((Key key) -> key.viewerId().equals(viewerId));
  }

  @Override
  public void clearViewerKey(String channel, String key, UUID viewerId) {
    clear((Key entry) -> entry.equals(new Key(channel, key, viewerId)));
  }

  @Override
  public void clearChannel(String channel) {
    clear((Key key) -> key.channel().equals(channel));
  }

  private void spawn(Key key, PlayerRef viewer, Loc location, BlockStateRef block, Rgb glowColor, Transform transform,
                     int ticks) {
    if (!viewer.isOnline()) {
      return;
    }
    BlockDisplayRef display = location.world().spawnBlockDisplay(location, block, (BlockDisplayRef spawned) -> {
      spawned.setPersistent(false);
      spawned.setInvulnerable(true);
      spawned.setGravity(false);
      spawned.setSilent(true);
      spawned.setVisibleByDefault(false);
      spawned.setViewRange(2F);
      spawned.setShadowRadius(0F);
      spawned.setBrightness(new Brightness(15, 15));
      spawned.setGlowColorOverride(glowColor);
      spawned.setGlowing(glowColor != null);
      spawned.setTransform(transform);
    });
    if (display == null) {
      return;
    }
    Shown shown = new Shown(display, location.blockPos());
    Shown previous = displays.put(key, shown);
    if (previous != null) {
      remove(previous.display());
    }
    Tasks.runEntity(viewer, () -> {
      if (displays.get(key) == shown && display.isValid()) {
        AdaptCore.platform().viewers().showEntity(viewer, display);
      }
    });
    if (ticks <= 0) {
      return;
    }
    Tasks.runAt(location, () -> {
      if (displays.remove(key, shown)) {
        remove(display);
      }
    }, ticks);
  }

  private void clear(Predicate<Key> matches) {
    for (Key key : List.copyOf(displays.keySet())) {
      if (!matches.test(key)) {
        continue;
      }
      Shown shown = displays.remove(key);
      if (shown != null) {
        remove(shown.display());
      }
    }
  }

  private static void remove(BlockDisplayRef display) {
    if (Tasks.isOwnedByCurrentRegion(display)) {
      display.remove();
      return;
    }
    Tasks.runEntity(display, display::remove);
  }

  private record Key(String channel, String key, UUID viewerId) {
  }

  private record Shown(BlockDisplayRef display, BlockPos block) {
  }
}
