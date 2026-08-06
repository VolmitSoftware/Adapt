package art.arcane.adapt.command;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.director.specialhandlers.NullableOfflinePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

@Director(name = "reset", origin = DirectorOrigin.BOTH, description = "Permanently delete all Adapt data for a player", descriptionKey = "command.help.permanently_delete_all_adapt_data_for_a_player")
public class CommandReset {
  private static final Map<UUID, PendingReset> pendingConfirmations = new HashMap<>();
  private static final long CONFIRMATION_TIMEOUT_MS = 30_000;

  @Director(description = "Permanently delete all Adapt data for a player. Requires op. Run twice to confirm.", descriptionKey = "command.help.permanently_delete_all_adapt_data_for_a_player_requires_op_run_twice_to_confirm")
  public void confirm(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullableOfflinePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      OfflinePlayer player
  ) {
    if (!BukkitDirectorContext.sender().isOp()) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.OPERATOR_ONLY)).send(BukkitDirectorContext.sender());
      return;
    }

    OfflinePlayer target = player;
    if (target == null && BukkitDirectorContext.isConsole()) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.PLAYER_REQUIRED_FROM_CONSOLE))
          .send(BukkitDirectorContext.sender());
      return;
    } else if (target == null) {
      target = BukkitDirectorContext.player();
    }

    UUID senderUuid = BukkitDirectorContext.isPlayer() ? BukkitDirectorContext.player().getUniqueId() : new UUID(0, 0);
    UUID targetUuid = target.getUniqueId();
    String targetName = target.getName() == null ? targetUuid.toString() : target.getName();
    long now = System.currentTimeMillis();

    PendingReset pending = pendingConfirmations.get(senderUuid);
    if (pending != null && pending.targetUuid.equals(targetUuid) && now - pending.timestamp < CONFIRMATION_TIMEOUT_MS) {
      pendingConfirmations.remove(senderUuid);

      boolean live = Adapt.instance.getAdaptServer().resetPlayerData(targetUuid);
      Adapt.info("Operator " + BukkitDirectorContext.name() + " reset all Adapt data for " + targetName
          + (live ? " (live)" : " (offline)"));

      if (live) {
        Player online = target.getPlayer();
        if (online != null) {
          FConst.success(AdaptLanguage.text(RuntimeMessages.DATA_DELETED_KICK)).send(online);
        }
      }

      FConst.success(AdaptLanguage.text(CommandRuntimeMessages.RESET_DELETED, untrusted("player", targetName)))
          .send(BukkitDirectorContext.sender());
      return;
    }

    pendingConfirmations.put(senderUuid, new PendingReset(targetUuid, now));
    FConst.error(AdaptLanguage.text(CommandRuntimeMessages.RESET_WARNING, untrusted("player", targetName)))
        .send(BukkitDirectorContext.sender());
    FConst.error(AdaptLanguage.text(CommandRuntimeMessages.RESET_INCLUDES)).send(BukkitDirectorContext.sender());
    FConst.error(AdaptLanguage.text(CommandRuntimeMessages.RESET_CONFIRM)).send(BukkitDirectorContext.sender());
  }

  private static class PendingReset {
    final UUID targetUuid;
    final long timestamp;

    PendingReset(UUID targetUuid, long timestamp) {
      this.targetUuid = targetUuid;
      this.timestamp = timestamp;
    }
  }
}
