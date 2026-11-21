package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.client.link.ClientLink;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.FrameCodec;
import art.arcane.adapt.client.server.ClientUiService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ClientLink} over the {@code adapt:ui} plugin messaging channel. A client may send its hello before the server
 * sees the channel registration; the accept is then held until {@link PlayerRegisterChannelEvent}. Inbound payloads go
 * to the running {@link ClientUiService}.
 */
public final class BukkitClientLink implements ClientLink, PluginMessageListener, Listener {
  private static volatile BukkitClientLink installed;

  private final Plugin plugin;
  private final Map<UUID, byte[]> heldAccepts = new ConcurrentHashMap<>();

  private BukkitClientLink(Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  public static void install(Plugin plugin) {
    BukkitClientLink link = new BukkitClientLink(plugin);
    Messenger messenger = plugin.getServer().getMessenger();
    messenger.registerOutgoingPluginChannel(plugin, ClientProtocol.CHANNEL);
    messenger.registerIncomingPluginChannel(plugin, ClientProtocol.CHANNEL, link);
    plugin.getServer().getPluginManager().registerEvents(link, plugin);
    AdaptPorts.install(ClientLink.class, link);
    installed = link;
  }

  public static void uninstall() {
    BukkitClientLink link = installed;
    installed = null;
    if (link == null) {
      return;
    }
    AdaptPorts.uninstall(ClientLink.class);
    HandlerList.unregisterAll(link);
    Messenger messenger = link.plugin.getServer().getMessenger();
    messenger.unregisterIncomingPluginChannel(link.plugin, ClientProtocol.CHANNEL, link);
    messenger.unregisterOutgoingPluginChannel(link.plugin, ClientProtocol.CHANNEL);
    link.heldAccepts.clear();
  }

  @Override
  public boolean open(UUID playerId) {
    Player player = plugin.getServer().getPlayer(playerId);
    return player != null && player.isOnline() && player.getListeningPluginChannels().contains(ClientProtocol.CHANNEL);
  }

  @Override
  public void send(UUID playerId, byte[] frame) {
    Player player = plugin.getServer().getPlayer(playerId);
    if (player == null || !player.isOnline()) {
      return;
    }
    if (player.getListeningPluginChannels().contains(ClientProtocol.CHANNEL)) {
      player.sendPluginMessage(plugin, ClientProtocol.CHANNEL, frame);
    } else if (frame.length > 0 && frame[0] == FrameCodec.S2C_ACCEPT) {
      heldAccepts.put(playerId, frame.clone());
    }
  }

  @Override
  public int maxPayloadBytes() {
    return Messenger.MAX_MESSAGE_SIZE;
  }

  @Override
  public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    ClientUiService service = ClientUiService.current();
    if (service != null && ClientProtocol.CHANNEL.equals(channel)) {
      service.receive(player.getUniqueId(), message);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerRegisterChannelEvent event) {
    if (!ClientProtocol.CHANNEL.equals(event.getChannel())) {
      return;
    }
    Player player = event.getPlayer();
    byte[] accept = heldAccepts.remove(player.getUniqueId());
    if (accept != null) {
      player.sendPluginMessage(plugin, ClientProtocol.CHANNEL, accept);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    heldAccepts.remove(event.getPlayer().getUniqueId());
  }
}
