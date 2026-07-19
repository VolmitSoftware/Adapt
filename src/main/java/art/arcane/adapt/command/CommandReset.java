package art.arcane.adapt.command;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Director(name = "reset", origin = DirectorOrigin.BOTH, description = "Permanently delete all Adapt data for a player")
public class CommandReset {
  private static final Map<UUID, PendingReset> pendingConfirmations = new HashMap<>();
  private static final long CONFIRMATION_TIMEOUT_MS = 30_000;

  @Director(description = "Permanently delete all Adapt data for a player. Requires op. Run twice to confirm.")
  public void confirm(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player
  ) {
    if (!BukkitDirectorContext.sender().isOp()) {
      FConst.error("This command can only be run by server operators.").send(BukkitDirectorContext.sender());
      return;
    }

    Player targetPlayer = player;
    if (targetPlayer == null && BukkitDirectorContext.isConsole()) {
      FConst.error("You must specify a player when using this command from console.").send(BukkitDirectorContext.sender());
      return;
    } else if (targetPlayer == null) {
      targetPlayer = BukkitDirectorContext.player();
    }

    UUID senderUuid = BukkitDirectorContext.isPlayer() ? BukkitDirectorContext.player().getUniqueId() : new UUID(0, 0);
    UUID targetUuid = targetPlayer.getUniqueId();
    long now = System.currentTimeMillis();

    PendingReset pending = pendingConfirmations.get(senderUuid);
    if (pending != null && pending.targetUuid.equals(targetUuid) && now - pending.timestamp < CONFIRMATION_TIMEOUT_MS) {
      pendingConfirmations.remove(senderUuid);

      AdaptPlayer adaptPlayer = Adapt.instance.getAdaptServer().getPlayer(targetPlayer);
      adaptPlayer.delete(targetUuid);
      Adapt.info("Operator " + BukkitDirectorContext.name() + " reset all Adapt data for " + targetPlayer.getName());
      FConst.success("All Adapt data for " + targetPlayer.getName() + " has been permanently deleted.").send(BukkitDirectorContext.sender());
      return;
    }

    pendingConfirmations.put(senderUuid, new PendingReset(targetUuid, now));
    FConst.error("WARNING: This will permanently delete ALL Adapt data for " + targetPlayer.getName() + ".").send(BukkitDirectorContext.sender());
    FConst.error("This includes XP, skills, adaptations, discoveries, stats, and advancements.").send(BukkitDirectorContext.sender());
    FConst.error("Run this command again within 30 seconds to confirm.").send(BukkitDirectorContext.sender());
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
