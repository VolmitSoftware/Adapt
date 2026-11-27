package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.bukkit.platform.BukkitText;
import art.arcane.adapt.pack.link.PackLink;
import art.arcane.adapt.pack.link.PackOffer;
import art.arcane.adapt.pack.link.PackState;
import art.arcane.adapt.util.common.compat.PaperApi;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;

import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@link PackLink} over the Bukkit player pack API ({@code addResourcePack}, {@code removeResourcePack} and
 * {@link PlayerResourcePackStatusEvent}), which Spigot, Paper and Folia share. Sends run on the player's own thread.
 */
public final class BukkitPackLink implements PackLink, Listener {
  /** The pack id reported for a pack the client loaded before join, which no offer of Adapt's ever carries. */
  static final UUID BEFORE_JOIN = new UUID(0L, 0L);
  private static volatile BukkitPackLink installed;

  private final Plugin plugin;
  private volatile StatusListener listener;

  private BukkitPackLink(Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  public static void install(Plugin plugin) {
    BukkitPackLink link = new BukkitPackLink(plugin);
    plugin.getServer().getPluginManager().registerEvents(link, plugin);
    AdaptPorts.install(PackLink.class, link);
    installed = link;
  }

  public static void uninstall() {
    BukkitPackLink link = installed;
    installed = null;
    if (link != null) {
      AdaptPorts.uninstall(PackLink.class);
      HandlerList.unregisterAll(link);
      link.listener = null;
    }
  }

  @Override
  public boolean available() {
    return true;
  }

  @Override
  public void offer(UUID playerId, PackOffer offer) {
    byte[] hash = HexFormat.of().parseHex(offer.sha1Hex());
    String prompt = offer.prompt().isEmpty() ? null : BukkitText.legacy(offer.prompt());
    withPlayer(playerId, (Player player) -> player.addResourcePack(offer.packId(), offer.url(), hash, prompt, false));
  }

  @Override
  public void remove(UUID playerId, UUID packId) {
    withPlayer(playerId, (Player player) -> player.removeResourcePack(packId));
  }

  @Override
  public void listen(StatusListener next) {
    listener = next;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerResourcePackStatusEvent event) {
    StatusListener active = listener;
    if (active != null) {
      active.status(event.getPlayer().getUniqueId(), event.getID(), state(event.getStatus()));
    }
  }

  /**
   * A pack the server sends while the client configures (server.properties) fires no status event, so a merged pack
   * is reported from the status Paper keeps for the player. Spigot keeps none; there a merged pack counts once the
   * client reports a pack sent after join.
   */
  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent event) {
    StatusListener active = listener;
    if (active == null || !PaperApi.isPaper()) {
      return;
    }
    PlayerResourcePackStatusEvent.Status status = PaperPackStatus.of(event.getPlayer());
    if (status != null) {
      active.status(event.getPlayer().getUniqueId(), BEFORE_JOIN, state(status));
    }
  }

  static PackState state(PlayerResourcePackStatusEvent.Status status) {
    return switch (status) {
      case ACCEPTED -> PackState.ACCEPTED;
      case DOWNLOADED -> PackState.DOWNLOADED;
      case SUCCESSFULLY_LOADED -> PackState.LOADED;
      case DECLINED -> PackState.DECLINED;
      case FAILED_DOWNLOAD, INVALID_URL -> PackState.DOWNLOAD_FAILED;
      case FAILED_RELOAD -> PackState.FAILED;
      case DISCARDED -> PackState.DISCARDED;
    };
  }

  private void withPlayer(UUID playerId, Consumer<Player> action) {
    Player player = plugin.getServer().getPlayer(playerId);
    if (player == null || !player.isOnline()) {
      return;
    }
    J.runEntity(player, () -> {
      if (player.isOnline()) {
        action.accept(player);
      }
    });
  }
}
