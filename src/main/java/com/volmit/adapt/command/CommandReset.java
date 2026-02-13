package com.volmit.adapt.command;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.util.command.FConst;
import com.volmit.adapt.util.decree.DecreeExecutor;
import com.volmit.adapt.util.decree.DecreeOrigin;
import com.volmit.adapt.util.decree.annotations.Decree;
import com.volmit.adapt.util.decree.annotations.Param;
import com.volmit.adapt.util.decree.specialhandlers.NullablePlayerHandler;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Decree(name = "reset", origin = DecreeOrigin.BOTH, description = "Permanently delete all Adapt data for a player")
public class CommandReset implements DecreeExecutor {
    private static final Map<UUID, PendingReset> pendingConfirmations = new HashMap<>();
    private static final long CONFIRMATION_TIMEOUT_MS = 30_000;

    @Decree(description = "Permanently delete all Adapt data for a player. Requires op. Run twice to confirm.")
    public void confirm(
        @Param(description = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player
    ) {
        if (!sender().isOp()) {
            FConst.error("This command can only be run by server operators.").send(sender());
            return;
        }

        Player targetPlayer = player;
        if (targetPlayer == null && sender().isConsole()) {
            FConst.error("You must specify a player when using this command from console.").send(sender());
            return;
        } else if (targetPlayer == null) {
            targetPlayer = player();
        }

        UUID senderUuid = sender().isPlayer() ? player().getUniqueId() : new UUID(0, 0);
        UUID targetUuid = targetPlayer.getUniqueId();
        long now = System.currentTimeMillis();

        PendingReset pending = pendingConfirmations.get(senderUuid);
        if (pending != null && pending.targetUuid.equals(targetUuid) && now - pending.timestamp < CONFIRMATION_TIMEOUT_MS) {
            pendingConfirmations.remove(senderUuid);

            AdaptPlayer adaptPlayer = Adapt.instance.getAdaptServer().getPlayer(targetPlayer);
            adaptPlayer.delete(targetUuid);
            Adapt.info("Operator " + sender().getName() + " reset all Adapt data for " + targetPlayer.getName());
            FConst.success("All Adapt data for " + targetPlayer.getName() + " has been permanently deleted.").send(sender());
            return;
        }

        pendingConfirmations.put(senderUuid, new PendingReset(targetUuid, now));
        FConst.error("WARNING: This will permanently delete ALL Adapt data for " + targetPlayer.getName() + ".").send(sender());
        FConst.error("This includes XP, skills, adaptations, discoveries, stats, and advancements.").send(sender());
        FConst.error("Run this command again within 30 seconds to confirm.").send(sender());
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
