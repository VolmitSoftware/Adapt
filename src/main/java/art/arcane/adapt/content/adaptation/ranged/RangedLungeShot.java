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
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.util.Vector;

public class RangedLungeShot extends SimpleAdaptation<RangedLungeShot.Config> {
  public RangedLungeShot() {
    super("ranged-lunge-shot");
    registerConfiguration(Config.class);
    setIcon(Material.RABBIT_HIDE);
    setInterval(4859);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ARROW)
        .key("challenge_ranged_lunge_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.FEATHER)
            .key("challenge_ranged_lunge_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_ranged_lunge_200", "ranged.lunge-shot.lunges", 200, 300);
    registerMilestone("challenge_ranged_lunge_2500", "ranged.lunge-shot.lunges", 2500, 1000);
  }

  private double getSpeed(double factor) {
    return (factor * getConfig().factor);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getSpeed(getLevelPercent(level)), 0), 1);
  }

  @EventHandler
  public void on(ProjectileLaunchEvent e) {
    if (e.getEntity().getShooter() instanceof Player p) {
      if (e.getEntity() instanceof AbstractArrow a) {
        if (hasActiveAdaptation(p)) {
          if (!p.isOnGround()) {
            Vector velocity = p.getPlayer().getLocation().getDirection().normalize().multiply(getSpeed(getLevelPercent(p)));
            p.setVelocity(p.getVelocity().subtract(velocity));
            addStat(p, "ranged.lunge-shot.lunges", 1);
            fx(p.getLocation().add(0, 1, 0), FxPriority.TRAIL)
                .trail(Particle.CLOUD, velocity.getX(), velocity.getY(), velocity.getZ(), 1.5D, 8)
                .dustRing(Color.fromRGB(200, 200, 210), 0.6D, 10, 1.0F)
                .chord(Sound.ITEM_ARMOR_EQUIP_TURTLE, 1F, 0.75F, Sound.ITEM_CROSSBOW_SHOOT, 1F, 1.95F)
                .sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5F, 0.9F);
          }
        }
      }
    }
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("While falling, firing arrows launches you in a random direction.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Factor for the Ranged Lunge Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double factor = 0.935;

    public Config() {
      baseCost = 3;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 8;
    }
  }
}
