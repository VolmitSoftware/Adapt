package art.arcane.adapt.command;

import art.arcane.adapt.util.command.Feedback;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandTargetExecutorTest {
  @Test
  void runsInlineOnThePrimaryPaperThread() {
    PlayerRef target = mock(PlayerRef.class);
    AtomicBoolean executed = new AtomicBoolean();

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(Tasks::isFoliaThreading).thenReturn(false);
      scheduling.when(Tasks::isPrimaryThread).thenReturn(true);

      assertThat(CommandTargetExecutor.run(target, () -> executed.set(true))).isTrue();

      assertThat(executed).isTrue();
      scheduling.verify(() -> Tasks.runEntity(same(target), any(Runnable.class)), never());
    }
  }

  @Test
  void dispatchesTargetWorkToTheEntitySchedulerOnFolia() {
    PlayerRef target = mock(PlayerRef.class);
    AtomicBoolean executed = new AtomicBoolean();
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(Tasks::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> Tasks.runEntity(same(target), any(Runnable.class))).thenAnswer(invocation -> {
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
    PlayerRef target = mock(PlayerRef.class);

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(Tasks::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> Tasks.runEntity(same(target), any(Runnable.class))).thenReturn(false);

      assertThat(CommandTargetExecutor.run(target, () -> {
      })).isFalse();
    }

    String source = Files.readString(
        Path.of("src/main/java/art/arcane/adapt/command/CommandTargetExecutor.java"));
    assertThat(source).contains("CommandRuntimeMessages.TARGET_DISPATCH_FAILED");
  }

  @Test
  void dispatchesFeedbackBackToAPlayerSenderOwner() {
    PlayerRef owner = mock(PlayerRef.class);
    CommandSource sender = mock(CommandSource.class);
    when(sender.player()).thenReturn(Optional.of(owner));
    Feedback feedback = mock(Feedback.class);
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(Tasks::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> Tasks.runEntity(same(owner), any(Runnable.class))).thenAnswer(invocation -> {
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
    CommandSource sender = mock(CommandSource.class);
    Feedback feedback = mock(Feedback.class);

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      CommandTargetExecutor.send(sender, feedback);

      verify(feedback).send(sender);
      scheduling.verifyNoInteractions();
    }
  }
}
