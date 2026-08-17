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
import art.arcane.volmlib.util.hud.HudActionBar;
import art.arcane.volmlib.util.hud.HudPriority;
import art.arcane.volmlib.util.hud.HudSegment;
import art.arcane.volmlib.util.hud.HudSlot;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

public final class AdaptHud {
  private static final String NOTICE_PURPOSE = "adapt:notice";
  private static final String XP_PURPOSE = "adapt:xp";
  private static final String TITLE_PURPOSE = "adapt:title";
  private static final String GUI_PURPOSE = "adapt:gui";
  private static final String AMBIENT_STATUS_PREFIX = "adapt:status:";
  private static final long NOTICE_TTL_MILLIS = 2500L;
  private static final long XP_TTL_MILLIS = 1500L;
  private static final long AMBIENT_STATUS_TTL_MILLIS = 2500L;
  private static final long GUI_TTL_MILLIS = 2500L;
  private static final List<HudSlot> NOTICE_SLOTS = List.of(HudSlot.CENTER, HudSlot.RIGHT);
  private static final List<HudSlot> XP_SLOTS = List.of(HudSlot.LEFT);
  private static final List<HudSlot> STATUS_SLOTS = List.of(HudSlot.CENTER, HudSlot.LEFT);

  private static volatile HudActionBar bar;

  private AdaptHud() {
  }

  public static void start(Adapt plugin) {
    bar = new HudActionBar(plugin);
  }

  public static void stop() {
    HudActionBar active = bar;
    bar = null;
    if (active != null) {
      active.shutdown();
    }
  }

  public static void actionBar(Player player, String message) {
    HudActionBar active = bar;
    if (active == null) {
      new VolmitSender(player).sendAction(message);
      return;
    }
    active.publish(player, new HudSegment(NOTICE_PURPOSE, HudPriority.NOTICE, NOTICE_TTL_MILLIS, NOTICE_SLOTS, legacyText(message)));
  }

  public static void xpTicker(Player player, String message) {
    HudActionBar active = bar;
    if (active == null) {
      new VolmitSender(player).sendAction(message);
      return;
    }
    active.publish(player, new HudSegment(XP_PURPOSE, HudPriority.AMBIENT, XP_TTL_MILLIS, XP_SLOTS, legacyText(message)));
  }

  public static void ambientStatus(Player player, String purpose, String message) {
    String hudPurpose = ambientStatusPurpose(purpose);
    HudActionBar active = bar;
    if (active == null) {
      new VolmitSender(player).sendAction(message);
      return;
    }
    active.publish(player, new HudSegment(hudPurpose, HudPriority.STATUS, AMBIENT_STATUS_TTL_MILLIS, STATUS_SLOTS, legacyText(message)));
  }

  public static void clearAmbientStatus(Player player, String purpose) {
    String hudPurpose = ambientStatusPurpose(purpose);
    HudActionBar active = bar;
    if (active == null) {
      new VolmitSender(player).sendAction(" ");
      return;
    }
    active.clear(player, hudPurpose);
  }

  public static void title(Player player, String title, String subtitle) {
    deliverNotice(player, TITLE_PURPOSE, HudPriority.NOTICE, NOTICE_TTL_MILLIS, title, subtitle);
  }

  public static void guiTitle(Player player, String title, String subtitle) {
    deliverNotice(player, GUI_PURPOSE, HudPriority.INTERACTIVE, GUI_TTL_MILLIS, title, subtitle);
  }

  public static void clear(Player player) {
    HudActionBar active = bar;
    if (active != null) {
      active.clearAll(player);
    }
  }

  static void clearXp(Player player) {
    HudActionBar active = bar;
    if (active != null) {
      active.clear(player, XP_PURPOSE);
    }
  }

  private static void deliverNotice(Player player, String purpose, int priority, long ttlMillis, String title, String subtitle) {
    String combined = combinedText(title, subtitle);
    HudActionBar active = bar;
    if (active == null) {
      new VolmitSender(player).sendAction(combined.isEmpty() ? " " : combined);
      return;
    }
    active.publish(player, new HudSegment(purpose, priority, ttlMillis, NOTICE_SLOTS, legacyText(combined)));
  }

  private static String combinedText(String title, String subtitle) {
    String safeTitle = title == null ? "" : title.trim();
    String safeSubtitle = subtitle == null ? "" : subtitle.trim();
    if (safeTitle.isEmpty()) {
      return safeSubtitle;
    }
    if (safeSubtitle.isEmpty()) {
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
}
