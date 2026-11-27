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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerAnimationEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


public class SeaborneTidecaller extends SimpleAdaptation<SeaborneTidecaller.Config> {
  public static final Id ID = Id.adapt("seaborne/tidecaller");
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", Text.key("adapt.seabornetidecaller.preferences.preference_sneak"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> ATTACK = CommonPreferences.toggle("attack", Text.key("adapt.seabornetidecaller.preferences.preference_attack"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> ATTACK_SNEAK = CommonPreferences.toggle("attack-sneak", Text.key("adapt.seabornetidecaller.preferences.preference_attack_sneak"), CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> ATTACK_WATER = CommonPreferences.toggle("attack-water", Text.key("adapt.seabornetidecaller.preferences.preference_attack_water"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> WATER = CommonPreferences.toggle("water", Text.key("adapt.seabornetidecaller.preferences.preference_water"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> RAIN = CommonPreferences.toggle("rain", Text.key("adapt.seabornetidecaller.preferences.preference_rain"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FLAT = CommonPreferences.toggle("flat", Text.key("adapt.seabornetidecaller.preferences.preference_flat"), CommonPreferences.Toggle.OFF);

  private static final Id HEART_OF_THE_SEA = Id.minecraft("heart_of_the_sea");

  private final Cooldowns fizzleThrottle = cooldowns();
  private final Set<UUID> teleportDashes = ConcurrentHashMap.newKeySet();

