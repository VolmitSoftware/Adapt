package art.arcane.adapt.service;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.content.gui.ConfigGui;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ConfigMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiCloseSuppression;
import art.arcane.adapt.util.common.plugin.AdaptService;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

public class ConfigInputSVC implements AdaptService {
  private static final long SESSION_TIMEOUT_MS = 45_000L;

  private final Map<UUID, PendingInput> sessions = new ConcurrentHashMap<>();
  private int cleanupTaskId = -1;

  @Override
  public void onEnable() {
    cleanupTaskId = J.sr(this::cleanupExpiredSessions, 20);
  }

  @Override
  public void onDisable() {
    if (cleanupTaskId != -1) {
      J.csr(cleanupTaskId);
      cleanupTaskId = -1;
    }
    sessions.clear();
  }

  public void beginSession(Player player, String valuePath, String returnSectionPath, int returnPage, Class<?> targetType, String label) {
    if (player == null) {
      return;
    }

    PendingInput pending = new PendingInput(
        player.getUniqueId(),
        valuePath,
        returnSectionPath == null ? "" : returnSectionPath,
        Math.max(0, returnPage),
        targetType,
        label == null ? valuePath : label,
        System.currentTimeMillis() + SESSION_TIMEOUT_MS
    );
    sessions.put(player.getUniqueId(), pending);

    GuiCloseSuppression.suppress(player);
    player.closeInventory();
    Adapt.messagePlayer(player, AdaptLanguage.text(ConfigMessages.ENTER_VALUE, untrusted("label", pending.label())));
    Adapt.messagePlayer(player, AdaptLanguage.text(ConfigMessages.INPUT_PATH, untrusted("path", pending.valuePath())));
    Adapt.messagePlayer(player, AdaptLanguage.text(
        ConfigMessages.EXPECTED_TYPE,
        untrusted("type", ConfigGui.typeName(targetType))
    ));
    Adapt.messagePlayer(player, AdaptLanguage.text(ConfigMessages.TYPE_CANCEL));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onAsyncChat(AsyncPlayerChatEvent event) {
    Player player = event.getPlayer();
    PendingInput pending = sessions.get(player.getUniqueId());
    if (pending == null) {
      return;
    }

    event.setCancelled(true);

    if (pending.isExpired()) {
      if (!sessions.remove(player.getUniqueId(), pending)) {
        return;
      }
      runForPlayer(player, pending, false, () -> {
        Adapt.messagePlayer(player, C.RED + AdaptLanguage.text(ConfigMessages.INPUT_TIMED_OUT));
        ConfigGui.open(player, pending.returnSectionPath(), pending.returnPage());
      });
      return;
    }

    String message = event.getMessage() == null ? "" : event.getMessage();
    if (message.equalsIgnoreCase("cancel")) {
      if (!sessions.remove(player.getUniqueId(), pending)) {
        return;
      }
      runForPlayer(player, pending, false, () -> {
        Adapt.messagePlayer(player, C.YELLOW + AdaptLanguage.text(ConfigMessages.EDIT_CANCELLED));
        ConfigGui.open(player, pending.returnSectionPath(), pending.returnPage());
      });
      return;
    }

    ConfigGui.ParseResult parsed = ConfigGui.parseInputValue(pending.targetType(), message);
    if (!parsed.success()) {
      runForPlayer(player, pending, true, () -> {
        Adapt.messagePlayer(player, C.RED + parsed.error());
        Adapt.messagePlayer(player, AdaptLanguage.text(ConfigMessages.TRY_AGAIN_OR_CANCEL));
      });
      return;
    }

    if (!sessions.remove(player.getUniqueId(), pending)) {
      return;
    }
    Object value = parsed.value();
    runForPlayer(player, pending, false, () -> {
      ConfigGui.confirmAndApply(player, pending.returnSectionPath(), pending.returnPage(), pending.valuePath(), value);
    });
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    sessions.remove(event.getPlayer().getUniqueId());
  }

  private void cleanupExpiredSessions() {
    long now = System.currentTimeMillis();
    for (Map.Entry<UUID, PendingInput> entry : sessions.entrySet()) {
      PendingInput pending = entry.getValue();
      if (pending.expiresAt() <= now) {
        sessions.remove(entry.getKey(), pending);
      }
    }
  }

  private void runForPlayer(Player player, PendingInput pending, boolean requireCurrent, Runnable task) {
    if (J.runEntity(player, () -> {
      PendingInput current = sessions.get(player.getUniqueId());
      if ((requireCurrent && current != pending) || (!requireCurrent && current != null && current != pending)) {
        return;
      }
      if (!player.isOnline()) {
        return;
      }
      task.run();
    })) {
      return;
    }

    sessions.remove(player.getUniqueId(), pending);
    Adapt.verbose("Could not schedule config input completion for " + player.getUniqueId());
  }

  private record PendingInput(
      UUID playerId,
      String valuePath,
      String returnSectionPath,
      int returnPage,
      Class<?> targetType,
      String label,
      long expiresAt
  ) {
    private boolean isExpired() {
      return System.currentTimeMillis() >= expiresAt;
    }
  }
}
