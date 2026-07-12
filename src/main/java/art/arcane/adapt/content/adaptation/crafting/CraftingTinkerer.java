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

package art.arcane.adapt.content.adaptation.crafting;

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
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class CraftingTinkerer extends SimpleAdaptation<CraftingTinkerer.Config> {

  public CraftingTinkerer() {
    super("crafting-tinkerer");
    registerConfiguration(Config.class);
    setIcon(Material.ANVIL);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_PICKAXE)
        .key("challenge_crafting_tinkerer_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_PICKAXE)
            .key("challenge_crafting_tinkerer_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_crafting_tinkerer_50", "crafting.tinkerer.tools-tinkered", 50, 400);
    registerMilestone("challenge_crafting_tinkerer_500", "crafting.tinkerer.tools-tinkered", 500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("crafting.tinkerer.lore1"));
    statLore(v, Form.pc(getPreserveChance(level), 0), 2);
    v.addLore(C.YELLOW + "~ " + C.GRAY + Localizer.dLocalize("crafting.tinkerer.lore3"));
  }

  static double preserveChance(double base, double factor, double levelPercent) {
    return Math.min(1.0, base + (levelPercent * factor));
  }

  private double getPreserveChance(int level) {
    return preserveChance(getConfig().preserveChanceBase, getConfig().preserveChanceFactor, getLevelPercent(level));
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    CraftingInventory inv = e.getInventory();
    ItemStack result = inv.getResult();
    if (result == null || result.getType().isAir() || !(result.getItemMeta() instanceof Damageable)) {
      return;
    }

    List<ItemStack> inputs = repairInputs(inv, result.getType());
    if (inputs == null) {
      return;
    }

    Map<Enchantment, Integer> merged = mergeEnchants(inputs);
    if (merged.isEmpty()) {
      return;
    }

    boolean lossless = level >= getMaxLevel() || ThreadLocalRandom.current().nextDouble() <= getPreserveChance(level);
    if (!lossless && merged.size() > 0) {
      dropRandomEnchant(merged);
    }

    if (merged.isEmpty()) {
      return;
    }

    ItemStack tinkered = result.clone();
    ItemMeta meta = tinkered.getItemMeta();
    for (Map.Entry<Enchantment, Integer> entry : merged.entrySet()) {
      meta.addEnchant(entry.getKey(), entry.getValue(), true);
    }
    tinkered.setItemMeta(meta);
    e.setCurrentItem(tinkered);

    addStat(p, "crafting.tinkerer.tools-tinkered", 1);
    fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
        .particle(Particles.ENCHANTMENT_TABLE, 12, 0, 0.3D, 0, 0.4D, 0.4D)
        .burst(Particles.CRIT_MAGIC, 6, 0.2D)
        .chord(Sound.BLOCK_ANVIL_USE, 0.5F, 1.1F, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5F, lossless ? 1.6F : 1.0F);
  }

  private List<ItemStack> repairInputs(CraftingInventory inv, Material resultType) {
    List<ItemStack> inputs = new ArrayList<>(2);
    for (ItemStack item : inv.getMatrix()) {
      if (item == null || item.getType().isAir()) {
        continue;
      }
      if (item.getType() != resultType || !(item.getItemMeta() instanceof Damageable)) {
        return null;
      }
      inputs.add(item);
      if (inputs.size() > 2) {
        return null;
      }
    }
    return inputs.size() == 2 ? inputs : null;
  }

  private Map<Enchantment, Integer> mergeEnchants(List<ItemStack> inputs) {
    Map<Enchantment, Integer> merged = new HashMap<>();
    for (ItemStack input : inputs) {
      for (Map.Entry<Enchantment, Integer> entry : input.getEnchantments().entrySet()) {
        merged.merge(entry.getKey(), entry.getValue(), Math::max);
      }
    }
    return merged;
  }

  private void dropRandomEnchant(Map<Enchantment, Integer> merged) {
    int target = ThreadLocalRandom.current().nextInt(merged.size());
    int index = 0;
    Enchantment chosen = null;
    for (Enchantment enchantment : merged.keySet()) {
      if (index == target) {
        chosen = enchantment;
        break;
      }
      index++;
    }
    if (chosen != null) {
      merged.remove(chosen);
    }
  }

  @ConfigDescription("Combine two damaged tools of the same type in the grid to keep their best enchantments instead of stripping them.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance to preserve every enchantment at level 1.", impact = "Higher values keep the full enchantment set more often at low levels.")
    double preserveChanceBase = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional preservation chance gained across levels.", impact = "Higher values reach lossless preservation sooner.")
    double preserveChanceFactor = 0.6;

    public Config() {
      baseCost = 4;
      costFactor = 0.4;
      maxLevel = 5;
      initialCost = 5;
    }
  }
}
