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
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class RangedLungeShot extends SimpleAdaptation<RangedLungeShot.Config> {
  public RangedLungeShot() {
    super("ranged-lunge-shot");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("rabbit_hide"));
    setInterval(4859);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("arrow"))
        .key("challenge_ranged_lunge_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("feather"))
            .key("challenge_ranged_lunge_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_ranged_lunge_200", "ranged.lunge-shot.lunges", 200, 300);
    registerMilestone("challenge_ranged_lunge_2500", "ranged.lunge-shot.lunges", 2500, 1000);
  }

  private double getSpeed(double factor) {
    return (factor * getConfig().factor);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, RangedPreferences.SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getSpeed(getLevelPercent(level)), 0), 1);
  }

  @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef projectile = e.projectile();
    if (RangedHeartseeker.isSeekingProjectile(projectile)) {
      return;
    }
    if (projectile.shooter() instanceof PlayerRef p) {
      if (preferenceEnabled(p, RangedPreferences.SNEAK) && !p.isSneaking()) {
        return;
      }
      if (RangedProjectiles.isArrowLike(projectile)) {
        if (hasActiveAdaptation(p)) {
          if (!p.isOnGround()) {
            Vec3 velocity = p.location().direction().normalize().multiply(getSpeed(getLevelPercent(p)));
            p.setVelocity(p.velocity().subtract(velocity));
            addStat(p, "ranged.lunge-shot.lunges", 1);
            fx(p.location().add(0, 1, 0), FxPriority.TRAIL)
                .trail(Particles.CLOUD, velocity.x(), velocity.y(), velocity.z(), 1.5D, 8)
                .dustRing(new Rgb(200, 200, 210), 0.6D, 10, 1.0F)
                .chord(Sounds.ITEM_ARMOR_EQUIP_TURTLE, 1F, 0.75F, Sounds.ITEM_CROSSBOW_SHOOT, 1F, 1.95F)
                .sound(Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 0.5F, 0.9F);
          }
        }
      }
    }
  }


  @ConfigDescription("While airborne, firing arrows kicks you backward away from your aim.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Factor for the Ranged Lunge Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double factor = 0.935;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 1;
    }
  }
}
