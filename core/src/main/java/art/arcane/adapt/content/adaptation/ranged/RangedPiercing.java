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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
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
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;

public class RangedPiercing extends SimpleAdaptation<RangedPiercing.Config> {
  private static final ProjectileMemo<Integer> PIERCE_HITS = ProjectileMemo.create();
  private static final ProjectileMemo<Boolean> PIERCE_INITIALIZED = ProjectileMemo.create();
  private static final Rgb WHITE = new Rgb(255, 255, 255);

  public RangedPiercing() {
    super("ranged-piercing");
    registerConfiguration(Config.class);
    setLocalizationKey("ranged.arrow_piercing");
    setIcon(Id.minecraft("fletching_table"));
    setInterval(4791);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spectral_arrow"))
        .key("challenge_ranged_piercing_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spectral_arrow"))
        .key("challenge_ranged_piercing_4")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_ranged_piercing_500", "ranged.piercing.extra-hits", 500, 400);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, RangedPreferences.SHOTS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, level, 1);
  }

  @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef arrow = e.projectile();
    if (RangedHeartseeker.isSeekingProjectile(arrow)
        || !RangedProjectiles.isArrowLike(arrow)
        || isPiercingInitialized(arrow)
        || !(arrow.shooter() instanceof PlayerRef player)) {
      return;
    }
    int level = getActiveLevel(player);
    if (level <= 0 || !preference(player, RangedPreferences.SHOTS).accepts(arrow)) {
      return;
    }
    arrow.setPierceLevel(arrow.pierceLevel() + level);
    PIERCE_INITIALIZED.set(arrow, true);
    xp(player, 5);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (RangedHeartseeker.isSeekingProjectile(e.damager())
        || !(e.damager() instanceof ProjectileRef arrow)
        || !RangedProjectiles.isArrowLike(arrow)
        || !(arrow.shooter() instanceof PlayerRef player)
        || !(e.entity() instanceof LivingRef target)
        || !isPiercingInitialized(arrow)
        || arrow.pierceLevel() <= 0
        || isProtectedFriendly(player, target)) {
      return;
    }

    int hits = readHits(arrow) + 1;
    PIERCE_HITS.set(arrow, hits);

    if (hits > 1) {
      Tasks.runEntity(player, () -> rewardPiercingHitOwned(player, hits));
      emitPierceSpark(target, arrow, hits);
    }
    if (hits >= 3) {
      fx(target, FxPriority.COMBAT)
          .column(Particles.ENCHANTMENT_TABLE, 8, 1.5D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.5F);
    }
  }

  static int readHits(ProjectileRef arrow) {
    return PIERCE_HITS.getOrDefault(arrow, 0);
  }

  static boolean isPiercingInitialized(ProjectileRef arrow) {
    return PIERCE_INITIALIZED.getOrDefault(arrow, false);
  }

  static void applyPiercingState(ProjectileRef target, boolean initialized, int hits) {
    if (!initialized) {
      return;
    }
    PIERCE_INITIALIZED.set(target, true);
    if (hits > 0) {
      PIERCE_HITS.set(target, hits);
    }
  }

  private void rewardPiercingHitOwned(PlayerRef player, int hits) {
    if (!player.isOnline()) {
      return;
    }
    addStat(player, "ranged.piercing.extra-hits", 1);
    if (hits >= 4 && grantOnce(player, "challenge_ranged_piercing_4")) {
      FxPresets.learnCelebration(this, player);
    }
  }

  private void emitPierceSpark(LivingRef target, ProjectileRef arrow, int hits) {
    Loc center = target.location().add(0, target.height() * 0.5D, 0);
    Vec3 velocity = arrow.velocity();
    float pitch = 1.0F + (Math.min(hits, 5) * 0.12F);
    if (velocity.lengthSquared() > 1.0E-6D) {
      Vec3 offset = velocity.normalize().multiply(0.6D);
      Loc entry = center.add(offset.multiply(-1D));
      Loc exit = center.add(offset);
      fx(entry, FxPriority.COMBAT)
          .line(Particles.CRIT_MAGIC, exit.x(), exit.y(), exit.z(), 6)
          .dustBurst(WHITE, 4, 0.2D, 0.4F)
          .sound(Sounds.ENTITY_ARROW_HIT, 0.8F, pitch);
      return;
    }
    fx(center, FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 6, 0.25D)
        .dustBurst(WHITE, 4, 0.2D, 0.4F)
        .sound(Sounds.ENTITY_ARROW_HIT, 0.8F, pitch);
  }


  @ConfigDescription("Projectiles pierce through multiple targets.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
