package art.arcane.adapt.mod.feature.fx;

import art.arcane.adapt.mod.platform.ModPlatform;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.ModText;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.BossBarColor;
import art.arcane.adapt.platform.fx.BossBarRef;
import art.arcane.adapt.platform.fx.BossBarStyle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/** Boss bar over {@link ServerBossEvent}; calls from other threads are queued onto the server thread. */
final class ModBossBar implements BossBarRef {
    private final ServerBossEvent event;
    private volatile boolean disposed;

    ModBossBar(String titleMarkup, BossBarColor color, BossBarStyle style) {
        this.event = new ServerBossEvent(ModText.component(titleMarkup), color(color), overlay(style));
    }

    @Override
    public void setTitle(String markup) {
        run(() -> event.setName(ModText.component(markup)));
    }

    @Override
    public void setProgress(float progress) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        run(() -> event.setProgress(clamped));
    }

    @Override
    public void setColor(BossBarColor color) {
        run(() -> event.setColor(color(color)));
    }

    @Override
    public void setStyle(BossBarStyle style) {
        run(() -> event.setOverlay(overlay(style)));
    }

    @Override
    public void show(PlayerRef viewer) {
        run(() -> {
            if (disposed) {
                return;
            }
            ServerPlayer player = ModRefs.player(viewer);
            if (player != null && !player.hasDisconnected()) {
                event.addPlayer(player);
            }
        });
    }

    @Override
    public void hide(PlayerRef viewer) {
        run(() -> {
            ServerPlayer player = ModRefs.player(viewer);
            if (player != null) {
                event.removePlayer(player);
            }
        });
    }

    @Override
    public void dispose() {
        disposed = true;
        run(event::removeAllPlayers);
    }

    private static void run(Runnable action) {
        MinecraftServer server = ModPlatform.server();
        if (server == null || server.isSameThread()) {
            action.run();
            return;
        }
        server.execute(action);
    }

    private static BossEvent.BossBarColor color(BossBarColor color) {
        return switch (color) {
            case PINK -> BossEvent.BossBarColor.PINK;
            case BLUE -> BossEvent.BossBarColor.BLUE;
            case RED -> BossEvent.BossBarColor.RED;
            case GREEN -> BossEvent.BossBarColor.GREEN;
            case YELLOW -> BossEvent.BossBarColor.YELLOW;
            case PURPLE -> BossEvent.BossBarColor.PURPLE;
            case WHITE -> BossEvent.BossBarColor.WHITE;
        };
    }

    private static BossEvent.BossBarOverlay overlay(BossBarStyle style) {
        return switch (style) {
            case SOLID -> BossEvent.BossBarOverlay.PROGRESS;
            case SEGMENTED_6 -> BossEvent.BossBarOverlay.NOTCHED_6;
            case SEGMENTED_10 -> BossEvent.BossBarOverlay.NOTCHED_10;
            case SEGMENTED_12 -> BossEvent.BossBarOverlay.NOTCHED_12;
            case SEGMENTED_20 -> BossEvent.BossBarOverlay.NOTCHED_20;
        };
    }
}
