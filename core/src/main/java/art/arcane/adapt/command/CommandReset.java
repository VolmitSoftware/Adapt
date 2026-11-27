package art.arcane.adapt.command;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.director.specialhandlers.NullableOfflinePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

@Director(name = "reset", origin = DirectorOrigin.BOTH, description = "Permanently delete all Adapt data for a player", descriptionKey = "adapt.command.help.permanently_delete_all_adapt_data_for_a_player")
public class CommandReset {
  private static final long CONFIRMATION_TIMEOUT_MS = 30_000;

  private final ResetConfirmationTracker pendingConfirmations =
      new ResetConfirmationTracker(CONFIRMATION_TIMEOUT_MS);

  @Director(description = "Permanently delete all Adapt data for a player. Requires adapt.clear. Run twice to confirm.", descriptionKey = "adapt.command.help.permanently_delete_all_adapt_data_for_a_player_requires_adapt_clear_run_twice_to_confirm")
  public void confirm(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullableOfflinePlayerHandler.class, descriptionKey = "adapt.command.help.target_player_defaults_to_you")
      KnownPlayer player
  ) {
    CommandSource sender = CommandContext.source();
    if (!CommandContext.hasPermission("adapt.clear")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.clear")))
          .send(sender);
      return;
    }

    PlayerRef senderPlayer = sender.player().orElse(null);
    KnownPlayer target = player;
    if (target == null && senderPlayer == null) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.PLAYER_REQUIRED_FROM_CONSOLE))
          .send(sender);
      return;
    } else if (target == null) {
      target = KnownPlayer.of(senderPlayer);
    }

    UUID senderUuid = senderPlayer != null ? senderPlayer.uuid() : new UUID(0, 0);
    UUID targetUuid = target.uuid();
    String targetName = target.displayName();
    long now = System.currentTimeMillis();

    if (pendingConfirmations.confirmOrRecord(senderUuid, targetUuid, now)) {
      ResetFeedback feedback = new ResetFeedback(sender, sender.name(), senderUuid, targetUuid, targetName);
      PlayerRef onlineTarget = target.online();
      if (onlineTarget != null) {
        if (!CommandTargetExecutor.run(
            onlineTarget,
            () -> resetPlayer(feedback),
            sender
        )) {
          pendingConfirmations.record(senderUuid, targetUuid, System.currentTimeMillis());
        }
      } else {
        resetPlayer(feedback);
      }
      return;
    }

    FConst.error(AdaptLanguage.text(CommandRuntimeMessages.RESET_WARNING, untrusted("player", targetName)))
        .send(sender);
    FConst.error(AdaptLanguage.text(CommandRuntimeMessages.RESET_INCLUDES)).send(sender);
    FConst.error(AdaptLanguage.text(CommandRuntimeMessages.RESET_CONFIRM)).send(sender);
  }

  private void resetPlayer(ResetFeedback feedback) {
    CompletableFuture<AdaptServer.PlayerDataResetResult> completion =
        AdaptCore.get().getAdaptServer().resetPlayerData(feedback.targetUuid());
    completion.thenAccept(resetResult -> completeResetPlayer(feedback, resetResult));
  }

  private void completeResetPlayer(ResetFeedback feedback, AdaptServer.PlayerDataResetResult resetResult) {
    if (resetResult == AdaptServer.PlayerDataResetResult.DISPATCH_REJECTED) {
      pendingConfirmations.record(feedback.senderUuid(), feedback.targetUuid(), System.currentTimeMillis());
      CommandTargetExecutor.send(feedback.sender(),
          FConst.error(AdaptLanguage.text(CommandRuntimeMessages.TARGET_DISPATCH_FAILED)));
      return;
    }
    boolean live = resetResult == AdaptServer.PlayerDataResetResult.LIVE;
    AdaptLog.info("Sender " + feedback.senderName() + " reset all Adapt data for " + feedback.targetName()
        + (live ? " (live)" : " (offline)"));

    PlayerRef completedTarget = live ? AdaptCore.platform().host().player(feedback.targetUuid()) : null;
    if (completedTarget != null) {
      CommandTargetExecutor.send(new PlayerCommandSource(completedTarget),
          FConst.success(AdaptLanguage.text(RuntimeMessages.DATA_DELETED_KICK)));
    }

    CommandTargetExecutor.send(feedback.sender(), FConst.success(AdaptLanguage.text(
        CommandRuntimeMessages.RESET_DELETED,
        untrusted("player", feedback.targetName())
    )));
  }

  private record ResetFeedback(CommandSource sender, String senderName, UUID senderUuid,
                               UUID targetUuid, String targetName) {
  }
}
