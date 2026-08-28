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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

public class AgilityCatReflexes extends SimpleAdaptation<AgilityCatReflexes.Config> {
  public AgilityCatReflexes() {
    super("agility-cat-reflexes");
    registerConfiguration(Config.class);
    setIcon(Material.RABBIT_HIDE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.RABBIT_HIDE)
        .key("challenge_agility_cat_reflexes_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.PHANTOM_MEMBRANE)
            .key("challenge_agility_cat_reflexes_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_agility_cat_reflexes_100", "agility.cat-reflexes.dodges", 100, 300);
    registerMilestone("challenge_agility_cat_reflexes_1k", "agility.cat-reflexes.dodges", 1000, 1200);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getDodgeChance(level), 0), 1);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player p) || !(e.getDamager() instanceof Projectile projectile)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (!p.isSprinting()) {
        return;
      }

      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      if (!M.r(getDodgeChance(level))) {
        return;
      }

      e.setCancelled(true);

      Vector look = p.getLocation().getDirection().clone().setY(0);
      if (look.lengthSquared() > 1.0E-4D) {
        look.normalize();
        Vector side = new Vector(-look.getZ(), 0, look.getX()).multiply(M.r(0.5D) ? 0.24D : -0.24D);
        p.setVelocity(p.getVelocity().add(side));
      }

      Location at = projectile.getLocation();
      fx(at, FxPriority.COMBAT)
          .burst(Particle.CRIT, 6, 0.2D)
          .chord(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5F, 1.7F, Sound.ENTITY_ARROW_HIT_PLAYER, 0.3F, 1.9F);

      addStat(p, "agility.cat-reflexes.dodges", 1);
      xp(p, getConfig().xpPerDodge);
    });
  }

  private double getDodgeChance(int level) {
    return Math.min(getConfig().maxDodgeChance,
        getConfig().dodgeChanceBase + (getLevelPercent(level) * getConfig().dodgeChanceFactor));
  }

  @ConfigDescription("A chance while sprinting to dodge incoming projectiles entirely.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base projectile dodge chance while sprinting before level scaling.", impact = "Higher values dodge more arrows even at low level.")
    double dodgeChanceBase = 0.08;
    @ConfigDoc(value = "Additional projectile dodge chance granted at max level.", impact = "Higher values widen the dodge-chance gain from leveling.")
    double dodgeChanceFactor = 0.3;
    @ConfigDoc(value = "Hard cap on projectile dodge chance regardless of level.", impact = "Higher values allow a higher maximum dodge rate.")
    double maxDodgeChance = 0.35;
    @ConfigDoc(value = "Experience granted per dodged projectile.", impact = "Higher values reward dodging with more skill xp.")
    double xpPerDodge = 4;

    public Config() {
      baseCost = 3;
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
