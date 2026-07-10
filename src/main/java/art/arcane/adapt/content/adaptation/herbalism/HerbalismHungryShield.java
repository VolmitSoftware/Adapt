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

package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

public class HerbalismHungryShield extends SimpleAdaptation<HerbalismHungryShield.Config> {
  private final Cooldowns shieldBreakCooldown = cooldowns();
  private final Cooldowns absorbFxCooldown = cooldowns();

  public HerbalismHungryShield() {
    super("herbalism-hungry-shield");
    registerConfiguration(Config.class);
    setIcon(Material.APPLE);
    setInterval(875);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BREAD)
        .key("challenge_herbalism_shield_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.GOLDEN_APPLE)
            .key("challenge_herbalism_shield_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_herbalism_shield_500", "herbalism.hungry-shield.damage-absorbed", 500, 400);
    registerMilestone("challenge_herbalism_shield_5k", "herbalism.hungry-shield.damage-absorbed", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.pc(getEffectiveness(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("herbalism.hungry_shield.lore1"));
  }



  private double getEffectiveness(double factor) {
    return Math.min(getConfig().maxEffectiveness, factor * factor + getConfig().effectivenessBase);
  }


  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (e.getEntity() instanceof Player p && hasActiveAdaptation(p)) {
      double f = getEffectiveness(getLevelPercent(p));
      double h = e.getDamage() * f;
      double d = e.getDamage() - h;

      if (getPlayer(p).consumeFood(h, 6)) {
        d += h;
        e.setDamage(d);
        addStat(p, "herbalism.hungry-shield.damage-absorbed", (int) Math.ceil(h));
        xp(p, d);

        if (absorbFxCooldown.isReady(p.getUniqueId(), 500L)) {
          absorbFxCooldown.mark(p.getUniqueId());
          int intensity = (int) Math.max(3, Math.min(12, Math.ceil(h)));
          double ringRadius = Math.min(1.6D, 0.7D + (h * 0.08D));
          fx(p.getLocation().add(0, 1, 0), FxPriority.COMBAT)
              .dustRing(Color.fromRGB(210, 140, 40), ringRadius, 16, 1.0F)
              .burst(Particles.CRIT_MAGIC, intensity, 0.5D)
              .chord(Sound.ITEM_SHIELD_BLOCK, 0.5F, 0.9F, Sound.BLOCK_GRASS_BREAK, 0.4F, 0.7F);
        }
      } else if (h > 2 && shieldBreakCooldown.isReady(p.getUniqueId(), 1500L)) {
        shieldBreakCooldown.mark(p.getUniqueId());
        fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
            .burst(Particles.SMOKE, 3, 0.3D)
            .chord(Sound.BLOCK_NOTE_BLOCK_BASS, 0.4F, 0.6F, Sound.ITEM_SHIELD_BLOCK, 0.3F, 0.5F);
      }
    }
  }

  @ConfigDescription("Take damage to your hunger before your health.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Effectiveness Base for the Herbalism Hungry Shield adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double effectivenessBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Effectiveness for the Herbalism Hungry Shield adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxEffectiveness = 0.95;

    public Config() {
      baseCost = 7;
      costFactor = 0.78;
      initialCost = 10;
    }
  }
}
