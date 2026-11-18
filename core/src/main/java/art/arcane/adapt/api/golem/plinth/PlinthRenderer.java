package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Quat;
import art.arcane.adapt.platform.value.Transform;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;

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

  static Loc itemAt(WorldRef world, PlinthSpot spot, float lift) {
    return new Loc(world, spot.x() + CENTER, spot.y() + CENTER + HOVER_HEIGHT + lift, spot.z() + CENTER);
  }

  void spawn(WorldRef world, PlinthSpot spot, ItemRef item) {
    despawn(spot);
    ItemDisplayRef display = platform.spawnDisplay(itemAt(world, spot, 0F), item);
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
    // Folia disables plugins on its shutdown thread after the regions stop, where removing an entity
    // throws. The displays are non-persistent, so the chunk save that follows drops them anyway.
    if (platform.regionsHalted()) {
      rendered.clear();
      return;
    }
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
    ItemDisplayRef display = entry.display;
    display.setInterpolationDelay(0);
    display.setInterpolationDuration(interpolationTicks);
    display.setTransform(new Transform(new Vec3(0D, entry.lift, 0D), Quat.axisAngle(0F, 1F, 0F, entry.angle),
        new Vec3(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE), Quat.IDENTITY));
  }

  private void remove(ItemDisplayRef display) {
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
    private final ItemDisplayRef display;
    private volatile long token;
    private float angle;
    private volatile float lift;
    private volatile float degreesPerTick = IDLE_DEGREES_PER_TICK;
    private volatile boolean ritual;

    private Rendered(ItemDisplayRef display) {
      this.display = Objects.requireNonNull(display);
    }

    private int stepTicks() {
      return ritual ? RITUAL_STEP_TICKS : IDLE_STEP_TICKS;
    }
  }
}
