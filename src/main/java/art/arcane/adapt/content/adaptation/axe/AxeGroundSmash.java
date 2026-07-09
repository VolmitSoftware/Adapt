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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.Impulse;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class AxeGroundSmash extends SimpleAdaptation<AxeGroundSmash.Config> {
  public AxeGroundSmash() {
    super("axe-ground-smash");
    registerConfiguration(Config.class);
    setIcon(Material.NETHERITE_AXE);
    setInterval(4333);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_AXE)
        .key("challenge_axe_ground_smash_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.NETHERITE_AXE)
        .key("challenge_axe_ground_smash_5")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_axe_ground_smash_500", "axe.ground-smash.mobs-hit", 500, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    double f = getLevelPercent(level);
    statLore(v, C.RED, "+ ", Form.f(getFalloffDamage(f), 1) + " - " + Form.f(getDamage(f), 1), 1);
    statLore(v, C.RED, "+ ", Form.f(getRadius(f), 1), 2);
    statLore(v, C.RED, "+ ", Form.pc(getForce(f), 0), 3);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTime(getLevelPercent(level)) * 50D, 1), 4);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.MeleeContext combat = resolveMeleeContext(e, this::isAxe);
    if (combat == null) {
      return;
    }

    Player p = combat.attacker();
    if (!p.isSneaking()) {
      return;
    }

    double f = getLevelPercent(combat.level());

    p.setCooldown(combat.mainHand().getType(), getCooldownTime(f));
    double radius = getRadius(f);
    int[] mobsHit = {0};
    new Impulse(radius)
        .damage(getDamage(f), getFalloffDamage(f))
        .force(getForce(f))
        .filter((Entity nearby) -> {
          if (nearby == p || !canDamageTarget(p, nearby)) {
            return false;
          }
          if (nearby instanceof LivingEntity) {
            mobsHit[0]++;
          }
          return true;
        })
        .punch(e.getEntity().getLocation());
    if (mobsHit[0] > 0) {
      addStat(p, "axe.ground-smash.mobs-hit", mobsHit[0]);
      if (mobsHit[0] >= 5) {
        grantOnce(p, "challenge_axe_ground_smash_5");
      }
    }
    Location center = e.getEntity().getLocation();
    int points = (int) Math.min(24.0D, Math.max(8.0D, radius * 2.0D));
    timeline(center)
        .duration(6)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          double r = 0.4D + ((radius - 0.4D) * progress);
          fx.ring(Particles.CRIT_MAGIC, r, points, 0.2D);
          fx.dustRing(r, points, 1.1F);
          if (tick == 0) {
            fx.burst(Particles.SMOKE, 6, 0.35D);
            fx.chord(Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.6F, 0.4F, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.5F, 0.1F, Sound.ENTITY_TURTLE_EGG_CRACK, 1.0F, 0.4F);
          }
        })
        .start();
  }


  public int getCooldownTime(double factor) {
    return (int) (((1D - factor) * getConfig().cooldownTicksInverseLevelMultiplier) + getConfig().cooldownTicksBase);
  }

  public double getRadius(double factor) {
    return getConfig().radiusLevelFactorMultiplier * factor;
  }

  public double getDamage(double factor) {
    return getConfig().damageLevelFactorMultiplier * factor;
  }

  public double getForce(double factor) {
    return (getConfig().forceFactorMultiplier * factor) + getConfig().forceBase;
  }

  public double getFalloffDamage(double factor) {
    return getConfig().falloffFactor * factor;
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Jump then crouch to smash all nearby enemies with your axe.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Falloff Factor for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double falloffFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Level Factor Multiplier for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusLevelFactorMultiplier = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Level Factor Multiplier for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageLevelFactorMultiplier = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Force Factor Multiplier for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double forceFactorMultiplier = 1.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Force Base for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double forceBase = 0.27;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Inverse Level Multiplier for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksInverseLevelMultiplier = 225;

    public Config() {
      baseCost = 6;
      costFactor = 0.75;
      initialCost = 8;
    }
  }
}
