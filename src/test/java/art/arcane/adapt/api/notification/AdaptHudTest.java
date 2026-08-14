package art.arcane.adapt.api.notification;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.volmlib.util.hud.HudBid;
import art.arcane.volmlib.util.hud.HudPriority;
import art.arcane.volmlib.util.hud.HudSurface;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptHudTest extends AdaptTestBase {
  @Test
  void clearingAmbientStatusImmediatelyRemovesItsFallbackLaneAndClaim() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Plugin foreignPlugin = mock(Plugin.class);
    when(foreignPlugin.getName()).thenReturn("React");
    MetadataValue foreignBid = mock(MetadataValue.class);
    when(foreignBid.getOwningPlugin()).thenReturn(foreignPlugin);
    long now = System.currentTimeMillis();
    when(foreignBid.asString()).thenReturn(new HudBid(
        HudPriority.PROGRESS,
        now - 1_000L,
        now,
        5_000L,
        "react:monitor"
    ).encode());
    when(player.getMetadata(HudSurface.ACTION_BAR.metadataKey())).thenReturn(List.of(foreignBid));
    BossBar bossBar = mock(BossBar.class);

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
         MockedStatic<FoliaScheduler> scheduling = mockStatic(FoliaScheduler.class)) {
      bukkit.when(() -> Bukkit.createBossBar(anyString(), eq(BarColor.WHITE), eq(BarStyle.SOLID)))
          .thenReturn(bossBar);
      scheduling.when(() -> FoliaScheduler.runEntity(
          same(plugin),
          same(player),
          any(Runnable.class),
          anyLong()
      )).thenReturn(true);
      AdaptHud.start(plugin);
      try {
        AdaptHud.ambientStatus(player, "sixth-sense", "Village E 120m");
        verify(bossBar).addPlayer(player);

        AdaptHud.clearAmbientStatus(player, "sixth-sense");

        verify(bossBar).removeAll();
        verify(player).removeMetadata(HudSurface.ACTION_BAR.metadataKey(), plugin);
      } finally {
        AdaptHud.stop();
      }
    }
  }

  @Test
  void clearingExpiredXpRemovesItsFallbackBossBarAndClaim() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Plugin foreignPlugin = mock(Plugin.class);
    when(foreignPlugin.getName()).thenReturn("React");
    MetadataValue foreignBid = mock(MetadataValue.class);
    when(foreignBid.getOwningPlugin()).thenReturn(foreignPlugin);
    long now = System.currentTimeMillis();
    when(foreignBid.asString()).thenReturn(new HudBid(
        HudPriority.PROGRESS,
        now - 1_000L,
        now,
        5_000L,
        "react:monitor"
    ).encode());
    when(player.getMetadata(HudSurface.ACTION_BAR.metadataKey())).thenReturn(List.of(foreignBid));
    BossBar bossBar = mock(BossBar.class);

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
         MockedStatic<FoliaScheduler> scheduling = mockStatic(FoliaScheduler.class)) {
      bukkit.when(() -> Bukkit.createBossBar(anyString(), eq(BarColor.GREEN), eq(BarStyle.SOLID)))
          .thenReturn(bossBar);
      scheduling.when(() -> FoliaScheduler.runEntity(
          same(plugin),
          same(player),
          any(Runnable.class),
          anyLong()
      )).thenReturn(true);
      AdaptHud.start(plugin);
      try {
        AdaptHud.xpTicker(player, "Discovery +5XP");
        verify(bossBar).addPlayer(player);

        AdaptHud.clearXp(player);

        verify(bossBar).removeAll();
        verify(player).removeMetadata(HudSurface.ACTION_BAR.metadataKey(), plugin);
      } finally {
        AdaptHud.stop();
      }
    }
  }

  @Test
  void refreshedFallbackLaneExpiresWithoutSchedulingPerUpdate() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Plugin foreignPlugin = mock(Plugin.class);
    when(foreignPlugin.getName()).thenReturn("React");
    MetadataValue foreignBid = mock(MetadataValue.class);
    when(foreignBid.getOwningPlugin()).thenReturn(foreignPlugin);
    long bidTime = System.currentTimeMillis();
    when(foreignBid.asString()).thenReturn(new HudBid(
        HudPriority.PROGRESS,
        bidTime - 1_000L,
        bidTime,
        5_000L,
        "react:monitor"
    ).encode());
    when(player.getMetadata(HudSurface.ACTION_BAR.metadataKey())).thenReturn(List.of(foreignBid));
    BossBar bossBar = mock(BossBar.class);
    AtomicLong nowNanos = new AtomicLong(1_000_000_000L);
    List<Runnable> expiryTasks = new ArrayList<>();

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
         MockedStatic<FoliaScheduler> scheduling = mockStatic(FoliaScheduler.class)) {
      bukkit.when(() -> Bukkit.createBossBar(anyString(), eq(BarColor.WHITE), eq(BarStyle.SOLID)))
          .thenReturn(bossBar);
      scheduling.when(() -> FoliaScheduler.runEntity(
          same(plugin),
          same(player),
          any(Runnable.class),
          anyLong()
      ))
          .thenAnswer(invocation -> {
            expiryTasks.add(invocation.getArgument(2));
            return true;
          });
      AdaptHud.start(plugin, nowNanos::get);
      try {
        AdaptHud.actionBar(player, "First notice");
        nowNanos.addAndGet(TimeUnit.SECONDS.toNanos(2L));
        AdaptHud.actionBar(player, "Refreshed notice");

        assertThat(expiryTasks).hasSize(1);
        expiryTasks.get(0).run();
        verify(bossBar, never()).removeAll();
        assertThat(expiryTasks).hasSize(2);

        nowNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(2_501L));
        expiryTasks.get(1).run();

        verify(bossBar).removeAll();
      } finally {
        AdaptHud.stop();
      }
    }
  }

  @Test
  void failedExpirySchedulingNeverLeavesFallbackVisible() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Plugin foreignPlugin = mock(Plugin.class);
    when(foreignPlugin.getName()).thenReturn("React");
    MetadataValue foreignBid = mock(MetadataValue.class);
    when(foreignBid.getOwningPlugin()).thenReturn(foreignPlugin);
    long now = System.currentTimeMillis();
    when(foreignBid.asString()).thenReturn(new HudBid(
        HudPriority.PROGRESS,
        now - 1_000L,
        now,
        5_000L,
        "react:monitor"
    ).encode());
    when(player.getMetadata(HudSurface.ACTION_BAR.metadataKey())).thenReturn(List.of(foreignBid));
    BossBar bossBar = mock(BossBar.class);

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
         MockedStatic<FoliaScheduler> scheduling = mockStatic(FoliaScheduler.class)) {
      bukkit.when(() -> Bukkit.createBossBar(anyString(), eq(BarColor.WHITE), eq(BarStyle.SOLID)))
          .thenReturn(bossBar);
      scheduling.when(() -> FoliaScheduler.runEntity(
          same(plugin),
          same(player),
          any(Runnable.class),
          anyLong()
      )).thenReturn(false).thenThrow(new IllegalStateException("scheduler unavailable"));
      AdaptHud.start(plugin);
      try {
        AdaptHud.actionBar(player, "Rejected notice");
        AdaptHud.actionBar(player, "Throwing notice");

        verify(bossBar, times(2)).removeAll();
      } finally {
        AdaptHud.stop();
      }
    }
  }
}
