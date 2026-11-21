package art.arcane.adapt.client.server.hud;

import art.arcane.adapt.api.notification.Notification;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.List;
import java.util.Objects;

/**
 * A queued client toast, so it keeps its place among the sounds queued with it. When the player no longer has client
 * toasts by the time it plays, the {@code fallback} notifications (the vanilla popups) play instead.
 */
public record ToastNotification(ServerFrame.Toast toast, String group, List<Notification> fallback)
    implements Notification {
  private static final long DURATION_MILLIS = 100L;

  public ToastNotification {
    Objects.requireNonNull(toast, "toast");
    Objects.requireNonNull(group, "group");
    fallback = List.copyOf(fallback);
  }

  @Override
  public long getTotalDuration() {
    return DURATION_MILLIS;
  }

  @Override
  public String getGroup() {
    return group;
  }

  @Override
  public void play(AdaptPlayer p) {
    PlayerRef player = p.getPlayer();
    if (player == null || ClientToasts.send(player.uuid(), toast)) {
      return;
    }
    for (Notification notification : fallback) {
      notification.play(p);
    }
  }
}
