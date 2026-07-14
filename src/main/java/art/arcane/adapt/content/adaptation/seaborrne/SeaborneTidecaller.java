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
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class SeaborneTidecaller extends SimpleAdaptation<SeaborneTidecaller.Config> {
  private final Cooldowns fizzleThrottle = cooldowns();

  public SeaborneTidecaller() {
    super("seaborne-tidecaller");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.tidecaller");
    setIcon(Material.HEART_OF_THE_SEA);
    setInterval(1600);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TRIDENT)
        .key("challenge_seaborne_tidecaller_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.HEART_OF_THE_SEA)
            .key("challenge_seaborne_tidecaller_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_seaborne_tidecaller_200", "seaborne.tidecaller.dashes", 200, 300);
    registerMilestone("challenge_seaborne_tidecaller_5k", "seaborne.tidecaller.dashes", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getDashDistance(level), 1), 1);
    v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("seaborn.tidecaller.lore2"));
    String triggerLabel = Localizer.dLocalize("seaborn.tidecaller.lore_trigger_label");
    List<String> combos = getTriggerCombos();
    if (combos.isEmpty()) {
      v.addLore(C.AQUA + "* " + C.GRAY + triggerLabel + ": " + C.WHITE + Localizer.dLocalize("seaborn.tidecaller.trigger_none"));
    } else {
      for (String combo : combos) {
        v.addLore(C.AQUA + "* " + C.GRAY + triggerLabel + ": " + C.WHITE + combo);
      }
    }
    v.addLore(C.AQUA + "* " + C.GRAY + Localizer.dLocalize("seaborn.tidecaller.lore_environment_label") + ": " + C.WHITE + getEnvironmentSummary());
  }

  private List<String> getTriggerCombos() {
    List<String> triggers = new ArrayList<>();
    String env = getEnvironmentSummary();
    String none = Localizer.dLocalize("seaborn.tidecaller.env_none");
    if (getConfig().enableSneakTrigger) {
      triggers.add(Localizer.dLocalize("seaborn.tidecaller.trigger_sneak") + (env.equals(none) ? "" : " (" + env + ")"));
    }

    if (getConfig().enableAttackTrigger) {
      String combo = getConfig().attackTriggerRequiresSneak
          ? Localizer.dLocalize("seaborn.tidecaller.trigger_sneak_attack")
          : Localizer.dLocalize("seaborn.tidecaller.trigger_attack");
      if (getConfig().attackTriggerWaterOnly) {
        combo += " (" + Localizer.dLocalize("seaborn.tidecaller.trigger_water_only") + ")";
      } else if (!env.equals(none)) {
        combo += " (" + env + ")";
      }
      triggers.add(combo);
    }

    return triggers;
  }

  private String getEnvironmentSummary() {
    List<String> modes = new ArrayList<>();
    if (getConfig().allowWaterTrigger) {
      modes.add(Localizer.dLocalize("seaborn.tidecaller.env_water"));
    }

    if (getConfig().allowRainTrigger) {
      modes.add(Localizer.dLocalize("seaborn.tidecaller.env_rain"));
    }

    if (modes.isEmpty()) {
      return Localizer.dLocalize("seaborn.tidecaller.env_none");
    }

    return String.join("/", modes);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    if (!e.isSneaking()) {
      return;
    }

    if (!getConfig().enableSneakTrigger || p.hasCooldown(Material.HEART_OF_THE_SEA)) {
      return;
    }

    tryDash(p, TriggerType.SNEAK);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerAnimationEvent e) {
    if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) {
      return;
    }

    Player p = e.getPlayer();
    if (!getConfig().enableAttackTrigger || p.hasCooldown(Material.HEART_OF_THE_SEA)) {
      return;
    }

    tryDash(p, TriggerType.ATTACK);
  }

  private void tryDash(Player p, TriggerType triggerType) {
    withAdaptedPlayer(p, () -> {
      if (p.hasCooldown(Material.HEART_OF_THE_SEA)) {
        return;
      }

      if (triggerType == TriggerType.SNEAK && !getConfig().enableSneakTrigger) {
        return;
      }

      if (triggerType == TriggerType.ATTACK) {
        if (!getConfig().enableAttackTrigger) {
          return;
        }

        if (getConfig().attackTriggerRequiresSneak && !p.isSneaking()) {
          return;
        }

        if (getConfig().attackTriggerWaterOnly && !isInWaterDashState(p)) {
          return;
        }
      }

      if (!isDashEnvironmentValid(p)) {
        return;
      }

      int level = getActiveLevel(p);
      Vector direction = resolveDashDirection(p);
      if (direction.lengthSquared() <= 0.000001) {
        fizzle(p);
        return;
      }

      if (isBlockedAhead(p, direction)) {
        fizzle(p);
        return;
      }

      boolean wasSwimming = p.isSwimming();
      double dashDistance = getDashDistance(level);
      Location launch = p.getLocation().clone();
      int fan = (int) Math.min(20L, 12L + Math.round(dashDistance));

      fx(launch.clone().add(0D, 1.0D, 0D), FxPriority.GAMEPLAY)
          .trail(Particle.SPLASH, direction.getX(), direction.getY(), direction.getZ(), Math.min(4.0D, dashDistance * 0.4D), fan)
          .particle(Particle.BUBBLE, 10, 0D, 0.2D, 0D, 0.35D, 0.05D)
          .ring(Particle.BUBBLE, 0.6D, 6, 0.0D)
          .dustBurst(6, 0.4D, 1.0F)
          .chord(Sound.ITEM_TRIDENT_RIPTIDE_2, 0.75F, 1.2F, Sound.ENTITY_DOLPHIN_SPLASH, 0.5F, 0.9F);

      Location target;
      if (getConfig().useVelocityDash) {
        applyVelocityDash(p, direction, level);
        target = p.getLocation().clone().add(direction.clone().multiply(Math.max(0.35D, dashDistance * 0.35D)));
        timeline(p)
            .duration(8)
            .priority(FxPriority.TRAIL)
            .cullRadius(32)
            .frame((f, tick, progress) -> {
              f.particle(Particle.BUBBLE, 3, 0D, 1.0D, 0D, 0.15D, 0.02D);
              if ((tick & 1) == 0) {
                f.particle(Particle.SPLASH, 2, 0D, 1.0D, 0D, 0.1D, 0.02D);
              }
            })
            .start();
      } else {
        Location origin = p.getLocation().clone();
        target = findSafeDashTarget(p, dashDistance);
        if (target == null) {
          fizzle(p);
          return;
        }
        J.teleport(p, target);
        applyDashMomentum(p, origin, target);
        fx(origin.clone().add(0D, 1.0D, 0D), FxPriority.TRAIL)
            .line(Particle.BUBBLE, target.getX(), target.getY() + 1.0D, target.getZ(), 12)
            .line(Particle.SPLASH, target.getX(), target.getY() + 1.0D, target.getZ(), 8);
      }

      preserveSwimStateAfterDash(p, wasSwimming);

      double arrivalRadius = Math.min(1.6D, 0.6D + (dashDistance * 0.08D));
      fx(target.clone().add(0D, 1.0D, 0D), FxPriority.GAMEPLAY)
          .particle(Particle.SPLASH, 24, 0D, 0D, 0D, 0.35D, 0.08D)
          .particle(Particle.BUBBLE, 14, 0D, 0D, 0D, 0.4D, 0.05D)
          .chord(Sound.ENTITY_DOLPHIN_SPLASH, 0.65F, 1.15F, Sound.ENTITY_PLAYER_SPLASH, 0.5F, 1.1F);
      timeline(target.clone())
          .duration(6)
          .priority(FxPriority.TRANSITION)
          .cullRadius(32)
          .frame((f, tick, progress) -> f.ring(Particle.BUBBLE, 0.3D + (arrivalRadius * progress), 12, 0.2D))
          .start();

      int cooldownTicks = getCooldownTicks(level);
      p.setCooldown(Material.HEART_OF_THE_SEA, cooldownTicks);
      J.runEntity(p, () -> {
        if (p.isOnline() && !p.hasCooldown(Material.HEART_OF_THE_SEA)) {
          FxPresets.readyPing(this, p);
        }
      }, cooldownTicks);
      xp(p, getConfig().xpPerBurst);
      addStat(p, "seaborne.tidecaller.dashes", 1);
    });
  }

  private void fizzle(Player p) {
    if (!fizzleThrottle.isReady(p.getUniqueId(), 500L)) {
      return;
    }

    fizzleThrottle.mark(p.getUniqueId());
    FxPresets.failFizzle(this, p);
  }

  private void applyVelocityDash(Player p, Vector direction, int level) {
    Vector velocity = direction.clone().normalize();
    double scalar = Math.max(0, getConfig().velocityStrengthBase + (getLevelPercent(level) * getConfig().velocityStrengthFactor));
    velocity.multiply(scalar);

    double y = getConfig().velocityVerticalBase + (getLevelPercent(level) * getConfig().velocityVerticalFactor);
    if (getConfig().velocityAdditive) {
      velocity = p.getVelocity().clone().add(velocity).add(new Vector(0, y, 0));
    } else {
      velocity.setY(y);
    }

    double max = Math.max(0, getConfig().maxResultingVelocity);
    if (max > 0 && velocity.lengthSquared() > max * max) {
      velocity = velocity.normalize().multiply(max);
    }

    p.setVelocity(velocity);
  }

  private void applyDashMomentum(Player p, org.bukkit.Location origin, org.bukkit.Location target) {
    if (!getConfig().applyForwardMomentumAfterDash) {
      return;
    }

    Vector momentum = target.toVector().subtract(origin.toVector());
    if (momentum.lengthSquared() <= 0.000001) {
      momentum = origin.getDirection().clone();
    }

    momentum.normalize().multiply(Math.max(0, getConfig().forwardMomentum));
    double y = p.getVelocity().getY();
    if (getConfig().replaceVerticalMomentum) {
      y = getConfig().verticalMomentum;
    } else {
      y += getConfig().verticalMomentum;
    }

    momentum.setY(y);
    p.setVelocity(momentum);
  }

  private Vector resolveDashDirection(Player p) {
    Vector direction = p.getLocation().getDirection().clone();
    if (getConfig().flattenVelocityDashDirection) {
      direction.setY(0);
    }

    if (direction.lengthSquared() <= 0.000001) {
      return new Vector();
    }

    return direction.normalize();
  }

  private boolean isBlockedAhead(Player p, Vector direction) {
    if (!getConfig().blockDashWhenWallAhead) {
      return false;
    }

    double distance = Math.max(0.1, getConfig().wallCheckDistance);
    org.bukkit.util.RayTraceResult hit = p.getWorld().rayTraceBlocks(p.getEyeLocation(), direction, distance, FluidCollisionMode.NEVER, true);
    return hit != null && hit.getHitBlock() != null;
  }

  private void preserveSwimStateAfterDash(Player p, boolean wasSwimming) {
    if (!getConfig().preserveSwimmingAfterDash || !wasSwimming) {
      return;
    }

    if (!isInWaterDashState(p)) {
      return;
    }

    p.setSwimming(true);
    J.runEntity(p, () -> {
      if (p.isOnline() && isInWaterDashState(p)) {
        p.setSwimming(true);
      }
    }, 1);
  }

  private boolean isDashEnvironmentValid(Player p) {
    if (getConfig().allowWaterTrigger && isInWaterDashState(p)) {
      return true;
    }

    return getConfig().allowRainTrigger && isRainingAt(p);
  }

  private boolean isInWaterDashState(Player p) {
    return p.isInWater() || p.isSwimming() || p.getLocation().getBlock().isLiquid() || p.getEyeLocation().getBlock().isLiquid();
  }

  private boolean isRainingAt(Player p) {
    if (!p.getWorld().hasStorm()) {
      return false;
    }

    int topY = p.getWorld().getHighestBlockYAt(p.getLocation());
    return p.getLocation().getY() >= topY - 1;
  }

  private org.bukkit.Location findSafeDashTarget(Player p, double maxDistance) {
    Vector direction = p.getLocation().getDirection().clone();
    if (direction.lengthSquared() <= 0.0001) {
      return null;
    }

    direction.normalize();
    for (double d = maxDistance; d >= 1.0; d -= 0.5) {
      org.bukkit.Location c = p.getLocation().clone().add(direction.clone().multiply(d));
      if (isSafe(c)) {
        return c;
      }
    }

    return null;
  }

  private boolean isSafe(org.bukkit.Location location) {
    Block feet = location.getBlock();
    Block head = location.clone().add(0, 1, 0).getBlock();
    Block floor = location.clone().subtract(0, 1, 0).getBlock();
    return feet.isPassable() && head.isPassable() && (floor.getType().isSolid() || floor.isLiquid());
  }

  private double getDashDistance(int level) {
    return getConfig().dashDistanceBase + (getLevelPercent(level) * getConfig().dashDistanceFactor);
  }

  private int getCooldownTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
  }


  private enum TriggerType {
    SNEAK,
    ATTACK
  }

  @ConfigDescription("Sneak while it is raining to dash like a water blink through the storm.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Dash Distance Base for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dashDistanceBase = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Dash Distance Factor for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dashDistanceFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 140;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Burst for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBurst = 11;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows the original rain-based trigger for Tidecaller dashes.", impact = "Disable this to make dashes water-only.")
    boolean allowRainTrigger = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows Tidecaller dashes while the player is in water.", impact = "Enable this to make sneak/attack water woosh work consistently.")
    boolean allowWaterTrigger = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables sneak-to-dash trigger.", impact = "Disable this if you only want attack-based trigger behavior.")
    boolean enableSneakTrigger = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables attack-swing trigger (any item or empty hand).", impact = "Enable this for left-click water flings without requiring special items.")
    boolean enableAttackTrigger = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Requires sneaking for attack-swing trigger.", impact = "True makes attack trigger only fire while sneaking; false allows it anytime in valid dash environments.")
    boolean attackTriggerRequiresSneak = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Restricts attack-swing trigger to water states only.", impact = "True prevents accidental attack-trigger dashes on land even if rain trigger is enabled.")
    boolean attackTriggerWaterOnly = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Uses velocity-based dash movement instead of teleporting to a target.", impact = "True makes tidecaller behave like a movement burst and prevents blink-style wall bypass.")
    boolean useVelocityDash = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "If true, removes pitch from velocity dash direction.", impact = "True keeps movement mostly horizontal; false follows exact look direction including up/down.")
    boolean flattenVelocityDashDirection = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base forward velocity strength of a velocity dash.", impact = "Higher values produce faster bursts.")
    double velocityStrengthBase = 1.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional forward velocity strength gained by adaptation level.", impact = "Higher values make higher levels burst farther/faster.")
    double velocityStrengthFactor = 0.85;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base vertical velocity contribution for velocity dashes.", impact = "Small positive values keep water movement fluid; lower values stay flatter.")
    double velocityVerticalBase = 0.01;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional vertical velocity contribution gained by adaptation level.", impact = "Higher values increase upward kick at higher levels.")
    double velocityVerticalFactor = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Adds dash velocity on top of current velocity when true.", impact = "True preserves momentum chains; false applies a fresh velocity vector.")
    boolean velocityAdditive = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on resulting velocity magnitude after dash.", impact = "Lower values are safer for anticheat and collisions.")
    double maxResultingVelocity = 2.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cancels dash if a solid block is detected directly ahead.", impact = "Prevents wall clipping and blink-like wall bypass.")
    boolean blockDashWhenWallAhead = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Distance ahead used to detect blocking walls.", impact = "Higher values are safer but can block dashes near tight spaces.")
    double wallCheckDistance = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Applies forward velocity after the dash teleport.", impact = "True makes the dash feel fluid instead of instantly stopping at the target.")
    boolean applyForwardMomentumAfterDash = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Horizontal forward momentum applied after the dash teleport.", impact = "Higher values create a stronger forward burst after each dash.")
    double forwardMomentum = 1.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vertical momentum added or set after the dash teleport.", impact = "Small positive values help keep water movement smooth; negative values push downward.")
    double verticalMomentum = 0.02;
    @art.arcane.adapt.util.config.ConfigDoc(value = "If true, replaces current vertical velocity with verticalMomentum.", impact = "False adds verticalMomentum on top of existing vertical motion.")
    boolean replaceVerticalMomentum = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Re-applies swimming pose after dash when the player started swimming and remains in water.", impact = "Prevents dash teleports from popping swimmers out of swim posture.")
    boolean preserveSwimmingAfterDash = true;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
