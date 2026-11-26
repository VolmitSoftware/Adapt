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
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.SeabornMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.Map;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class SeaborneSpeed extends SimpleAdaptation<SeaborneSpeed.Config> {
  private static final double MAX_TRACKED_DISTANCE_PER_MOVE = 4D;
  private static final double STAT_FLUSH_DISTANCE = 4D;
  private static final long ELIGIBILITY_CHECK_INTERVAL_MILLIS = 500L;
  private static final long TRAIL_INTERVAL_MILLIS = 500L;

  private final Map<UUID, SwimSession> swimSessions = playerState();
  private final Cooldowns swimSound = cooldowns();
  private final Cooldowns eligibilityCheck = cooldowns();
  private final Cooldowns swimTrail = cooldowns();

  public SeaborneSpeed() {
    super("seaborne-speed");
    needs(SeaborneSwim.NEED);
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.dolphin_grace");
    setIcon(Id.minecraft("prismarine_crystals"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("heart_of_the_sea"))
        .key("challenge_seaborne_speed_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("trident"))
            .key("challenge_seaborne_speed_100k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_seaborne_speed_10k", "seaborne.speed.blocks-swum", 10000, 300);
    registerMilestone("challenge_seaborne_speed_100k", "seaborne.speed.blocks-swum", 100000, 1500);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    endSession(player.getPlayer());
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GRAY + AdaptLanguage.text(
        SeabornMessages.DOLPHIN_GRACE_SPEED,
        trusted("level", C.GREEN + String.valueOf(level) + C.GRAY)
    ));
    v.addLore(C.ITALIC + AdaptLanguage.text(SeabornMessages.DOLPHIN_GRACE_LORE3));
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    if (!hasPositionChanged(e.from(), e.to())) {
      return;
    }

    PlayerRef player = e.player();
    boolean inWater = player.isInWater();
    if (!inWater && swimSessions.isEmpty()) {
      return;
    }

    UUID playerId = player.uuid();
    boolean activeSession = swimSessions.containsKey(playerId);
    if (!inWater && !activeSession) {
      return;
    }
    if (!activeSession) {
      if (!eligibilityCheck.isReady(playerId, ELIGIBILITY_CHECK_INTERVAL_MILLIS)) {
        return;
      }
      eligibilityCheck.mark(playerId);
    }

    Loc to = e.to();
    double horizontalDistance = trackedHorizontalDistance(
        to.x() - e.from().x(),
        to.z() - e.from().z());
    withPlayerThread(player, e, () -> handleMovement(player, horizontalDistance));
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerTeleportEvent e) {
    PlayerRef player = e.player();
    if (!swimSessions.containsKey(player.uuid())) {
      return;
    }
    withPlayerThread(player, e, () -> endSession(player));
  }

  @Handle(priority = EventPriority.LOWEST, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    endSession(e.player());
  }

  private void handleMovement(PlayerRef player, double horizontalDistance) {
    if (!player.isInWater()) {
      endSession(player);
      return;
    }

    UUID playerId = player.uuid();
    SwimSession session = swimSessions.get(playerId);
    boolean started = session == null;
    if (started) {
      int level = resolveEligibleLevel(player);
      if (level <= 0) {
        return;
      }
      session = new SwimSession(level, System.currentTimeMillis() + ELIGIBILITY_CHECK_INTERVAL_MILLIS);
      swimSessions.put(playerId, session);
      applySwimBoost(player, level);
      applySwimGrace(player, level);
    } else if (System.currentTimeMillis() >= session.nextEligibilityCheckAt) {
      int level = resolveEligibleLevel(player);
      if (level <= 0) {
        endSession(player);
        return;
      }
      session.level = level;
      applySwimBoost(player, level);
      applySwimGrace(player, level);
      session.nextEligibilityCheckAt = System.currentTimeMillis() + ELIGIBILITY_CHECK_INTERVAL_MILLIS;
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

  private int resolveEligibleLevel(PlayerRef player) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return 0;
    }
    return SeaborneSwim.wearsDepthStrider(player) ? 0 : level;
  }

  private void applySwimBoost(PlayerRef player, int level) {
    SeaborneSwim.applyBoost(this, player, SeaborneSwim.waterEfficiencyAmount(level, getMaxLevel()));
  }

  private void applySwimGrace(PlayerRef player, int level) {
    SeaborneSwim.applyGrace(player, SeaborneSwim.graceDurationTicks(level, getMaxLevel()));
  }

  private void playEntryEffect(PlayerRef player) {
    timeline(player)
        .duration(10)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((f, tick, progress) -> {
          f.ring(Particles.BUBBLE, 0.8D - (0.5D * progress), 12, 0.1D);
          f.dustRing(0.8D - (0.5D * progress), 8, 0.9F);
          if (tick == 0) {
            f.chord(Sounds.ENTITY_DOLPHIN_SPLASH, 0.45F, 1.2F, Sounds.BLOCK_CONDUIT_AMBIENT_SHORT, 0.3F, 1.4F);
          }
        })
        .start();
  }

  private void playTrail(PlayerRef player, UUID playerId) {
    if (!swimTrail.isReady(playerId, TRAIL_INTERVAL_MILLIS)) {
      return;
    }
    swimTrail.mark(playerId);

    Vec3 velocity = player.velocity();
    double speedSquared = velocity.lengthSquared();
    if (speedSquared <= 0.09D) {
      return;
    }

    int count = Math.min(6, 3 + (int) (speedSquared * 2D));
    FxEmitter emitter = fx(player.location(), FxPriority.TRAIL)
        .trail(Particles.BUBBLE, -velocity.x(), -velocity.y(), -velocity.z(), 1.4D, count)
        .particle(Particles.GLOW, 1, 0D, 0.4D, 0D, 0.1D, 0D);
    if (swimSound.isReady(playerId, 2000L)) {
      swimSound.mark(playerId);
      emitter.sound(Sounds.ENTITY_DOLPHIN_SPLASH, 0.25F, 1.1F);
    }
  }

  private void endSession(PlayerRef player) {
    SwimSession session = swimSessions.remove(player.uuid());
    if (session == null) {
      return;
    }
    SeaborneSwim.removeBoost(this, player);
    flushDistance(player, session);
  }

  private void flushDistance(PlayerRef player, SwimSession session) {
    if (session.pendingDistance <= 0D) {
      return;
    }
    addStat(player, "seaborne.speed.blocks-swum", session.pendingDistance);
    session.pendingDistance = 0D;
  }

  private static boolean hasPositionChanged(Loc from, Loc to) {
    return to != null
        && from.sameWorld(to)
        && (from.x() != to.x() || from.y() != to.y() || from.z() != to.z());
  }

  static double trackedHorizontalDistance(double deltaX, double deltaZ) {
    if (!Double.isFinite(deltaX) || !Double.isFinite(deltaZ)) {
      return 0D;
    }
    return Math.min(MAX_TRACKED_DISTANCE_PER_MOVE, Math.sqrt((deltaX * deltaX) + (deltaZ * deltaZ)));
  }

  @ConfigDescription("Swim faster with dolphin-like grace.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      initialCost = 2;
      baseCost = 3;
      costFactor = 0.6;
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
