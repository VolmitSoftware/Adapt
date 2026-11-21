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
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.AgilityMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AgilityKipUp extends SimpleAdaptation<AgilityKipUp.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> SPEED_BURST = CommonPreferences.toggle("speed-burst", AgilityMessages.AGILITYKIPUP_PREFERENCE_SPEED_BURST, CommonPreferences.Toggle.ON);

  private final Cooldowns knockbackAt = cooldowns();
  private final Cooldowns kipCooldown = cooldowns();
  private final Map<UUID, Boolean> wasOnGround = playerState();

  public AgilityKipUp() {
    super("agility-kip-up");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("shield"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_agility_kip_up_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_scrap"))
            .key("challenge_agility_kip_up_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_agility_kip_up_100", "agility.kip-up.recoveries", 100, 300);
    registerMilestone("challenge_agility_kip_up_1k", "agility.kip-up.recoveries", 1000, 1200);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SPEED_BURST);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    AdaptAttributeService.get().remove(player.getPlayer(), getName(), "speed", Attributes.MOVEMENT_SPEED);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.duration(getRecoveryWindowMillis(level), 1), 1);
    statLore(v, C.YELLOW, "* ", getSpeedAmplifier(level) + 1, 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || e.damage() <= 0) {
      return;
    }

    if (hasActiveAdaptation(p)) {
      knockbackAt.mark(p.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    UUID id = p.uuid();
    boolean groundNow = p.isOnGround();
    Boolean previousGround = wasOnGround.put(id, groundNow);
    boolean groundBefore = previousGround == null ? groundNow : previousGround;

    if (!groundBefore || groundNow) {
      return;
    }

    if (p.velocity().y() <= getConfig().jumpVelocityThreshold) {
      return;
    }

    withPlayerThread(p, e, () -> attemptKip(p, e));
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    wasOnGround.remove(id);
    knockbackAt.clear(id);
  }

  private void attemptKip(PlayerRef p, PlayerMoveEvent e) {
    if (!isKipEligible(p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    UUID id = p.uuid();
    if (knockbackAt.remaining(id, getRecoveryWindowMillis(level)) <= 0) {
      return;
    }

    long cooldownMillis = Math.max(0L, getConfig().cooldownMillis);
    if (!kipCooldown.isReady(id, cooldownMillis)) {
      return;
    }

    Vec3 direction = intendedDirection(p, e);
    if (direction == null) {
      return;
    }

    kipCooldown.mark(id, cooldownMillis);
    knockbackAt.clear(id);

    Vec3 current = p.velocity();
    double magnitude = getConfig().recoverySpeed;
    p.setVelocity(new Vec3(direction.x() * magnitude, current.y(), direction.z() * magnitude));

    int speedDurationTicks = getSpeedDurationTicks();
    if (speedDurationTicks > 0 && preferenceEnabled(p, SPEED_BURST)) {
      AdaptAttributeService.get().applyTimed(p, getName(), "speed", Attributes.MOVEMENT_SPEED,
          speedBonus(getSpeedAmplifier(level)), AttributeOperation.ADD_MULTIPLIED_TOTAL, speedDurationTicks);
    }

    fx(p.location(), FxPriority.GAMEPLAY)
        .trail(Particles.CLOUD, direction.x(), 0.1D, direction.z(), 0.8D, 5)
        .ring(Particles.CRIT, 0.7D, 8, 0.1D)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 0.6F, 1.3F, Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.6F, 1.0F);

    addStat(p, "agility.kip-up.recoveries", 1);
    xp(p, getConfig().xpPerRecovery);
  }

  private Vec3 intendedDirection(PlayerRef p, PlayerMoveEvent e) {
    Loc to = e.to();
    Vec3 move = to == null ? null : new Vec3(to.x() - e.from().x(), 0D, to.z() - e.from().z());
    if (move != null) {
      if (move.lengthSquared() > 1.0E-4D) {
        return move.normalize();
      }
    }

    Vec3 look = p.location().direction().withY(0);
    if (look.lengthSquared() > 1.0E-4D) {
      return look.normalize();
    }

    return null;
  }

  private boolean isKipEligible(PlayerRef p) {
    GameMode mode = p.gameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    return !p.isFlying() && !p.isGliding() && !p.isSwimming() && p.vehicle() == null;
  }

  private long getRecoveryWindowMillis(int level) {
    return Math.max(120L, Math.round(getConfig().recoveryWindowMillisBase
        + (getLevelPercent(level) * getConfig().recoveryWindowMillisFactor)));
  }

  private int getSpeedAmplifier(int level) {
    return speedAmplifier(getLevelPercent(level), getConfig().speedAmplifierBase, getConfig().speedAmplifierFactor);
  }

  private int getSpeedDurationTicks() {
    return speedDurationTicks(getConfig().speedDurationTicks);
  }

  static int speedAmplifier(double levelPercent, double amplifierBase, double amplifierFactor) {
    return Math.max(0, (int) Math.round(amplifierBase + (levelPercent * amplifierFactor)));
  }

  static int speedDurationTicks(int configuredTicks) {
    return Math.max(10, configuredTicks);
  }

  static double speedBonus(int amplifier) {
    return 0.2D * (amplifier + 1);
  }

  @ConfigDescription("Jump right after taking a hit to convert knockback into recovered momentum and a speed burst.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base recovery window in milliseconds after a hit before level scaling.", impact = "Higher values give more time to kip-up after taking knockback.")
    double recoveryWindowMillisBase = 350;
    @ConfigDoc(value = "Additional recovery window in milliseconds granted at max level.", impact = "Higher values widen the timing window from leveling.")
    double recoveryWindowMillisFactor = 550;
    @ConfigDoc(value = "Base speed-effect amplifier granted on a recovery before level scaling.", impact = "Higher values start the recovery speed burst stronger.")
    double speedAmplifierBase = 0;
    @ConfigDoc(value = "Additional speed-effect amplifier granted at max level.", impact = "Higher values widen the speed-burst gain from leveling.")
    double speedAmplifierFactor = 1.6;
    @ConfigDoc(value = "Duration in ticks of the recovery speed burst.", impact = "Higher values keep the post-recovery speed longer.")
    int speedDurationTicks = 40;
    @ConfigDoc(value = "Horizontal velocity applied toward the intended direction on recovery.", impact = "Higher values fling the player harder out of the knockback.")
    double recoverySpeed = 0.5;
    @ConfigDoc(value = "Minimum upward velocity treated as a jump for triggering a kip-up.", impact = "Higher values require a stronger jump; lower values are more sensitive.")
    double jumpVelocityThreshold = 0.2;
    @ConfigDoc(value = "Cooldown between kip-up recoveries in milliseconds.", impact = "Higher values prevent chaining recoveries too quickly.")
    long cooldownMillis = 3000;
    @ConfigDoc(value = "Experience granted per successful recovery.", impact = "Higher values reward clutch recoveries with more skill xp.")
    double xpPerRecovery = 5;

    public Config() {
      baseCost = 3;
      costFactor = 0.55;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