  public SeaborneTidecaller() {
    super(ID);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.seaborn.tidecaller");
    setIcon(Id.minecraft("heart_of_the_sea"));
    setInterval(1600);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("trident"))
        .key("challenge_seaborne_tidecaller_200")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("heart_of_the_sea"))
            .key("challenge_seaborne_tidecaller_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_seaborne_tidecaller_200", "seaborne.tidecaller.dashes", 200, 300);
    registerMilestone("challenge_seaborne_tidecaller_5k", "seaborne.tidecaller.dashes", 5000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SNEAK, ATTACK, ATTACK_SNEAK, ATTACK_WATER, WATER, RAIN, FLAT);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getDashDistance(level), 1), 1);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getCooldownTicks(level) * 50D, 1), 2);
    List<String> combos = getTriggerCombos();
    if (combos.isEmpty()) {
      v.addLore(TextFormat.AQUA + "* " + TextFormat.GRAY + Text.key("adapt.seaborn.tidecaller.trigger_line").withMarkup("trigger", TextFormat.WHITE + Text.key("adapt.seaborn.tidecaller.trigger_none").resolve()).resolve());
    } else {
      for (String combo : combos) {
        v.addLore(TextFormat.AQUA + "* " + TextFormat.GRAY + Text.key("adapt.seaborn.tidecaller.trigger_line").withMarkup("trigger", TextFormat.WHITE + combo).resolve());
      }
    }
    v.addLore(TextFormat.AQUA + "* " + TextFormat.GRAY + Text.key("adapt.seaborn.tidecaller.environment_line").withMarkup("environment", TextFormat.WHITE + getEnvironmentSummary()).resolve());
  }

  @Override
  public void unregister() {
    teleportDashes.clear();
    super.unregister();
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    teleportDashes.remove(e.player().uuid());
  }

  private List<String> getTriggerCombos() {
    List<String> triggers = new ArrayList<>();
    String env = getEnvironmentSummary();
    String none = Text.key("adapt.seaborn.tidecaller.env_none").resolve();
    if (getConfig().enableSneakTrigger) {
      String trigger = Text.key("adapt.seaborn.tidecaller.trigger_sneak").resolve();
      triggers.add(env.equals(none) ? trigger : Text.key("adapt.seaborn.tidecaller.trigger_context").withMarkup("trigger", trigger).withMarkup("environment", env).resolve());
    }

    if (getConfig().enableAttackTrigger) {
      String combo = getConfig().attackTriggerRequiresSneak
          ? Text.key("adapt.seaborn.tidecaller.trigger_sneak_attack").resolve()
          : Text.key("adapt.seaborn.tidecaller.trigger_attack").resolve();
      if (getConfig().attackTriggerWaterOnly) {
        combo = Text.key("adapt.seaborn.tidecaller.trigger_context").withMarkup("trigger", combo).withMarkup("environment", Text.key("adapt.seaborn.tidecaller.trigger_water_only").resolve()).resolve();
      } else if (!env.equals(none)) {
        combo = Text.key("adapt.seaborn.tidecaller.trigger_context").withMarkup("trigger", combo).withMarkup("environment", env).resolve();
      }
      triggers.add(combo);
    }

    return triggers;
  }

  private String getEnvironmentSummary() {
    List<String> modes = new ArrayList<>();
    if (getConfig().allowWaterTrigger) {
      modes.add(Text.key("adapt.seaborn.tidecaller.env_water").resolve());
    }

    if (getConfig().allowRainTrigger) {
      modes.add(Text.key("adapt.seaborn.tidecaller.env_rain").resolve());
    }

    if (modes.isEmpty()) {
      return Text.key("adapt.seaborn.tidecaller.env_none").resolve();
    }

    return String.join("/", modes);
  }


  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    if (!e.isSneaking()) {
      return;
    }

    if (!usesSneakTrigger(p) || hydroReservesSneak(p) || p.hasCooldown(HEART_OF_THE_SEA)) {
      return;
    }

    tryDash(p, TriggerType.SNEAK);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerAnimationEvent e) {
    if (e.animation() != PlayerAnimationEvent.Animation.ARM_SWING) {
      return;
    }

    PlayerRef p = e.player();
    if (!getConfig().enableAttackTrigger || !preferenceEnabled(p, ATTACK) || p.hasCooldown(HEART_OF_THE_SEA)) {
      return;
    }

    tryDash(p, TriggerType.ATTACK);
  }

  public boolean usesSneakTrigger(PlayerRef p) {
    return isPlayerEnabled(p) && getActiveLevel(p) > 0 && getConfig().enableSneakTrigger
        && preferenceEnabled(p, SNEAK) && isDashEnvironmentValid(p);
  }

  private boolean hydroReservesSneak(PlayerRef p) {
    for (Adaptation<?> candidate : getSkill().getAdaptations()) {
      if (candidate instanceof SeaborneHydroJet hydro && hydro.reservesSneak(p)) {
        return true;
      }
    }
    return false;
  }

  private void tryDash(PlayerRef p, TriggerType triggerType) {
    withAdaptedPlayer(p, () -> {
      if (p.hasCooldown(HEART_OF_THE_SEA)) {
        return;
      }
      if (teleportDashes.contains(p.uuid())) {
        return;
      }

      if (triggerType == TriggerType.SNEAK && (!usesSneakTrigger(p) || hydroReservesSneak(p))) {
        return;
      }

      if (triggerType == TriggerType.ATTACK) {
        if (!getConfig().enableAttackTrigger || !preferenceEnabled(p, ATTACK)) {
          return;
        }

        if ((getConfig().attackTriggerRequiresSneak || preferenceEnabled(p, ATTACK_SNEAK)) && !p.isSneaking()) {
          return;
        }

        if ((getConfig().attackTriggerWaterOnly || preferenceEnabled(p, ATTACK_WATER)) && !SeaborneSurge.inWater(p)) {
          return;
        }
      }

      if (!isDashEnvironmentValid(p)) {
        return;
      }

      int level = getActiveLevel(p);
      Vec3 direction = resolveDashDirection(p);
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
      Loc launch = p.location();
      int fan = (int) Math.min(20L, 12L + Math.round(dashDistance));

      fx(launch.add(0D, 1.0D, 0D), FxPriority.GAMEPLAY)
          .trail(Particles.SPLASH, direction.x(), direction.y(), direction.z(), Math.min(4.0D, dashDistance * 0.4D), fan)
          .particle(Particles.BUBBLE, 10, 0D, 0.2D, 0D, 0.35D, 0.05D)
          .ring(Particles.BUBBLE, 0.6D, 6, 0.0D)
          .dustBurst(6, 0.4D, 1.0F)
          .chord(Sounds.ITEM_TRIDENT_RIPTIDE_2, 0.75F, 1.2F, Sounds.ENTITY_DOLPHIN_SPLASH, 0.5F, 0.9F);
      if (getConfig().useVelocityDash) {
        applyVelocityDash(p, direction, level);
        Loc target = p.location()
            .add(direction.multiply(Math.max(0.35D, dashDistance * 0.35D)));
        timeline(p)
            .duration(8)
            .priority(FxPriority.TRAIL)
            .cullRadius(32)
            .frame((f, tick, progress) -> {
              f.particle(Particles.BUBBLE, 3, 0D, 1.0D, 0D, 0.15D, 0.02D);
              if ((tick & 1) == 0) {
                f.particle(Particles.SPLASH, 2, 0D, 1.0D, 0D, 0.1D, 0.02D);
              }
            })
            .start();
        finishSuccessfulDash(p, target, wasSwimming, dashDistance, level);
        return;
      }

      Loc origin = p.location();
      Loc target = findSafeDashTarget(p, dashDistance);
      if (target == null) {
        fizzle(p);
        return;
      }
      startTeleportDash(p, origin, target, wasSwimming, dashDistance, level);
    });
  }

  private void startTeleportDash(PlayerRef p, Loc origin, Loc target, boolean wasSwimming,
                                 double dashDistance, int level) {
    UUID playerId = p.uuid();
    if (!teleportDashes.add(playerId)) {
      return;
    }
    CompletableFuture<Boolean> teleport;
    try {
      teleport = p.teleport(target, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      teleportDashes.remove(playerId);
      AdaptApi.log().error("Seaborne Tidecaller could not start teleport dash for " + playerId + ".", null);
      AdaptApi.log().error("SeaborneTidecaller failed", error);
      fizzle(p);
      return;
    }
    if (teleport == null) {
      teleportDashes.remove(playerId);
      fizzle(p);
      return;
    }
    teleport.whenComplete((success, failure) ->
        completeTeleportDash(p, playerId, origin, wasSwimming, dashDistance, level, success, failure));
  }

  private void completeTeleportDash(PlayerRef p, UUID playerId, Loc origin, boolean wasSwimming,
                                    double dashDistance, int level,
                                    Boolean success, Throwable failure) {
    if (failure != null) {
      AdaptApi.log().error("Seaborne Tidecaller teleport dash failed for " + playerId + ".", null);
      AdaptApi.log().error("SeaborneTidecaller failed", failure);
    }
    if (!successfulTeleportDash(success, failure)) {
      teleportDashes.remove(playerId);
      AdaptApi.scheduler().runEntity(p, () -> {
        if (p.isOnline()) {
          fizzle(p);
        }
      });
      return;
    }
    if (!AdaptApi.scheduler().runEntity(p, () -> finishTeleportDashOwned(
        p,
        playerId,
        origin,
        wasSwimming,
        dashDistance,
        level
    ))) {
      teleportDashes.remove(playerId);
      AdaptApi.log().warn("Seaborne Tidecaller completed teleport dash but could not schedule its effects for "
          + playerId + ".");
    }
  }

  private void finishTeleportDashOwned(PlayerRef p, UUID playerId, Loc origin,
                                       boolean wasSwimming, double dashDistance, int level) {
    teleportDashes.remove(playerId);
    if (!p.isOnline() || !isRuntimeRegistered() || getActiveLevel(p) <= 0) {
      return;
    }
    Loc actualTarget = p.location();
    applyDashMomentum(p, origin, actualTarget);
    fx(origin.add(0D, 1.0D, 0D), FxPriority.TRAIL)
        .line(Particles.BUBBLE, actualTarget.x(), actualTarget.y() + 1.0D, actualTarget.z(), 12)
        .line(Particles.SPLASH, actualTarget.x(), actualTarget.y() + 1.0D, actualTarget.z(), 8);
    finishSuccessfulDash(p, actualTarget, wasSwimming, dashDistance, level);
  }

  private void finishSuccessfulDash(PlayerRef p, Loc target, boolean wasSwimming,
                                    double dashDistance, int level) {
    preserveSwimStateAfterDash(p, wasSwimming);

    double arrivalRadius = Math.min(1.6D, 0.6D + (dashDistance * 0.08D));
    fx(target.add(0D, 1.0D, 0D), FxPriority.GAMEPLAY)
        .particle(Particles.SPLASH, 24, 0D, 0D, 0D, 0.35D, 0.08D)
        .particle(Particles.BUBBLE, 14, 0D, 0D, 0D, 0.4D, 0.05D)
        .chord(Sounds.ENTITY_DOLPHIN_SPLASH, 0.65F, 1.15F, Sounds.ENTITY_PLAYER_SPLASH, 0.5F, 1.1F);
    timeline(target)
        .duration(6)
        .priority(FxPriority.TRANSITION)
        .cullRadius(32)
        .frame((f, tick, progress) -> f.ring(Particles.BUBBLE, 0.3D + (arrivalRadius * progress), 12, 0.2D))
        .start();
    int cooldownTicks = getCooldownTicks(level);
    p.setCooldown(HEART_OF_THE_SEA, cooldownTicks);
    HudStatus.cooldown(p, this, cooldownTicks * 50L);
    AdaptApi.scheduler().runEntity(p, () -> {
      if (p.isOnline() && !p.hasCooldown(HEART_OF_THE_SEA)) {
        FxPresets.readyPing(this, p);
      }
    }, cooldownTicks);
    xp(p, getConfig().xpPerBurst);
    addStat(p, "seaborne.tidecaller.dashes", 1);
  }

  static boolean successfulTeleportDash(Boolean success, Throwable failure) {
    return failure == null && Boolean.TRUE.equals(success);
  }

  private void fizzle(PlayerRef p) {
    if (!fizzleThrottle.isReady(p.uuid(), 500L)) {
      return;
    }

    fizzleThrottle.mark(p.uuid());
    FxPresets.failFizzle(this, p);
  }

  private void applyVelocityDash(PlayerRef p, Vec3 direction, int level) {
    double strength = getConfig().velocityStrengthBase + (getLevelPercent(level) * getConfig().velocityStrengthFactor);
    double vertical = getConfig().velocityVerticalBase + (getLevelPercent(level) * getConfig().velocityVerticalFactor);
    p.setVelocity(SeaborneSurge.velocity(p.velocity(), direction, strength, vertical, getConfig().velocityAdditive,
        getConfig().maxResultingVelocity));
  }

  private void applyDashMomentum(PlayerRef p, Loc origin, Loc target) {
    if (!getConfig().applyForwardMomentumAfterDash) {
      return;
    }

    Vec3 momentum = target.position().subtract(origin.position());
    if (momentum.lengthSquared() <= 0.000001) {
      momentum = origin.direction();
    }

    momentum = momentum.normalize().multiply(Math.max(0, getConfig().forwardMomentum));
    double y = p.velocity().y();
    if (getConfig().replaceVerticalMomentum) {
      y = getConfig().verticalMomentum;
    } else {
      y += getConfig().verticalMomentum;
    }

    momentum = momentum.withY(y);
    p.setVelocity(momentum);
  }

  private Vec3 resolveDashDirection(PlayerRef p) {
    return SeaborneSurge.direction(p, getConfig().flattenVelocityDashDirection || preferenceEnabled(p, FLAT));
  }

  private boolean isBlockedAhead(PlayerRef p, Vec3 direction) {
    return getConfig().blockDashWhenWallAhead && SeaborneSurge.blockedAhead(p, direction, getConfig().wallCheckDistance);
  }

  private void preserveSwimStateAfterDash(PlayerRef p, boolean wasSwimming) {
    if (!getConfig().preserveSwimmingAfterDash || !wasSwimming) {
      return;
    }

    SeaborneSurge.keepSwimming(p);
  }

  private boolean isDashEnvironmentValid(PlayerRef p) {
    if (getConfig().allowWaterTrigger && preferenceEnabled(p, WATER) && SeaborneSurge.inWater(p)) {
      return true;
    }

    return getConfig().allowRainTrigger && preferenceEnabled(p, RAIN) && SeaborneWet.isRainingOn(p);
  }

  private Loc findSafeDashTarget(PlayerRef p, double maxDistance) {
    Vec3 direction = p.location().direction();
    if (direction.lengthSquared() <= 0.0001) {
      return null;
    }

    direction = direction.normalize();
    for (double d = maxDistance; d >= 1.0; d -= 0.5) {
      Loc c = p.location().add(direction.multiply(d));
      if (isSafe(c)) {
        return c;
      }
    }

    return null;
  }

  private boolean isSafe(Loc location) {
    WorldRef world = location.world();
    BlockRef feet = world.blockAt(location);
    BlockRef head = world.blockAt(location.add(0, 1, 0));
    BlockRef floor = world.blockAt(location.add(0, -1, 0));
    return feet.isPassable() && head.isPassable() && (floor.isSolid() || floor.isLiquid());
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
    @ConfigDoc(value = "Controls Dash Distance Base for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dashDistanceBase = 6;
    @ConfigDoc(value = "Controls Dash Distance Factor for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dashDistanceFactor = 8;
    @ConfigDoc(value = "Controls Cooldown Ticks Base for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 140;
    @ConfigDoc(value = "Controls Cooldown Ticks Factor for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 80;
    @ConfigDoc(value = "Controls Xp Per Burst for the Seaborne Tidecaller adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBurst = 11;
    @ConfigDoc(value = "Allows the original rain-based trigger for Tidecaller dashes.", impact = "Disable this to make dashes water-only.")
    boolean allowRainTrigger = true;
    @ConfigDoc(value = "Allows Tidecaller dashes while the player is in water.", impact = "Enable this to make sneak/attack water woosh work consistently.")
    boolean allowWaterTrigger = true;
    @ConfigDoc(value = "Enables sneak-to-dash trigger.", impact = "Disable this if you only want attack-based trigger behavior.")
    boolean enableSneakTrigger = true;
    @ConfigDoc(value = "Enables attack-swing trigger (any item or empty hand).", impact = "Enable this for left-click water flings without requiring special items.")
    boolean enableAttackTrigger = true;
    @ConfigDoc(value = "Requires sneaking for attack-swing trigger.", impact = "True makes attack trigger only fire while sneaking; false allows it anytime in valid dash environments.")
    boolean attackTriggerRequiresSneak = false;
    @ConfigDoc(value = "Restricts attack-swing trigger to water states only.", impact = "True prevents accidental attack-trigger dashes on land even if rain trigger is enabled.")
    boolean attackTriggerWaterOnly = true;
    @ConfigDoc(value = "Uses velocity-based dash movement instead of teleporting to a target.", impact = "True makes tidecaller behave like a movement burst and prevents blink-style wall bypass.")
    boolean useVelocityDash = true;
    @ConfigDoc(value = "If true, removes pitch from velocity dash direction.", impact = "True keeps movement mostly horizontal; false follows exact look direction including up/down.")
    boolean flattenVelocityDashDirection = false;
    @ConfigDoc(value = "Base forward velocity strength of a velocity dash.", impact = "Higher values produce faster bursts.")
    double velocityStrengthBase = 1.05;
    @ConfigDoc(value = "Additional forward velocity strength gained by adaptation level.", impact = "Higher values make higher levels burst farther/faster.")
    double velocityStrengthFactor = 0.85;
    @ConfigDoc(value = "Base vertical velocity contribution for velocity dashes.", impact = "Small positive values keep water movement fluid; lower values stay flatter.")
    double velocityVerticalBase = 0.01;
    @ConfigDoc(value = "Additional vertical velocity contribution gained by adaptation level.", impact = "Higher values increase upward kick at higher levels.")
    double velocityVerticalFactor = 0.05;
    @ConfigDoc(value = "Adds dash velocity on top of current velocity when true.", impact = "True preserves momentum chains; false applies a fresh velocity vector.")
    boolean velocityAdditive = true;
    @ConfigDoc(value = "Hard cap on resulting velocity magnitude after dash.", impact = "Lower values are safer for anticheat and collisions.")
    double maxResultingVelocity = 2.25;
    @ConfigDoc(value = "Cancels dash if a solid block is detected directly ahead.", impact = "Prevents wall clipping and blink-like wall bypass.")
    boolean blockDashWhenWallAhead = true;
    @ConfigDoc(value = "Distance ahead used to detect blocking walls.", impact = "Higher values are safer but can block dashes near tight spaces.")
    double wallCheckDistance = 1.2;
    @ConfigDoc(value = "Applies forward velocity after the dash teleport.", impact = "True makes the dash feel fluid instead of instantly stopping at the target.")
    boolean applyForwardMomentumAfterDash = true;
    @ConfigDoc(value = "Horizontal forward momentum applied after the dash teleport.", impact = "Higher values create a stronger forward burst after each dash.")
    double forwardMomentum = 1.05;
    @ConfigDoc(value = "Vertical momentum added or set after the dash teleport.", impact = "Small positive values help keep water movement smooth; negative values push downward.")
    double verticalMomentum = 0.02;
    @ConfigDoc(value = "If true, replaces current vertical velocity with verticalMomentum.", impact = "False adds verticalMomentum on top of existing vertical motion.")
    boolean replaceVerticalMomentum = false;
    @ConfigDoc(value = "Re-applies swimming pose after dash when the player started swimming and remains in water.", impact = "Prevents dash teleports from popping swimmers out of swim posture.")
    boolean preserveSwimmingAfterDash = true;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
