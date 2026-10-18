package art.arcane.adapt.api.golem.gui;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CrookGestures<T> {
  public static final long LINK_WINDOW_MILLIS = 30_000L;
  public static final long ENTITY_CLICK_ECHO_MILLIS = 150L;

  private final Map<UUID, Link<T>> links = new ConcurrentHashMap<>();
  private final Map<UUID, Long> entityClicks = new ConcurrentHashMap<>();

  public static Action onEntity(boolean ownGolem) {
    return ownGolem ? Action.GOLEM_GUI : Action.NONE;
  }

  public static Action onAir(boolean sneaking, boolean entityClickEcho) {
    return sneaking && !entityClickEcho ? Action.ROSTER_GUI : Action.NONE;
  }

  public static Action onLeftAir(boolean sneaking, boolean entityClickEcho) {
    if (entityClickEcho) {
      return Action.NONE;
    }
    return sneaking ? Action.CYCLE_SIGNAL : Action.SEND_SIGNAL;
  }

  public static Action onBlock(boolean linkArmed, boolean container) {
    return linkArmed && container ? Action.LINK_CHEST : Action.NONE;
  }

  public void armLink(UUID player, T target, long nowMillis) {
    links.put(player, new Link<>(target, nowMillis + LINK_WINDOW_MILLIS));
  }

  public Optional<T> linkTarget(UUID player, long nowMillis) {
    Link<T> link = links.get(player);
    if (link == null) {
      return Optional.empty();
    }
    if (nowMillis > link.expiresAt()) {
      links.remove(player, link);
      return Optional.empty();
    }
    return Optional.of(link.target());
  }

  public void clearLink(UUID player) {
    links.remove(player);
  }

  public void entityClicked(UUID player, long nowMillis) {
    entityClicks.put(player, nowMillis);
  }

  public boolean isEntityClickEcho(UUID player, long nowMillis) {
    Long clickedAt = entityClicks.get(player);
    return clickedAt != null && nowMillis - clickedAt <= ENTITY_CLICK_ECHO_MILLIS;
  }

  public void forget(UUID player) {
    links.remove(player);
    entityClicks.remove(player);
  }

  public enum Action {
    NONE,
    GOLEM_GUI,
    ROSTER_GUI,
    LINK_CHEST,
    SEND_SIGNAL,
    CYCLE_SIGNAL
  }

  private record Link<T>(T target, long expiresAt) {
  }
}
