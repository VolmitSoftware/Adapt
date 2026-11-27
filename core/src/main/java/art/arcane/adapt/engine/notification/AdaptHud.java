package art.arcane.adapt.engine.notification;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.server.hud.ClientHud;
import art.arcane.adapt.client.server.hud.ClientHudPort;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

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

  private static volatile HudPort bar;

  private AdaptHud() {
  }

  /**
   * Starts the HUD on the platform's installed {@link HudPort}, or on Adapt's own compositor when none is, behind a
   * {@link ClientHudPort} so players with the client HUD get frames instead.
   */
  public static void start() {
    HudPort installed = AdaptPorts.get(HudPort.class, null);
    bar = new ClientHudPort(installed == null ? new ActionBarHud() : installed);
  }

  public static void stop() {
    HudPort active = bar;
    bar = null;
    if (active != null) {
      active.shutdown();
    }
  }

  public static void actionBar(PlayerRef player, String message) {
    actionBar(player, message, NOTICE_TTL_MILLIS);
  }

  static void actionBar(PlayerRef player, String message, long durationMillis) {
    HudPort active = bar;
    if (active == null) {
      player.sendActionBar(message);
      return;
    }
    active.publish(player, new HudSegment(NOTICE_PURPOSE, HudPriority.NOTICE, normalizeDisplayDuration(durationMillis), NOTICE_SLOTS, legacyText(message)));
  }

  public static void xpTicker(PlayerRef player, String message) {
    xpTicker(player, message, XP_TTL_MILLIS);
  }

  static void xpTicker(PlayerRef player, String message, long durationMillis) {
    HudPort active = bar;
    if (active == null) {
      player.sendActionBar(message);
      return;
    }
    active.publish(player, new HudSegment(XP_PURPOSE, HudPriority.AMBIENT, normalizeDisplayDuration(durationMillis), XP_SLOTS, legacyText(message)));
  }

  public static void ambientStatus(PlayerRef player, String purpose, String message) {
    String hudPurpose = ambientStatusPurpose(purpose);
    HudPort active = bar;
    if (active == null) {
      player.sendActionBar(message);
      return;
    }
    active.publish(player, new HudSegment(hudPurpose, HudPriority.STATUS, AMBIENT_STATUS_TTL_MILLIS, STATUS_SLOTS, legacyText(message)));
  }

  /**
   * Sends a structured status to a player with the client HUD. Returns false for everyone else, who keep their own
   * output. {@link #clearStatus} with the same purpose removes it.
   */
  public static boolean status(UUID playerId, String purpose, HudStatusSpec spec) {
    if (playerId == null || !ClientHud.capable(playerId, ClientProtocol.HUD)) {
      return false;
    }
    return ClientHud.send(playerId, ClientProtocol.HUD, new ServerFrame.HudStatus(ambientStatusPurpose(purpose),
        spec.glyph(), spec.skillId(), spec.icon(), legacyText(spec.label()), spec.startedAtMillis(), spec.durationMillis(),
        spec.stacks(), spec.maxStacks(), spec.lit()));
  }

  /** Removes a structured status from a player with the client HUD; nothing for everyone else. */
  public static void clearStatus(UUID playerId, String purpose) {
    if (playerId != null && ClientHud.capable(playerId, ClientProtocol.HUD)) {
      ClientHud.send(playerId, ClientProtocol.HUD, new ServerFrame.HudClear(ambientStatusPurpose(purpose)));
    }
  }

  public static void clearAmbientStatus(PlayerRef player, String purpose) {
    String hudPurpose = ambientStatusPurpose(purpose);
    HudPort active = bar;
    if (active == null) {
      player.sendActionBar(" ");
      return;
    }
    active.clear(player, hudPurpose);
  }

  public static void title(PlayerRef player, String title, String subtitle) {
    title(player, title, subtitle, NOTICE_TTL_MILLIS);
  }

  static void title(PlayerRef player, String title, String subtitle, long durationMillis) {
    deliverNotice(player, TITLE_PURPOSE, HudPriority.NOTICE, durationMillis, title, subtitle);
  }

  public static void guiTitle(PlayerRef player, String title, String subtitle) {
    deliverNotice(player, GUI_PURPOSE, HudPriority.INTERACTIVE, GUI_TTL_MILLIS, title, subtitle);
  }

  public static void clear(PlayerRef player) {
    HudPort active = bar;
    if (active != null) {
      active.clearAll(player);
    }
  }

  private static void deliverNotice(PlayerRef player, String purpose, int priority, long ttlMillis, String title, String subtitle) {
    String combined = combinedText(title, subtitle);
    HudPort active = bar;
    if (active == null) {
      player.sendActionBar(combined.isEmpty() ? " " : combined);
      return;
    }
    active.publish(player, new HudSegment(purpose, priority, normalizeDisplayDuration(ttlMillis), NOTICE_SLOTS, legacyText(combined)));
  }

  static long normalizeDisplayDuration(long durationMillis) {
    return Math.max(1L, durationMillis);
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
    return MarkupPort.current().legacy(message);
  }

  private static String ambientStatusPurpose(String purpose) {
    String normalized = Objects.requireNonNull(purpose).trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("HUD status purpose cannot be empty");
    }
    return AMBIENT_STATUS_PREFIX + normalized;
  }
}
