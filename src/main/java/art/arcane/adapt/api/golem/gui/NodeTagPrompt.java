package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.node.NodeFilters;
import art.arcane.adapt.api.golem.node.NodeItemTags;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRules;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ConfigMessages;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiCloseSuppression;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

public final class NodeTagPrompt implements Listener {
  public static final int TIMEOUT_TICKS = 900;
  private static final String CANCEL = "cancel";

  private final NodeTagLookup tags;
  private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

  public NodeTagPrompt() {
    this(NodeItemTags.shared());
  }

  NodeTagPrompt(NodeTagLookup tags) {
    this.tags = Objects.requireNonNull(tags);
  }

  public void begin(Player player, NodeKey key) {
    Session session = new Session(key);
    sessions.put(player.getUniqueId(), session);
    GuiCloseSuppression.suppress(player);
    player.closeInventory();
    Adapt.messagePlayer(player, C.AQUA + AdaptLanguage.text(GolemancyMessages.NODE_TAG_PROMPT));
    Adapt.messagePlayer(player, AdaptLanguage.text(ConfigMessages.TYPE_CANCEL));
    if (!J.runEntity(player, () -> expire(player, session), TIMEOUT_TICKS)) {
      sessions.remove(player.getUniqueId(), session);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(AsyncPlayerChatEvent e) {
    Player player = e.getPlayer();
    Session session = sessions.get(player.getUniqueId());
    if (session == null) {
      return;
    }
    e.setCancelled(true);
    String message = e.getMessage() == null ? "" : e.getMessage().trim();
    if (!J.runEntity(player, () -> submit(player, session, message))) {
      sessions.remove(player.getUniqueId(), session);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    sessions.remove(e.getPlayer().getUniqueId());
  }

  void submit(Player player, Session session, String message) {
    UUID playerId = player.getUniqueId();
    if (sessions.get(playerId) != session || !player.isOnline()) {
      return;
    }
    if (message.equalsIgnoreCase(CANCEL)) {
      sessions.remove(playerId, session);
      Adapt.messagePlayer(player, C.YELLOW + AdaptLanguage.text(GolemancyMessages.NODE_TAG_CANCELLED));
      NodeGui.open(player, session.key(), this);
      return;
    }
    Optional<String> tag = NodeFilters.normalizeTag(message).filter(tags::exists);
    if (tag.isEmpty()) {
      Adapt.messagePlayer(player, C.RED + AdaptLanguage.text(GolemancyMessages.NODE_TAG_INVALID, untrusted("tag", message)));
      Adapt.messagePlayer(player, AdaptLanguage.text(ConfigMessages.TRY_AGAIN_OR_CANCEL));
      return;
    }
    sessions.remove(playerId, session);
    int level = NodeGui.level(player);
    if (!NodeRules.allowsBlacklistAndTags(level)) {
      NodeGui.open(player, session.key(), this);
      return;
    }
    int slots = NodeGui.filterSlots(level);
    String entry = tag.get();
    NodeGui.openAfter(player, session.key(), this, current -> {
      NodeFilters.FilterEdit change = NodeFilters.toggleTag(current, entry, slots);
      switch (change.outcome()) {
        case ADDED -> GolemGuiSupport.notify(player, GolemancyMessages.NODE_FILTER_ADDED, untrusted("item", entry));
        case REMOVED -> GolemGuiSupport.notify(player, GolemancyMessages.NODE_FILTER_REMOVED, untrusted("item", entry));
        case FULL -> GolemGuiSupport.warn(player, GolemancyMessages.NODE_FILTER_FULL, trusted("count", slots));
        case REPLACED, UNCHANGED -> {
        }
      }
      return change.record();
    });
  }

  private void expire(Player player, Session session) {
    if (sessions.remove(player.getUniqueId(), session) && player.isOnline()) {
      Adapt.messagePlayer(player, C.RED + AdaptLanguage.text(GolemancyMessages.NODE_TAG_TIMED_OUT));
    }
  }

  static final class Session {
    private final NodeKey key;

    Session(NodeKey key) {
      this.key = Objects.requireNonNull(key);
    }

    NodeKey key() {
      return key;
    }
  }
}
