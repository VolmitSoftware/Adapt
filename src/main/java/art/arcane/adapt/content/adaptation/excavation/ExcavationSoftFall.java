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

package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

public class ExcavationSoftFall extends SimpleAdaptation<ExcavationSoftFall.Config> {
  public ExcavationSoftFall() {
    super("excavation-soft-fall");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("excavation.soft_fall.description"));
    setDisplayName(Localizer.dLocalize("excavation.soft_fall.name"));
    setIcon(Material.SAND);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(3530);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SAND)
        .key("challenge_excavation_softfall_1k")
        .title(Localizer.dLocalize("advancement.challenge_excavation_softfall_1k.title"))
        .description(Localizer.dLocalize("advancement.challenge_excavation_softfall_1k.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_excavation_softfall_1k", "excavation.soft-fall.damage-prevented", 1000, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.pc(getReduction(level), 0) + C.GRAY + " " + Localizer.dLocalize("excavation.soft_fall.lore1"));
    v.addLore(C.GRAY + Localizer.dLocalize("excavation.soft_fall.lore2"));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) {
      return;
    }

    Block feet = p.getLocation().getBlock();
    if (!isSoftGround(feet.getType()) && !isSoftGround(feet.getRelative(BlockFace.DOWN).getType())) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      double reduction = getReduction(level);
      double prevented = e.getDamage() * reduction;
      if (prevented <= 0) {
        return;
      }

      e.setDamage(Math.max(0, e.getDamage() - prevented));
      if (e.getDamage() <= 0.01) {
        e.setCancelled(true);
      }

      if (areParticlesEnabled()) {
        p.spawnParticle(Particle.CLOUD, p.getLocation(), 8, 0.3, 0.1, 0.3, 0.02);
      }

      SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.BLOCK_ROOTED_DIRT_BREAK, 0.6f, 0.7f);
      getPlayer(p).getData().addStat("excavation.soft-fall.damage-prevented", prevented);
      xp(p, prevented * getConfig().xpPerDamagePrevented);
    });
  }

  private boolean isSoftGround(Material type) {
    return switch (type) {
      case DIRT, GRASS_BLOCK, COARSE_DIRT, ROOTED_DIRT, PODZOL, MYCELIUM,
           DIRT_PATH, FARMLAND, SAND, RED_SAND, GRAVEL, CLAY, MUD,
           MUDDY_MANGROVE_ROOTS, SOUL_SAND, SOUL_SOIL, SNOW, SNOW_BLOCK ->
          true;
      default -> false;
    };
  }

  private double getReduction(int level) {
    return Math.min(getConfig().maxReduction, getConfig().reductionBase + (getLevelPercent(level) * getConfig().reductionFactor));
  }

  @Override
  public void onTick() {

  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Landing on soft diggable ground reduces fall damage, up to full negation.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reduction Base for the Excavation Soft Fall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reduction Factor for the Excavation Soft Fall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionFactor = 0.85;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Reduction for the Excavation Soft Fall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxReduction = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Damage Prevented for the Excavation Soft Fall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerDamagePrevented = 3.0;
  }
}
