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
  static final int IDLE_STEP_TICKS = 20;
  static final int RITUAL_STEP_TICKS = 5;
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
    Rendered entry = new Rendered(display);
    rendered.put(spot, entry);
    apply(entry, 0);
    scheduleStep(spot, entry, tokens.incrementAndGet());
  }

  void animate(PlinthSpot spot, float lift, float degreesPerTick) {
    Rendered entry = rendered.get(spot);
    if (entry == null) {
      return;
    }
    entry.lift = lift;
    entry.degreesPerTick = degreesPerTick;
    if (!entry.ritual) {
      entry.ritual = true;
      scheduleStep(spot, entry, tokens.incrementAndGet());
    }
  }

  void rest(PlinthSpot spot) {
    Rendered entry = rendered.get(spot);
    if (entry != null) {
      entry.lift = 0F;
      entry.degreesPerTick = IDLE_DEGREES_PER_TICK;
      entry.ritual = false;
    }
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

  private void scheduleStep(PlinthSpot spot, Rendered entry, long token) {
    entry.token = token;
    platform.runEntity(entry.display, () -> step(spot, entry, token), entry.stepTicks());
  }

  private void step(PlinthSpot spot, Rendered entry, long token) {
    if (rendered.get(spot) != entry || entry.token != token || !entry.display.isValid()) {
      return;
    }
    int stepTicks = entry.stepTicks();
    entry.angle = (entry.angle + (float) Math.toRadians(entry.degreesPerTick * stepTicks)) % (float) (Math.PI * 2D);
    apply(entry, stepTicks);
    scheduleStep(spot, entry, token);
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
    private volatile long token;
    private float angle;
    private volatile float lift;
    private volatile float degreesPerTick = IDLE_DEGREES_PER_TICK;
    private volatile boolean ritual;

    private Rendered(ItemDisplay display) {
      this.display = Objects.requireNonNull(display);
    }

    private int stepTicks() {
      return ritual ? RITUAL_STEP_TICKS : IDLE_STEP_TICKS;
    }
  }
}
