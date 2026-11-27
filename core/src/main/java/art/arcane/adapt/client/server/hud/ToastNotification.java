package art.arcane.adapt.client.server.hud;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.engine.notification.Notification;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.pack.hud.PackToasts;
import art.arcane.adapt.pack.link.PackAudience;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.List;
import java.util.Objects;

/**
 * A queued toast, so it keeps its place among the sounds queued with it: the client toast, else for a pack viewer the
 * vanilla toast with the pack's icon. When neither reaches the player by the time it plays, the {@code fallback}
 * notifications (the vanilla popups) play instead.
 */
public record ToastNotification(ServerFrame.Toast toast, String group, List<Notification> fallback)
    implements Notification {
  private static final long DURATION_MILLIS = 100L;

  public ToastNotification {
    Objects.requireNonNull(toast, "toast");
    Objects.requireNonNull(group, "group");
    fallback = List.copyOf(fallback);
  }

  /** True when a toast reaches the player: Adapt client toasts, or the vanilla toast for a pack viewer. */
  public static boolean shown(PlayerRef player) {
    return player != null && (ClientToasts.capable(player.uuid()) || PackAudience.DELIVERED.book(player));
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
    AdaptCore core = AdaptCore.current();
    if (core != null && PackToasts.running().show(core.getPlatform().advancements(), player, toast, PackToasts.frame(toast.kind()))) {
      return;
    }
    for (Notification notification : fallback) {
      notification.play(p);
    }
  }
}
