package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.node.NodeFilters;
import art.arcane.adapt.api.golem.node.NodeItemTags;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRules;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ConfigMessages;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerChatEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

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
    LanguageAudience.run(player.uuid(), () -> {
      GolemGuiSupport.message(player, C.AQUA + AdaptLanguage.text(GolemancyMessages.NODE_TAG_PROMPT));
      GolemGuiSupport.message(player, AdaptLanguage.text(ConfigMessages.TYPE_CANCEL));
    });
    if (!Tasks.runEntity(player, () -> LanguageAudience.run(player.uuid(), () -> expire(player, session)), TIMEOUT_TICKS)) {
      sessions.remove(player.uuid(), session);
    }
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
    if (!Tasks.runEntity(player, () -> LanguageAudience.run(player.uuid(), () -> submit(player, session, message)))) {
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
      GolemGuiSupport.message(player, C.YELLOW + AdaptLanguage.text(GolemancyMessages.NODE_TAG_CANCELLED));
      NodeGui.open(player, session.key(), this, areas);
      return;
    }
    Optional<String> tag = NodeFilters.normalizeTag(message).filter(tags::exists);
    if (tag.isEmpty()) {
      GolemGuiSupport.message(player, C.RED + AdaptLanguage.text(GolemancyMessages.NODE_TAG_INVALID, untrusted("tag", message)));
      GolemGuiSupport.message(player, AdaptLanguage.text(ConfigMessages.TRY_AGAIN_OR_CANCEL));
      return;
    }
    sessions.remove(playerId, session);
    int level = NodeGui.level(player);
    if (!NodeRules.allowsBlacklistAndTags(level)) {
      NodeGui.open(player, session.key(), this, areas);
      return;
    }
    int slots = NodeGui.filterSlots(level);
    String entry = tag.get();
    NodeGui.openAfter(player, session.key(), this, areas, current -> {
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

  private void expire(PlayerRef player, Session session) {
    if (sessions.remove(player.uuid(), session) && player.isOnline()) {
      GolemGuiSupport.message(player, C.RED + AdaptLanguage.text(GolemancyMessages.NODE_TAG_TIMED_OUT));
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
