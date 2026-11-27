package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Rift Visage's calm: an enderman that turns on the player is called off, with a stare credited once per window. */
public final class RiftEndermanCalm {
  private static final long STARE_STAT_WINDOW_MILLIS = 10_000L;

  private final Adaptation<?> owner;
  private final Cooldowns aversionThrottle;
  private final Consumer<PlayerRef> onStare;
  private final Map<UUID, Long> stareCredits = new ConcurrentHashMap<>();

  /** {@code onStare} runs once per enderman per window. */
  public RiftEndermanCalm(Adaptation<?> owner, Cooldowns aversionThrottle, Consumer<PlayerRef> onStare) {
    this.owner = owner;
    this.aversionThrottle = aversionThrottle;
    this.onStare = onStare;
  }

  /** The targeted player when an enderman is turning on one, else null. */
  public static PlayerRef endermanTarget(EntityTargetEvent event) {
    return event.entity().type().equals(RiftVisage.ENDERMAN) && event.target() instanceof PlayerRef player ? player : null;
  }

  public void calm(EntityTargetEvent event, PlayerRef player) {
    EntityRef entity = event.entity();
    event.setCancelled(true);
    long now = System.currentTimeMillis();
    Long lastCredit = stareCredits.get(entity.uuid());
    if (lastCredit == null || now - lastCredit >= STARE_STAT_WINDOW_MILLIS) {
      stareCredits.put(entity.uuid(), now);
      onStare.accept(player);
    }

    if (aversionThrottle.isReady(player.uuid(), 2000L)) {
      aversionThrottle.mark(player.uuid());
      RiftVisage.aversionFx(owner, entity, player);
    }
  }

  public void prune() {
    long now = System.currentTimeMillis();
    stareCredits.values().removeIf((Long at) -> now - at >= STARE_STAT_WINDOW_MILLIS);
  }
}
