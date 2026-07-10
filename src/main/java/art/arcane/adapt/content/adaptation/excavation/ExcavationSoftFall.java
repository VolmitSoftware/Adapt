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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

public class ExcavationSoftFall extends SimpleAdaptation<ExcavationSoftFall.Config> {
  public ExcavationSoftFall() {
    super("excavation-soft-fall");
    registerConfiguration(Config.class);
    setIcon(Material.SAND);
    setInterval(3530);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SAND)
        .key("challenge_excavation_softfall_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_excavation_softfall_1k", "excavation.soft-fall.damage-prevented", 1000, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getReduction(level), 0), 1);
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
      boolean fullNegate = false;
      if (e.getDamage() <= 0.01) {
        e.setCancelled(true);
        fullNegate = true;
      }

      Block soft = isSoftGround(feet.getType()) ? feet : feet.getRelative(BlockFace.DOWN);
      BlockData groundData = soft.getBlockData();
      Location at = p.getLocation();
      int puff = (int) Math.min(20.0D, 4.0D + prevented);
      float pitch = (float) Math.max(0.5D, 0.9D - (prevented * 0.02D));
      fx(at, FxPriority.TRANSITION)
          .particle(Particle.CLOUD, puff, 0, 0.1D, 0, 0.3D, 0.02D)
          .particle(Particles.BLOCK_CRACK, Math.min(12, 3 + (int) prevented), 0, 0.1D, 0, 0.3D, 0.05D, groundData)
          .chord(Sound.BLOCK_ROOTED_DIRT_BREAK, 0.6f, pitch, Sound.BLOCK_SAND_BREAK, 0.4f, 0.8f);

      if (fullNegate) {
        cushionRing(at);
      }

      addStat(p, "excavation.soft-fall.damage-prevented", prevented);
      xp(p, prevented * getConfig().xpPerDamagePrevented);
    });
  }

  private void cushionRing(Location center) {
    timeline(center)
        .duration(4)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20)
        .frame((fx, tick, progress) -> {
          fx.ring(Particle.CLOUD, 0.3D + (1.2D * progress), 12, 0.1D);
          if (tick == 0) {
            fx.sound(Sound.BLOCK_WOOL_FALL, 0.5f, 1.2f);
          }
        })
        .start();
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


  @ConfigDescription("Landing on soft diggable ground reduces fall damage, up to full negation.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reduction Base for the Excavation Soft Fall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reduction Factor for the Excavation Soft Fall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionFactor = 0.85;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Reduction for the Excavation Soft Fall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxReduction = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Damage Prevented for the Excavation Soft Fall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerDamagePrevented = 3.0;

    public Config() {
      baseCost = 3;
      costFactor = 0.55;
      initialCost = 3;
    }
  }
}
