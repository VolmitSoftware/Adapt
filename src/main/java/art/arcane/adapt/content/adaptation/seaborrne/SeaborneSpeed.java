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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class SeaborneSpeed extends SimpleAdaptation<SeaborneSpeed.Config> {
  private static final double MAX_TRACKED_DISTANCE_PER_MOVE = 4D;
  private static final double STAT_FLUSH_DISTANCE = 4D;
  private static final int EFFECT_DURATION_TICKS = 80;
  private static final int EFFECT_REFRESH_THRESHOLD_TICKS = 40;
  private static final long ELIGIBILITY_CHECK_INTERVAL_MILLIS = 500L;
  private static final long EFFECT_CHECK_INTERVAL_MILLIS = 1000L;
  private static final long TRAIL_INTERVAL_MILLIS = 500L;

  private final Map<UUID, SwimSession> swimSessions = playerState();
  private final Cooldowns swimSound = cooldowns();
  private final Cooldowns eligibilityCheck = cooldowns();
  private final Cooldowns effectCheck = cooldowns();
  private final Cooldowns swimTrail = cooldowns();

  public SeaborneSpeed() {
    super("seaborne-speed");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.dolphin_grace");
    setIcon(Material.PRISMARINE_CRYSTALS);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.HEART_OF_THE_SEA)
        .key("challenge_seaborne_speed_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.TRIDENT)
            .key("challenge_seaborne_speed_100k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_seaborne_speed_10k", "seaborne.speed.blocks-swum", 10000, 300);
    registerMilestone("challenge_seaborne_speed_100k", "seaborne.speed.blocks-swum", 100000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + Localizer.dLocalize("seaborn.dolphin_grace.lore1") + C.GREEN + (level) + C.GRAY + Localizer.dLocalize("seaborn.dolphin_grace.lore2"));
    v.addLore(C.ITALIC + Localizer.dLocalize("seaborn.dolphin_grace.lore3"));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    if (e instanceof PlayerTeleportEvent || !hasPositionChanged(e.getFrom(), e.getTo())) {
      return;
    }

    Player player = e.getPlayer();
    UUID playerId = player.getUniqueId();
    boolean activeSession = swimSessions.containsKey(playerId);
    if (!player.isInWater() && !activeSession) {
      return;
    }
    if (!activeSession) {
      if (!eligibilityCheck.isReady(playerId, ELIGIBILITY_CHECK_INTERVAL_MILLIS)) {
        return;
      }
      eligibilityCheck.mark(playerId);
    }

    Location to = e.getTo();
    double horizontalDistance = trackedHorizontalDistance(
        to.getX() - e.getFrom().getX(),
        to.getZ() - e.getFrom().getZ());
    withPlayerThread(player, e, () -> handleMovement(player, horizontalDistance));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerTeleportEvent e) {
    Player player = e.getPlayer();
    if (!swimSessions.containsKey(player.getUniqueId())) {
      return;
    }
    withPlayerThread(player, e, () -> endSession(player));
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerQuitEvent e) {
    endSession(e.getPlayer());
  }

  private void handleMovement(Player player, double horizontalDistance) {
    if (!player.isInWater()) {
      endSession(player);
      return;
    }

    UUID playerId = player.getUniqueId();
    SwimSession session = swimSessions.get(playerId);
    boolean started = session == null;
    if (started) {
      int level = resolveEligibleLevel(player);
      if (level <= 0) {
        return;
      }
      session = new SwimSession(level, System.currentTimeMillis() + ELIGIBILITY_CHECK_INTERVAL_MILLIS);
      swimSessions.put(playerId, session);
    } else if (System.currentTimeMillis() >= session.nextEligibilityCheckAt) {
      int level = resolveEligibleLevel(player);
      if (level <= 0) {
        endSession(player);
        return;
      }
      session.level = level;
      session.nextEligibilityCheckAt = System.currentTimeMillis() + ELIGIBILITY_CHECK_INTERVAL_MILLIS;
    }

    if (started || effectCheck.isReady(playerId, EFFECT_CHECK_INTERVAL_MILLIS)) {
      effectCheck.mark(playerId);
      refreshDolphinsGrace(player, session.level);
    }
    if (horizontalDistance > 0D) {
      session.pendingDistance += horizontalDistance;
      if (session.pendingDistance >= STAT_FLUSH_DISTANCE) {
        flushDistance(player, session);
      }
    }

    if (started) {
      playEntryEffect(player);
    }
    playTrail(player, playerId);
  }

  private int resolveEligibleLevel(Player player) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return 0;
    }
    return player.getInventory().getBoots() != null
        && player.getInventory().getBoots().containsEnchantment(Enchantment.DEPTH_STRIDER)
        ? 0
        : level;
  }

  private void refreshDolphinsGrace(Player player, int level) {
    PotionEffect current = player.getPotionEffect(PotionEffectType.DOLPHINS_GRACE);
    if (current == null || shouldRefreshEffect(current.getDuration(), current.getAmplifier(), level)) {
      player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, EFFECT_DURATION_TICKS, level));
    }
  }

  private void playEntryEffect(Player player) {
    timeline(player)
        .duration(10)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((f, tick, progress) -> {
          f.ring(Particle.BUBBLE, 0.8D - (0.5D * progress), 12, 0.1D);
          f.dustRing(0.8D - (0.5D * progress), 8, 0.9F);
          if (tick == 0) {
            f.chord(Sound.ENTITY_DOLPHIN_SPLASH, 0.45F, 1.2F, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 0.3F, 1.4F);
          }
        })
        .start();
  }

  private void playTrail(Player player, UUID playerId) {
    if (!swimTrail.isReady(playerId, TRAIL_INTERVAL_MILLIS)) {
      return;
    }
    swimTrail.mark(playerId);

    Vector velocity = player.getVelocity();
    double speedSquared = velocity.lengthSquared();
    if (speedSquared <= 0.09D) {
      return;
    }

    int count = Math.min(6, 3 + (int) (speedSquared * 2D));
    FxEmitter emitter = fx(player.getLocation(), FxPriority.TRAIL)
        .trail(Particle.BUBBLE, -velocity.getX(), -velocity.getY(), -velocity.getZ(), 1.4D, count)
        .particle(Particle.GLOW, 1, 0D, 0.4D, 0D, 0.1D, 0D);
    if (swimSound.isReady(playerId, 2000L)) {
      swimSound.mark(playerId);
      emitter.sound(Sound.ENTITY_DOLPHIN_SPLASH, 0.25F, 1.1F);
    }
  }

  private void endSession(Player player) {
    SwimSession session = swimSessions.remove(player.getUniqueId());
    if (session != null) {
      flushDistance(player, session);
    }
  }

  private void flushDistance(Player player, SwimSession session) {
    if (session.pendingDistance <= 0D) {
      return;
    }
    addStat(player, "seaborne.speed.blocks-swum", session.pendingDistance);
    session.pendingDistance = 0D;
  }

  private static boolean hasPositionChanged(Location from, Location to) {
    return to != null
        && from.getWorld().equals(to.getWorld())
        && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ());
  }

  static double trackedHorizontalDistance(double deltaX, double deltaZ) {
    if (!Double.isFinite(deltaX) || !Double.isFinite(deltaZ)) {
      return 0D;
    }
    return Math.min(MAX_TRACKED_DISTANCE_PER_MOVE, Math.sqrt((deltaX * deltaX) + (deltaZ * deltaZ)));
  }

  static boolean shouldRefreshEffect(int currentDuration, int currentAmplifier, int targetAmplifier) {
    return currentAmplifier < targetAmplifier
        || (currentAmplifier == targetAmplifier && currentDuration <= EFFECT_REFRESH_THRESHOLD_TICKS);
  }

  @ConfigDescription("Swim faster with dolphin-like grace.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 3;
      costFactor = 0.525;
      maxLevel = 7;
    }
  }

  private static final class SwimSession {
    private int level;
    private long nextEligibilityCheckAt;
    private double pendingDistance;

    private SwimSession(int level, long nextEligibilityCheckAt) {
      this.level = level;
      this.nextEligibilityCheckAt = nextEligibilityCheckAt;
    }
  }
}
