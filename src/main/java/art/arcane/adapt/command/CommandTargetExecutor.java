package art.arcane.adapt.command;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.command.Feedback;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

final class CommandTargetExecutor {
  private CommandTargetExecutor() {
  }

  static boolean run(Player target, Runnable task) {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(task, "task");
    if (!J.isFoliaThreading() && J.isPrimaryThread()) {
      task.run();
      return true;
    }
    return J.runEntity(target, task);
  }

  static boolean run(Player target, Runnable task, CommandSender failureRecipient) {
    if (run(target, task)) {
      return true;
    }
    send(failureRecipient, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.TARGET_DISPATCH_FAILED)));
    return false;
  }

  static boolean send(CommandSender sender, Feedback feedback) {
    Objects.requireNonNull(sender, "sender");
    Objects.requireNonNull(feedback, "feedback");
    if (sender instanceof Player player) {
      return run(player, () -> feedback.send(sender));
    }
    feedback.send(sender);
    return true;
  }
}
