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
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;

public class RangedHeavyDraw extends SimpleAdaptation<RangedHeavyDraw.Config> {
  public static final Id ID = Id.adapt("ranged/heavy_draw");
  private static final ProjectileMemo<Integer> HEAVY_LEVEL = ProjectileMemo.create();

  public RangedHeavyDraw() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("anvil"));
    setInterval(3277);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("anvil"))
        .key("challenge_ranged_heavy_hits_250")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_block"))
            .key("challenge_ranged_heavy_hits_2500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_ranged_heavy_hits_250", "ranged.heavy-draw.heavy-hits", 250, 500);
    registerMilestone("challenge_ranged_heavy_hits_2500", "ranged.heavy-draw.heavy-hits", 2500, 2000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, RangedPreferences.SHOTS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getDamageBonus(level), 0), 1);
    statLore(v, TextFormat.RED, "- ", Format.pc(getVelocityPenalty(level), 0), 2);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef projectile = e.projectile();
    if (RangedHeartseeker.isSeekingProjectile(projectile)
        || readHeavyLevel(projectile) > 0
        || !(projectile.shooter() instanceof PlayerRef p)
        || !RangedShots.isHeavyCapable(projectile)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || !preference(p, RangedPreferences.SHOTS).accepts(projectile)) {
      return;
    }

    Vec3 velocity = projectile.velocity().multiply(getVelocityFactor(level));
    projectile.setVelocity(velocity);
    HEAVY_LEVEL.set(projectile, level);
    fx(projectile.location(), FxPriority.TRAIL)
        .dustBurst(new Rgb(120, 90, 60), 4, 0.15D, 1.1F)
        .chord(Sounds.ITEM_CROSSBOW_SHOOT, 0.4F, 0.6F, Sounds.BLOCK_ANVIL_STEP, 0.25F, 0.7F);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (RangedHeartseeker.isSeekingProjectile(e.damager())
        || !(e.damager() instanceof ProjectileRef projectile)
        || !(projectile.shooter() instanceof PlayerRef p)) {
      return;
    }

    int level = readHeavyLevel(projectile);
    if (level <= 0 || isProtectedFriendly(p, e.entity()) || e.damage() <= 0D) {
      return;
    }

    double multiplier = 1D + getDamageBonus(level);
    if (isVelocityScaledDamage(projectile)) {
      multiplier /= Math.max(0.05D, getVelocityFactor(level));
    }

    RangedShots.strikeHeavy(e, multiplier);
    AdaptApi.scheduler().runEntity(p, () -> rewardHeavyHitOwned(p));
    fx(e.entity(), FxPriority.COMBAT)
        .burst(Particles.CRIT, 8, 0.3D)
        .chord(Sounds.BLOCK_ANVIL_LAND, 0.3F, 1.6F, Sounds.ENTITY_ARROW_HIT, 0.6F, 0.7F);
  }

  static int readHeavyLevel(ProjectileRef projectile) {
    return Math.max(0, HEAVY_LEVEL.getOrDefault(projectile, 0));
  }

  static void applyHeavyState(ProjectileRef target, int level) {
    if (level > 0) {
      HEAVY_LEVEL.set(target, level);
    }
  }

  private void rewardHeavyHitOwned(PlayerRef player) {
    if (!player.isOnline()) {
      return;
    }
    xp(player, getConfig().xpPerHeavyHit);
    addStat(player, "ranged.heavy-draw.heavy-hits", 1);
  }

  private boolean isVelocityScaledDamage(ProjectileRef projectile) {
    return RangedProjectiles.isArrowLike(projectile) && !projectile.type().equals(RangedProjectiles.TRIDENT);
  }

  private double getVelocityFactor(int level) {
    return Math.min(1D, Math.max(0.05D, 1D - getVelocityPenalty(level)));
  }

  private double getVelocityPenalty(int level) {
    return lerpByLevel(getConfig().velocityPenaltyStart, getConfig().velocityPenaltyEnd, level);
  }

  private double getDamageBonus(int level) {
    return lerpByLevel(getConfig().damageBonusStart, getConfig().damageBonusEnd, level);
  }

  private double lerpByLevel(double start, double end, int level) {
    int maxLevel = Math.max(1, getMaxLevel());
    if (maxLevel == 1) {
      return end;
    }

    double t = Math.min(1D, Math.max(0D, (level - 1) / (double) (maxLevel - 1)));
    return start + ((end - start) * t);
  }

  @ConfigDescription("Heavier projectiles fly slower but hit far harder.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Fraction of projectile speed removed at level 1 for the Ranged Heavy Draw adaptation.", impact = "Higher values slow low-level projectiles more.")
    double velocityPenaltyStart = 0.5;
    @ConfigDoc(value = "Fraction of projectile speed removed at max level for the Ranged Heavy Draw adaptation.", impact = "Higher values slow max-level projectiles more.")
    double velocityPenaltyEnd = 0.1;
    @ConfigDoc(value = "Bonus damage fraction at level 1 for the Ranged Heavy Draw adaptation.", impact = "Higher values increase low-level projectile damage.")
    double damageBonusStart = 0.1;
    @ConfigDoc(value = "Bonus damage fraction at max level for the Ranged Heavy Draw adaptation.", impact = "Higher values increase max-level projectile damage.")
    double damageBonusEnd = 1.5;
    @ConfigDoc(value = "Controls Xp Per Heavy Hit for the Ranged Heavy Draw adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerHeavyHit = 4;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
