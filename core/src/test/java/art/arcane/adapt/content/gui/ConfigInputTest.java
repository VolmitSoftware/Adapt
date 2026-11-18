package art.arcane.adapt.content.gui;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerChatEvent;
import art.arcane.adapt.util.common.scheduling.Tasks;
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

class ConfigInputTest {
  @Test
  void replacedSessionRejectsQueuedCompletionFromPreviousSession() {
    ConfigInput service = new ConfigInput();
    PlayerRef player = mock(PlayerRef.class);
    UUID playerId = UUID.randomUUID();
    AtomicReference<Runnable> queuedCompletion = new AtomicReference<>();
    when(player.uuid()).thenReturn(playerId);
    when(player.isOnline()).thenReturn(true);
    PlayerChatEvent event = new PlayerChatEvent(player, "first-value", true);

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class);
         MockedStatic<ConfigGui> configGui = mockStatic(ConfigGui.class)) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        queuedCompletion.set(invocation.getArgument(1));
        return true;
      });
      configGui.when(() -> ConfigGui.typeName(String.class)).thenReturn("text");
      configGui.when(() -> ConfigGui.parseInputValue(String.class, "first-value"))
          .thenReturn(ConfigGui.ParseResult.ok("old-value"));

      service.beginSession(player, "first.path", "first", 0, String.class, "first");
      service.onChat(event);
      service.beginSession(player, "second.path", "second", 0, String.class, "second");

      queuedCompletion.get().run();

      configGui.verify(
          () -> ConfigGui.confirmAndApply(player, "first", 0, "first.path", "old-value"),
          never()
      );
    }
  }
}
