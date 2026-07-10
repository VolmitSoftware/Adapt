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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.math.VelocitySpeed;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class AgilityParkourMomentum extends SimpleAdaptation<AgilityParkourMomentum.Config> {
  private static final BlockFace[] HORIZONTAL_FACES = {
      BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
  };
  private final Map<UUID, Integer> momentum = playerState();
  private final Map<UUID, Boolean> wasOnGround = playerState();
  private final Map<UUID, Boolean> speedBoosting = playerState();
  private final Map<UUID, Integer> lastSpeedAmp = playerState();

  public AgilityParkourMomentum() {
    super("agility-parkour-momentum");
    registerConfiguration(Config.class);
    setIcon(Material.RABBIT_FOOT);
    setInterval(10);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.RABBIT_FOOT)
        .key("challenge_agility_parkour_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_agility_parkour_500", "agility.parkour-momentum.ledge-landings", 500, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getMaxMomentum(level), 1);
    statLore(v, getMaxSpeedAmplifier(level), 2);
    statLore(v, getMaxJumpAmplifier(level), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    if (e.getTo() == null) {
      return;
    }

    if (e.getFrom().getWorld() == e.getTo().getWorld()
        && e.getFrom().distanceSquared(e.getTo()) < getConfig().minimumMoveSquared) {
      return;
    }

    Player p = e.getPlayer();
    withPlayerThread(p, e, () -> {
      UUID id = p.getUniqueId();
      int level = getActiveLevel(p);
      if (level <= 0) {
        momentum.remove(id);
        wasOnGround.remove(id);
        lastSpeedAmp.remove(id);
        invalidateMomentumSpeed(p, id, true);
        speedBoosting.remove(id);
        return;
      }

      boolean onGroundNow = p.isOnGround();
      boolean onGroundBefore = wasOnGround.getOrDefault(id, onGroundNow);
      int current = momentum.getOrDefault(id, 0);

      if (!onGroundBefore && onGroundNow) {
        if (isMomentumLanding(p) && isOnLedge(p)) {
          current += getConfig().landingGain;
          addStat(p, "agility.parkour-momentum.ledge-landings", 1);
          fx(p.getLocation(), FxPriority.TRAIL)
              .particle(Particles.BLOCK_CRACK, 4, 0, 0.1D, 0, 0.2D, 0.05D, p.getLocation().getBlock().getRelative(BlockFace.DOWN).getBlockData());
        } else {
          if (current > 0) {
            fx(p.getLocation(), FxPriority.TRAIL)
                .burst(Particles.SMOKE, 3, 0.1D);
          }
          current -= getConfig().failedLandingPenalty;
        }
      } else if (onGroundNow && !p.isSprinting()) {
        current -= getConfig().groundDecayOnMove;
      }

      current = clampMomentum(current, getMaxMomentum(level));
      if (current > 0) {
        momentum.put(id, current);
      } else {
        momentum.remove(id);
        lastSpeedAmp.remove(id);
        invalidateMomentumSpeed(p, id, false);
      }
      wasOnGround.put(id, onGroundNow);
    });
  }

  @Override
  public void onTick() {
    for (UUID id : momentum.keySet()) {
      Player p = Bukkit.getPlayer(id);
      if (p == null || !p.isOnline()) {
        momentum.remove(id);
        wasOnGround.remove(id);
        speedBoosting.remove(id);
        lastSpeedAmp.remove(id);
        continue;
      }

      withPlayerThread(p, () -> {
        int level = getActiveLevel(p);
        if (level <= 0) {
          momentum.remove(id);
          wasOnGround.remove(id);
          lastSpeedAmp.remove(id);
          invalidateMomentumSpeed(p, id, true);
          return;
        }

        int maxMomentum = getMaxMomentum(level);
        int current = momentum.getOrDefault(id, 0);
        if (current <= 0) {
          momentum.remove(id);
          lastSpeedAmp.remove(id);
          invalidateMomentumSpeed(p, id, false);
          return;
        }

        if (p.isOnGround() && !isOnLedge(p)) {
          current -= getConfig().offLedgeDecayPerTick;
          int remaining = clampMomentum(current, maxMomentum);
          if (remaining > 0) {
            momentum.put(id, remaining);
          } else {
            momentum.remove(id);
            lastSpeedAmp.remove(id);
            invalidateMomentumSpeed(p, id, false);
          }
          brakeMomentumSpeed(p, id);
          return;
        }

        int maxSpeedAmp = getMaxSpeedAmplifier(level);
        int speedAmp = Math.max(0, Math.min(maxSpeedAmp, (int) Math.floor((current / (double) maxMomentum) * (maxSpeedAmp + 1)) - 1));
        int jumpAmp = Math.max(0, Math.min(getMaxJumpAmplifier(level), (int) Math.floor((current / (double) maxMomentum) * (getMaxJumpAmplifier(level) + 1)) - 1));

        PotionEffect jumpBoost = p.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (jumpBoost == null || jumpBoost.getAmplifier() != jumpAmp || jumpBoost.getDuration() <= 10) {
          p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30, jumpAmp, false, false));
        }

        if (speedAmp <= 0) {
          brakeMomentumSpeed(p, id);
        } else if (!isVelocityEligible(p)) {
          invalidateMomentumSpeed(p, id, true);
        } else {
          VelocitySpeed.InputSnapshot input = VelocitySpeed.readInput(p, getConfig().fallbackInputVelocityThresholdSquared());
          if (!input.hasHorizontal()) {
            brakeMomentumSpeed(p, id);
          } else {
            applyMomentumSpeed(p, id, input, speedAmp);
            if (speedAmp >= maxSpeedAmp && M.r(0.25)) {
              fx(p.getLocation(), FxPriority.TRAIL).particle(Particle.CRIT, 1, 0, 0.1D, 0, 0.1D, 0.02D);
            }
          }
        }

        int prevAmp = lastSpeedAmp.getOrDefault(id, 0);
        if (speedAmp > prevAmp) {
          Vector back = p.getLocation().getDirection().setY(0).multiply(-1);
          fx(p.getLocation(), FxPriority.TRANSITION)
              .trail(Particle.CLOUD, back.getX(), 0.1D, back.getZ(), 0.6D, 4)
              .sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.2F, 1.4F);
        }
        if (speedAmp != prevAmp) {
          lastSpeedAmp.put(id, speedAmp);
        }

        if (p.isOnGround() && !p.isSprinting()) {
          current -= getConfig().passiveGroundDecayPerTick;
        }

        int remaining = clampMomentum(current, maxMomentum);
        if (remaining > 0) {
          momentum.put(id, remaining);
        } else {
          momentum.remove(id);
          lastSpeedAmp.remove(id);
          invalidateMomentumSpeed(p, id, false);
        }
      });
    }
  }

  private void applyMomentumSpeed(Player p, UUID id, VelocitySpeed.InputSnapshot input, int speedAmp) {
    Vector direction = VelocitySpeed.resolveHorizontalDirection(p, input);
    if (direction.lengthSquared() <= VelocitySpeed.EPSILON) {
      brakeMomentumSpeed(p, id);
      return;
    }

    double targetSpeed = Math.min(getConfig().maxHorizontalSpeed,
        Math.max(0, getConfig().baseHorizontalSpeed * VelocitySpeed.speedAmplifierScalar(speedAmp)));
    Vector horizontal = VelocitySpeed.horizontalOnly(p.getVelocity());
    Vector targetHorizontal = direction.multiply(targetSpeed);
    Vector nextHorizontal = VelocitySpeed.moveTowards(horizontal, targetHorizontal, Math.max(0, getConfig().accelPerTick));
    nextHorizontal = VelocitySpeed.clampHorizontal(nextHorizontal, getConfig().maxHorizontalSpeed);
    VelocitySpeed.setHorizontalVelocity(p, nextHorizontal);
    speedBoosting.put(id, true);
  }

  private void brakeMomentumSpeed(Player p, UUID id) {
    if (!speedBoosting.getOrDefault(id, false)) {
      return;
    }

    Vector horizontal = VelocitySpeed.horizontalOnly(p.getVelocity());
    double stopThreshold = Math.max(0, getConfig().stopThreshold);
    if (horizontal.lengthSquared() <= stopThreshold * stopThreshold) {
      VelocitySpeed.hardStopHorizontal(p);
      speedBoosting.remove(id);
      return;
    }

    Vector nextHorizontal = VelocitySpeed.moveTowards(horizontal, new Vector(), Math.max(0, getConfig().brakePerTick));
    if (nextHorizontal.lengthSquared() <= stopThreshold * stopThreshold) {
      VelocitySpeed.hardStopHorizontal(p);
      speedBoosting.remove(id);
      return;
    }

    VelocitySpeed.setHorizontalVelocity(p, nextHorizontal);
  }

  private void invalidateMomentumSpeed(Player p, UUID id, boolean invalidState) {
    if (!speedBoosting.getOrDefault(id, false)) {
      return;
    }

    if (invalidState && getConfig().hardStopOnInvalidState) {
      VelocitySpeed.hardStopHorizontal(p);
    }

    speedBoosting.remove(id);
  }

  private boolean isVelocityEligible(Player p) {
    GameMode mode = p.getGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    return !p.isDead() && !p.isFlying() && !p.isGliding() && !p.isSwimming() && p.getVehicle() == null;
  }

  private boolean isMomentumLanding(Player p) {
    return p.isSprinting() && !p.isSwimming() && !p.isGliding() && !p.isFlying();
  }

  private boolean isOnLedge(Player p) {
    if (!p.isOnGround()) {
      return false;
    }

    Block feet = p.getLocation().getBlock();
    Block below = feet.getRelative(BlockFace.DOWN);
    if (!below.getType().isSolid()) {
      return false;
    }

    for (BlockFace side : HORIZONTAL_FACES) {
      Block sideAtFeet = feet.getRelative(side);
      Block sideBelow = below.getRelative(side);
      if (!sideAtFeet.getType().isSolid() && !sideBelow.getType().isSolid()) {
        return true;
      }
    }

    return false;
  }

  private int clampMomentum(int value, int max) {
    return Math.max(0, Math.min(max, value));
  }

  private int getMaxMomentum(int level) {
    return Math.max(3, (int) Math.round(getConfig().momentumBase + (getLevelPercent(level) * getConfig().momentumFactor)));
  }

  private int getMaxSpeedAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().speedAmplifierBase + (getLevelPercent(level) * getConfig().speedAmplifierFactor)));
  }

  private int getMaxJumpAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().jumpAmplifierBase + (getLevelPercent(level) * getConfig().jumpAmplifierFactor)));
  }

  @ConfigDescription("Build momentum by chaining sprint-jumps and landings to gain speed and jump boosts.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Momentum Base for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double momentumBase = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Momentum Factor for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double momentumFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Speed Amplifier Base for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedAmplifierBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Speed Amplifier Factor for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedAmplifierFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Jump Amplifier Base for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double jumpAmplifierBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Jump Amplifier Factor for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double jumpAmplifierFactor = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Landing Gain for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int landingGain = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Failed Landing Penalty for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int failedLandingPenalty = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ground Decay On Move for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int groundDecayOnMove = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Passive Ground Decay Per Tick for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int passiveGroundDecayPerTick = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Off Ledge Decay Per Tick for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int offLedgeDecayPerTick = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Minimum Move Squared for the Agility Parkour Momentum adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minimumMoveSquared = 0.0025;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base horizontal speed used for momentum velocity scaling.", impact = "Higher values increase movement speed when momentum speed is active.")
    double baseHorizontalSpeed = 0.13;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum horizontal speed this adaptation can force.", impact = "Acts as a hard cap to prevent excessive momentum carry.")
    double maxHorizontalSpeed = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How fast velocity accelerates toward the momentum target per tick.", impact = "Higher values accelerate faster; lower values feel smoother.")
    double accelPerTick = 0.04;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How fast velocity decays when movement input is released.", impact = "Higher values stop faster and reduce carry momentum.")
    double brakePerTick = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Horizontal velocity threshold considered fully stopped.", impact = "Higher values stop sooner; lower values preserve tiny motion longer.")
    double stopThreshold = 0.01;
    @art.arcane.adapt.util.config.ConfigDoc(value = "If true, speed velocity is force-cleared when entering invalid states.", impact = "Prevents retained boosts if state transitions skip expected checks.")
    boolean hardStopOnInvalidState = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fallback movement threshold used when direct input API is unavailable.", impact = "Only used on runtimes without Player input access.")
    double fallbackInputVelocityThreshold = 0.0008;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 3;
    }

    double fallbackInputVelocityThresholdSquared() {
      double threshold = Math.max(0, fallbackInputVelocityThreshold);
      return threshold * threshold;
    }
  }
}
