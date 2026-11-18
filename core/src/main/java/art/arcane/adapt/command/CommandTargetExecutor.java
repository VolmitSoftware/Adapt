package art.arcane.adapt.command;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.command.Feedback;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.Objects;
import java.util.Optional;

public final class CommandTargetExecutor {
  private CommandTargetExecutor() {
  }

  public static boolean run(PlayerRef target, Runnable task) {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(task, "task");
    if (!Tasks.isFoliaThreading() && Tasks.isPrimaryThread()) {
      task.run();
      return true;
    }
    return Tasks.runEntity(target, task);
  }

  public static boolean run(PlayerRef target, Runnable task, CommandSource failureRecipient) {
    if (run(target, task)) {
      return true;
    }
    send(failureRecipient, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.TARGET_DISPATCH_FAILED)));
    return false;
  }

  public static boolean send(CommandSource sender, Feedback feedback) {
    Objects.requireNonNull(sender, "sender");
    Objects.requireNonNull(feedback, "feedback");
    Optional<PlayerRef> player = sender.player();
    if (player.isPresent()) {
      return run(player.get(), () -> feedback.send(sender));
    }
    feedback.send(sender);
    return true;
  }
}
