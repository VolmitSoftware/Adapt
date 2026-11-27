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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
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
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
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

import java.util.List;
import java.util.UUID;

public class RangedForce extends SimpleAdaptation<RangedForce.Config> {
  public static final Id ID = Id.adapt("ranged/force");

  public RangedForce() {
    super(ID);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.ranged.force_shot");
    setIcon(Id.minecraft("tipped_arrow"));
    setInterval(4900);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spectral_arrow"))
        .key("challenge_force_30")
        .title(Text.key("adapt.ranged.force_shot.advancementname"))
        .description(Text.key("adapt.ranged.force_shot.advancementlore"))
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spectral_arrow"))
        .key("challenge_ranged_force_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_ranged_force_500", "ranged.force.long-range-hits", 500, 500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, RangedPreferences.SHOTS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getSpeed(getLevelPercent(level)), 0), 1);
  }

  private double getSpeed(double factor) {
    return (factor * getConfig().speedFactor);
  }

  @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
  public void on(EntityDamageByEntityEvent e) {
    if (RangedHeartseeker.isSeekingProjectile(e.damager())) {
      return;
    }
    if (!(e.damager() instanceof ProjectileRef projectile)
        || !(projectile.shooter() instanceof PlayerRef player)
        || !(e.entity() instanceof LivingRef target)) {
      return;
    }

    ForceHitTarget snapshot = captureForceHitTarget(target);
    AdaptApi.scheduler().runEntity(player, () -> rewardForceHitOwned(player, snapshot));
  }

  private ForceHitTarget captureForceHitTarget(LivingRef target) {
    return new ForceHitTarget(
        target.location(),
        target instanceof PlayerRef,
        isProtectedFriendly(null, target),
        target.tameOwner()
    );
  }

  private void rewardForceHitOwned(PlayerRef player, ForceHitTarget target) {
    int level = getActiveLevel(player);
    if (level <= 0 || target.protectedTarget()
        || player.uuid().equals(target.tameOwnerId())) {
      return;
    }
    boolean allowed = target.playerTarget()
        ? canPVP(player, target.location())
        : canPVE(player, target.location());
    if (!allowed) {
      return;
    }

    Loc targetLocation = target.location().withY(0);
    Loc playerLocation = player.location().withY(0);
    xp(player, 5);
    double distanceSquared = targetLocation.distanceSquared(playerLocation);
    boolean longRange = isLongRangeHit(distanceSquared);

    if (longRange && grantOnce(player, "challenge_force_30")) {
      xp(player, getConfig().challengeRewardLongShotReward, "challenge-long-shot");
      FxPresets.learnCelebration(this, player);
    }

    if (longRange) {
      addStat(player, "ranged.force.long-range-hits", 1);
    }
  }

  static boolean isLongRangeHit(double distanceSquared) {
    return distanceSquared > 900D;
  }

  @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef projectile = e.projectile();
    if (RangedHeartseeker.isSeekingProjectile(projectile)) {
      return;
    }
    if (projectile.shooter() instanceof PlayerRef p) {
      if (!preference(p, RangedPreferences.SHOTS).accepts(projectile)) {
        return;
      }
      int level = getActiveLevel(p);
      if (level > 0) {
        double factor = getLevelPercent(level);
        Vec3 velocity = RangedShots.quicken(projectile, getSpeed(factor));
        int critCount = Math.min(12, 4 + (int) Math.round(factor * 6));
        fx(projectile.location(), FxPriority.TRAIL)
            .trail(Particles.CRIT, velocity.x(), velocity.y(), velocity.z(), 2.0D, critCount)
            .dustBurst(new Rgb(40, 180, 60), 4, 0.15D, 0.9F)
            .chord(Sounds.ENTITY_SNOWBALL_THROW, 0.5F + ((float) factor * 0.25F), 0.7F + (float) (factor / 2F),
                Sounds.BLOCK_NOTE_BLOCK_HAT, 0.4F, 1.4F + ((float) factor * 0.4F));
      }
    }
  }

  @ConfigDescription("Shoot projectiles further and faster.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Challenge Reward Long Shot Reward for the Ranged Force adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeRewardLongShotReward = 2000;
    @ConfigDoc(value = "Controls Speed Factor for the Ranged Force adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedFactor = 1.135;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 7;
      initialCost = 1;
    }
  }

  private record ForceHitTarget(
      Loc location,
      boolean playerTarget,
      boolean protectedTarget,
      UUID tameOwnerId
  ) {
  }
}
