package art.arcane.adapt.engine.notification;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.server.hud.ClientHud;
import art.arcane.adapt.client.server.hud.ClientHudPort;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.pack.hud.HudXp;
import art.arcane.adapt.pack.hud.PackHudPort;
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
  private static volatile PackHudPort pack;

  private AdaptHud() {
  }

  /**
   * Starts the HUD on the platform's installed {@link HudPort}, or on Adapt's own compositor when none is, behind a
   * {@link PackHudPort} for pack viewers' strip and a {@link ClientHudPort} so players with the client HUD get frames.
   */
  public static void start() {
    HudPort installed = AdaptPorts.get(HudPort.class, null);
    pack = PackHudPort.over(installed == null ? new ActionBarHud() : installed);
    bar = new ClientHudPort(pack);
  }

  public static void stop() {
    HudPort active = bar;
    bar = null;
    pack = null;
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

  /** True when the player sees structured statuses and xp: the client HUD or the pack strip. */
  public static boolean capable(UUID playerId) {
    return playerId != null && (ClientHud.capable(playerId, ClientProtocol.HUD) || packViewer(playerId) != null);
  }

  /**
   * Sends a structured status to a player with the client HUD, or puts it on a pack viewer's strip. Returns false for
   * everyone else, who keep their own output. {@link #clearStatus} with the same purpose removes it.
   */
  public static boolean status(UUID playerId, String purpose, HudStatusSpec spec) {
    if (playerId == null) {
      return false;
    }
    String hudPurpose = ambientStatusPurpose(purpose);
    if (ClientHud.capable(playerId, ClientProtocol.HUD)) {
      return ClientHud.send(playerId, ClientProtocol.HUD, new ServerFrame.HudStatus(hudPurpose,
          spec.glyph(), spec.skillId(), spec.icon(), legacyText(spec.label()), spec.startedAtMillis(), spec.durationMillis(),
          spec.stacks(), spec.maxStacks(), spec.lit()));
    }
    PackHudPort strip = pack;
    PlayerRef viewer = packViewer(playerId);
    return strip != null && viewer != null && strip.status(viewer, hudPurpose, spec);
  }

  /** Removes a structured status from a player with the client HUD or the pack strip; nothing for everyone else. */
  public static void clearStatus(UUID playerId, String purpose) {
    if (playerId == null) {
      return;
    }
    if (ClientHud.capable(playerId, ClientProtocol.HUD)) {
      ClientHud.send(playerId, ClientProtocol.HUD, new ServerFrame.HudClear(ambientStatusPurpose(purpose)));
      return;
    }
    PackHudPort strip = pack;
    PlayerRef viewer = packViewer(playerId);
    if (strip != null && viewer != null) {
      strip.clearStatus(viewer, ambientStatusPurpose(purpose));
    }
  }

  /** Sends an xp award to a player with the client HUD, or shows it on a pack viewer's strip. */
  public static boolean xpEarned(UUID playerId, ServerFrame.XpEarned award) {
    if (ClientHud.send(playerId, ClientProtocol.HUD, award)) {
      return true;
    }
    PackHudPort strip = pack;
    PlayerRef viewer = strip == null ? null : packViewer(playerId);
    Skill<?> skill = viewer == null ? null : AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill(award.skillId());
    if (skill == null) {
      return false;
    }
    HudXp line = new HudXp(award.skillId(), MarkupPort.current().plain(skill.getLocalizedName()),
        skill.getColor().packed(), award.level(), award.progress(), award.amount());
    return strip.xp(viewer, line, AdaptConfig.get().getActionbarXpDurationMillis());
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

  private static PlayerRef packViewer(UUID playerId) {
    PackHudPort strip = pack;
    AdaptCore core = AdaptCore.current();
    if (strip == null || core == null) {
      return null;
    }
    PlayerRef player = core.getPlatform().host().player(playerId);
    return strip.viewing(player) ? player : null;
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
