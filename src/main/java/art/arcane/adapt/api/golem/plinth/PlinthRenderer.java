package art.arcane.adapt.api.golem.plinth;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class PlinthRenderer {
  static final int STEP_TICKS = 5;
  static final float IDLE_DEGREES_PER_TICK = 4.5F;
  private static final double HOVER_HEIGHT = 0.85D;
  private static final float ITEM_SCALE = 0.5F;
  private static final double CENTER = 0.5D;

  private final PlinthPlatform platform;
  private final Map<PlinthSpot, Rendered> rendered = new ConcurrentHashMap<>();
  private final AtomicLong tokens = new AtomicLong();

  PlinthRenderer(PlinthPlatform platform) {
    this.platform = Objects.requireNonNull(platform);
  }

  static Location itemAt(World world, PlinthSpot spot, float lift) {
    return new Location(world, spot.x() + CENTER, spot.y() + CENTER + HOVER_HEIGHT + lift, spot.z() + CENTER);
  }

  void spawn(World world, PlinthSpot spot, ItemStack item) {
    despawn(spot);
    ItemDisplay display = platform.spawnDisplay(itemAt(world, spot, 0F), item);
    Rendered entry = new Rendered(display, tokens.incrementAndGet());
    rendered.put(spot, entry);
    apply(entry, 0);
    scheduleStep(spot, entry);
  }

  void animate(PlinthSpot spot, float lift, float degreesPerTick) {
    Rendered entry = rendered.get(spot);
    if (entry != null) {
      entry.lift = lift;
      entry.degreesPerTick = degreesPerTick;
    }
  }

  void rest(PlinthSpot spot) {
    animate(spot, 0F, IDLE_DEGREES_PER_TICK);
  }

  void despawn(PlinthSpot spot) {
    Rendered entry = rendered.remove(spot);
    if (entry != null) {
      remove(entry.display);
    }
  }

  void shutdown() {
    for (PlinthSpot spot : List.copyOf(rendered.keySet())) {
      despawn(spot);
    }
  }

  private void scheduleStep(PlinthSpot spot, Rendered entry) {
    platform.runEntity(entry.display, () -> step(spot, entry.token), STEP_TICKS);
  }

  private void step(PlinthSpot spot, long token) {
    Rendered entry = rendered.get(spot);
    if (entry == null || entry.token != token || !entry.display.isValid()) {
      return;
    }
    entry.angle = (entry.angle + (float) Math.toRadians(entry.degreesPerTick * STEP_TICKS)) % (float) (Math.PI * 2D);
    apply(entry, STEP_TICKS);
    scheduleStep(spot, entry);
  }

  private static void apply(Rendered entry, int interpolationTicks) {
    ItemDisplay display = entry.display;
    display.setInterpolationDelay(0);
    display.setInterpolationDuration(interpolationTicks);
    display.setTransformation(new Transformation(new Vector3f(0F, entry.lift, 0F), new Quaternionf().rotationY(entry.angle),
        new Vector3f(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE), new Quaternionf()));
  }

  private void remove(ItemDisplay display) {
    if (!display.isValid()) {
      return;
    }
    if (platform.owns(display)) {
      display.remove();
      return;
    }
    platform.runEntity(display, () -> {
      if (display.isValid()) {
        display.remove();
      }
    }, 0);
  }

  private static final class Rendered {
    private final ItemDisplay display;
    private final long token;
    private float angle;
    private volatile float lift;
    private volatile float degreesPerTick = IDLE_DEGREES_PER_TICK;

    private Rendered(ItemDisplay display, long token) {
      this.display = Objects.requireNonNull(display);
      this.token = token;
    }
  }
}
