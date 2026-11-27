package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.viewer.ViewerDisplayPort;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Wounded prey and the private blood trail each one draws for its hunter, kept per owning adaptation. */
public final class HunterWounds {
  private static final Id TRAIL_BLOCK = Id.minecraft("red_stained_glass");
  private final Adaptation<?> owner;
  private final Consumer<PlayerRef> onFollowed;
  private final Map<UUID, Wound> wounds = new ConcurrentHashMap<>();

  public HunterWounds(Adaptation<?> owner, Consumer<PlayerRef> onFollowed) {
    this.owner = owner;
    this.onFollowed = onFollowed;
  }

  public record Style(double thickness, int displayDurationTicks) {
  }

  public static boolean isWounded(double healthAfter, double maxHealth, double fraction) {
    return healthAfter > 0 && maxHealth > 0 && healthAfter <= maxHealth * fraction;
  }

  /**
   * Starts or refreshes the trail behind {@code victim}. Returns true when the wound is new to this hunter.
   */
  public boolean mark(PlayerRef hunter, LivingRef victim, long durationTicks, double range, int maxTracked) {
    long expiresAt = System.currentTimeMillis() + (durationTicks * 50L);
    Wound previous = wounds.put(victim.uuid(), new Wound(hunter.uuid(), expiresAt, range, victim.world().uid()));
    if (wounds.size() > Math.max(1, maxTracked)) {
      pruneOldest();
    }
    return previous == null || !previous.hunterId.equals(hunter.uuid());
  }

  public boolean isEmpty() {
    return wounds.isEmpty();
  }

  public void tick(int maxTracked, Style style) {
    long now = System.currentTimeMillis();
    int processed = 0;
    int limit = Math.max(1, maxTracked);
    Iterator<Map.Entry<UUID, Wound>> iterator = wounds.entrySet().iterator();
    while (iterator.hasNext() && processed < limit) {
      Map.Entry<UUID, Wound> entry = iterator.next();
      Wound wound = entry.getValue();
      if (wound.expiresAt <= now) {
        iterator.remove();
        continue;
      }
      processed++;
      render(entry.getKey(), wound, style);
    }
  }

  /** The nearest live prey whose trail {@code hunter} can see, or null. */
  public EntityRef nearestPrey(PlayerRef hunter) {
    PlatformHost host = AdaptApi.platform().host();
    Loc at = hunter.location();
    long now = System.currentTimeMillis();
    EntityRef best = null;
    double bestSquared = Double.MAX_VALUE;
    for (Map.Entry<UUID, Wound> entry : wounds.entrySet()) {
      Wound wound = entry.getValue();
      if (!wound.hunterId.equals(hunter.uuid()) || wound.expiresAt <= now || !hunter.world().uid().equals(wound.worldId)) {
        continue;
      }
      EntityRef prey = host.entity(entry.getKey());
      if (prey == null || !prey.isValid() || prey.isDead()) {
        continue;
      }
      double squared = at.distanceSquared(prey.location());
      if (squared <= wound.range * wound.range && squared < bestSquared) {
        best = prey;
        bestSquared = squared;
      }
    }
    return best;
  }

  public void forget(UUID playerId) {
    wounds.entrySet().removeIf((Map.Entry<UUID, Wound> entry) -> entry.getValue().hunterId.equals(playerId));
    displays().clearViewer(owner.getId().toString(), playerId);
  }

  public void clear() {
    wounds.clear();
    displays().clearChannel(owner.getId().toString());
  }

  private void pruneOldest() {
    UUID oldestKey = null;
    long oldestExpiry = Long.MAX_VALUE;
    for (Map.Entry<UUID, Wound> entry : wounds.entrySet()) {
      if (entry.getValue().expiresAt < oldestExpiry) {
        oldestExpiry = entry.getValue().expiresAt;
        oldestKey = entry.getKey();
      }
    }
    if (oldestKey != null) {
      wounds.remove(oldestKey);
    }
  }

  private void render(UUID entityId, Wound wound, Style style) {
    PlatformHost host = AdaptApi.platform().host();
    EntityRef mob = host.entity(entityId);
    PlayerRef hunter = host.player(wound.hunterId);
    if (mob == null || hunter == null) {
      wounds.remove(entityId);
      return;
    }

    AdaptApi.scheduler().runEntity(mob, () -> {
      if (!mob.isValid()) {
        wounds.remove(entityId);
        return;
      }
      Loc current = mob.location().add(0, mob.height() * 0.35D, 0);
      AdaptApi.scheduler().runEntity(hunter, () -> emitTrail(hunter, entityId, wound, current, style));
    });
  }

  private void emitTrail(PlayerRef hunter, UUID entityId, Wound wound, Loc current, Style style) {
    if (!hunter.isOnline() || wounds.get(entityId) != wound || owner.getActiveLevel(hunter) <= 0
        || !owner.preferenceEnabled(hunter, HunterPreferences.TRAIL)) {
      return;
    }

    WorldRef hunterWorld = hunter.world();
    if (!hunterWorld.uid().equals(wound.worldId) || current.world() == null || !hunterWorld.equals(current.world())) {
      wound.lastRender = null;
      return;
    }

    Loc hunterLocation = hunter.location();
    if (hunterLocation.distanceSquared(current) > wound.range * wound.range) {
      wound.lastRender = null;
      return;
    }

    if (!wound.followed) {
      wound.followed = true;
      onFollowed.accept(hunter);
    }

    Loc previous = wound.lastRender;
    if (previous != null && previous.world() != null && previous.world().equals(current.world())) {
      if (previous.distanceSquared(current) > 1.0E-5D) {
        displays().showLine(
            owner.getId().toString(),
            "wound-" + entityId,
            hunter,
            previous,
            current,
            AdaptApi.platform().host().items().blockState(TRAIL_BLOCK),
            owner.preference(hunter, HunterPreferences.GLOW).color(),
            style.thickness(),
            style.displayDurationTicks()
        );
      }
    }
    wound.lastRender = current;
  }

  private static ViewerDisplayPort displays() {
    return ViewerDisplayPort.current();
  }

  private static final class Wound {
    private final UUID hunterId;
    private final long expiresAt;
    private final double range;
    private final UUID worldId;
    private volatile Loc lastRender;
    private volatile boolean followed;

    private Wound(UUID hunterId, long expiresAt, double range, UUID worldId) {
      this.hunterId = hunterId;
      this.expiresAt = expiresAt;
      this.range = range;
      this.worldId = worldId;
    }
  }
}
