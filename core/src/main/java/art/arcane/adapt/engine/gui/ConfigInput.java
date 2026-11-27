package art.arcane.adapt.engine.gui;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ConfigMessages;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerChatEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.util.common.inventorygui.GuiCloseSuppression;
import art.arcane.adapt.api.AdaptApi;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

/** Chat capture for config values typed after clicking a number or text entry in {@link ConfigGui}. */
public final class ConfigInput {
  private static final long SESSION_TIMEOUT_MS = 45_000L;
  private static volatile ConfigInput current;

  private final Map<UUID, PendingInput> sessions = new ConcurrentHashMap<>();
  private TaskHandle cleanupTask;

  public static ConfigInput current() {
    return current;
  }

  public void start() {
    cleanupTask = AdaptApi.scheduler().repeatGlobal(this::cleanupExpiredSessions, 20, 20);
    current = this;
  }

  public void stop() {
    if (current == this) {
      current = null;
    }
    TaskHandle task = cleanupTask;
    cleanupTask = null;
    if (task != null) {
      task.cancel();
    }
    sessions.clear();
  }

  public void beginSession(PlayerRef player, String valuePath, String returnSectionPath, int returnPage, Class<?> targetType, String label) {
    if (player == null) {
      return;
    }

    PendingInput pending = new PendingInput(
        player.uuid(),
        valuePath,
        returnSectionPath == null ? "" : returnSectionPath,
        Math.max(0, returnPage),
        targetType,
        label == null ? valuePath : label,
        System.currentTimeMillis() + SESSION_TIMEOUT_MS
    );
    sessions.put(player.uuid(), pending);

    GuiCloseSuppression.suppress(player.uuid());
    player.closeInventory();
    ConfigGui.message(player, AdaptLanguage.text(ConfigMessages.ENTER_VALUE, untrusted("label", pending.label())));
    ConfigGui.message(player, AdaptLanguage.text(ConfigMessages.INPUT_PATH, untrusted("path", pending.valuePath())));
    ConfigGui.message(player, AdaptLanguage.text(
        ConfigMessages.EXPECTED_TYPE,
        untrusted("type", ConfigGui.typeName(targetType))
    ));
    ConfigGui.message(player, AdaptLanguage.text(ConfigMessages.TYPE_CANCEL));
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onChat(PlayerChatEvent event) {
    PlayerRef player = event.player();
    PendingInput pending = sessions.get(player.uuid());
    if (pending == null) {
      return;
    }

    event.setCancelled(true);

    if (pending.isExpired()) {
      if (!sessions.remove(player.uuid(), pending)) {
        return;
      }
      runForPlayer(player, pending, false, () -> {
        ConfigGui.message(player, TextFormat.RED + AdaptLanguage.text(ConfigMessages.INPUT_TIMED_OUT));
        ConfigGui.open(player, pending.returnSectionPath(), pending.returnPage());
      });
      return;
    }

    String message = event.message();
    if (message.equalsIgnoreCase("cancel")) {
      if (!sessions.remove(player.uuid(), pending)) {
        return;
      }
      runForPlayer(player, pending, false, () -> {
        ConfigGui.message(player, TextFormat.YELLOW + AdaptLanguage.text(ConfigMessages.EDIT_CANCELLED));
        ConfigGui.open(player, pending.returnSectionPath(), pending.returnPage());
      });
      return;
    }

    ConfigGui.ParseResult parsed = ConfigGui.parseInputValue(pending.targetType(), message);
    if (!parsed.success()) {
      runForPlayer(player, pending, true, () -> {
        ConfigGui.message(player, TextFormat.RED + parsed.error());
        ConfigGui.message(player, AdaptLanguage.text(ConfigMessages.TRY_AGAIN_OR_CANCEL));
      });
      return;
    }

    if (!sessions.remove(player.uuid(), pending)) {
      return;
    }
    Object value = parsed.value();
    runForPlayer(player, pending, false, () -> {
      ConfigGui.confirmAndApply(player, pending.returnSectionPath(), pending.returnPage(), pending.valuePath(), value);
    });
  }

  @Handle
  public void onQuit(PlayerQuitEvent event) {
    sessions.remove(event.player().uuid());
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

  private void runForPlayer(PlayerRef player, PendingInput pending, boolean requireCurrent, Runnable task) {
    if (AdaptApi.scheduler().runEntity(player, () -> {
      PendingInput active = sessions.get(player.uuid());
      if ((requireCurrent && active != pending) || (!requireCurrent && active != null && active != pending)) {
        return;
      }
      if (!player.isOnline()) {
        return;
      }
      task.run();
    })) {
      return;
    }

    sessions.remove(player.uuid(), pending);
    AdaptLog.verbose("Could not schedule config input completion for " + player.uuid());
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
