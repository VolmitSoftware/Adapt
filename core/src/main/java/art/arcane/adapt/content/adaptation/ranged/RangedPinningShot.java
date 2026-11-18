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

package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
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
import art.arcane.adapt.api.projectile.ProjectileCooperativeKeys;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RangedPinningShot extends SimpleAdaptation<RangedPinningShot.Config> {
  private final Map<UUID, Long> targetProcTimes = playerState();
  private final DataKey<Integer> shotLevelKey;
  private final DataKey<String> shotOwnerKey;
  private final DataKey<String> shotDampenKey;

  public RangedPinningShot() {
    super("ranged-pinning-shot");
    shotLevelKey = DataKey.adapt("ranged_pinning_shot_level", DataCodecs.INT);
    shotOwnerKey = DataKey.adapt("ranged_pinning_shot_owner", DataCodecs.STRING);
    shotDampenKey = DataKey.adapt("ranged_pinning_shot_preference", DataCodecs.STRING);
    ProjectileCooperativeKeys.register(shotLevelKey.id(), shotOwnerKey.id(), shotDampenKey.id());
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("tripwire_hook"));
    setInterval(2200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("arrow"))
        .key("challenge_ranged_pinning_300")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_ranged_pinning_300", "ranged.pinning-shot.targets-pinned", 300, 400);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, RangedPreferences.SNEAK, RangedPreferences.DAMPEN);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getProcChance(level), 0), 1);
    statLore(v, Form.duration(getDurationTicks(level) * 50D, 1), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getReapplyCooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef projectile = e.projectile();
    if (RangedHeartseeker.isSeekingProjectile(projectile)
        || !(projectile.shooter() instanceof PlayerRef player)) {
      return;
    }

    int level = getActiveLevel(player);
    if (level <= 0 || (preferenceEnabled(player, RangedPreferences.SNEAK) && !player.isSneaking())) {
      return;
    }

    DataHolder data = projectile.data();
    data.set(shotLevelKey, level);
    data.set(shotOwnerKey, player.uuid().toString());
    data.set(shotDampenKey, Boolean.toString(preferenceEnabled(player, RangedPreferences.DAMPEN)));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (RangedHeartseeker.isSeekingProjectile(e.damager())
        || !(e.damager() instanceof ProjectileRef projectile)
        || !(e.entity() instanceof LivingRef target)) {
      return;
    }

    ShotAuthorization authorization = readShotAuthorization(projectile);
    if (authorization == null || isProtectedTarget(authorization.ownerId(), target)) {
      return;
    }

    int level = authorization.level();

    long now = System.currentTimeMillis();
    cleanupExpired(now);
    long reapply = getReapplyCooldownMillis(level);
    Long last = targetProcTimes.get(target.uuid());
    if (last != null && last + reapply > now) {
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() > getProcChance(level)) {
      return;
    }

    targetProcTimes.put(target.uuid(), now);
    int durationTicks = getDurationTicks(level);
    if (durationTicks > 0) {
      AdaptAttributeService.get().applyTimed(target, getName(), "pin", Attributes.MOVEMENT_SPEED, pinSpeedScalar(getAmplifier(level)), AttributeOperation.ADD_MULTIPLIED_TOTAL, durationTicks);
    }

    if (getConfig().dampenVelocityOnProc
        && !"false".equals(projectile.data().get(shotDampenKey))) {
      Vec3 velocity = target.velocity();
      target.setVelocity(new Vec3(velocity.x() * getConfig().horizontalVelocityFactor, velocity.y(), velocity.z() * getConfig().horizontalVelocityFactor));
      fx(target, FxPriority.COMBAT).dustBurst(new Rgb(140, 120, 100), 6, 0.2D, 1.0F);
    }

    Loc center = target.location();
    double ringRadius = 0.6D + Math.min(0.4D, durationTicks / 300.0D);
    fx(center, FxPriority.COMBAT)
        .column(Particles.CRIT_MAGIC, 6, 1.6D)
        .dustRing(new Rgb(120, 120, 140), ringRadius, 12, 1.0F)
        .particle(Particles.ENCHANTMENT_TABLE, 8, 0, 1.0D, 0, 0.35D, 0.0D)
        .chord(Sounds.BLOCK_BELL_USE, 1.1F, 0.48F, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.55F)
        .sound(Sounds.BLOCK_ANVIL_LAND, 0.3F, 0.6F);
    rewardProc(authorization.ownerId());
  }

  private ShotAuthorization readShotAuthorization(ProjectileRef projectile) {
    DataHolder data = projectile.data();
    Integer level = data.get(shotLevelKey);
    String owner = data.get(shotOwnerKey);
    if (level == null || level <= 0 || owner == null) {
      return null;
    }
    try {
      return new ShotAuthorization(UUID.fromString(owner), level);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private boolean isProtectedTarget(UUID ownerId, LivingRef target) {
    if (isProtectedFriendly(null, target)) {
      return true;
    }
    UUID tamer = target.tameOwner();
    return tamer != null && ownerId.equals(tamer);
  }

  private void rewardProc(UUID ownerId) {
    PlayerRef owner = AdaptCore.platform().host().player(ownerId);
    if (owner != null) {
      Tasks.runEntity(owner, () -> rewardProcOwned(owner));
    }
  }

  private void rewardProcOwned(PlayerRef owner) {
    if (!owner.isOnline() || !isRuntimeRegistered() || getActiveLevel(owner) <= 0) {
      return;
    }
    addStat(owner, "ranged.pinning-shot.targets-pinned", 1);
    xp(owner, getConfig().xpOnProc);
  }

  private void cleanupExpired(long now) {
    if (targetProcTimes.size() < getConfig().cleanupThreshold) {
      return;
    }

    targetProcTimes.entrySet().removeIf(entry -> entry.getValue() + getConfig().entryTtlMillis < now);
  }

  private double getProcChance(int level) {
    return Math.min(getConfig().maxProcChance, getConfig().procChanceBase + (getLevelPercent(level) * getConfig().procChanceFactor));
  }

  private int getDurationTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().durationTicksBase + (getLevelPercent(level) * getConfig().durationTicksFactor)));
  }

  private int getAmplifier(int level) {
    return Math.max(0, (int) Math.floor(getConfig().amplifierBase + (getLevelPercent(level) * getConfig().amplifierFactor)));
  }

  static double pinSpeedScalar(int amplifier) {
    return -Math.min(1.0D, 0.15D * (Math.max(0, amplifier) + 1.0D));
  }

  private long getReapplyCooldownMillis(int level) {
    return Math.max(1000, (long) Math.round(getConfig().reapplyCooldownMillisBase - (getLevelPercent(level) * getConfig().reapplyCooldownMillisFactor)));
  }

  private record ShotAuthorization(UUID ownerId, int level) {
  }

  @ConfigDescription("Projectiles can pin targets with heavy slowness.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Dampen Velocity On Proc for the Ranged Pinning Shot adaptation.", impact = "True enables this behavior and false disables it.")
    boolean dampenVelocityOnProc = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Proc Chance Base for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double procChanceBase = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Proc Chance Factor for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double procChanceFactor = 0.42;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Proc Chance for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxProcChance = 0.65;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Base for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationTicksBase = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Factor for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationTicksFactor = 90;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Amplifier Base for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double amplifierBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Amplifier Factor for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double amplifierFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reapply Cooldown Millis Base for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reapplyCooldownMillisBase = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reapply Cooldown Millis Factor for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reapplyCooldownMillisFactor = 2800;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Horizontal Velocity Factor for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double horizontalVelocityFactor = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cleanup Threshold for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int cleanupThreshold = 128;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Entry Ttl Millis for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long entryTtlMillis = 60000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Proc for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnProc = 12;

    public Config() {
      costFactor = 0.74;
      maxLevel = 6;
      initialCost = 4;
    }
  }
}
