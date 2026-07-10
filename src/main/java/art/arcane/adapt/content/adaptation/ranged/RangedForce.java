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
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.util.Vector;

public class RangedForce extends SimpleAdaptation<RangedForce.Config> {

  public RangedForce() {
    super("ranged-force");
    registerConfiguration(Config.class);
    setLocalizationKey("ranged.force_shot");
    setIcon(Material.TIPPED_ARROW);
    setInterval(4900);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPECTRAL_ARROW)
        .key("challenge_force_30")
        .title(Localizer.dLocalize("ranged.force_shot.advancementname"))
        .description(Localizer.dLocalize("ranged.force_shot.advancementlore"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPECTRAL_ARROW)
        .key("challenge_ranged_force_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_ranged_force_500", "ranged.force.long-range-hits", 500, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getSpeed(getLevelPercent(level)), 0), 1);
  }

  private double getSpeed(double factor) {
    return (factor * getConfig().speedFactor);
  }

  @EventHandler
  public void on(EntityDamageByEntityEvent e) {
    if (RangedHeartseeker.isSeekingProjectile(e.getDamager())) {
      return;
    }
    art.arcane.adapt.api.adaptation.Adaptation.ProjectileContext combat = resolveProjectileContext(e);
    if (combat == null) {
      return;
    }

    Player p = combat.attacker();
    Location a = e.getEntity().getLocation().clone();
    Location b = p.getLocation().clone();
    a.setY(0);
    b.setY(0);
    xp(p, 5);
    double distSq = a.distanceSquared(b);

    if (distSq > 10 && grantOnce(p, "challenge_force_30")) {
      xp(p, getConfig().challengeRewardLongShotReward, "challenge-long-shot");
      FxPresets.learnCelebration(this, p);
    }

    if (distSq > 900) {
      addStat(p, "ranged.force.long-range-hits", 1);
    }
  }

  @EventHandler
  public void on(ProjectileLaunchEvent e) {
    if (RangedHeartseeker.isSeekingProjectile(e.getEntity())) {
      return;
    }
    if (e.getEntity().getShooter() instanceof Player p) {
      int level = getActiveLevel(p);
      if (level > 0) {
        double factor = getLevelPercent(level);
        Vector velocity = e.getEntity().getVelocity().clone().multiply(1 + getSpeed(factor));
        e.getEntity().setVelocity(velocity);
        int critCount = Math.min(12, 4 + (int) Math.round(factor * 6));
        fx(e.getEntity().getLocation(), FxPriority.TRAIL)
            .trail(Particles.CRIT_MAGIC, velocity.getX(), velocity.getY(), velocity.getZ(), 2.0D, critCount)
            .dustBurst(Color.fromRGB(40, 180, 60), 4, 0.15D, 0.9F)
            .chord(Sound.ENTITY_SNOWBALL_THROW, 0.5F + ((float) factor * 0.25F), 0.7F + (float) (factor / 2F),
                Sound.BLOCK_NOTE_BLOCK_HAT, 0.4F, 1.4F + ((float) factor * 0.4F));
      }
    }
  }


  @ConfigDescription("Shoot projectiles further and faster.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Reward Long Shot Reward for the Ranged Force adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeRewardLongShotReward = 2000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Speed Factor for the Ranged Force adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedFactor = 1.135;

    public Config() {
      baseCost = 2;
      costFactor = 0.225;
      maxLevel = 7;
      initialCost = 5;
    }
  }
}
