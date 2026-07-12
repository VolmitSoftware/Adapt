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

package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class SwordsExecutionersEdge extends SimpleAdaptation<SwordsExecutionersEdge.Config> {
  private static final Color EXECUTE_RED = Color.fromRGB(0xB00000);

  public SwordsExecutionersEdge() {
    super("sword-executioners-edge");
    registerConfiguration(Config.class);
    setIcon(Material.STONE_SWORD);
    setInterval(1900);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_swords_execute_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_SWORD)
            .key("challenge_swords_execute_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_swords_execute_200", "swords.executioners-edge.executions", 200, 400);
    registerMilestone("challenge_swords_execute_2500", "swords.executioners-edge.executions", 2500, 1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.NETHERITE_AXE)
        .key("challenge_swords_execute_5in10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getBonusDamage(level), 0), 1);
    statLore(v, Form.pc(getThreshold(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.MeleeContext combat = resolveMeleeContext(e, this::isSword);
    if (combat == null) {
      return;
    }

    Player p = combat.attacker();
    LivingEntity target = combat.target();

    double maxHealth = getMaxHealth(target);
    if (maxHealth <= 0) {
      return;
    }

    double hpPercent = Math.max(0, target.getHealth() / maxHealth);
    double threshold = getThreshold(combat.level());
    if (hpPercent > threshold) {
      return;
    }

    double multiplier = 1D + getBonusDamage(combat.level());
    e.setDamage(e.getDamage() * multiplier);
    boolean lethal = e.getFinalDamage() >= target.getHealth();
    Location center = target.getLocation().add(0, 1, 0);
    fx(center, FxPriority.COMBAT)
        .particle(Particle.SWEEP_ATTACK, 1, 0, 0, 0, 0, 0)
        .particle(Particle.CRIT, 12, 0, 0, 0, 0.3D, 0.1D)
        .particle(Particle.DAMAGE_INDICATOR, 4, 0, 0, 0, 0.1D, 0.05D)
        .dustBurst(EXECUTE_RED, 6, 0.35D, 1.1F)
        .chord(Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F, 1.4F, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1F, 0.7F, Sound.BLOCK_ANVIL_LAND, 0.35F, 1.6F);
    if (lethal) {
      timeline(target.getLocation())
          .duration(4)
          .priority(FxPriority.COMBAT)
          .cullRadius(24D)
          .frame((fx, tick, progress) -> {
            fx.ring(Particle.SOUL, 1.4D - (1.1D * progress), 12, 0.2D);
            if (tick == 0) {
              fx.sound(Sound.ITEM_TRIDENT_RETURN, 0.5F, 0.6F);
            }
          })
          .start();
    }
    xp(p, e.getDamage() * getConfig().xpPerBuffedDamage);
    addStat(p, "swords.executioners-edge.executions", 1);

    long now = System.currentTimeMillis();
    long windowStart = getStorageLong(p, "executeWindowStart", 0L);
    int windowCount = getStorageInt(p, "executeWindowCount", 0);
    if (now - windowStart > 10000L) {
      windowStart = now;
      windowCount = 1;
    } else {
      windowCount++;
    }
    setStorage(p, "executeWindowStart", windowStart);
    setStorage(p, "executeWindowCount", windowCount);
    if (windowCount >= 5 && grantOnce(p, "challenge_swords_execute_5in10")) {
      fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
          .burst(Particles.TOTEM, 10, 0.4D)
          .particle(Particle.FLASH, 1, 0, 0, 0, 0, 0)
          .sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1F);
    }
  }

  private double getMaxHealth(LivingEntity entity) {
    AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
    return attr == null ? entity.getHealth() : attr.getValue();
  }

  private double getBonusDamage(int level) {
    return getConfig().bonusDamageBase + (getLevelPercent(level) * getConfig().bonusDamageFactor);
  }

  private double getThreshold(int level) {
    return Math.min(getConfig().maxThreshold, getConfig().thresholdBase + (getLevelPercent(level) * getConfig().thresholdFactor));
  }


  @ConfigDescription("Sword strikes deal bonus damage to low-health targets.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Damage Base for the Swords Executioners Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDamageBase = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Damage Factor for the Swords Executioners Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDamageFactor = 0.42;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Threshold Base for the Swords Executioners Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double thresholdBase = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Threshold Factor for the Swords Executioners Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double thresholdFactor = 0.33;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Threshold for the Swords Executioners Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxThreshold = 0.65;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Buffed Damage for the Swords Executioners Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBuffedDamage = 1.9;

    public Config() {
      baseCost = 3;
      costFactor = 0.65;
      maxLevel = 6;
      initialCost = 4;
    }
  }
}
