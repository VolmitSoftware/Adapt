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
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

public class AxeOrchardist extends SimpleAdaptation<AxeOrchardist.Config> {
  private static final Set<Material> SOIL = EnumSet.of(
      Material.DIRT, Material.GRASS_BLOCK, Material.PODZOL, Material.COARSE_DIRT,
      Material.ROOTED_DIRT, Material.MOSS_BLOCK, Material.MYCELIUM, Material.MUD);

  public AxeOrchardist() {
    super("axe-orchardist");
    registerConfiguration(Config.class);
    setIcon(Material.OAK_SAPLING);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.OAK_SAPLING)
        .key("challenge_axe_orchardist_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_axe_orchardist_500", "axe.orchardist.trees-replanted", 500, 600);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, C.GREEN, "+ ", Form.pc(getReplantChance(level), 0), 1);
    if (getLevelPercent(level) >= 1.0D) {
      v.addLore(C.AQUA + "" + Localizer.dLocalize("axe.orchardist.lore2"));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    if (!isAxe(p.getInventory().getItemInMainHand())) {
      return;
    }

    Block block = e.getBlock();
    Material sapling = saplingFor(block.getType());
    if (sapling == null || !SOIL.contains(block.getRelative(0, -1, 0).getType())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!M.r(getReplantChance(level)) || !canBlockPlace(p, block.getLocation())) {
      return;
    }

    Location plantAt = block.getLocation();
    boolean bonus = getLevelPercent(level) >= 1.0D && M.r(getConfig().bonusSaplingChance);
    J.runAt(plantAt, () -> replantOwned(plantAt, sapling, bonus));
    addStat(p, "axe.orchardist.trees-replanted", 1);
    xp(p, getConfig().xpPerReplant);
  }

  private void replantOwned(Location plantAt, Material sapling, boolean bonus) {
    Block target = plantAt.getBlock();
    if (!target.getType().isAir() || !SOIL.contains(target.getRelative(0, -1, 0).getType())) {
      if (bonus && plantAt.getWorld() != null) {
        plantAt.getWorld().dropItem(plantAt.clone().add(0.5D, 0.5D, 0.5D), new ItemStack(sapling, 1));
      }
      return;
    }

    target.setType(sapling, true);
    if (bonus && plantAt.getWorld() != null) {
      plantAt.getWorld().dropItem(plantAt.clone().add(0.5D, 0.5D, 0.5D), new ItemStack(sapling, 1));
    }

    fx(plantAt.clone().add(0.5D, 0.4D, 0.5D), FxPriority.TRANSITION)
        .burst(Particles.VILLAGER_HAPPY, 6, 0.25D)
        .sound(Sound.ITEM_CROP_PLANT, 0.6F, 1.1F);
  }

  private double getReplantChance(int level) {
    return Math.min(1D, getConfig().replantChanceBase + (getLevelPercent(level) * getConfig().replantChanceFactor));
  }

  static Material saplingFor(Material log) {
    String name = log.name();
    if (!(name.endsWith("_LOG") || name.endsWith("_WOOD"))) {
      return null;
    }
    if (name.contains("CRIMSON") || name.contains("WARPED")) {
      return null;
    }
    if (name.contains("MANGROVE")) {
      return Material.MANGROVE_PROPAGULE;
    }
    if (name.contains("PALE_OAK")) {
      return null;
    }
    if (name.contains("DARK_OAK")) {
      return Material.DARK_OAK_SAPLING;
    }
    if (name.contains("OAK")) {
      return Material.OAK_SAPLING;
    }
    if (name.contains("SPRUCE")) {
      return Material.SPRUCE_SAPLING;
    }
    if (name.contains("BIRCH")) {
      return Material.BIRCH_SAPLING;
    }
    if (name.contains("JUNGLE")) {
      return Material.JUNGLE_SAPLING;
    }
    if (name.contains("ACACIA")) {
      return Material.ACACIA_SAPLING;
    }
    if (name.contains("CHERRY")) {
      return Material.CHERRY_SAPLING;
    }
    return null;
  }

  @ConfigDescription("Felling a tree with an axe automatically replants a matching sapling.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base replant chance before level scaling.", impact = "Higher values replant saplings more often at low levels.")
    double replantChanceBase = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra replant chance granted by leveling, up to a guaranteed replant.", impact = "Higher values reach guaranteed replanting sooner.")
    double replantChanceFactor = 0.65;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance at maximum level to also drop a bonus sapling.", impact = "Higher values make max-level replants yield spare saplings more often.")
    double bonusSaplingChance = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per successful replant.", impact = "Higher values accelerate skill progression from replanting.")
    double xpPerReplant = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.4;
      maxLevel = 4;
      initialCost = 3;
    }
  }
}
