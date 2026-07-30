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
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdaptHud {
  private static final String NOTICE_PURPOSE = "adapt:notice";
  private static final String XP_PURPOSE = "adapt:xp";
  private static final String TITLE_PURPOSE = "adapt:title";
  private static final String GUI_PURPOSE = "adapt:gui";
  private static final String TITLE_LANE = "adapt:title";
  private static final long NOTICE_TTL_MILLIS = 2500L;
  private static final long AMBIENT_TTL_MILLIS = 1500L;
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
  private static final ConcurrentHashMap<UUID, Long> xpResolveMillis = new ConcurrentHashMap<>();
  private static volatile HudSlotService slots;
  private static volatile HudBossBarLane lanes;

  private AdaptHud() {
  }

  public static void start(Adapt plugin) {
    slots = new HudSlotService(plugin);
    lanes = new HudBossBarLane();
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
    xpResolveMillis.clear();
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
      return;
    }
    if (surface == HudSurface.BOSS_BAR) {
      laneSet.show(player, NOTICE_PURPOSE, legacyText(message), 1.0D, BarColor.WHITE, BarStyle.SOLID, NOTICE_STALE_MILLIS);
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
      laneSet.hide(player, XP_PURPOSE);
      return;
    }
    if (surface == HudSurface.BOSS_BAR) {
      laneSet.show(player, XP_PURPOSE, legacyText(message), 1.0D, BarColor.GREEN, BarStyle.SOLID, XP_STALE_MILLIS);
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
    xpResolveMillis.remove(playerId);
    HudSlotService service = slots;
    if (service != null) {
      service.clear(player);
    }
    HudBossBarLane laneSet = lanes;
    if (laneSet != null) {
      laneSet.hideAll(player);
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
      return;
    }
    if (surface == HudSurface.ACTION_BAR) {
      new VolmitSender(player).sendAction(subtitle == null ? " " : subtitle);
      return;
    }
    if (surface == HudSurface.BOSS_BAR) {
      long staleMillis = Math.max(MIN_TITLE_STALE_MILLIS, (inTicks + stayTicks + outTicks) * 50L);
      laneSet.show(player, TITLE_LANE, legacyText(laneText(title, subtitle)), 1.0D, BarColor.PURPLE, BarStyle.SOLID, staleMillis);
    }
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
}
