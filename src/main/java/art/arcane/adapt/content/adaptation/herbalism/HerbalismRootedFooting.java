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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.HerbalismMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class HerbalismRootedFooting extends SimpleAdaptation<HerbalismRootedFooting.Config> {
  private final Cooldowns trampleFx = cooldowns();

  public HerbalismRootedFooting() {
    super("herbalism-rooted-footing");
    registerConfiguration(Config.class);
    setIcon(Material.FARMLAND);
    setInterval(2050);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.FARMLAND)
        .key("challenge_herbalism_rooted_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_herbalism_rooted_500", "herbalism.rooted-footing.farmland-saved", 500, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    double absorb = getFallAbsorb(level);
    statLore(v, Form.pc(absorb, 0), 1);
    statLore(v, C.YELLOW, "* ", getConfig().foodPerDamage, 2);
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(HerbalismMessages.ROOTED_FOOTING_LORE3));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {

    if (e.getAction() != Action.PHYSICAL || e.getClickedBlock() == null || !(e.getPlayer() instanceof Player p)) {
      return;
    }

    if (!hasActiveAdaptation(p)) {
      return;
    }

    if (e.getClickedBlock().getType() == Material.FARMLAND) {
      e.setCancelled(true);
      addStat(p, "herbalism.rooted-footing.farmland-saved", 1);
      if (trampleFx.isReady(p.getUniqueId(), 500L)) {
        trampleFx.mark(p.getUniqueId());
        fx(e.getClickedBlock().getLocation().add(0.5, 1.0, 0.5), FxPriority.AMBIENT)
            .particle(Particle.COMPOSTER, 2, 0, 0, 0, 0.2D, 0.01D)
            .sound(Sound.BLOCK_ROOTED_DIRT_PLACE, 0.3F, 1.2F);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p) || e.getCause() != EntityDamageEvent.DamageCause.FALL) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || !isNatureGround(p)) {
      return;
    }

    double absorbCap = e.getDamage() * getFallAbsorb(level);
    int foodRequired = (int) Math.ceil(absorbCap * getConfig().foodPerDamage);
    if (foodRequired <= 0 || p.getFoodLevel() <= 0) {
      return;
    }

    int usableFood = Math.min(p.getFoodLevel(), foodRequired);
    double absorbed = usableFood / getConfig().foodPerDamage;
    if (absorbed <= 0) {
      return;
    }

    p.setFoodLevel(Math.max(0, p.getFoodLevel() - usableFood));
    e.setDamage(Math.max(0, e.getDamage() - absorbed));

    int count = (int) Math.max(4, Math.min(12, Math.ceil(absorbed)));
    double ringRadius = Math.min(2.2D, 0.9D + (absorbed * 0.12D));
    FxEmitter landing = fx(p.getLocation(), FxPriority.COMBAT)
        .dustRing(Color.fromRGB(120, 180, 90), ringRadius, 16, 1.0F)
        .dome(Particle.COMPOSTER, ringRadius, count)
        .chord(Sound.BLOCK_GRASS_BREAK, 0.6F, 0.8F, Sound.BLOCK_ROOTED_DIRT_PLACE, 0.5F, 1.1F);

    if (e.getDamage() <= 0.01) {
      e.setCancelled(true);
      landing.particle(Particle.HAPPY_VILLAGER, 4, 0, 0.4D, 0, 0.4D, 0.02D)
          .sound(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4F, 1.4F);
    }
  }

  private boolean isNatureGround(Player p) {
    Block under = p.getLocation().clone().add(0, -1, 0).getBlock();
    Material type = under.getType();
    return type == Material.FARMLAND
        || type == Material.GRASS_BLOCK
        || type == Material.MOSS_BLOCK
        || type == Material.MYCELIUM
        || type == Material.DIRT
        || type == Material.ROOTED_DIRT;
  }

  private double getFallAbsorb(int level) {
    return Math.min(getConfig().maxAbsorbPercent, getConfig().absorbBase + (getLevelPercent(level) * getConfig().absorbFactor));
  }


  @ConfigDescription("Protect farmland and convert fall damage into hunger on natural ground.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Absorb Base for the Herbalism Rooted Footing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double absorbBase = 0.28;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Absorb Factor for the Herbalism Rooted Footing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double absorbFactor = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Absorb Percent for the Herbalism Rooted Footing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxAbsorbPercent = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Food Per Damage for the Herbalism Rooted Footing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double foodPerDamage = 1.8;

    public Config() {
      permanent = true;
      baseCost = 3;
      costFactor = 0.65;
      maxLevel = 1;
      initialCost = 3;
    }
  }
}
