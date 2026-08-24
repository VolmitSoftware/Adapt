package art.arcane.adapt.service;

import art.arcane.adapt.content.gui.ConfigGui;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class ConfigInputSVCTest {
  @Test
  void replacedSessionRejectsQueuedCompletionFromPreviousSession() {
    ConfigInputSVC service = new ConfigInputSVC();
    Player player = mock(Player.class);
    AsyncPlayerChatEvent event = mock(AsyncPlayerChatEvent.class);
    UUID playerId = UUID.randomUUID();
    AtomicReference<Runnable> queuedCompletion = new AtomicReference<>();
    when(player.getUniqueId()).thenReturn(playerId);
    when(player.isOnline()).thenReturn(true);
    when(event.getPlayer()).thenReturn(player);
    when(event.getMessage()).thenReturn("first-value");

    try (MockedStatic<J> scheduling = mockStatic(J.class);
         MockedStatic<ConfigGui> configGui = mockStatic(ConfigGui.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        queuedCompletion.set(invocation.getArgument(1));
        return true;
      });
      configGui.when(() -> ConfigGui.typeName(String.class)).thenReturn("text");
      configGui.when(() -> ConfigGui.parseInputValue(String.class, "first-value"))
          .thenReturn(ConfigGui.ParseResult.ok("old-value"));

      service.beginSession(player, "first.path", "first", 0, String.class, "first");
      service.onAsyncChat(event);
      service.beginSession(player, "second.path", "second", 0, String.class, "second");

      queuedCompletion.get().run();

      configGui.verify(
          () -> ConfigGui.confirmAndApply(player, "first", 0, "first.path", "old-value"),
          never()
      );
    }
  }
}
