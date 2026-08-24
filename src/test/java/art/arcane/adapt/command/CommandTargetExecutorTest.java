package art.arcane.adapt.command;

import art.arcane.adapt.util.command.Feedback;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CommandTargetExecutorTest {
  @Test
  void runsInlineOnThePrimaryPaperThread() {
    Player target = mock(Player.class);
    AtomicBoolean executed = new AtomicBoolean();

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(J::isPrimaryThread).thenReturn(true);

      assertThat(CommandTargetExecutor.run(target, () -> executed.set(true))).isTrue();

      assertThat(executed).isTrue();
      scheduling.verify(() -> J.runEntity(same(target), any(Runnable.class)), never());
    }
  }

  @Test
  void dispatchesTargetWorkToTheEntitySchedulerOnFolia() {
    Player target = mock(Player.class);
    AtomicBoolean executed = new AtomicBoolean();
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runEntity(same(target), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(1));
        return true;
      });

      assertThat(CommandTargetExecutor.run(target, () -> executed.set(true))).isTrue();

      assertThat(executed).isFalse();
      assertThat(ownerTask.get()).isNotNull();
      ownerTask.get().run();
      assertThat(executed).isTrue();
    }
  }

  @Test
  void reportsRejectedEntityDispatch() throws IOException {
    Player target = mock(Player.class);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runEntity(same(target), any(Runnable.class))).thenReturn(false);

      assertThat(CommandTargetExecutor.run(target, () -> {
      })).isFalse();
    }

    String source = Files.readString(
        Path.of("src/main/java/art/arcane/adapt/command/CommandTargetExecutor.java"));
    assertThat(source).contains("CommandRuntimeMessages.TARGET_DISPATCH_FAILED");
  }

  @Test
  void dispatchesFeedbackBackToAPlayerSenderOwner() {
    Player sender = mock(Player.class);
    Feedback feedback = mock(Feedback.class);
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runEntity(same(sender), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(1));
        return true;
      });

      CommandTargetExecutor.send(sender, feedback);

      verify(feedback, never()).send(sender);
      assertThat(ownerTask.get()).isNotNull();
      ownerTask.get().run();
      verify(feedback).send(sender);
    }
  }

  @Test
  void sendsConsoleFeedbackWithoutAnEntityDispatch() {
    CommandSender sender = mock(CommandSender.class);
    Feedback feedback = mock(Feedback.class);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      CommandTargetExecutor.send(sender, feedback);

      verify(feedback).send(sender);
      scheduling.verifyNoInteractions();
    }
  }
}
