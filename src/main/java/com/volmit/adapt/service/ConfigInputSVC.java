package com.volmit.adapt.service;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.content.gui.ConfigGui;
import com.volmit.adapt.util.AdaptService;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.J;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigInputSVC implements AdaptService {
    private static final long SESSION_TIMEOUT_MS = 45_000L;

    private final Map<UUID, PendingInput> sessions = new ConcurrentHashMap<>();
    private int cleanupTaskId = -1;

    @Override
    public void onEnable() {
        cleanupTaskId = J.sr(this::cleanupExpiredSessions, 20);
    }

    @Override
    public void onDisable() {
        if (cleanupTaskId != -1) {
            J.csr(cleanupTaskId);
            cleanupTaskId = -1;
        }
        sessions.clear();
    }

    public void beginSession(Player player, String valuePath, String returnSectionPath, int returnPage, Class<?> targetType, String label) {
        if (player == null) {
            return;
        }

        PendingInput pending = new PendingInput(
                player.getUniqueId(),
                valuePath,
                returnSectionPath == null ? "" : returnSectionPath,
                Math.max(0, returnPage),
                targetType,
                label == null ? valuePath : label,
                System.currentTimeMillis() + SESSION_TIMEOUT_MS
        );
        sessions.put(player.getUniqueId(), pending);

        ConfigGui.suppressClose(player);
        player.closeInventory();
        Adapt.messagePlayer(player, C.AQUA + "Enter value for " + C.WHITE + pending.label());
        Adapt.messagePlayer(player, C.AQUA + "Path: " + C.WHITE + pending.valuePath());
        Adapt.messagePlayer(player, C.AQUA + "Expected type: " + C.WHITE + ConfigGui.typeName(targetType));
        Adapt.messagePlayer(player, C.GRAY + "Type " + C.WHITE + "cancel" + C.GRAY + " to abort.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PendingInput pending = sessions.get(player.getUniqueId());
        if (pending == null) {
            return;
        }

        event.setCancelled(true);

        if (pending.isExpired()) {
            sessions.remove(player.getUniqueId());
            J.s(() -> {
                Adapt.messagePlayer(player, C.RED + "Config input timed out.");
                ConfigGui.open(player, pending.returnSectionPath(), pending.returnPage());
            });
            return;
        }

        String message = event.getMessage() == null ? "" : event.getMessage();
        if (message.equalsIgnoreCase("cancel")) {
            sessions.remove(player.getUniqueId());
            J.s(() -> {
                Adapt.messagePlayer(player, C.YELLOW + "Config edit cancelled.");
                ConfigGui.open(player, pending.returnSectionPath(), pending.returnPage());
            });
            return;
        }

        ConfigGui.ParseResult parsed = ConfigGui.parseInputValue(pending.targetType(), message);
        if (!parsed.success()) {
            J.s(() -> {
                Adapt.messagePlayer(player, C.RED + parsed.error());
                Adapt.messagePlayer(player, C.GRAY + "Try again or type " + C.WHITE + "cancel");
            });
            return;
        }

        sessions.remove(player.getUniqueId());
        Object value = parsed.value();
        J.s(() -> {
            ConfigGui.confirmAndApply(player, pending.returnSectionPath(), pending.returnPage(), pending.valuePath(), value);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
    }

    private record PendingInput(
            UUID playerId,
            String valuePath,
            String returnSectionPath,
            int returnPage,
            Class<?> targetType,
            String label,
            long expiresAt
    ) {
        private boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }
}
