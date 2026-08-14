/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.api.notification;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.format.AdventureCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.plugin.VolmitSender;
import art.arcane.volmlib.util.hud.HudBossBarLane;
import art.arcane.volmlib.util.hud.HudPriority;
import art.arcane.volmlib.util.hud.HudSlotClaim;
import art.arcane.volmlib.util.hud.HudSlotRequest;
import art.arcane.volmlib.util.hud.HudSlotService;
import art.arcane.volmlib.util.hud.HudSurface;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class AdaptHud {
  private static final String NOTICE_PURPOSE = "adapt:notice";
  private static final String XP_PURPOSE = "adapt:xp";
  private static final String TITLE_PURPOSE = "adapt:title";
  private static final String GUI_PURPOSE = "adapt:gui";
  private static final String TITLE_LANE = "adapt:title";
  private static final String AMBIENT_STATUS_PREFIX = "adapt:status:";
  private static final long NOTICE_TTL_MILLIS = 2500L;
  private static final long AMBIENT_TTL_MILLIS = 1500L;
  private static final long AMBIENT_STATUS_TTL_MILLIS = 2500L;
  private static final long INTERACTIVE_TTL_MILLIS = 1500L;
  private static final long NOTICE_STALE_MILLIS = 2500L;
  private static final long XP_STALE_MILLIS = 4000L;
  private static final long MIN_TITLE_STALE_MILLIS = 1500L;
  private static final long RESOLVE_THROTTLE_MILLIS = 250L;
  private static final HudSlotRequest NOTICE_REQUEST = new HudSlotRequest(NOTICE_PURPOSE, HudPriority.NOTICE, NOTICE_TTL_MILLIS, List.of(HudSurface.ACTION_BAR, HudSurface.BOSS_BAR));
  private static final HudSlotRequest XP_REQUEST = new HudSlotRequest(XP_PURPOSE, HudPriority.AMBIENT, AMBIENT_TTL_MILLIS, List.of(HudSurface.ACTION_BAR, HudSurface.BOSS_BAR));
  private static final HudSlotRequest TITLE_REQUEST = new HudSlotRequest(TITLE_PURPOSE, HudPriority.NOTICE, NOTICE_TTL_MILLIS, List.of(HudSurface.TITLE, HudSurface.ACTION_BAR, HudSurface.BOSS_BAR));
  private static final HudSlotRequest GUI_REQUEST = new HudSlotRequest(GUI_PURPOSE, HudPriority.INTERACTIVE, INTERACTIVE_TTL_MILLIS, List.of(HudSurface.TITLE, HudSurface.ACTION_BAR, HudSurface.BOSS_BAR));

  private static final ConcurrentHashMap<UUID, HudSlotClaim> noticeClaims = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<UUID, HudSlotClaim> xpClaims = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<UUID, HudSlotClaim> titleClaims = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<UUID, HudSlotClaim> guiClaims = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<StatusKey, HudSlotClaim> ambientStatusClaims = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<UUID, Long> xpResolveMillis = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, LaneExpiry> laneExpiries = new ConcurrentHashMap<>();
  private static volatile HudSlotService slots;
  private static volatile HudBossBarLane lanes;
  private static volatile LongSupplier nanoClock = System::nanoTime;

  private AdaptHud() {
  }

  public static void start(Adapt plugin) {
    start(plugin, System::nanoTime);
  }

  static void start(Adapt plugin, LongSupplier clock) {
    slots = new HudSlotService(plugin);
    lanes = new HudBossBarLane();
    laneExpiries.clear();
    nanoClock = clock;
  }

  public static void stop() {
    HudSlotService service = slots;
    HudBossBarLane laneSet = lanes;
    slots = null;
    lanes = null;
    noticeClaims.clear();
    xpClaims.clear();
    titleClaims.clear();
    guiClaims.clear();
    ambientStatusClaims.clear();
    xpResolveMillis.clear();
    laneExpiries.clear();
    nanoClock = System::nanoTime;
    if (service != null) {
      service.shutdown();
    }
    if (laneSet != null) {
      laneSet.shutdown();
    }
  }

  public static void actionBar(Player player, String message) {
    HudSlotService service = slots;
    HudBossBarLane laneSet = lanes;
    if (service == null || laneSet == null) {
      new VolmitSender(player).sendAction(message);
      return;
    }
    HudSlotClaim claim = claimFor(noticeClaims, service, player, NOTICE_REQUEST);
    HudSurface surface = claim.resolve();
    if (surface == HudSurface.ACTION_BAR) {
      new VolmitSender(player).sendAction(message);
      hideLane(laneSet, player, NOTICE_PURPOSE);
      return;
    }
    if (surface == HudSurface.BOSS_BAR) {
      showLane(laneSet, player, NOTICE_PURPOSE, legacyText(message), 1.0D, BarColor.WHITE, BarStyle.SOLID, NOTICE_STALE_MILLIS);
    }
  }

  public static void xpTicker(Player player, String message) {
    HudSlotService service = slots;
    HudBossBarLane laneSet = lanes;
    if (service == null || laneSet == null) {
      new VolmitSender(player).sendAction(message);
      return;
    }
    HudSlotClaim claim = claimFor(xpClaims, service, player, XP_REQUEST);
    UUID playerId = player.getUniqueId();
    long now = System.currentTimeMillis();
    Long lastResolve = xpResolveMillis.get(playerId);
    HudSurface surface;
    if (lastResolve == null || now - lastResolve >= RESOLVE_THROTTLE_MILLIS || claim.granted() == null) {
      surface = claim.resolve();
      xpResolveMillis.put(playerId, now);
    } else {
      surface = claim.granted();
    }
    if (surface == HudSurface.ACTION_BAR) {
      new VolmitSender(player).sendAction(message);
      hideLane(laneSet, player, XP_PURPOSE);
      return;
    }
    if (surface == HudSurface.BOSS_BAR) {
      showLane(laneSet, player, XP_PURPOSE, legacyText(message), 1.0D, BarColor.GREEN, BarStyle.SOLID, XP_STALE_MILLIS);
    }
  }

  public static void ambientStatus(Player player, String purpose, String message) {
    String hudPurpose = ambientStatusPurpose(purpose);
    HudSlotService service = slots;
    HudBossBarLane laneSet = lanes;
    if (service == null || laneSet == null) {
      new VolmitSender(player).sendAction(message);
      return;
    }

    StatusKey key = new StatusKey(player.getUniqueId(), hudPurpose);
    HudSlotClaim claim = ambientStatusClaims.computeIfAbsent(key, ignored -> service.open(
        player,
        new HudSlotRequest(
            hudPurpose,
            HudPriority.AMBIENT - 1,
            AMBIENT_STATUS_TTL_MILLIS,
            List.of(HudSurface.ACTION_BAR, HudSurface.BOSS_BAR)
        )
    ));
    HudSurface surface = claim.resolve();
    if (surface == HudSurface.ACTION_BAR) {
      new VolmitSender(player).sendAction(message);
      hideLane(laneSet, player, hudPurpose);
      return;
    }
    if (surface == HudSurface.BOSS_BAR) {
      showLane(laneSet, player, hudPurpose, legacyText(message), 1.0D, BarColor.WHITE, BarStyle.SOLID, AMBIENT_STATUS_TTL_MILLIS);
    }
  }

  public static void clearAmbientStatus(Player player, String purpose) {
    String hudPurpose = ambientStatusPurpose(purpose);
    HudSlotService service = slots;
    HudBossBarLane laneSet = lanes;
    if (service == null || laneSet == null) {
      new VolmitSender(player).sendAction(" ");
      return;
    }

    StatusKey key = new StatusKey(player.getUniqueId(), hudPurpose);
    HudSlotClaim claim = ambientStatusClaims.remove(key);
    HudSurface surface = claim == null ? null : claim.granted();
    if (claim != null) {
      claim.release();
    }
    hideLane(laneSet, player, hudPurpose);
    if (surface == HudSurface.ACTION_BAR) {
      new VolmitSender(player).sendAction(" ");
    }
  }

  public static void title(Player player, String title, String subtitle, int inTicks, int stayTicks, int outTicks) {
    deliverTitle(titleClaims, player, TITLE_REQUEST, title, subtitle, inTicks, stayTicks, outTicks);
  }

  public static void guiTitle(Player player, String title, String subtitle, int inTicks, int stayTicks, int outTicks) {
    deliverTitle(guiClaims, player, GUI_REQUEST, title, subtitle, inTicks, stayTicks, outTicks);
  }

  public static void clear(Player player) {
    UUID playerId = player.getUniqueId();
    noticeClaims.remove(playerId);
    xpClaims.remove(playerId);
    titleClaims.remove(playerId);
    guiClaims.remove(playerId);
    ambientStatusClaims.keySet().removeIf(key -> key.playerId().equals(playerId));
    xpResolveMillis.remove(playerId);
    disarmAllLanes(player);
    HudSlotService service = slots;
    if (service != null) {
      service.clear(player);
    }
    HudBossBarLane laneSet = lanes;
    if (laneSet != null) {
      laneSet.hideAll(player);
    }
  }

  static void clearXp(Player player) {
    UUID playerId = player.getUniqueId();
    HudSlotClaim claim = xpClaims.remove(playerId);
    xpResolveMillis.remove(playerId);
    if (claim != null) {
      claim.release();
    }
    HudBossBarLane laneSet = lanes;
    if (laneSet != null) {
      hideLane(laneSet, player, XP_PURPOSE);
    }
  }

  private static void deliverTitle(ConcurrentHashMap<UUID, HudSlotClaim> cache, Player player, HudSlotRequest request, String title, String subtitle, int inTicks, int stayTicks, int outTicks) {
    HudSlotService service = slots;
    HudBossBarLane laneSet = lanes;
    if (service == null || laneSet == null) {
      player.sendTitle(title, subtitle, inTicks, stayTicks, outTicks);
      return;
    }
    HudSlotClaim claim = claimFor(cache, service, player, request);
    HudSurface surface = claim.resolve();
    if (surface == HudSurface.TITLE) {
      player.sendTitle(title, subtitle, inTicks, stayTicks, outTicks);
      hideLane(laneSet, player, TITLE_LANE);
      return;
    }
    if (surface == HudSurface.ACTION_BAR) {
      new VolmitSender(player).sendAction(subtitle == null ? " " : subtitle);
      hideLane(laneSet, player, TITLE_LANE);
      return;
    }
    if (surface == HudSurface.BOSS_BAR) {
      long staleMillis = Math.max(MIN_TITLE_STALE_MILLIS, (inTicks + stayTicks + outTicks) * 50L);
      showLane(laneSet, player, TITLE_LANE, legacyText(laneText(title, subtitle)), 1.0D, BarColor.PURPLE, BarStyle.SOLID, staleMillis);
    }
  }

  private static void showLane(HudBossBarLane laneSet, Player player, String laneId, String title, double progress, BarColor color, BarStyle style, long staleMillis) {
    laneSet.show(player, laneId, title, progress, color, style, staleMillis);
    armLaneExpiry(player, laneId, staleMillis);
  }

  private static void hideLane(HudBossBarLane laneSet, Player player, String laneId) {
    disarmLane(player, laneId);
    laneSet.hide(player, laneId);
  }

  private static void armLaneExpiry(Player player, String laneId, long staleMillis) {
    String key = laneKey(player, laneId);
    long deadlineNanos = nanoClock.getAsLong() + Math.max(1L, staleMillis) * 1_000_000L;
    while (true) {
      LaneExpiry expiry = laneExpiries.computeIfAbsent(key, ignored -> new LaneExpiry(player, laneId));
      boolean schedule;
      synchronized (expiry) {
        if (laneExpiries.get(key) != expiry) {
          continue;
        }
        expiry.deadlineNanos = deadlineNanos;
        schedule = !expiry.scheduled;
        expiry.scheduled = true;
      }
      if (schedule) {
        scheduleLaneExpiry(key, expiry);
      }
      return;
    }
  }

  private static void scheduleLaneExpiry(String key, LaneExpiry expiry) {
    int delayTicks;
    synchronized (expiry) {
      if (laneExpiries.get(key) != expiry || !expiry.scheduled) {
        return;
      }
      delayTicks = delayTicks(expiry.deadlineNanos - nanoClock.getAsLong());
    }
    boolean scheduled;
    try {
      scheduled = FoliaScheduler.runEntity(
          Adapt.instance,
          expiry.player,
          () -> expireLane(key, expiry),
          delayTicks
      );
    } catch (Throwable error) {
      scheduled = false;
      Adapt.error("Failed to schedule HUD fallback expiry for " + expiry.player.getUniqueId() + ".");
      error.printStackTrace();
    }
    if (!scheduled) {
      discardLaneExpiry(key, expiry, true);
    }
  }

  private static void expireLane(String key, LaneExpiry expiry) {
    boolean expired;
    synchronized (expiry) {
      if (laneExpiries.get(key) != expiry || !expiry.scheduled) {
        return;
      }
      expired = expiry.deadlineNanos - nanoClock.getAsLong() <= 0L;
      if (expired) {
        laneExpiries.remove(key, expiry);
        expiry.scheduled = false;
      }
    }
    if (expired) {
      HudBossBarLane laneSet = lanes;
      if (laneSet != null) {
        laneSet.hide(expiry.player, expiry.laneId);
      }
      return;
    }
    scheduleLaneExpiry(key, expiry);
  }

  private static void discardLaneExpiry(String key, LaneExpiry expiry, boolean hide) {
    boolean removed;
    synchronized (expiry) {
      removed = laneExpiries.remove(key, expiry);
      if (removed) {
        expiry.scheduled = false;
      }
    }
    HudBossBarLane laneSet = lanes;
    if (removed && hide && laneSet != null) {
      laneSet.hide(expiry.player, expiry.laneId);
    }
  }

  private static void disarmLane(Player player, String laneId) {
    String key = laneKey(player, laneId);
    LaneExpiry expiry = laneExpiries.get(key);
    if (expiry != null) {
      discardLaneExpiry(key, expiry, false);
    }
  }

  private static void disarmAllLanes(Player player) {
    String prefix = player.getUniqueId() + "|";
    for (String key : laneExpiries.keySet()) {
      if (key.startsWith(prefix)) {
        LaneExpiry expiry = laneExpiries.get(key);
        if (expiry != null) {
          discardLaneExpiry(key, expiry, false);
        }
      }
    }
  }

  private static int delayTicks(long remainingNanos) {
    if (remainingNanos <= 0L) {
      return 1;
    }
    long tickNanos = 50_000_000L;
    long ticks = remainingNanos / tickNanos;
    if (remainingNanos % tickNanos != 0L) {
      ticks++;
    }
    return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, ticks));
  }

  private static String laneKey(Player player, String laneId) {
    return player.getUniqueId() + "|" + laneId;
  }

  private static HudSlotClaim claimFor(ConcurrentHashMap<UUID, HudSlotClaim> cache, HudSlotService service, Player player, HudSlotRequest request) {
    return cache.computeIfAbsent(player.getUniqueId(), id -> service.open(player, request));
  }

  private static String laneText(String title, String subtitle) {
    String safeTitle = title == null ? "" : title;
    String safeSubtitle = subtitle == null ? "" : subtitle;
    if (safeTitle.trim().isEmpty()) {
      return safeSubtitle;
    }
    if (safeSubtitle.trim().isEmpty()) {
      return safeTitle;
    }
    return safeTitle + " " + safeSubtitle;
  }

  private static String legacyText(String message) {
    return AdventureCompat.toLegacySection(C.translateAlternateColorCodes('&', message == null ? "" : message));
  }

  private static String ambientStatusPurpose(String purpose) {
    String normalized = Objects.requireNonNull(purpose).trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("HUD status purpose cannot be empty");
    }
    return AMBIENT_STATUS_PREFIX + normalized;
  }

  private record StatusKey(UUID playerId, String purpose) {
  }

  private static final class LaneExpiry {
    private final Player player;
    private final String laneId;
    private long deadlineNanos;
    private boolean scheduled;

    private LaneExpiry(Player player, String laneId) {
      this.player = player;
      this.laneId = laneId;
    }
  }
}
