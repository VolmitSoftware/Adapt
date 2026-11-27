package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.golem.node.NodeFilters;
import art.arcane.adapt.content.golem.node.NodeItemTags;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRules;
import art.arcane.adapt.content.golem.node.NodeTagLookup;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerChatEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuInput;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class NodeTagPrompt {
  public static final int TIMEOUT_TICKS = 900;
  private static final String CANCEL = "cancel";

  private final NodeTagLookup tags;
  private final NodeAreaSelector areas;
  private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

  public NodeTagPrompt(NodeAreaSelector areas) {
    this(NodeItemTags.shared(), areas);
  }

  NodeTagPrompt(NodeTagLookup tags, NodeAreaSelector areas) {
    this.tags = Objects.requireNonNull(tags);
    this.areas = Objects.requireNonNull(areas);
  }

  public void begin(PlayerRef player, NodeKey key) {
    Session session = new Session(key);
    sessions.put(player.uuid(), session);
    player.closeInventory();
    Text.renderFor(player.uuid(), () -> {
      GolemGuiSupport.message(player, TextFormat.AQUA + Text.key("adapt.golemancy.node_message.tag_prompt").resolve());
      GolemGuiSupport.message(player, Text.key("adapt.config.input.type_cancel").resolve());
    });
    if (!AdaptApi.scheduler().runEntity(player, () -> Text.renderFor(player.uuid(), () -> expire(player, session)), TIMEOUT_TICKS)) {
      sessions.remove(player.uuid(), session);
    }
  }

  /** Adds or removes a tag typed into the open node menu, with no chat prompt; an invalid tag leaves the menu open. */
  public MenuInput input(NodeKey key) {
    Objects.requireNonNull(key);
    return (MenuClick context, String text) -> {
      PlayerRef player = context.viewer();
      Text.renderFor(player.uuid(), () -> enter(player, key, text.trim()));
    };
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerChatEvent e) {
    PlayerRef player = e.player();
    Session session = sessions.get(player.uuid());
    if (session == null) {
      return;
    }
    e.setCancelled(true);
    String message = e.message().trim();
    if (!AdaptApi.scheduler().runEntity(player, () -> Text.renderFor(player.uuid(), () -> submit(player, session, message)))) {
      sessions.remove(player.uuid(), session);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    sessions.remove(e.player().uuid());
  }

  void submit(PlayerRef player, Session session, String message) {
    UUID playerId = player.uuid();
    if (sessions.get(playerId) != session || !player.isOnline()) {
      return;
    }
    if (message.equalsIgnoreCase(CANCEL)) {
      sessions.remove(playerId, session);
      GolemGuiSupport.message(player, TextFormat.YELLOW + Text.key("adapt.golemancy.node_message.tag_cancelled").resolve());
      NodeGui.open(player, session.key(), this, areas);
      return;
    }
    Optional<String> tag = tag(player, message);
    if (tag.isEmpty()) {
      GolemGuiSupport.message(player, Text.key("adapt.config.input.try_again_or_cancel").resolve());
      return;
    }
    sessions.remove(playerId, session);
    apply(player, session.key(), tag.get());
  }

  private void enter(PlayerRef player, NodeKey key, String message) {
    if (player.isOnline()) {
      tag(player, message).ifPresent(entry -> apply(player, key, entry));
    }
  }

  private Optional<String> tag(PlayerRef player, String message) {
    Optional<String> tag = NodeFilters.normalizeTag(message).filter(tags::exists);
    if (tag.isEmpty()) {
      GolemGuiSupport.message(player, TextFormat.RED + Text.key("adapt.golemancy.node_message.tag_invalid").with("tag", message).resolve());
    }
    return tag;
  }

  private void apply(PlayerRef player, NodeKey key, String entry) {
    int level = NodeGui.level(player);
    if (!NodeRules.allowsBlacklistAndTags(level)) {
      NodeGui.open(player, key, this, areas);
      return;
    }
    int slots = NodeGui.filterSlots(level);
    NodeGui.openAfter(player, key, this, areas, current -> {
      NodeFilters.FilterEdit change = NodeFilters.toggleTag(current, entry, slots);
      switch (change.outcome()) {
        case ADDED -> GolemGuiSupport.notify(player, Text.key("adapt.golemancy.node_message.filter_added").with("item", entry));
        case REMOVED -> GolemGuiSupport.notify(player, Text.key("adapt.golemancy.node_message.filter_removed").with("item", entry));
        case FULL -> GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node_message.filter_full").withMarkup("count", String.valueOf(slots)));
        case REPLACED, UNCHANGED -> {
        }
      }
      return change.record();
    });
  }

  private void expire(PlayerRef player, Session session) {
    if (sessions.remove(player.uuid(), session) && player.isOnline()) {
      GolemGuiSupport.message(player, TextFormat.RED + Text.key("adapt.golemancy.node_message.tag_timed_out").resolve());
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
